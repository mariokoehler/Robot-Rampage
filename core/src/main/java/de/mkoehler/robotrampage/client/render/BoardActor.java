package de.mkoehler.robotrampage.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable;
import com.badlogic.gdx.utils.Align;
import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Laser;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.Pusher;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.client.board.BoardGeometry;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Draws a {@link Board} and the robots on it, in the look of the mockups. The board sits at the actor's position, square
 * {@code (0, 0)} at the bottom left, and every square is {@code tileSize} pixels wide, so an actor of any size fits any
 * layout; the numbers in the mockups are for a 50 px square.
 * <p>
 * Everything is drawn from the pictures in {@code assets/tiles}, {@code assets/board} and {@code assets/robots}, turned to
 * face the right way, plus the beams of the board lasers, which are drawn as bars, and the numbers of flags, start squares
 * and pushers, which are drawn with the game fonts. The layers, from the bottom: the ground, the start squares, crushers,
 * pushers and flags, the lasers, the walls, and the robots.
 *
 * @author Mario Koehler
 */
public final class BoardActor extends Actor {

    private static final float PICTURE = 128f;
    private static final float WALL_INSET = 7f / PICTURE;
    private static final float EMITTER_LENGTH = 0.3f;
    private static final float BEAM_END_GAP = 0.12f;
    private static final float BEAM_WIDTH = 0.1f;
    private static final float BEAM_CORE_WIDTH = 0.03f;
    private static final float BEAM_SPACING = 0.14f;

    private final UiKit ui;
    private final Board board;
    private final float tile;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tint = new Color();
    private final TextureRegionDrawable floor;
    private final TextureRegionDrawable belt;
    private final TextureRegionDrawable expressBelt;
    private final TextureRegionDrawable gearClockwise;
    private final TextureRegionDrawable gearCounterclockwise;
    private final TextureRegionDrawable pit;
    private final TextureRegionDrawable repairSite;
    private final TextureRegionDrawable startSquare;
    private final TextureRegionDrawable crusher;
    private final TextureRegionDrawable pusher;
    private final TextureRegionDrawable flag;
    private final TextureRegionDrawable emitter;
    private final TextureRegionDrawable wall;
    private final TextureRegionDrawable wedge;
    private final TextureRegionDrawable badge;
    private final TransformDrawable beamOuter;
    private final TransformDrawable beamCore;
    private final TextureRegionDrawable[] bodies = new TextureRegionDrawable[RobotLook.COUNT];
    private List<RobotPose> robots = List.of();

    /**
     * Creates the actor for a board, with no robots.
     *
     * @param ui       the widget kit, which loads the pictures and holds the fonts
     * @param board    the board to draw
     * @param tileSize the width and height of one square in pixels
     */
    public BoardActor(UiKit ui, Board board, float tileSize) {
        this.ui = ui;
        this.board = board;
        this.tile = tileSize;
        floor = ui.image("tiles/floor.png");
        belt = ui.image("tiles/belt.png");
        expressBelt = ui.image("tiles/belt-express.png");
        gearClockwise = ui.image("tiles/gear-clockwise.png");
        gearCounterclockwise = ui.image("tiles/gear-counterclockwise.png");
        pit = ui.image("tiles/pit.png");
        repairSite = ui.image("tiles/repair-site.png");
        startSquare = ui.image("board/start-square.png");
        crusher = ui.image("board/crusher.png");
        pusher = ui.image("board/pusher.png");
        flag = ui.image("board/flag.png");
        emitter = ui.image("board/laser-emitter.png");
        wall = ui.image("board/wall.png");
        wedge = ui.image("board/robot-wedge.png");
        badge = ui.image("board/robot-badge.png");
        beamOuter = (TransformDrawable) ui.solid(Theme.DANGER);
        beamCore = (TransformDrawable) ui.solid(Theme.SURFACE_RAISED);
        for (int seat = 0; seat < bodies.length; seat++) {
            bodies[seat] = ui.image(RobotLook.picture(seat));
        }
        setSize(board.width() * tileSize, board.height() * tileSize);
    }

