package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.rules.DestructionCause;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@link MetricsText} words the metrics, above all which facts it flags.
 *
 * @author Mario Koehler
 */
class MetricsTextTest {

    /**
     * Seats are listed with their walks, a large spread and an unreachable flag are flagged.
     */
    @Test
    void walksAreListedAndProblemsFlagged() {
        BoardMetrics fair = new BoardMetrics(List.of(new BoardMetrics.SeatRoute(0, 8, 20),
            new BoardMetrics.SeatRoute(1, 10, 22)), 12, 2, true, 144, 4, 20, 6, 2, 1, 1, 2, 3, 30);
        BoardMetrics unfair = new BoardMetrics(List.of(new BoardMetrics.SeatRoute(0, 3, 15),
            new BoardMetrics.SeatRoute(1, 250, 262)), 12, 247, false, 144, 4, 20, 6, 2, 1, 1, 2, 3, 30);

        List<MetricsText.Line> fine = MetricsText.instant(fair);
        List<MetricsText.Line> bad = MetricsText.instant(unfair);

        assertEquals(new MetricsText.Line("Steps to flag 1 by seat: 8|10", MetricsText.Tone.NORMAL), fine.get(0));
        assertEquals(new MetricsText.Line("Spread: 2 steps (seat 1 nearest, seat 2 farthest)", MetricsText.Tone.GOOD),
            fine.get(1));
        assertEquals("Flag to flag: 12 steps; the whole route is 20 steps to 22 steps", fine.get(2).text());
        assertEquals(MetricsText.Tone.BAD, bad.get(0).tone());
        assertEquals("Steps to flag 1 by seat: 3|–", bad.get(1).text());
        assertEquals(MetricsText.Tone.WARNING, bad.get(2).tone());
        assertTrue(fine.stream().anyMatch(line -> line.text().contains("2 gears, 1 pusher")));
    }

    /**
     * Bot results are averaged per game, and games that rarely finish or one seat winning most are flagged.
     */
    @Test
    void botResultsAreAveragedAndFlagged() {
        Playouts.Report report = new Playouts.Report(10, 4, 100, 30, Map.of(DestructionCause.PIT, 20),
            new int[] {3, 1, 0});

        List<MetricsText.Line> lines = MetricsText.playouts(report, 20);

        assertEquals("Bots played 10 of 20 games…", lines.get(0).text());
        assertEquals(MetricsText.Tone.WARNING, lines.get(1).tone(), "fewer than half the games finished");
        assertTrue(lines.get(1).text().contains("25.0 turns on average"));
        assertEquals("Robots destroyed per game: 2.0 (pits 2.0)", lines.get(3).text());
        assertEquals("Wins by seat: 3|1|0", lines.get(4).text());
        assertEquals("Playing the first of 20 bot games…", MetricsText.playouts(null, 20).get(0).text());
    }
}
