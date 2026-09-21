package de.mkoehler.robotrampage.client.board;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Laser;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.board.BoardGeometry.WallSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the drawing geometry of {@link BoardGeometry} and {@link RobotPose}.
 *
 * @author Mario Koehler
 */
class BoardGeometryTest {

    private static Position at(int x, int y) {
        return new Position(x, y);
    }

    /**
     * Pictures of things facing north are turned counter-clockwise: west is a quarter turn, east three quarters.
     */
    @Test
    void rotationCountsCounterClockwiseFromNorth() {
        assertEquals(0f, BoardGeometry.rotation(Direction.NORTH));
        assertEquals(90f, BoardGeometry.rotation(Direction.WEST));
        assertEquals(180f, BoardGeometry.rotation(Direction.SOUTH));
        assertEquals(270f, BoardGeometry.rotation(Direction.EAST));
    }

    /**
     * A picture drawn for east, such as a belt, needs no turn to face east, a quarter turn to face north and three
     * quarters to face south.
     */
    @Test
    void rotationFromABaseDirection() {
        assertEquals(0f, BoardGeometry.rotationFrom(Direction.EAST, Direction.EAST));
        assertEquals(90f, BoardGeometry.rotationFrom(Direction.EAST, Direction.NORTH));
        assertEquals(180f, BoardGeometry.rotationFrom(Direction.EAST, Direction.WEST));
        assertEquals(270f, BoardGeometry.rotationFrom(Direction.EAST, Direction.SOUTH));
        assertEquals(90f, BoardGeometry.rotationFrom(Direction.NORTH, Direction.WEST));
    }

    /**
     * A robot standing still has whole-number coordinates and the angle of its heading.
     */
    @Test
    void aRobotAtRestHasWholeCoordinates() {
        RobotPose pose = RobotPose.at(3, at(4, 7), Direction.WEST);

        assertEquals(new RobotPose(3, 4f, 7f, 90f), pose);
    }

    /**
     * A wall between two squares is returned once, however it is stored, and walls on the north and east sides of the
     * board's edge squares are returned as they are.
     */
    @Test
    void wallsAreReturnedOnce() {
        Board board = new Board.Builder(3, 2)
            .wall(at(0, 0), Direction.EAST)
            .wall(at(2, 1), Direction.EAST)
            .wall(at(1, 0), Direction.SOUTH)
            .build();

        List<WallSegment> segments = BoardGeometry.wallSegments(board);

        assertEquals(3, segments.size());
        assertTrue(segments.contains(new WallSegment(at(0, 0), Direction.EAST)));
        assertTrue(segments.contains(new WallSegment(at(2, 1), Direction.EAST)));
        assertTrue(segments.contains(new WallSegment(at(1, 0), Direction.SOUTH)));
    }

    /**
     * A wall drawn for the west side of a square is the same wall as the east side of its neighbour, and is not returned
     * twice.
     */
    @Test
    void aWestWallIsTheNeighboursEastWall() {
        Board board = new Board.Builder(3, 1).wall(at(2, 0), Direction.WEST).build();

        List<WallSegment> segments = BoardGeometry.wallSegments(board);

        assertEquals(List.of(new WallSegment(at(1, 0), Direction.EAST)), segments);
    }

    private static Board belts(int width, int height, Object... belts) {
        Board.Builder builder = new Board.Builder(width, height);
        for (int i = 0; i < belts.length; i += 3) {
            builder.belt((Position) belts[i], (Direction) belts[i + 1], (Boolean) belts[i + 2]);
        }
        return builder.build();
    }

    /**
     * A belt with nothing around it, and a belt fed only from behind, are plain belts.
     */
    @Test
    void aBeltInALineIsStraight() {
        Board board = belts(3, 1, at(0, 0), Direction.EAST, false, at(1, 0), Direction.EAST, false);

        assertEquals(BoardGeometry.BeltPiece.STRAIGHT, BoardGeometry.beltPiece(board, at(0, 0)));
        assertEquals(BoardGeometry.BeltPiece.STRAIGHT, BoardGeometry.beltPiece(board, at(1, 0)));
    }

