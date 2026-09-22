package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.client.board.BoardGeometry;
import de.mkoehler.robotrampage.client.lobby.RobotLook;

import java.util.EnumMap;
import java.util.Map;

/**
 * The four-way picker of the respawn dialog: a tile with the robot on it, and a square button on each side that turns it to
 * face that way. The button of the facing that is currently picked is filled; the others are outlined.
 *
 * @author Mario Koehler
 */
public final class FacingPicker extends Group {

    /** The width and height of the picker, so it can be centred in a layout that would otherwise stretch it. */
    public static final float SIZE = 440f;
    private static final float TILE = 288f;
    private static final float BUTTON = 60f;
    private static final float PICTURE = 80.64f;
    private static final float ICON = 24f;

    private final UiKit ui;
    private final Drawable outline;
    private final Drawable filled;
    private final Image wedge;
    private final Map<Direction, Button> buttons = new EnumMap<>(Direction.class);
    private Direction facing;

    /**
     * Builds the picker.
     *
     * @param ui      the widget kit
     * @param seat    the seat of the robot, which picks its picture
     * @param initial the facing that starts picked
     */
    public FacingPicker(UiKit ui, int seat, Direction initial) {
        setSize(SIZE, SIZE);
        this.ui = ui;
        this.facing = initial;
        this.outline = ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL, 12);
        this.filled = ui.rounded(Theme.ACCENT, Theme.ACCENT, 0, 12);

        put(new Image(ui.image("tiles/floor.png")), (SIZE - TILE) / 2f, (SIZE - TILE) / 2f, TILE, TILE);
        put(new Image(ui.image(RobotLook.picture(seat))), (SIZE - PICTURE) / 2f, (SIZE - PICTURE) / 2f, PICTURE, PICTURE);
        wedge = new Image(ui.image("board/robot-wedge.png"));
        put(wedge, (SIZE - PICTURE) / 2f, (SIZE - PICTURE) / 2f, PICTURE, PICTURE);

        button(Direction.NORTH, SIZE / 2f - BUTTON / 2f, SIZE - BUTTON);
        button(Direction.SOUTH, SIZE / 2f - BUTTON / 2f, 0f);
        button(Direction.WEST, 0f, SIZE / 2f - BUTTON / 2f);
        button(Direction.EAST, SIZE - BUTTON, SIZE / 2f - BUTTON / 2f);
        refresh();
    }

    /**
     * Returns the facing that is picked.
     *
     * @return the facing
     */
    public Direction facing() {
        return facing;
    }

    /**
     * Builds one of the four direction buttons.
     *
     * @param direction the facing it picks
     * @param left      its left edge
     * @param bottom    its bottom edge
     */
    private void button(Direction direction, float left, float bottom) {
        Button button = new Button(outline);
        Image icon = new Image(ui.image("icons/chevron-left.png"));
        icon.setRotation(BoardGeometry.rotationFrom(Direction.WEST, direction));
        icon.setOrigin(ICON / 2f, ICON / 2f);
        button.add(icon).size(ICON);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                facing = direction;
                refresh();
            }
        });
        buttons.put(direction, button);
        button.setBounds(left, bottom, BUTTON, BUTTON);
        addActor(button);
    }

    /**
     * Highlights the picked button and turns the wedge to match.
     */
    private void refresh() {
        buttons.forEach((direction, button) -> {
            Drawable look = direction == facing ? filled : outline;
            button.setStyle(new Button.ButtonStyle(look, look, look));
        });
        wedge.setRotation(BoardGeometry.rotation(facing));
        wedge.setOrigin(PICTURE / 2f, PICTURE / 2f);
    }

    /**
     * Places a widget at a position and size, bottom-left origin, local to the picker.
     *
     * @param actor  the widget
     * @param left   the left edge
     * @param bottom the bottom edge
     * @param width  the width
     * @param height the height
     */
    private void put(Image actor, float left, float bottom, float width, float height) {
        actor.setBounds(left, bottom, width, height);
        addActor(actor);
    }
}