    /**
     * Sets the robots to draw.
     *
     * @param poses where and how each robot stands
     */
    public void setRobots(List<RobotPose> poses) {
        this.robots = List.copyOf(poses);
    }

    /**
     * Draws the board and the robots.
     *
     * @param batch       the batch
     * @param parentAlpha the transparency inherited from the parents
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        batch.setColor(1f, 1f, 1f, getColor().a * parentAlpha);
        drawGround(batch);
        drawStartSquares(batch);
        drawCrushers(batch);
        drawPushers(batch);
        drawFlags(batch);
        drawLasers(batch);
        drawWalls(batch);
        drawRobots(batch);
    }

    /**
     * Draws the ground of every square: floor, belt, gear, pit or repair site.
     *
     * @param batch the batch
     */
    private void drawGround(Batch batch) {
        for (int x = 0; x < board.width(); x++) {
            for (int y = 0; y < board.height(); y++) {
                Position position = new Position(x, y);
                SquareFeature feature = board.featureAt(position);
                Belt onBelt = board.beltAt(position).orElse(null);
                if (feature == SquareFeature.PIT) {
                    square(batch, pit, position, 0f);
                } else if (feature == SquareFeature.GEAR_CLOCKWISE) {
                    square(batch, gearClockwise, position, 0f);
                } else if (feature == SquareFeature.GEAR_COUNTERCLOCKWISE) {
                    square(batch, gearCounterclockwise, position, 0f);
                } else if (feature == SquareFeature.REPAIR) {
                    square(batch, repairSite, position, 0f);
                } else if (onBelt != null) {
                    square(batch, onBelt.express() ? expressBelt : belt, position,
                        BoardGeometry.rotationFrom(Direction.EAST, onBelt.direction()));
                } else {
                    square(batch, floor, position, 0f);
                }
            }
        }
    }

    /**
     * Draws the dashed ring and the number of every start square. The number is the seat, counted from one.
     *
     * @param batch the batch
     */
    private void drawStartSquares(Batch batch) {
        List<StartSquare> starts = board.startSquares();
        for (int seat = 0; seat < starts.size(); seat++) {
            Position position = starts.get(seat).position();
            square(batch, startSquare, position, 0f);
            text(batch, Theme.TextStyle.BUTTON, String.valueOf(seat + 1), center(position, 0.5f, 0.492f), 0.3f,
                Theme.INK_MUTED);
        }
    }

    /**
     * Draws every crusher.
     *
     * @param batch the batch
     */
    private void drawCrushers(Batch batch) {
        for (Position position : board.crusherPositions()) {
            square(batch, crusher, position, 0f);
        }
    }

    /**
     * Draws every pusher on its edge, with the registers it pushes in.
     *
     * @param batch the batch
     */
    private void drawPushers(Batch batch) {
        for (Pusher each : board.pushers()) {
            float rotation = BoardGeometry.rotationFrom(Direction.NORTH, each.side());
            square(batch, pusher, each.position(), rotation);
            String registers = each.registers().stream().sorted().map(String::valueOf).collect(Collectors.joining(" "));
            float[] bar = turned(0.5f, 0.84f, rotation);
            float along = each.side() == Direction.EAST ? 270f : each.side() == Direction.WEST ? 90f : 0f;
            text(batch, Theme.TextStyle.NAME, registers, center(each.position(), bar[0], bar[1]), 0.16f, Color.WHITE,
                along);
        }
    }

    /**
     * Draws every flag with its number, counted from one in the order the flags must be touched.
     *
     * @param batch the batch
     */
    private void drawFlags(Batch batch) {
        List<Position> flags = board.flags();
        for (int i = 0; i < flags.size(); i++) {
            square(batch, flag, flags.get(i), 0f);
            text(batch, Theme.TextStyle.BUTTON, String.valueOf(i + 1), center(flags.get(i), 0.5f, 0.64f), 0.2f, Color.WHITE);
        }
    }

