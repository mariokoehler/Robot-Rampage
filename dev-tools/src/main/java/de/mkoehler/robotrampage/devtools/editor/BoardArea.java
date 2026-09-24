package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.client.board.BoardGeometry;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.render.BoardActor;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * The board in the editor: drawn by the game's own {@link BoardActor}, so it looks exactly as it will in a game, plus
 * what only an editor needs on top: a faint robot on every start square (showing its seat and facing) and a highlight of
 * the square or side the pointer would change. It turns the mouse into
 * {@link BoardEditor} gestures: the left button places, the right button removes, and dragging continues the gesture.
 *
 * @author Mario Koehler
 */
final class BoardArea extends Group {

    private static final float START_ROBOT_ALPHA = 0.5f;
    private static final float EDGE_THICKNESS = 8f;
    private static final float OUTLINE = 3f;

    private final UiKit ui;
    private final BoardEditor editor;
    private final float tile;
    private final Runnable onEdit;
    private final Drawable squareHighlight;
    private final Drawable edgeHighlight;
    private final Drawable removeHighlight;
    private int shownRevision = -1;
    private Direction pressedSide;
    private Position hover;
    private Direction hoverSide;
    private boolean removing;

    /**
     * Creates the board area.
     *
     * @param ui       the widget kit
     * @param editor   the editor whose draft is shown and changed
     * @param tileSize the size of one square in pixels
     * @param onEdit   called after every gesture step, so the rest of the window can refresh
     */
    BoardArea(UiKit ui, BoardEditor editor, float tileSize, Runnable onEdit) {
        this.ui = ui;
        this.editor = editor;
        this.tile = tileSize;
        this.onEdit = onEdit;
        squareHighlight = ui.solid(new Color(Theme.ACCENT.r, Theme.ACCENT.g, Theme.ACCENT.b, 0.3f));
        edgeHighlight = ui.solid(Theme.ACCENT);
        removeHighlight = ui.solid(new Color(Theme.DANGER.r, Theme.DANGER.g, Theme.DANGER.b, 0.6f));
        setSize(BoardDraft.SIZE * tileSize, BoardDraft.SIZE * tileSize);
        setTouchable(Touchable.enabled);
        addListener(new PointerListener());
    }

    /**
     * Rebuilds the drawing whenever the draft has changed.
     *
     * @param delta the seconds since the last frame
     */
    @Override
    public void act(float delta) {
        super.act(delta);
        if (editor.revision() != shownRevision) {
            rebuild();
        }
    }

    /**
     * Draws the board, then the highlight of what the pointer would change.
     *
     * @param batch       the batch
     * @param parentAlpha the transparency inherited from the parents
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (hover == null) {
            return;
        }
        float left = getX() + hover.x() * tile;
        float bottom = getY() + hover.y() * tile;
        if (editor.tool().onEdge()) {
            Drawable bar = removing ? removeHighlight : edgeHighlight;
            switch (hoverSide) {
                case NORTH -> bar.draw(batch, left, bottom + tile - EDGE_THICKNESS, tile, EDGE_THICKNESS);
                case SOUTH -> bar.draw(batch, left, bottom, tile, EDGE_THICKNESS);
                case EAST -> bar.draw(batch, left + tile - EDGE_THICKNESS, bottom, EDGE_THICKNESS, tile);
                case WEST -> bar.draw(batch, left, bottom, EDGE_THICKNESS, tile);
            }
        } else {
            Drawable fill = removing ? removeHighlight : squareHighlight;
            fill.draw(batch, left, bottom, tile, tile);
            edgeHighlight.draw(batch, left, bottom, tile, OUTLINE);
            edgeHighlight.draw(batch, left, bottom + tile - OUTLINE, tile, OUTLINE);
            edgeHighlight.draw(batch, left, bottom, OUTLINE, tile);
            edgeHighlight.draw(batch, left + tile - OUTLINE, bottom, OUTLINE, tile);
        }
    }

    /**
     * Replaces the drawing with one of the draft as it is now.
     */
    private void rebuild() {
        shownRevision = editor.revision();
        clearChildren();
        BoardDraft draft = editor.draft();
        BoardActor board = new BoardActor(ui, draft.toBoard(), tile);
        board.setTouchable(Touchable.disabled);
        List<RobotPose> robots = new ArrayList<>();
        List<StartSquare> starts = draft.starts();
        for (int seat = 0; seat < starts.size(); seat++) {
            StartSquare start = starts.get(seat);
            robots.add(new RobotPose(seat, start.position().x(), start.position().y(),
                BoardGeometry.rotation(start.facing()), START_ROBOT_ALPHA, 0, true));
        }
        board.setRobots(robots);
        addActor(board);
    }

