package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.client.render.BoardActor;
import de.mkoehler.robotrampage.client.ui.ModalDialog;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;
import de.mkoehler.robotrampage.devtools.analysis.Playouts;
import de.mkoehler.robotrampage.devtools.generate.Suggestions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * The dialog of the editor's Suggest button (design.md 3.14): up to six suggested boards side by side, each drawn small
 * with what makes it different in words and numbers. Clicking a board picks it. While the dialog is open, bots play a few
 * games on each board in turn on a background thread and each caption fills in how evenly the seats did — flags touched
 * per seat, a denser measure than wins — and once every board has been played, the most even one is marked, but only
 * if it leads the runner-up by {@link #CLEAR_LEAD} (less is within what eight games vary by). Closing the
 * dialog or picking a board stops the games.
 *
 * @author Mario Koehler
 */
final class SuggestionsDialog {

    /** How many bot games each suggestion gets. */
    static final int GAMES = 8;
    private static final float TILE = 22f;
    private static final float COLUMN = BoardDraft.SIZE * TILE;
    private static final int COLUMNS = 3;
    private static final long SEED = 1L;
    /**
     * How much more even, in flags per game, a board must be than the runner-up to be marked as the most even. Eight games
     * of the same board vary by up to about half a flag between seeds, so a smaller lead is noise.
     */
    static final double CLEAR_LEAD = 0.3;

    private final UiKit ui;
    private final List<Suggestions.Suggestion> suggestions;
    private final ModalDialog dialog;
    private final List<Label> botLines = new ArrayList<>();
    private final List<BoardActor> thumbnails = new ArrayList<>();
    private final AtomicReferenceArray<Playouts.Report> reports;
    private final Playouts.Report[] shown;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "suggestion-playouts");
        thread.setDaemon(true);
        return thread;
    });
    private boolean fairestMarked;

    /**
     * Builds the dialog and starts the bot games.
     *
     * @param ui          the widget kit
     * @param suggestions the suggestions, at least one
     * @param onPick      told the suggestion that was clicked
     * @param onClose     told when the dialog is closed without a pick
     */
    SuggestionsDialog(UiKit ui, List<Suggestions.Suggestion> suggestions, Consumer<Suggestions.Suggestion> onPick,
                      Runnable onClose) {
        this.ui = ui;
        this.suggestions = List.copyOf(suggestions);
        this.reports = new AtomicReferenceArray<>(suggestions.size());
        this.shown = new Playouts.Report[suggestions.size()];
        dialog = new ModalDialog(ui, COLUMNS * (COLUMN + 2 * Theme.SPACE_6) + 2 * Theme.SPACE_8, Theme.ACCENT);
        dialog.title("Suggestions");
        dialog.text("Different boards from the one on the canvas. Click one to use it; Ctrl+Z undoes it.",
            Theme.TextStyle.BODY, Theme.INK_MUTED);
        Table grid = new Table();
        for (int index = 0; index < this.suggestions.size(); index++) {
            grid.add(cell(this.suggestions.get(index), onPick)).top().pad(Theme.SPACE_3);
            if ((index + 1) % COLUMNS == 0) {
                grid.row();
            }
        }
        dialog.row(grid);
        TextButton close = ui.button("Close", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        close.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClose.run();
            }
        });
        dialog.buttons(200f, close);
        dialog.onEscape(onClose);
        worker.submit(this::playAll);
    }

    /**
     * Returns the dialog to show.
     *
     * @return the dialog
     */
    ModalDialog dialog() {
        return dialog;
    }

    /**
     * Returns the drawn boards, in the order of the suggestions, for tools that click them.
     *
     * @return the thumbnails
     */
    List<BoardActor> thumbnails() {
        return thumbnails;
    }

    /**
     * Returns the suggestions shown, in order.
     *
     * @return the suggestions
     */
    List<Suggestions.Suggestion> suggestions() {
        return suggestions;
    }

    /**
     * Shows the bot results that arrived since the last frame. Call once a frame while the dialog is open.
     */
    void update() {
        boolean allDone = true;
        for (int index = 0; index < shown.length; index++) {
            Playouts.Report report = reports.get(index);
            if (report != null && report != shown[index]) {
                shown[index] = report;
                ui.setText(botLines.get(index), Theme.TextStyle.CAPTION, botText(report));
            }
            allDone &= report != null && report.games() >= GAMES;
        }
        if (allDone && !fairestMarked && shown.length > 1) {
            fairestMarked = true;
            int fairest = 0;
            for (int index = 1; index < shown.length; index++) {
                if (shown[index].flagGap() < shown[fairest].flagGap()) {
                    fairest = index;
                }
            }
            double runnerUp = Double.MAX_VALUE;
            for (int index = 0; index < shown.length; index++) {
                if (index != fairest) {
                    runnerUp = Math.min(runnerUp, shown[index].flagGap());
                }
            }
            if (runnerUp - shown[fairest].flagGap() < CLEAR_LEAD) {
                return;
            }
            Label line = botLines.get(fairest);
            ui.setText(line, Theme.TextStyle.CAPTION, botText(shown[fairest]) + " — the most even");
            line.getStyle().fontColor = Theme.SUCCESS;
        }
    }

    /**
     * Returns whether every suggestion has had all its bot games, for tools that wait for them.
     *
     * @return {@code true} once all games are played
     */
    boolean allPlayed() {
        for (int index = 0; index < reports.length(); index++) {
            Playouts.Report report = reports.get(index);
            if (report == null || report.games() < GAMES) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stops the bot games.
     */
    void dispose() {
        stopped.set(true);
        worker.shutdownNow();
    }

    /**
     * Builds one suggestion's column: the board, what sets it apart, its numbers and the bot line.
     *
     * @param suggestion the suggestion
     * @param onPick     told when the board is clicked
     * @return the column
     */
    private Table cell(Suggestions.Suggestion suggestion, Consumer<Suggestions.Suggestion> onPick) {
        Table column = new Table();
        column.top();
        BoardActor board = new BoardActor(ui, suggestion.draft().toBoard(), TILE);
        board.setTouchable(Touchable.enabled);
        board.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onPick.accept(suggestion);
            }
        });
        thumbnails.add(board);
        column.add(board).size(COLUMN).row();
        column.add(wrapped(ui.label(suggestion.cell().words(), Theme.TextStyle.LABEL, Theme.INK))).width(COLUMN).left()
            .padTop(Theme.SPACE_2).row();
        column.add(wrapped(ui.label(String.format(Locale.ROOT, "Hazard %d%% · route %.0f steps · moving %d%%",
            Math.round(suggestion.hazardShare() * 100), suggestion.route(), Math.round(suggestion.movingShare() * 100)),
            Theme.TextStyle.CAPTION, Theme.INK_MUTED))).width(COLUMN).left().row();
        Label bots = wrapped(ui.label("Bots are waiting to play…", Theme.TextStyle.CAPTION, Theme.INK_MUTED));
        botLines.add(bots);
        column.add(bots).width(COLUMN).left();
        return column;
    }

    /**
     * Lets a label wrap within its column instead of running into the next.
     *
     * @param label the label
     * @return the same label
     */
    private static Label wrapped(Label label) {
        label.setWrap(true);
        return label;
    }

    /**
     * Plays the bot games on every suggestion in turn, on the worker.
     */
    private void playAll() {
        for (int index = 0; index < suggestions.size() && !stopped.get(); index++) {
            Board board = suggestions.get(index).draft().toBoard();
            int slot = index;
            try {
                Playouts.run(board, GAMES, SEED, report -> reports.set(slot, report), stopped::get);
            } catch (RuntimeException e) {
                return;
            }
        }
    }

    /**
     * Words a suggestion's bot results.
     *
     * @param report the games so far
     * @return for example {@code Bots: 8 games, seats 1.3 flags apart}
     */
    private static String botText(Playouts.Report report) {
        return String.format(Locale.ROOT, "Bots: %d of %d games, seats %.1f flags apart", report.games(), GAMES,
            report.flagGap());
    }
}
