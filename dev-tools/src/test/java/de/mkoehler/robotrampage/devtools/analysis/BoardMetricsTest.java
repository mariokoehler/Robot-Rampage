package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BoardMetrics}.
 *
 * @author Mario Koehler
 */
class BoardMetricsTest {

    /**
     * A 6x6 board with two start squares on the bottom row, two flags, a pit, a belt and a laser.
     *
     * @return the board
     */
    private static Board small() {
        return new Board.Builder(6, 6)
            .startSquare(new Position(0, 0), Direction.NORTH)
            .startSquare(new Position(5, 0), Direction.NORTH)
            .flag(new Position(0, 5))
            .flag(new Position(5, 5))
            .feature(new Position(2, 2), SquareFeature.PIT)
            .belt(new Position(3, 3), Direction.EAST, true)
            .laser(new Position(0, 4), Direction.WEST, 1)
            .build();
    }

    /**
     * Each seat's walk is its way to flag 1 plus the flag-to-flag route; the spread says how unequal the seats start.
     */
    @Test
    void seatsRoutesAndSpread() {
        BoardMetrics metrics = BoardMetrics.of(small());

        assertEquals(5, metrics.flagRoute());
        assertEquals(List.of(new BoardMetrics.SeatRoute(0, 5, 10), new BoardMetrics.SeatRoute(1, 10, 15)),
            metrics.seats());
        assertEquals(5, metrics.firstFlagSpread());
        assertTrue(metrics.allReachable());
    }

    /**
     * Features are counted, and a laser's line of fire runs from its emitter to the far edge.
     */
    @Test
    void featuresAndLaserSquares() {
        BoardMetrics metrics = BoardMetrics.of(small());

        assertEquals(1, metrics.pits());
        assertEquals(1, metrics.belts());
        assertEquals(1, metrics.expressBelts());
        assertEquals(1, metrics.lasers());
        assertEquals(6, metrics.laserSquares(), "the whole row the laser fires along");
        assertEquals((1 + 6) / 36.0, metrics.hazardShare(), 1e-9);
    }

    /**
     * A flag nobody can walk to makes the board unreachable.
     */
    @Test
    void aWalledOffFlagIsUnreachable() {
        Board board = new Board.Builder(3, 3)
            .startSquare(new Position(0, 0), Direction.NORTH)
            .flag(new Position(2, 2))
            .wall(new Position(2, 2), Direction.WEST)
            .wall(new Position(2, 2), Direction.SOUTH)
            .build();

        assertFalse(BoardMetrics.of(board).allReachable());
    }

    /**
     * The real first board can be walked from every seat.
     */
    @Test
    void provingGroundsIsReachableFromEverySeat() {
        BoardMetrics metrics = BoardMetrics.of(provingGrounds());

        assertTrue(metrics.allReachable());
        assertEquals(8, metrics.seats().size());
    }
    /**
     * Reads the first board from the assets folder, where the game loads it from.
     *
     * @return the board
     */
    static Board provingGrounds() {
        try {
            return BoardLoader.parse(Files.readString(Path.of("..", "assets", "boards", "proving-grounds.json"))).board();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
