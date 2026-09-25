package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;
import de.mkoehler.robotrampage.devtools.analysis.BoardMetrics;
import de.mkoehler.robotrampage.devtools.analysis.MetricsText;
import de.mkoehler.robotrampage.devtools.analysis.Playouts;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The editor's metrics list (design.md 3.13): the facts that need no playing are shown at once for every valid board, and
 * once the board has stayed unchanged for a moment, bots play it on a background thread and their results fill in game
 * by game. Any change to the board cancels a run still going and starts over.
 * <p>
 * Threading: the worker only ever reads an immutable {@link Board} and hands reports over through an
 * {@link AtomicReference}; everything touching widgets happens in {@link #update(float)} on the render thread.
 *
 * @author Mario Koehler
 */
final class MetricsPanel {

    /** How many bot games a run plays. */
    static final int GAMES = 20;
    /** The seed of every run, so the same board always gives the same numbers. */
    private static final long SEED = 1L;
    /** How long the board must stay unchanged before the bots start, in seconds. */
    private static final float QUIET_SECONDS = 1f;

    /**
     * A report and the run it belongs to.
     *
     * @param run    the run's number
     * @param report the report
     */
    private record Tagged(int run, Playouts.Report report, String failure) {
    }

    private final UiKit ui;
    private final float width;
    private final Table table = new Table();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "board-playouts");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger run = new AtomicInteger();
    private final AtomicReference<Tagged> latest = new AtomicReference<>();
    private List<MetricsText.Line> instantLines = List.of();
    private Board pending;
    private float quiet;
    private boolean running;
    private Tagged shown;

    /**
     * Creates the panel.
     *
     * @param ui    the widget kit
     * @param width the width lines wrap at
     */
    MetricsPanel(UiKit ui, float width) {
        this.ui = ui;
        this.width = width;
        table.top().left();
    }

    /**
     * Returns the widget to put into the window.
     *
     * @return the table of lines
     */
    Table table() {
        return table;
    }

    /**
     * Takes in a changed board: shows its instant facts, cancels any bot run still going and schedules a new one.
     *
     * @param board the board, or {@code null} while it is not valid (nothing is measured then)
     */
    void boardChanged(Board board) {
        run.incrementAndGet();
        running = false;
        shown = null;
        pending = board;
        quiet = 0f;
        BoardMetrics measured = board == null ? null : BoardMetrics.of(board);
        instantLines = measured == null
            ? List.of(new MetricsText.Line("Fix the checks above to see the metrics.", MetricsText.Tone.NORMAL))
            : MetricsText.instant(measured);
        if (measured != null && !measured.allReachable()) {
            pending = null;
            rebuild(List.of(new MetricsText.Line("No bot games while a flag cannot be reached.",
                MetricsText.Tone.NORMAL)));
            return;
        }
        rebuild(List.of());
    }

    /**
     * Starts the bots once the board has been left alone long enough, and shows new results. Call once a frame.
     *
     * @param delta seconds since the last frame
     */
    void update(float delta) {
        if (pending != null) {
            quiet += delta;
            if (quiet >= QUIET_SECONDS) {
                start(pending);
                pending = null;
            }
        }
        Tagged report = latest.get();
        if (running && report != shown && report != null && report.run() == run.get()) {
            shown = report;
            rebuild(report.failure() != null
                ? List.of(new MetricsText.Line("The bot games failed: " + report.failure(), MetricsText.Tone.BAD))
                : MetricsText.playouts(report.report(), GAMES));
        }
    }

    /**
     * Returns how many bot games of the current run are shown, for tools that wait for results.
     *
     * @return the games, 0 before the first has finished
     */
    int gamesShown() {
        return shown == null || shown.report() == null ? 0 : shown.report().games();
    }

    /**
     * Stops the worker thread.
     */
    void dispose() {
        run.incrementAndGet();
        worker.shutdownNow();
    }

    /**
     * Starts a bot run on the worker. Should the games throw, the panel says so in red instead of waiting forever.
     *
     * @param board the board to play
     */
    private void start(Board board) {
        int number = run.get();
        running = true;
        rebuild(MetricsText.playouts(null, GAMES));
        worker.submit(() -> {
            try {
                Playouts.run(board, GAMES, SEED, report -> latest.set(new Tagged(number, report, null)),
                    () -> run.get() != number);
            } catch (RuntimeException e) {
                latest.set(new Tagged(number, null, e.toString()));
            }
        });
    }

    /**
     * Shows the instant lines followed by the bot lines.
     *
     * @param playoutLines the bot lines, empty before a run has started
     */
    private void rebuild(List<MetricsText.Line> playoutLines) {
        table.clearChildren();
        instantLines.forEach(this::addLine);
        if (!playoutLines.isEmpty()) {
            table.add(ui.label("Bot games", Theme.TextStyle.LABEL, Theme.INK)).left().padTop(Theme.SPACE_4)
                .padBottom(Theme.SPACE_2).row();
            playoutLines.forEach(this::addLine);
        }
    }

    /**
     * Adds one line.
     *
     * @param line the line
     */
    private void addLine(MetricsText.Line line) {
        Label label = ui.label(line.text(), Theme.TextStyle.BODY, color(line.tone()));
        label.setWrap(true);
        table.add(label).width(width).left().padBottom(Theme.SPACE_2).row();
    }

    /**
     * Picks the color of a tone.
     *
     * @param tone the tone
     * @return the color
     */
    private static Color color(MetricsText.Tone tone) {
        return switch (tone) {
            case NORMAL -> Theme.INK;
            case GOOD -> Theme.SUCCESS;
            case WARNING -> Theme.PRIMARY;
            case BAD -> Theme.DANGER;
        };
    }
}