    /**
     * Finds the square under a point of this actor.
     *
     * @param x the horizontal position, in this actor's coordinates
     * @param y the vertical position, in this actor's coordinates
     * @return the square, or {@code null} if the point is off the board
     */
    private Position squareAt(float x, float y) {
        if (x < 0f || y < 0f || x >= getWidth() || y >= getHeight()) {
            return null;
        }
        return new Position((int) (x / tile), (int) (y / tile));
    }

    /**
     * Finds the side of a square nearest a point in it.
     *
     * @param square the square
     * @param x      the horizontal position, in this actor's coordinates
     * @param y      the vertical position, in this actor's coordinates
     * @return the nearest side
     */
    private Direction sideAt(Position square, float x, float y) {
        return BoardEditor.nearestSide(x / tile - square.x(), y / tile - square.y());
    }

    /**
     * Remembers what the pointer is over, for the highlight.
     *
     * @param x the horizontal position, in this actor's coordinates
     * @param y the vertical position, in this actor's coordinates
     */
    private void hoverAt(float x, float y) {
        hover = squareAt(x, y);
        hoverSide = hover == null ? null : sideAt(hover, x, y);
    }

    /**
     * Turns mouse input over the board into editor gestures.
     */
    private final class PointerListener extends InputListener {

        private boolean pressed;

        /**
         * Starts a gesture with the left (place) or right (remove) button.
         *
         * @param event   the event
         * @param x       the horizontal position
         * @param y       the vertical position
         * @param pointer the pointer
         * @param button  the mouse button
         * @return {@code true} to receive the drag and release
         */
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            Position square = squareAt(x, y);
            if (pressed || square == null || (button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT)) {
                return false;
            }
            getStage().setKeyboardFocus(null);
            pressed = true;
            removing = button == Input.Buttons.RIGHT;
            pressedSide = sideAt(square, x, y);
            editor.press(square, pressedSide, removing);
            hoverAt(x, y);
            onEdit.run();
            return true;
        }

        /**
         * Continues the gesture onto the square under the pointer. The highlight keeps the side the gesture was pressed
         * on, since an edge tool keeps using it for the whole drag.
         *
         * @param event   the event
         * @param x       the horizontal position
         * @param y       the vertical position
         * @param pointer the pointer
         */
        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            Position square = squareAt(x, y);
            if (square != null) {
                editor.drag(square);
                onEdit.run();
            }
            hoverAt(x, y);
            if (hover != null) {
                hoverSide = pressedSide;
            }
        }

        /**
         * Ends the gesture.
         *
         * @param event   the event
         * @param x       the horizontal position
         * @param y       the vertical position
         * @param pointer the pointer
         * @param button  the mouse button
         */
        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            editor.release();
            pressed = false;
            removing = false;
            onEdit.run();
        }

        /**
         * Follows the pointer for the highlight.
         *
         * @param event the event
         * @param x     the horizontal position
         * @param y     the vertical position
         * @return {@code false}, the move is not consumed
         */
        @Override
        public boolean mouseMoved(InputEvent event, float x, float y) {
            hoverAt(x, y);
            return false;
        }

        /**
         * Clears the highlight once the pointer leaves the board.
         *
         * @param event   the event
         * @param x       the horizontal position
         * @param y       the vertical position
         * @param pointer the pointer
         * @param toActor the actor the pointer moved to
         */
        @Override
        public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
            if (!pressed && (toActor == null || !toActor.isDescendantOf(BoardArea.this))) {
                hover = null;
            }
        }
    }
}
