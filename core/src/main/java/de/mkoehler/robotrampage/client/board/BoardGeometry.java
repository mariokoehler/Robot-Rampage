package de.mkoehler.robotrampage.client.board;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Laser;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The geometry behind drawing a board, without any graphics: which way to turn a picture for a direction, where a wall
 * is drawn, and how far a laser beam reaches. The picture is drawn with y pointing up, so square {@code (0, 0)} is at
 * the bottom left and {@link Direction#NORTH} points up, exactly as in the rules.
 *
 * @author Mario Koehler
 */
public final class BoardGeometry {

    /**
     * A stretch of wall on one edge of a square.
     *
     * @param position the square
     * @param side     the side of the square the wall is on
     */
    public record WallSegment(Position position, Direction side) {
    }

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private BoardGeometry() {
    }

    /**
     * Returns the angle that turns a picture of something facing north so that it faces a direction, counted
     * counter-clockwise in degrees as the graphics library does.
     *
     * @param direction the direction to face
     * @return 0 for north, 90 for west, 180 for south and 270 for east
     */
    public static float rotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 0f;
            case WEST -> 90f;
            case SOUTH -> 180f;
            case EAST -> 270f;
        };
    }

    /**
     * Returns the angle that turns a picture drawn for one direction so that it fits another, counter-clockwise in degrees.
     *
     * @param base   the direction the picture was drawn for
     * @param target the direction it should show
     * @return an angle from 0 (inclusive) to 360 (exclusive)
     */
    public static float rotationFrom(Direction base, Direction target) {
        return (rotation(target) - rotation(base) + 360f) % 360f;
    }

    /**
     * Returns every wall of a board once. A wall between two squares is stored for both of them; it is returned for the
     * square whose wall is on its north or east side. A wall on the edge of the board, which has no square on the other side,
     * is returned as it is.
     *
     * @param board the board
     * @return the wall segments, in no particular order
     */
    public static List<WallSegment> wallSegments(Board board) {
        List<WallSegment> segments = new ArrayList<>();
        for (Map.Entry<Position, Set<Direction>> entry : board.walls().entrySet()) {
            for (Direction side : entry.getValue()) {
                boolean drawnFromHere = side == Direction.NORTH || side == Direction.EAST
                    || !board.inBounds(entry.getKey().step(side));
                if (drawnFromHere) {
                    segments.add(new WallSegment(entry.getKey(), side));
                }
            }
        }
        return segments;
    }

    /**
     * Returns the squares a board laser's beam crosses: the square it is mounted on and every square after it in the firing
     * direction, up to and including the square whose far side has a wall, or the last square of the board. Robots are not
     * considered; this is the beam as the empty board shows it.
     *
     * @param board the board
     * @param laser the laser
     * @return the squares in the order the beam crosses them, never empty
     */
    public static List<Position> beamPath(Board board, Laser laser) {
        Direction direction = laser.firingDirection();
        List<Position> path = new ArrayList<>();
        Position current = laser.position();
        while (true) {
            path.add(current);
            if (board.hasWall(current, direction)) {
                break;
            }
            Position next = current.step(direction);
            if (!board.inBounds(next)) {
                break;
            }
            current = next;
        }
        return path;
    }
}
