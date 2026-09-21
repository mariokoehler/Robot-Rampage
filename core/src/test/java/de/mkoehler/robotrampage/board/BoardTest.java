package de.mkoehler.robotrampage.board;

import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Board} and its {@link Board.Builder}, and the {@link AsciiBoard}
 * test helper that all later rules tests depend on for their scenarios.
 *
 * @author Mario Koehler
 */
class BoardTest {

    /**
     * A wall added on one square must be visible from the neighbour's opposite side.
     */
    @Test
    void wallsAreSymmetric() {
        Board board = new Board.Builder(3, 3).wall(new Position(1, 1), Direction.EAST).build();

        assertTrue(board.hasWall(new Position(1, 1), Direction.EAST));
        assertTrue(board.hasWall(new Position(2, 1), Direction.WEST));
        assertFalse(board.hasWall(new Position(1, 1), Direction.WEST));
    }

    /**
     * An outer wall has no neighbour to mirror onto, and must not fail because of that.
     */
    @Test
    void outerWallsNeedNoNeighbour() {
        Board board = new Board.Builder(2, 2).wall(new Position(0, 0), Direction.WEST).build();

        assertTrue(board.hasWall(new Position(0, 0), Direction.WEST));
    }

    /**
     * Lasers and pushers are wall-mounted, so adding one must add the wall as well.
     */
    @Test
    void lasersAndPushersImplyAWall() {
        Board board = new Board.Builder(4, 4)
            .laser(new Position(0, 2), Direction.WEST, 2)
            .pusher(new Position(2, 3), Direction.NORTH, 2, 4)
            .build();

        assertTrue(board.hasWall(new Position(0, 2), Direction.WEST));
        assertEquals(Direction.EAST, board.lasers().get(0).firingDirection());
        assertTrue(board.hasWall(new Position(2, 3), Direction.NORTH));
        assertEquals(Direction.SOUTH, board.pushers().get(0).pushDirection());
        assertTrue(board.pushers().get(0).isActiveIn(4));
        assertFalse(board.pushers().get(0).isActiveIn(3));
    }

    /**
     * Elements placed outside the grid, or duplicated on one square, are programming
     * errors and must be rejected when building.
     */
    @Test
    void builderRejectsInvalidPlacements() {
        Board.Builder builder = new Board.Builder(2, 2);
        assertThrows(IllegalArgumentException.class, () -> builder.belt(new Position(2, 0), Direction.NORTH, false));
        assertThrows(IllegalArgumentException.class, () -> builder.laser(new Position(0, 0), Direction.WEST, 4));
        assertThrows(IllegalArgumentException.class, () -> builder.crusher(new Position(0, 0), 6));
        assertThrows(IllegalArgumentException.class, () -> new Board.Builder(0, 5));

        builder.belt(new Position(0, 0), Direction.NORTH, false);
        assertThrows(IllegalArgumentException.class, () -> builder.belt(new Position(0, 0), Direction.EAST, true));
        builder.feature(new Position(1, 1), SquareFeature.PIT);
        assertThrows(IllegalArgumentException.class, () -> builder.feature(new Position(1, 1), SquareFeature.REPAIR));
    }

    /**
     * A crusher may share its square with a belt, and reports the registers it is active in.
     */
    @Test
    void crusherCanSitOnABelt() {
        Board board = new Board.Builder(2, 1)
            .belt(new Position(0, 0), Direction.EAST, false)
            .crusher(new Position(0, 0), 2, 4)
            .build();

        assertTrue(board.beltAt(new Position(0, 0)).isPresent());
        assertEquals(SquareFeature.CRUSHER, board.featureAt(new Position(0, 0)));
        assertTrue(board.isCrusherActive(new Position(0, 0), 2));
        assertFalse(board.isCrusherActive(new Position(0, 0), 1));
        assertEquals(SquareFeature.NONE, board.featureAt(new Position(1, 0)));
    }

    /**
     * The ASCII helper must put north at the top, so the top text row has the highest {@code y}.
     */
    @Test
    void asciiBoardPutsNorthAtTheTop() {
        Board board = AsciiBoard.board("""
            o . .
            . > 1
            """);

        assertEquals(3, board.width());
        assertEquals(2, board.height());
        assertTrue(board.isPit(new Position(0, 1)));
        assertEquals(Direction.EAST, board.beltAt(new Position(1, 0)).orElseThrow().direction());
        assertEquals(new Position(2, 0), board.flags().get(0));
    }

    /**
     * Vertical bars and dash lines in the picture must become walls on the right edges.
     */
    @Test
    void asciiBoardParsesWalls() {
        Board board = AsciiBoard.board("""
            . | . . .
                  -
            . . . .
            """);

        assertTrue(board.hasWall(new Position(0, 1), Direction.EAST));
        assertTrue(board.hasWall(new Position(1, 1), Direction.WEST));
        assertFalse(board.hasWall(new Position(1, 1), Direction.EAST));
        assertTrue(board.hasWall(new Position(2, 1), Direction.SOUTH));
        assertTrue(board.hasWall(new Position(2, 0), Direction.NORTH));
        assertFalse(board.hasWall(new Position(1, 1), Direction.SOUTH));
    }

    /**
     * Express and normal belts, gears, repair and crushers must all be recognised.
     */
    @Test
    void asciiBoardParsesEveryTerrainCharacter() {
        Board board = AsciiBoard.board("""
            E W N S
            > < ^ v
            c a + x
            """);

        assertTrue(board.beltAt(new Position(0, 2)).orElseThrow().express());
        assertEquals(Direction.WEST, board.beltAt(new Position(1, 1)).orElseThrow().direction());
        assertFalse(board.beltAt(new Position(1, 1)).orElseThrow().express());
        assertEquals(SquareFeature.GEAR_CLOCKWISE, board.featureAt(new Position(0, 0)));
        assertEquals(SquareFeature.GEAR_COUNTERCLOCKWISE, board.featureAt(new Position(1, 0)));
        assertEquals(SquareFeature.REPAIR, board.featureAt(new Position(2, 0)));
        assertTrue(board.isCrusherActive(new Position(3, 0), 5));
    }

    /**
     * Malformed pictures must fail loudly instead of producing a wrong board.
     */
    @Test
    void asciiBoardRejectsMalformedPictures() {
        assertThrows(IllegalArgumentException.class, () -> AsciiBoard.board("? ."));
        assertThrows(IllegalArgumentException.class, () -> AsciiBoard.board(". . .\n. ."));
        assertThrows(IllegalArgumentException.class, () -> AsciiBoard.board(". 2"));
        assertThrows(IllegalArgumentException.class, () -> AsciiBoard.board(". .\n   -\n. ."));
    }

    /**
     * The robot picture must place robots by id at the right squares, and
     * {@link AsciiBoard#assertRobots} must accept the same picture back.
     */
    @Test
    void asciiRobotPictureRoundTrips() {
        var state = AsciiBoard.state("""
            . . .
            . . .
            """, """
            . 1 .
            0 . .
            """);

        assertEquals(new Position(1, 1), state.robot(1).position());
        assertEquals(new Position(0, 0), state.robot(0).position());
        AsciiBoard.assertRobots(state, """
            . 1 .
            0 . .
            """);
    }
}