    /**
     * A belt fed only from one side turns: from the left or from the right of its direction.
     */
    @Test
    void aBeltFedFromOneSideIsACorner() {
        Board left = belts(3, 3, at(1, 1), Direction.NORTH, false, at(0, 1), Direction.EAST, false);
        Board right = belts(3, 3, at(1, 1), Direction.NORTH, false, at(2, 1), Direction.WEST, false);
        Board turned = belts(3, 3, at(1, 1), Direction.EAST, false, at(1, 2), Direction.SOUTH, false);

        assertEquals(BoardGeometry.BeltPiece.CORNER_LEFT, BoardGeometry.beltPiece(left, at(1, 1)));
        assertEquals(BoardGeometry.BeltPiece.CORNER_RIGHT, BoardGeometry.beltPiece(right, at(1, 1)));
        assertEquals(BoardGeometry.BeltPiece.CORNER_LEFT, BoardGeometry.beltPiece(turned, at(1, 1)));
    }

    /**
     * A belt fed from behind and from one side is a join on that side.
     */
    @Test
    void aBeltFedFromBehindAndOneSideIsAJoin() {
        Board left = belts(3, 3, at(1, 1), Direction.NORTH, false, at(1, 0), Direction.NORTH, false,
            at(0, 1), Direction.EAST, false);
        Board right = belts(3, 3, at(1, 1), Direction.NORTH, false, at(1, 0), Direction.NORTH, false,
            at(2, 1), Direction.WEST, false);

        assertEquals(BoardGeometry.BeltPiece.JOIN_LEFT, BoardGeometry.beltPiece(left, at(1, 1)));
        assertEquals(BoardGeometry.BeltPiece.JOIN_RIGHT, BoardGeometry.beltPiece(right, at(1, 1)));
    }

    /**
     * Feeders from both sides make a T without a belt behind and an X with one.
     */
    @Test
    void feedersFromBothSidesMakeATOrAnX() {
        Board t = belts(3, 3, at(1, 1), Direction.NORTH, false, at(0, 1), Direction.EAST, false,
            at(2, 1), Direction.WEST, true);
        Board x = belts(3, 3, at(1, 1), Direction.NORTH, true, at(0, 1), Direction.EAST, false,
            at(2, 1), Direction.WEST, false, at(1, 0), Direction.NORTH, false);

        assertEquals(BoardGeometry.BeltPiece.T_JUNCTION, BoardGeometry.beltPiece(t, at(1, 1)));
        assertEquals(BoardGeometry.BeltPiece.X_JUNCTION, BoardGeometry.beltPiece(x, at(1, 1)));
    }

    /**
     * Belts that point away do not feed a square, and a square without a belt has no piece.
     */
    @Test
    void beltsPointingAwayDoNotFeed() {
        Board board = belts(3, 3, at(1, 1), Direction.NORTH, false, at(0, 1), Direction.WEST, false,
            at(1, 0), Direction.SOUTH, false);

        assertEquals(BoardGeometry.BeltPiece.STRAIGHT, BoardGeometry.beltPiece(board, at(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.beltPiece(board, at(2, 2)));
    }

    /**
     * A laser's beam starts on its own square and runs to the edge of the board when nothing is in the way.
     */
    @Test
    void aBeamRunsToTheEdgeOfTheBoard() {
        Board board = new Board.Builder(4, 2).laser(at(0, 1), Direction.WEST, 1).build();

        List<Position> path = BoardGeometry.beamPath(board, board.lasers().get(0));

        assertEquals(List.of(at(0, 1), at(1, 1), at(2, 1), at(3, 1)), path);
    }

    /**
     * A wall stops the beam on the square before it.
     */
    @Test
    void aWallStopsTheBeam() {
        Board board = new Board.Builder(5, 1)
            .laser(at(0, 0), Direction.WEST, 2)
            .wall(at(2, 0), Direction.EAST)
            .build();

        List<Position> path = BoardGeometry.beamPath(board, board.lasers().get(0));

        assertEquals(List.of(at(0, 0), at(1, 0), at(2, 0)), path);
    }

    /**
     * A beam fired south from a laser on the north side of a square runs down the column.
     */
    @Test
    void aBeamCanRunSouth() {
        Laser laser = new Laser(at(1, 2), Direction.NORTH, 1);
        Board board = new Board.Builder(3, 3).laser(laser.position(), laser.side(), 1).build();

        assertEquals(List.of(at(1, 2), at(1, 1), at(1, 0)), BoardGeometry.beamPath(board, laser));
    }
}
