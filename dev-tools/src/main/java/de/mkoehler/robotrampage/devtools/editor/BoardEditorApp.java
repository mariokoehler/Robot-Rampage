package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.InvalidBoardException;
import de.mkoehler.robotrampage.board.ValidationResult;
import de.mkoehler.robotrampage.client.ui.ModalDialog;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;
import de.mkoehler.robotrampage.devtools.generate.BoardGenerator;
import de.mkoehler.robotrampage.devtools.generate.Suggestions;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * The board editor window (design.md 3.13): a tool palette on the left, the board in the middle, the board's name and
 * the live validation on the right, and New/Open/Save on top. It works on the {@code boards} folder of the working
 * directory, which is {@code assets/boards} when started as documented, where the game loads its boards from.
 * <p>
 * Save is only possible while the board has no validation errors (warnings are fine), since the game refuses to load a
 * board with errors, and so would the editor when opening it again. Everything the editing does lives in the
 * libGDX-free {@link BoardEditor}; this class only lays out widgets and forwards clicks and keys.
 *
 * @author Mario Koehler
 */
public final class BoardEditorApp extends ApplicationAdapter {

    private static final float TILE = 72f;
    private static final float SIDE_WIDTH = 440f;
    private static final float CHECKS_HEIGHT = 80f;
    private static final int SUGGESTIONS = 6;
    private static final float CHECKS_HEIGHT_INVALID = 420f;
    private static final float FIELD_HEIGHT = 48f;
    private static final float PILL_HEIGHT = 40f;
    private static final String NEW_ID = "new-board";
    private static final String NEW_NAME = "New board";
    private static final Map<Tool, String> ICONS = Map.ofEntries(
        Map.entry(Tool.ERASE, "tiles/floor.png"),
        Map.entry(Tool.PIT, "tiles/pit.png"),
        Map.entry(Tool.REPAIR, "tiles/repair-site.png"),
        Map.entry(Tool.GEAR_CLOCKWISE, "tiles/gear-clockwise.png"),
        Map.entry(Tool.GEAR_COUNTERCLOCKWISE, "tiles/gear-counterclockwise.png"),
        Map.entry(Tool.CRUSHER, "board/crusher.png"),
        Map.entry(Tool.BELT, "tiles/belt.png"),
        Map.entry(Tool.EXPRESS_BELT, "tiles/belt-express.png"),
        Map.entry(Tool.WALL, "board/wall.png"),
        Map.entry(Tool.LASER, "board/laser-emitter.png"),
        Map.entry(Tool.PUSHER, "board/pusher.png"),
        Map.entry(Tool.FLAG, "board/flag.png"),
        Map.entry(Tool.START, "board/start-square.png"));

