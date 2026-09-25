package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.board.Board;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Playouts} on the first board.
 *
 * @author Mario Koehler
 */
class PlayoutsTest {

    private static final Board BOARD = BoardMetricsTest.provingGrounds();

    /**
     * Games are reported one by one, finish, and a seed always gives the same report.
     */
    @Test
    void gamesAreReportedAndReproducible() {
        List<Playouts.Report> first = new ArrayList<>();
        long start = System.nanoTime();
        Playouts.run(BOARD, 2, 5L, first::add, () -> false);
        System.out.println("PlayoutsTest: 2 games with 8 bots in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        List<Playouts.Report> second = new ArrayList<>();
        Playouts.run(BOARD, 2, 5L, second::add, () -> false);

        assertEquals(2, first.size());
        Playouts.Report last = first.get(1);
        assertEquals(2, last.games());
        assertTrue(last.finished() >= 1, "bots should finish a game on the first board");
        assertEquals(last.finished(), Arrays.stream(last.winsBySeat()).sum());
        assertEquals(last.turns(), second.get(1).turns());
        assertEquals(last.deaths(), second.get(1).deaths());
        assertTrue(Arrays.equals(last.winsBySeat(), second.get(1).winsBySeat()));
        assertEquals(last.flags(), Arrays.stream(last.flagsBySeat()).sum(), "flags by seat add up to all flags");
        assertTrue(last.flagGap() >= 0);
    }

    /**
     * A cancelled run stops at once and reports nothing more.
     */
    @Test
    void aCancelledRunStops() {
        List<Playouts.Report> reports = new ArrayList<>();

        Playouts.run(BOARD, 5, 1L, reports::add, () -> true);

        assertTrue(reports.isEmpty());
    }
}