    /**
     * Draws the emitters and beams of the board lasers. A laser with several beams draws them side by side.
     *
     * @param batch the batch
     */
    private void drawLasers(Batch batch) {
        for (Laser laser : board.lasers()) {
            Direction fire = laser.firingDirection();
            float length = BoardGeometry.beamPath(board, laser).size() * tile - (EMITTER_LENGTH + BEAM_END_GAP) * tile;
            float angle = (BoardGeometry.rotation(fire) + 90f) % 360f;
            float centerX = tileX(laser.position()) + tile / 2f;
            float centerY = tileY(laser.position()) + tile / 2f;
            for (int beam = 0; beam < laser.beams(); beam++) {
                float offset = (beam - (laser.beams() - 1) / 2f) * BEAM_SPACING * tile;
                float startX = centerX + fire.dx() * (EMITTER_LENGTH - 0.5f) * tile - fire.dy() * offset;
                float startY = centerY + fire.dy() * (EMITTER_LENGTH - 0.5f) * tile + fire.dx() * offset;
                bar(batch, beamOuter, startX, startY, length, BEAM_WIDTH * tile, angle);
                bar(batch, beamCore, startX, startY, length, BEAM_CORE_WIDTH * tile, angle);
            }
            square(batch, emitter, laser.position(), BoardGeometry.rotationFrom(Direction.WEST, laser.side()));
        }
    }

    /**
     * Draws every wall as a line along the edge between two squares.
     *
     * @param batch the batch
     */
    private void drawWalls(Batch batch) {
        for (BoardGeometry.WallSegment segment : BoardGeometry.wallSegments(board)) {
            float shift = WALL_INSET * tile;
            Direction side = segment.side();
            square(batch, wall, segment.position(), BoardGeometry.rotationFrom(Direction.EAST, side),
                side.dx() * shift, side.dy() * shift);
        }
    }

    /**
     * Draws the robots: the body, the wedge showing where it faces, and the badge with the seat number.
     *
     * @param batch the batch
     */
    private void drawRobots(Batch batch) {
        for (RobotPose pose : robots) {
            float x = getX() + pose.x() * tile;
            float y = getY() + pose.y() * tile;
            bodies[pose.seat()].draw(batch, x, y, tile, tile);
            wedge.draw(batch, x, y, tile / 2f, tile / 2f, tile, tile, 1f, 1f, pose.rotation());
            badge.draw(batch, x, y, tile, tile);
            text(batch, Theme.TextStyle.BUTTON, String.valueOf(pose.seat() + 1),
                new float[] {x + tile * 108.8f / PICTURE, y + tile * (1f - 110.08f / PICTURE)}, 0.17f, Theme.ROBOT_OUTLINE);
        }
    }

    /**
     * Draws a picture over one square, turned about the middle of the square.
     *
     * @param batch    the batch
     * @param picture  the picture
     * @param position the square
     * @param rotation the angle in degrees, counter-clockwise
     */
    private void square(Batch batch, TextureRegionDrawable picture, Position position, float rotation) {
        square(batch, picture, position, rotation, 0f, 0f);
    }

    /**
     * Draws a picture over one square, turned about the middle of the square and then moved.
     *
     * @param batch    the batch
     * @param picture  the picture
     * @param position the square
     * @param rotation the angle in degrees, counter-clockwise
     * @param dx       how far to move it to the right afterwards
     * @param dy       how far to move it up afterwards
     */
    private void square(Batch batch, TextureRegionDrawable picture, Position position, float rotation, float dx,
                        float dy) {
        picture.draw(batch, tileX(position) + dx, tileY(position) + dy, tile / 2f, tile / 2f, tile, tile, 1f, 1f, rotation);
    }

