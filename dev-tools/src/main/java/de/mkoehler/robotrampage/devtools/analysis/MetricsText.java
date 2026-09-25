package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.board.WalkingDistances;
import de.mkoehler.robotrampage.rules.DestructionCause;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Words the metrics of a board for the editor's panel (design.md 3.13), one line per fact, each with a tone so the panel
 * can color what deserves attention. Free of libGDX, so the wording can be tested.
 *
 * @author Mario Koehler
 */
public final class MetricsText {

    /** A first-flag spread above this many steps is flagged as unfair between seats. */
    static final int UNFAIR_SPREAD = 4;

    /**
     * How a line should look.
     */
    public enum Tone {
        /** A plain fact. */
        NORMAL,
        /** A fact that is fine and worth confirming. */
        GOOD,
        /** A fact worth a second look. */
        WARNING,
        /** A fact that breaks the board. */
        BAD
    }

    /**
     * One line of the panel.
     *
     * @param text the words
     * @param tone how it should look
     */
    public record Line(String text, Tone tone) {
    }

    /**
     * Not instantiated.
     */
    private MetricsText() {
    }

    /**
     * Words the facts that need no playing: walks by seat, fairness, hazards and moving parts.
     *
     * @param metrics the metrics
     * @return the lines
     */
    public static List<Line> instant(BoardMetrics metrics) {
        List<Line> lines = new ArrayList<>();
        if (!metrics.allReachable()) {
            lines.add(new Line("A flag cannot be walked to from some seat or from the flag before it.", Tone.BAD));
        }
        if (!metrics.seats().isEmpty()) {
            StringJoiner walks = new StringJoiner("|");
            BoardMetrics.SeatRoute nearest = metrics.seats().get(0);
            BoardMetrics.SeatRoute farthest = nearest;
            for (BoardMetrics.SeatRoute seat : metrics.seats()) {
                walks.add(seat.toFirstFlag() >= WalkingDistances.UNREACHABLE ? "–" : String.valueOf(seat.toFirstFlag()));
                nearest = seat.toFirstFlag() < nearest.toFirstFlag() ? seat : nearest;
                farthest = seat.toFirstFlag() > farthest.toFirstFlag() ? seat : farthest;
            }
            lines.add(new Line("Steps to flag 1 by seat: " + walks, Tone.NORMAL));
            boolean unfair = metrics.firstFlagSpread() > UNFAIR_SPREAD;
            lines.add(new Line("Spread: " + steps(metrics.firstFlagSpread()) + " (seat " + (nearest.seat() + 1)
                + " nearest, seat " + (farthest.seat() + 1) + " farthest)", unfair ? Tone.WARNING : Tone.GOOD));
            lines.add(new Line("Flag to flag: " + steps(metrics.flagRoute()) + "; the whole route is "
                + steps(nearest.route()) + " to " + steps(farthest.route()), Tone.NORMAL));
        } else {
            lines.add(new Line("Flag to flag: " + steps(metrics.flagRoute()), Tone.NORMAL));
        }
        lines.add(new Line("Hazards: " + count(metrics.pits(), "pit") + ", " + count(metrics.crushers(), "crusher")
            + ", " + count(metrics.lasers(), "laser") + " covering " + metrics.laserSquares() + " squares ("
            + percent(metrics.hazardShare()) + " of the board)", Tone.NORMAL));
        lines.add(new Line("Moving parts: " + metrics.belts() + " belts (" + metrics.expressBelts() + " express), "
            + count(metrics.gears(), "gear") + ", " + count(metrics.pushers(), "pusher") + " (" + percent(metrics.movingShare())
            + " of the board moves robots)", Tone.NORMAL));
        lines.add(new Line("Repair sites: " + metrics.repairSites(), Tone.NORMAL));
        return lines;
    }

    /**
     * Words what the bot games showed so far.
     *
     * @param report the report, or {@code null} while the first game is still being played
     * @param total  how many games the run plays in all
     * @return the lines
     */
    public static List<Line> playouts(Playouts.Report report, int total) {
        List<Line> lines = new ArrayList<>();
        if (report == null) {
            lines.add(new Line("Playing the first of " + total + " bot games…", Tone.NORMAL));
            return lines;
        }
        boolean done = report.games() >= total;
        lines.add(new Line("Bots played " + report.games() + " of " + total + " games"
            + (done ? "" : "…"), Tone.NORMAL));
        boolean fewFinished = report.finished() * 2 < report.games();
        lines.add(new Line("Finished: " + report.finished() + " of " + report.games()
            + (report.finished() > 0 ? String.format(Locale.ROOT, ", %.1f turns on average", report.averageTurns()) : "")
            + (fewFinished ? " — games often run past " + Playouts.TURN_LIMIT + " turns" : ""),
            fewFinished ? Tone.WARNING : Tone.GOOD));
        lines.add(new Line(String.format(Locale.ROOT, "Flags touched per game: %.1f",
            report.flags() / (double) report.games()), Tone.NORMAL));
        StringJoiner causes = new StringJoiner(", ");
        for (Map.Entry<DestructionCause, Integer> entry : report.deaths().entrySet()) {
            causes.add(causeName(entry.getKey()) + String.format(Locale.ROOT, " %.1f",
                entry.getValue() / (double) report.games()));
        }
        lines.add(new Line(String.format(Locale.ROOT, "Robots destroyed per game: %.1f", report.deathsPerGame())
            + (causes.length() > 0 ? " (" + causes + ")" : ""), Tone.NORMAL));
        StringJoiner wins = new StringJoiner("|");
        int best = 0;
        for (int seat = 0; seat < report.winsBySeat().length; seat++) {
            wins.add(String.valueOf(report.winsBySeat()[seat]));
            best = Math.max(best, report.winsBySeat()[seat]);
        }
        boolean lopsided = report.finished() >= 5 && best * 2 > report.finished();
        lines.add(new Line("Wins by seat: " + wins + (lopsided ? " — one seat wins most games" : ""),
            lopsided ? Tone.WARNING : Tone.NORMAL));
        return lines;
    }

    /**
     * Words a walking distance.
     *
     * @param steps the steps
     * @return for example {@code 12 steps}, or {@code unreachable}
     */
    private static String steps(int steps) {
        return steps >= WalkingDistances.UNREACHABLE ? "unreachable" : steps + " steps";
    }

    /**
     * Words a count of things with the right plural.
     *
     * @param count the count
     * @param thing the thing, singular
     * @return for example {@code 1 gear} or {@code 2 gears}
     */
    private static String count(int count, String thing) {
        return count + " " + thing + (count == 1 ? "" : "s");
    }

    /**
     * Words a share as a whole percentage.
     *
     * @param share the share, 0 to 1
     * @return for example {@code 18%}
     */
    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    /**
     * Names a cause of destruction the short way the panel uses.
     *
     * @param cause the cause
     * @return the name
     */
    private static String causeName(DestructionCause cause) {
        return switch (cause) {
            case PIT -> "pits";
            case LEFT_BOARD -> "off the edge";
            case DAMAGE -> "damage";
            case CRUSHER -> "crushers";
            case FORFEIT -> "forfeits";
        };
    }
}