    private final String initialBoard;
    private UiKit ui;
    private Stage stage;
    private BoardFiles files;
    private BoardEditor editor;
    private String openedId;
    private boolean dialogOpen;
    private boolean closeConfirmed;
    private boolean updatingFields;
    private int shownRevision = -1;
    private Table tools;
    private Table options;
    private Table checks;
    private TextField idField;
    private TextField nameField;
    private TextField authorField;
    private TextButton saveButton;
    private Label status;
    private BoardArea boardArea;
    private MetricsPanel metrics;
    private final ExecutorService generatorWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "board-generator");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Object> generated = new AtomicReference<>();
    private final Random seeds = new Random();
    private TextButton generateButton;
    private TextButton suggestButton;
    private SuggestionsDialog suggestionsDialog;
    private boolean generating;
    private int generationRevision;
    private long generationSeed;
    private String notice;
    private int noticeRevision = -1;
    private Cell<ScrollPane> checksCell;
    private BoardDefinition measuredLayout;

    /**
     * Creates the editor.
     *
     * @param initialBoard the id of a board in the boards folder to open at start, or {@code null} for a new board
     */
    public BoardEditorApp(String initialBoard) {
        this.initialBoard = initialBoard;
    }

    /**
     * Loads the fonts and pictures, builds the window and opens the first board.
     */
    @Override
    public void create() {
        ui = new UiKit(Gdx.files.internal("fonts"), fontScale());
        stage = new Stage(new FitViewport(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT));
        files = new BoardFiles(Path.of("boards"));
        editor = new BoardEditor(new BoardDraft(NEW_ID, NEW_NAME, null));
        build();
        if (initialBoard != null) {
            open(initialBoard);
        }
        Gdx.input.setInputProcessor(stage);
    }

    /**
     * Returns the editor behind the window, for tools that drive it without a mouse.
     *
     * @return the editor
     */
    BoardEditor editor() {
        return editor;
    }

    /**
     * Returns the metrics panel, for tools that wait for its bot games.
     *
     * @return the panel
     */
    MetricsPanel metrics() {
        return metrics;
    }

    /**
     * Returns the board area, for tools that send it pointer events.
     *
     * @return the board area
     */
    BoardArea boardArea() {
        return boardArea;
    }

    /**
     * Chooses a tool and shows its options, as a click on its button does.
     *
     * @param tool the tool
     */
    void selectTool(Tool tool) {
        editor.setTool(tool);
        rebuildTools();
        rebuildOptions();
    }

    /**
     * Returns the stage, for tools that draw the window off screen.
     *
     * @return the stage
     */
    Stage stage() {
        return stage;
    }

    /**
     * Refreshes whatever depends on the draft, then draws the window.
     */
    @Override
    public void render() {
        if (editor.revision() != shownRevision) {
            refresh();
        }
        metrics.update(Gdx.graphics.getDeltaTime());
        takeGeneratedBoard();
        if (suggestionsDialog != null) {
            suggestionsDialog.update();
        }
        Gdx.gl.glClearColor(Theme.SURFACE.r, Theme.SURFACE.g, Theme.SURFACE.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    /**
     * Fits the fixed 1920x1080 layout into the window.
     *
     * @param width  the new width
     * @param height the new height
     */
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /**
     * Frees the stage and the widget kit.
     */
    @Override
    public void dispose() {
        if (suggestionsDialog != null) {
            suggestionsDialog.dispose();
        }
        generatorWorker.shutdownNow();
        metrics.dispose();
        stage.dispose();
        ui.dispose();
    }

    /**
     * Decides whether closing the window may go ahead: at once when everything is saved, otherwise only after the user
     * confirms throwing the changes away.
     *
     * @return {@code true} if the window may close now
     */
    boolean closeRequested() {
        if (closeConfirmed || !editor.isDirty()) {
            return true;
        }
        confirmDiscard("Quit without saving?", () -> {
            closeConfirmed = true;
            Gdx.app.exit();
        });
        return false;
    }

    /**
     * Refreshes the parts of the window that show the draft: validation, the Save button and the status line.
     */
    void refresh() {
        shownRevision = editor.revision();
        ValidationResult result = editor.draft().validate();
        boolean idValid = BoardEditor.isValidId(editor.draft().id());
        ui.setFieldError(idField, !idValid);
        saveButton.setDisabled(!result.isValid() || !idValid);
        checks.clearChildren();
        checks.top().left();
        if (!idValid) {
            addCheck("The id must be lower-case letters and digits joined by hyphens, like proving-grounds",
                Theme.DANGER);
        }
        result.errors().forEach(error -> addCheck(error, Theme.DANGER));
        result.warnings().forEach(warning -> addCheck("Warning: " + warning, Theme.INK_MUTED));
        if (result.isValid() && idValid && result.warnings().isEmpty()) {
            addCheck("No problems. The board can be saved.", Theme.SUCCESS);
        } else if (result.isValid() && idValid) {
            addCheck("No errors. The board can be saved; the warnings are legal but worth a look.", Theme.SUCCESS);
        }
        checksCell.height(result.isValid() && idValid ? CHECKS_HEIGHT : CHECKS_HEIGHT_INVALID);
        checksCell.getTable().invalidateHierarchy();
        BoardDefinition layout = result.isValid() ? layoutOf(editor.draft().toDefinition()) : null;
        if (layout == null || !layout.equals(measuredLayout)) {
            measuredLayout = layout;
            metrics.boardChanged(layout == null ? null : editor.draft().toBoard());
        }
        String file = "boards/" + editor.draft().id() + ".json";
        String state = editor.isDirty() ? "Unsaved changes, will save to " : openedId == null ? "Not saved yet, will save to "
            : "Saved as ";
        ui.setText(status, Theme.TextStyle.BODY, state + file
            + (notice != null && noticeRevision == editor.revision() ? " · " + notice : ""));
        Gdx.graphics.setTitle("Board Editor - " + editor.draft().id() + (editor.isDirty() ? " *" : ""));
    }

    /**
     * Starts generating a board from the one on the canvas, on the generator's own thread (design.md 3.14). The canvas is
     * copied here, on the render thread; the result is taken in by {@link #takeGeneratedBoard()}.
     */
    void generate() {
        startGenerator("Generating a board…", (canvas, seed) -> BoardGenerator.generate(canvas, seed, () -> false));
    }

    /**
     * Starts searching for several different boards from the one on the canvas (design.md 3.14); the result opens the
     * suggestions dialog.
     */
    void suggest() {
        startGenerator("Looking for suggestions…",
            (canvas, seed) -> Suggestions.suggest(canvas, seed, SUGGESTIONS, () -> false));
    }

    /**
     * Runs a generator on its own thread from a copy of the canvas made here, on the render thread. The result is taken
     * in by {@link #takeGeneratedBoard()}.
     *
     * @param message   what the status line says meanwhile
     * @param generator the generator, given the canvas copy and the seed
     */
    private void startGenerator(String message, BiFunction<BoardDraft, Long, Object> generator) {
        if (generating || dialogOpen) {
            return;
        }
        generating = true;
        generateButton.setDisabled(true);
        suggestButton.setDisabled(true);
        ui.setText(status, Theme.TextStyle.BODY, message);
        generationRevision = editor.revision();
        generationSeed = seeds.nextInt(1_000_000);
        BoardDraft canvas = editor.draft().copy();
        long seed = generationSeed;
        generatorWorker.submit(() -> {
            try {
                generated.set(generator.apply(canvas, seed));
            } catch (RuntimeException e) {
                generated.set(e);
            }
        });
    }

    /**
     * Puts a finished generated board on the canvas as one undo step, unless the board was changed by hand while the
     * generator ran: then the result is dropped rather than overwriting that work. Call once a frame.
     */
    void takeGeneratedBoard() {
        Object result = generated.getAndSet(null);
        if (result == null) {
            return;
        }
        generating = false;
        generateButton.setDisabled(false);
        suggestButton.setDisabled(false);
        if (result instanceof RuntimeException failure) {
            notice = "Generating failed: " + failure;
        } else if (editor.revision() != generationRevision) {
            notice = "The board changed while generating, so the result was dropped";
        } else if (result instanceof BoardGenerator.Candidate candidate) {
            editor.applyGenerated(candidate.draft());
            notice = "Generated from seed " + generationSeed + ", Ctrl+Z undoes it";
        } else if (result instanceof List<?> found && found.isEmpty()) {
            notice = "No valid board was found, try again";
        } else if (result instanceof List<?> found) {
            showSuggestions(found.stream().map(Suggestions.Suggestion.class::cast).toList());
            notice = "Suggestions from seed " + generationSeed;
        }
        noticeRevision = editor.revision();
        refresh();
    }

    /**
     * Opens the suggestions dialog.
     *
     * @param found the suggestions, at least one
     */
    void showSuggestions(List<Suggestions.Suggestion> found) {
        suggestionsDialog = new SuggestionsDialog(ui, found, this::pickSuggestion, this::closeSuggestions);
        openDialog(suggestionsDialog.dialog());
    }

    /**
     * Puts a suggestion on the canvas as one undo step and closes the dialog.
     *
     * @param suggestion the picked suggestion
     */
    void pickSuggestion(Suggestions.Suggestion suggestion) {
        closeSuggestions();
        editor.applyGenerated(suggestion.draft());
        notice = suggestion.cell().words() + ", Ctrl+Z undoes it";
        noticeRevision = editor.revision();
        refresh();
    }

    /**
     * Closes the suggestions dialog and stops its bot games.
     */
    void closeSuggestions() {
        if (suggestionsDialog == null) {
            return;
        }
        suggestionsDialog.dispose();
        closeDialog(suggestionsDialog.dialog());
        suggestionsDialog = null;
    }

    /**
     * Returns the open suggestions dialog, for tools that pick from it.
     *
     * @return the dialog, or {@code null} if none is open
     */
    SuggestionsDialog suggestionsDialog() {
        return suggestionsDialog;
    }

    /**
     * Returns whether the generator is running, for tools that wait for it.
     *
     * @return {@code true} while a board is being generated
     */
    boolean generating() {
        return generating;
    }

    /**
     * Strips a board's id, name and author, so typing them does not count as a change to what is measured.
     *
     * @param definition the board
     * @return the same board without its metadata
     */
    private static BoardDefinition layoutOf(BoardDefinition definition) {
        return new BoardDefinition(definition.formatVersion(), "", "", null, definition.generator(), definition.seed(),
            definition.width(), definition.height(), definition.squares(), definition.edges(), definition.flags(),
            definition.startSquares());
    }

    /**
     * Lays the whole window out.
     */
    private void build() {
        // Created here, not as field initializers: a Table built before libGDX has started (the app object itself is
        // made in main(), before Lwjgl3Application) recurses in Cell.defaults() until the stack overflows.
        tools = new Table();
        options = new Table();
        checks = new Table();
        metrics = new MetricsPanel(ui, SIDE_WIDTH - 2 * Theme.SPACE_6 - Theme.SPACE_2);
        Table root = new Table();
        root.setFillParent(true);
        root.pad(Theme.SPACE_8).top();
        stage.addActor(root);

        Table top = new Table();
        top.add(ui.label("Board editor", Theme.TextStyle.HEADING, Theme.INK)).left().padRight(Theme.SPACE_6);
        status = ui.label("", Theme.TextStyle.BODY, Theme.INK_MUTED);
        top.add(status).left().expandX();
        generateButton = ui.button("Generate", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        onClick(generateButton, this::generate);
        top.add(generateButton).size(200f, 52f).padLeft(Theme.SPACE_4);
        suggestButton = ui.button("Suggest", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        onClick(suggestButton, this::suggest);
        top.add(suggestButton).size(180f, 52f).padLeft(Theme.SPACE_4);
        TextButton newButton = ui.button("New", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton openButton = ui.button("Open", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        saveButton = ui.button("Save", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        onClick(newButton, () -> confirmDiscard("Start a new board without saving?", this::newBoard));
        onClick(openButton, this::showOpenDialog);
        onClick(saveButton, this::save);
        top.add(newButton).size(140f, 52f).padLeft(Theme.SPACE_4);
        top.add(openButton).size(140f, 52f).padLeft(Theme.SPACE_4);
        top.add(saveButton).size(160f, 52f).padLeft(Theme.SPACE_4);
        root.add(top).colspan(3).growX().padBottom(Theme.SPACE_6).row();

        root.add(leftPanel()).width(SIDE_WIDTH).growY().top();
        boardArea = new BoardArea(ui, editor, TILE, this::rebuildOptions);
        root.add(boardArea).size(BoardDraft.SIZE * TILE).top().padLeft(Theme.SPACE_8).padRight(Theme.SPACE_8);
        root.add(rightPanel()).width(SIDE_WIDTH).growY().top();

        stage.addListener(new ShortcutListener());
        stage.addCaptureListener(new InputListener() {
            /**
             * Takes the keyboard away from a text field as soon as anything else is clicked, so typing a shortcut
             * afterwards never ends up in the board's id or name.
             *
             * @param event   the event
             * @param x       the horizontal position
             * @param y       the vertical position
             * @param pointer the pointer
             * @param button  the mouse button
             * @return {@code false}, the click goes on to its target
             */
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!dialogOpen && !(event.getTarget() instanceof TextField)) {
                    stage.setKeyboardFocus(null);
                }
                return false;
            }
        });
        rebuildTools();
        rebuildOptions();
        refresh();
    }

    /**
     * Builds the tool palette, its options and the help text.
     *
     * @return the panel
     */
    private Table leftPanel() {
        Table panel = ui.panel();
        panel.top().left().pad(Theme.SPACE_6);
        panel.add(ui.label("Tools", Theme.TextStyle.HEADING, Theme.INK)).left().padBottom(Theme.SPACE_3).row();
        panel.add(tools).growX().left().row();
        panel.add(options).growX().left().padTop(Theme.SPACE_4).row();
        Label help = ui.label("Left click places, right click removes, drag to paint. Drag a belt along its path. "
            + "Drag flags and start squares to move them; click a start square to turn it.\n"
            + "R / Shift+R turns the direction. Ctrl+Z undo, Ctrl+Y redo, Ctrl+S save.", Theme.TextStyle.CAPTION,
            Theme.INK_MUTED);
        help.setWrap(true);
        panel.add(help).growX().expandY().bottom().left().padTop(Theme.SPACE_4);
        return panel;
    }

    /**
     * Builds the metadata fields, the validation list and the metrics.
     *
     * @return the panel
     */
    private Table rightPanel() {
        Table panel = ui.panel();
        panel.top().left().pad(Theme.SPACE_6);
        panel.add(ui.label("Board", Theme.TextStyle.HEADING, Theme.INK)).left().padBottom(Theme.SPACE_3).row();
        idField = ui.textField("", 40);
        nameField = ui.textField("", 60);
        authorField = ui.textField("", 60);
        addField(panel, "Id (file name)", idField);
        addField(panel, "Name", nameField);
        addField(panel, "Author", authorField);
        ChangeListener metadata = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!updatingFields) {
                    editor.draft().setMetadata(idField.getText().trim(), nameField.getText().trim(),
                        authorField.getText().trim());
                    editor.markChanged();
                }
            }
        };
        for (TextField field : List.of(idField, nameField, authorField)) {
            field.addListener(metadata);
            field.setTextFieldListener((typedIn, character) -> {
                if (character == '\r' || character == '\n') {
                    stage.setKeyboardFocus(null);
                }
            });
        }
        fillFields();
        panel.add(ui.label("Checks", Theme.TextStyle.HEADING, Theme.INK)).left().padTop(Theme.SPACE_4)
            .padBottom(Theme.SPACE_3).row();
        ScrollPane scroll = new ScrollPane(checks);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        checksCell = panel.add(scroll).growX().height(CHECKS_HEIGHT).left().top();
        checksCell.row();
        panel.add(ui.label("Metrics", Theme.TextStyle.HEADING, Theme.INK)).left().padTop(Theme.SPACE_4)
            .padBottom(Theme.SPACE_3).row();
        ScrollPane metricsScroll = new ScrollPane(metrics.table());
        metricsScroll.setFadeScrollBars(false);
        metricsScroll.setScrollingDisabled(true, false);
        panel.add(metricsScroll).grow().left().top();
        return panel;
    }

    /**
     * Adds a labelled text field to a panel.
     *
     * @param panel the panel
     * @param label the label above the field
     * @param field the field
     */
    private void addField(Table panel, String label, TextField field) {
        panel.add(ui.label(label, Theme.TextStyle.LABEL, Theme.INK)).left().padBottom(Theme.SPACE_1).row();
        panel.add(field).growX().height(FIELD_HEIGHT).padBottom(Theme.SPACE_3).row();
    }

    /**
     * Puts the draft's metadata into the fields, without it counting as an edit.
     */
    private void fillFields() {
        updatingFields = true;
        idField.setText(editor.draft().id());
        nameField.setText(editor.draft().name());
        authorField.setText(editor.draft().author() == null ? "" : editor.draft().author());
        updatingFields = false;
    }

    /**
     * Adds one line to the validation list.
     *
     * @param text  the message
     * @param color its color
     */
    private void addCheck(String text, Color color) {
        Label line = ui.label(text, Theme.TextStyle.BODY, color);
        line.setWrap(true);
        checks.add(line).width(SIDE_WIDTH - 2 * Theme.SPACE_6 - Theme.SPACE_2).left().padBottom(Theme.SPACE_2).row();
    }

    /**
     * Rebuilds the tool buttons, highlighting the chosen one.
     */
    private void rebuildTools() {
        tools.clearChildren();
        Tool[] all = Tool.values();
        for (int index = 0; index < all.length; index++) {
            Tool tool = all[index];
            Table item = pill(tool.label(), editor.tool() == tool, () -> selectTool(tool));
            item.clearChildren();
            item.add(new Image(ui.image(ICONS.get(tool)))).size(32f).padRight(Theme.SPACE_2);
            item.add(ui.label(tool.label(), Theme.TextStyle.BODY, editor.tool() == tool ? Theme.ON_PRIMARY : Theme.INK))
                .left().expandX();
            tools.add(item).growX().height(PILL_HEIGHT + 4f + UiKit.SHAPE_RESERVE).uniformX()
                .padRight(index % 2 == 0 ? Theme.SPACE_2 : 0f).padBottom(Theme.SPACE_1);
            if (index % 2 == 1) {
                tools.row();
            }
        }
    }

    /**
     * Rebuilds the options that matter for the chosen tool: the direction for belts and start squares, the beams for
     * lasers, the registers for crushers and pushers.
     */
    private void rebuildOptions() {
        options.clearChildren();
        options.left();
        Tool tool = editor.tool();
        if (tool == Tool.BELT || tool == Tool.EXPRESS_BELT || tool == Tool.START) {
            Table row = new Table();
            for (Direction direction : Direction.values()) {
                row.add(pill(title(direction), editor.direction() == direction, () -> {
                    editor.setDirection(direction);
                    rebuildOptions();
                })).height(PILL_HEIGHT + UiKit.SHAPE_RESERVE).padRight(Theme.SPACE_1);
            }
            addOption("Direction", row);
        }
        if (tool == Tool.LASER) {
            Table row = new Table();
            for (int beams = 1; beams <= 3; beams++) {
                int count = beams;
                row.add(pill(count + (count == 1 ? " beam" : " beams"), editor.beams() == count, () -> {
                    editor.setBeams(count);
                    rebuildOptions();
                })).height(PILL_HEIGHT + UiKit.SHAPE_RESERVE).padRight(Theme.SPACE_1);
            }
            addOption("Beams", row);
        }
        if (tool == Tool.CRUSHER || tool == Tool.PUSHER) {
            Table row = new Table();
            for (int register = 1; register <= 5; register++) {
                int number = register;
                row.add(pill(String.valueOf(number), editor.registers().contains(number), () -> {
                    editor.toggleRegister(number);
                    rebuildOptions();
                })).size(56f, PILL_HEIGHT + UiKit.SHAPE_RESERVE).padRight(Theme.SPACE_1);
            }
            addOption("Active in registers", row);
        }
        if (tool.onEdge()) {
            Label hint = ui.label("Acts on the side of the square nearest the pointer."
                + (tool == Tool.WALL ? "" : " Fires or pushes away from that side."), Theme.TextStyle.CAPTION,
                Theme.INK_MUTED);
            hint.setWrap(true);
            options.add(hint).width(SIDE_WIDTH - 2 * Theme.SPACE_6).left().row();
        }
    }

    /**
     * Adds a labelled row of options.
     *
     * @param label the label
     * @param row   the options
     */
    private void addOption(String label, Table row) {
        options.add(ui.label(label, Theme.TextStyle.LABEL, Theme.INK)).left().padBottom(Theme.SPACE_1).row();
        options.add(row).left().padBottom(Theme.SPACE_3).row();
    }

    /**
     * Builds one selectable pill: dark when selected, plain otherwise.
     *
     * @param label    the text
     * @param selected whether it is selected
     * @param onPick   what a click does
     * @return the pill
     */
    private Table pill(String label, boolean selected, Runnable onPick) {
        Table item = new Table();
        item.setBackground(selected ? ui.rounded(Theme.INK, Theme.INK, 0, 8)
            : ui.rounded(Theme.SURFACE_RAISED, Theme.LINE, 2, 8));
        item.pad(0f, Theme.SPACE_3, UiKit.SHAPE_RESERVE, Theme.SPACE_3);
        item.add(ui.label(label, Theme.TextStyle.BODY, selected ? Theme.ON_PRIMARY : Theme.INK));
        item.setTouchable(Touchable.enabled);
        item.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onPick.run();
            }
        });
        return item;
    }

    /**
     * Starts a new, empty board.
     */
    private void newBoard() {
        editor.load(new BoardDraft(NEW_ID, NEW_NAME, null));
        openedId = null;
        fillFields();
    }

    /**
     * Shows the list of boards in the folder to open one, after confirming that unsaved changes may be lost.
     */
    private void showOpenDialog() {
        confirmDiscard("Open another board without saving?", () -> {
            List<String> ids;
            try {
                ids = files.list();
            } catch (UncheckedIOException e) {
                showError("Could not list " + files.folder().toAbsolutePath() + ": " + e.getMessage());
                return;
            }
            ModalDialog dialog = new ModalDialog(ui, 640f, Theme.ACCENT);
            dialog.title("Open a board");
            if (ids.isEmpty()) {
                dialog.text("There are no boards in " + files.folder().toAbsolutePath(), Theme.TextStyle.BODY,
                    Theme.INK_MUTED);
            }
            for (String id : ids) {
                TextButton button = ui.button(id, Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
                onClick(button, () -> {
                    closeDialog(dialog);
                    open(id);
                });
                Table row = new Table();
                row.add(button).growX().height(52f);
                dialog.row(row);
            }
            TextButton cancel = ui.button("Cancel", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
            onClick(cancel, () -> closeDialog(dialog));
            dialog.buttons(160f, cancel).onEscape(() -> closeDialog(dialog));
            openDialog(dialog);
        });
    }

    /**
     * Opens a board from the folder.
     *
     * @param id the board's identifier
     */
    private void open(String id) {
        try {
            BoardDefinition definition = files.read(id);
            editor.load(BoardDraft.of(definition));
            openedId = id;
            fillFields();
        } catch (InvalidBoardException e) {
            showError("boards/" + id + ".json is not a valid board:\n" + String.join("\n", e.errors()));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            showError("Could not open boards/" + id + ".json: " + e.getMessage());
        }
    }

    /**
     * Saves the board, after confirming before it replaces a different board's file.
     */
    private void save() {
        if (saveButton.isDisabled()) {
            return;
        }
        String id = editor.draft().id();
        if (!id.equals(openedId) && files.exists(id)) {
            ModalDialog dialog = new ModalDialog(ui, 640f, Theme.DANGER);
            dialog.title("Replace boards/" + id + ".json?");
            dialog.text("A board with this id already exists. Saving replaces it.", Theme.TextStyle.BODY, Theme.INK);
            TextButton keep = ui.button("Cancel", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
            TextButton replace = ui.button("Replace", Theme.ButtonKind.DANGER, Theme.TextStyle.BUTTON);
            onClick(keep, () -> closeDialog(dialog));
            onClick(replace, () -> {
                closeDialog(dialog);
                write();
            });
            dialog.buttons(160f, keep, replace).onEscape(() -> closeDialog(dialog));
            openDialog(dialog);
        } else {
            write();
        }
    }

    /**
     * Writes the board to its file.
     */
    private void write() {
        try {
            files.write(editor.draft().toDefinition());
            openedId = editor.draft().id();
            editor.markSaved();
            refresh();
        } catch (UncheckedIOException e) {
            showError("Could not save: " + e.getMessage());
        }
    }

    /**
     * Runs an action at once if nothing is unsaved, otherwise after the user confirms throwing the changes away.
     *
     * @param question the question to ask
     * @param action   what to do
     */
    private void confirmDiscard(String question, Runnable action) {
        if (!editor.isDirty()) {
            action.run();
            return;
        }
        ModalDialog dialog = new ModalDialog(ui, 640f, Theme.DANGER);
        dialog.title(question);
        dialog.text("The board has changes that were not saved.", Theme.TextStyle.BODY, Theme.INK);
        TextButton keep = ui.button("Keep editing", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton discard = ui.button("Discard", Theme.ButtonKind.DANGER, Theme.TextStyle.BUTTON);
        onClick(keep, () -> closeDialog(dialog));
        onClick(discard, () -> {
            closeDialog(dialog);
            action.run();
        });
        dialog.buttons(new float[] {220f, 180f}, keep, discard).onEscape(() -> closeDialog(dialog));
        openDialog(dialog);
    }

    /**
     * Shows an error with a single OK button.
     *
     * @param message the error
     */
    private void showError(String message) {
        ModalDialog dialog = new ModalDialog(ui, 720f, Theme.DANGER);
        dialog.title("Something went wrong");
        dialog.text(message, Theme.TextStyle.BODY, Theme.INK);
        TextButton ok = ui.button("OK", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        onClick(ok, () -> closeDialog(dialog));
        dialog.buttons(140f, ok).onEscape(() -> closeDialog(dialog));
        openDialog(dialog);
    }

    /**
     * Shows a dialog; keyboard shortcuts pause while it is open.
     *
     * @param dialog the dialog
     */
    private void openDialog(ModalDialog dialog) {
        dialogOpen = true;
        dialog.show(stage);
    }

    /**
     * Hides a dialog.
     *
     * @param dialog the dialog
     */
    private void closeDialog(ModalDialog dialog) {
        dialog.hide();
        dialogOpen = false;
        stage.setKeyboardFocus(null);
    }

    /**
     * Runs an action when a button is clicked, unless it is disabled.
     *
     * @param button the button
     * @param action the action
     */
    private static void onClick(TextButton button, Runnable action) {
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!button.isDisabled()) {
                    action.run();
                }
            }
        });
    }

    /**
     * Names a direction in title case, for example {@code "North"}.
     *
     * @param direction the direction
     * @return the name
     */
    private static String title(Direction direction) {
        String name = direction.name();
        return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Returns how much larger than the design size to generate the fonts, so they stay sharp on a large monitor.
     *
     * @return the scale, at least 1
     */
    private static float fontScale() {
        return Math.max(1f, Gdx.graphics.getDisplayMode().height / (float) Theme.VIEW_HEIGHT);
    }

    /**
     * The keyboard shortcuts: undo, redo, save and turning the direction. They pause while a text field has the
     * keyboard or a dialog is open.
     */
    private final class ShortcutListener extends InputListener {

        /**
         * Handles a key press.
         *
         * @param event   the event
         * @param keycode the key
         * @return {@code true} if it was a shortcut
         */
        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (dialogOpen || stage.getKeyboardFocus() instanceof TextField) {
                return false;
            }
            boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            if (control && keycode == Input.Keys.Z) {
                if (shift ? editor.redo() : editor.undo()) {
                    fillFields();
                }
                return true;
            }
            if (control && keycode == Input.Keys.Y) {
                if (editor.redo()) {
                    fillFields();
                }
                return true;
            }
            if (control && keycode == Input.Keys.S) {
                save();
                return true;
            }
            if (!control && keycode == Input.Keys.R) {
                editor.rotateDirection(!shift);
                rebuildOptions();
                return true;
            }
            return false;
        }
    }
}