    /**
     * Draws a bar, given by where its left end is, how long and thick it is, and the angle it points in.
     *
     * @param batch     the batch
     * @param paint     the color
     * @param startX    the horizontal position of the middle of the start of the bar
     * @param startY    the vertical position of the middle of the start of the bar
     * @param length    the length
     * @param thickness the thickness
     * @param angle     the angle in degrees, counter-clockwise from pointing right
     */
    private void bar(Batch batch, TransformDrawable paint, float startX, float startY, float length, float thickness,
                     float angle) {
        paint.draw(batch, startX, startY - thickness / 2f, 0f, thickness / 2f, length, thickness, 1f, 1f, angle);
    }

    /**
     * Turns a point of a picture the way the picture is turned.
     *
     * @param x        the horizontal position in the picture, from 0 to 1
     * @param y        the vertical position in the picture, from 0 to 1
     * @param rotation the angle in degrees, counter-clockwise
     * @return the turned point, from 0 to 1 in both directions
     */
    private static float[] turned(float x, float y, float rotation) {
        double radians = Math.toRadians(rotation);
        float relativeX = x - 0.5f;
        float relativeY = y - 0.5f;
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[] {0.5f + relativeX * cos - relativeY * sin, 0.5f + relativeX * sin + relativeY * cos};
    }

    /**
     * Converts a point in a square, given as fractions of the square, to pixels.
     *
     * @param position the square
     * @param fractionX the horizontal fraction, from 0 (left) to 1 (right)
     * @param fractionY the vertical fraction, from 0 (bottom) to 1 (top)
     * @return the pixel position as x and y
     */
    private float[] center(Position position, float fractionX, float fractionY) {
        return new float[] {tileX(position) + fractionX * tile, tileY(position) + fractionY * tile};
    }

    /**
     * Returns the left edge of a square in pixels.
     *
     * @param position the square
     * @return the horizontal pixel position
     */
    private float tileX(Position position) {
        return getX() + position.x() * tile;
    }

    /**
     * Returns the bottom edge of a square in pixels.
     *
     * @param position the square
     * @return the vertical pixel position
     */
    private float tileY(Position position) {
        return getY() + position.y() * tile;
    }

    /**
     * Draws a text centred on a point, in one of the game fonts made smaller or larger to fit.
     *
     * @param batch  the batch
     * @param style  the type style whose font is used
     * @param text   the text
     * @param at     the pixel position of the middle of the text, as x and y
     * @param height the size of the text as a fraction of the square
     * @param color  the color
     */
    private void text(Batch batch, Theme.TextStyle style, String text, float[] at, float height, Color color) {
        text(batch, style, text, at, height, color, 0f);
    }

    /**
     * Draws a text centred on a point and turned about that point, in one of the game fonts made smaller or larger to fit.
     *
     * @param batch    the batch
     * @param style    the type style whose font is used
     * @param text     the text
     * @param at       the pixel position of the middle of the text, as x and y
     * @param height   the size of the text as a fraction of the square
     * @param color    the color
     * @param rotation the angle in degrees, counter-clockwise
     */
    private void text(Batch batch, Theme.TextStyle style, String text, float[] at, float height, Color color,
                      float rotation) {
        Matrix4 saved = new Matrix4(batch.getTransformMatrix());
        if (rotation != 0f) {
            batch.setTransformMatrix(new Matrix4(saved).translate(at[0], at[1], 0f).rotate(0f, 0f, 1f, rotation)
                .translate(-at[0], -at[1], 0f));
        }
        BitmapFont font = ui.fonts().get(style);
        BitmapFont.BitmapFontData data = font.getData();
        float scaleX = data.scaleX;
        float scaleY = data.scaleY;
        float factor = height * tile / style.size();
        data.setScale(scaleX * factor, scaleY * factor);
        tint.set(color.r, color.g, color.b, color.a * getColor().a);
        layout.setText(font, text, tint, 0f, Align.left, false);
        font.draw(batch, layout, at[0] - layout.width / 2f, at[1] + layout.height / 2f);
        data.setScale(scaleX, scaleY);
        if (rotation != 0f) {
            batch.setTransformMatrix(saved);
        }
    }
}
