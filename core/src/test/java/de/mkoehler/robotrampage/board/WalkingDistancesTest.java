package de.mkoehler.robotrampage.board;

import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link WalkingDistances}.
 *
 * @author Mario Koehler
 */
class WalkingDistancesTest {

    /**
     * The walking distance goes around walls and pits, not through them, and a pit itself counts as unreachable.
     */
    @Test
    void distancesGoAroundWallsAndPits() {
        Board board = AsciiBoard.board(". . | 1\n. o .\n. . .");

        int[][] steps = WalkingDistances.to(board, new Position(2, 2));

        assertEquals(0, steps[2][2]);
        assertEquals(6, steps[0][2], "around the wall, down the left, along the bottom and up the right");
        assertEquals(WalkingDistances.UNREACHABLE, steps[1][1], "the pit");
    }

    /**
     * A square walled off completely is unreachable.
     */
    @Test
    void aWalledOffSquareIsUnreachable() {
        Board board = AsciiBoard.board(". | 1 | .\n    -");

        int[][] steps = WalkingDistances.to(board, new Position(1, 0));

        assertEquals(WalkingDistances.UNREACHABLE, steps[0][0]);
        assertEquals(WalkingDistances.UNREACHABLE, steps[2][0]);
    }
}
