package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.settings.ClientSettings;
import de.mkoehler.robotrampage.client.ui.ModalDialog;
import de.mkoehler.robotrampage.client.ui.PillToggle;
import de.mkoehler.robotrampage.client.ui.Slider;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

import java.util.function.Consumer;

/**
 * Builds the Settings dialog (design.md 4.6, the canvas's {@code Dlg-Settings}), reachable from both the Startup
 * screen and, once in a game, the in-game Menu — the reason this lives in its own class rather than inline in either
 * screen: both need the exact same content, only what closes it differs.
 * <p>
 * Two changes from the mockup, at the owner's request: a single volume slider stands in for the separate Music and
 * Sound effects sliders, since the game has no music; and "Skip resolution automatically" is dropped rather than
 * replaced — the turn timer the owner wanted instead is a game rule, not a personal preference, and lives in the
 * Lobby (host only) rather than here.
 * <p>
 * Every control applies and saves at once, live, rather than waiting for "Done": there is no "Cancel" in the mockup to
 * make a two-step apply/confirm meaningful, and seeing a volume or window change take effect immediately is the more
 * useful behaviour anyway. "Done" and Escape both simply close the dialog; "Reset" puts every control back to
 * {@link ClientSettings#defaults()}'s preferences and applies that at once too.
 *
 * @author Mario Koehler
 */
final class SettingsDialog {

    private static final float WIDTH = 1380f;
    private static final float COLUMN_GAP = 40f;
    private static final float SLIDER_WIDTH = 380f;

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private SettingsDialog() {
    }

    /**
     * Builds the dialog, already wired to apply and save every change against the game's current settings.
     *
     * @param game   the game, for the current settings and to apply and save changes
     * @param ui     the widget kit
     * @param onDone what closes the dialog (the caller's own {@code closeDialog}), also used for Escape
     * @return the dialog, not yet shown
     */
    static ModalDialog build(RobotRampageGame game, UiKit ui, Runnable onDone) {
        Model model = new Model(game.settings());

        Table audio = column(ui, "Audio", volumeRow(ui, game, model));
        Table graphics = column(ui, "Graphics",
            toggleRow(ui, game, model, "Fullscreen", model.fullscreen, value -> model.fullscreen = value,
                toggle -> model.fullscreenToggle = toggle),
            toggleRow(ui, game, model, "Vertical sync", model.vsync, value -> model.vsync = value,
                toggle -> model.vsyncToggle = toggle),
            windowSizeRow(ui, game, model));
        Table gameSection = column(ui, "Game", speedRow(ui, game, model), ghostPathRow(ui, game, model));

        Table columns = new Table();
        columns.add(audio).top().left().expandX();
        columns.add(graphics).top().left().expandX().padLeft(COLUMN_GAP);
        columns.add(gameSection).top().left().expandX().padLeft(COLUMN_GAP);

        TextButton reset = ui.button("Reset", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                model.resetToDefaults(game);
            }
        });
        TextButton done = ui.button("Done", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        done.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onDone.run();
            }
        });

        return new ModalDialog(ui, WIDTH, null)
            .title("Settings")
            .row(columns)
            .text("Settings are saved on this computer.", Theme.TextStyle.BODY_LARGE, Theme.INK_MUTED)
            .buttons(new float[] {180f, 240f}, reset, done)
            .onEscape(onDone);
    }

    /**
     * The dialog's own copy of the controls' state, applied and saved after every change. Kept together so
     * {@link #resetToDefaults} can put every control back to the defaults without the caller having to enumerate them.
     */
    private static final class Model {
        float volume;
        boolean fullscreen;
        boolean vsync;
        int windowWidth;
        int windowHeight;
        float resolutionSpeed;
        boolean showGhostPath;
        private Runnable refreshWindowSize;
        private Runnable refreshSpeed;
        private PillToggle fullscreenToggle;
        private PillToggle vsyncToggle;
        private PillToggle ghostToggle;
        private Slider volumeSlider;
        private Label volumeLabel;

        Model(ClientSettings settings) {
            volume = settings.volume();
            fullscreen = settings.fullscreen();
            vsync = settings.vsync();
            windowWidth = settings.windowWidth();
            windowHeight = settings.windowHeight();
            resolutionSpeed = settings.resolutionSpeed();
            showGhostPath = settings.showGhostPath();
        }

        /**
         * Saves and applies the current state of every control.
         *
         * @param game the game to save and apply against
         */
        void apply(RobotRampageGame game) {
            game.saveAndApplyPreferences(game.settings().withPreferences(volume, fullscreen, vsync, windowWidth,
                windowHeight, resolutionSpeed, showGhostPath));
        }

        /**
         * Puts every control back to {@link ClientSettings#defaults()}'s preferences, refreshes the ones that need
         * rebuilding to show it, and applies and saves the result.
         *
         * @param game the game to apply and save against
         */
        void resetToDefaults(RobotRampageGame game) {
            ClientSettings defaults = ClientSettings.defaults();
            volume = defaults.volume();
            fullscreen = defaults.fullscreen();
            vsync = defaults.vsync();
            windowWidth = defaults.windowWidth();
            windowHeight = defaults.windowHeight();
            resolutionSpeed = defaults.resolutionSpeed();
            showGhostPath = defaults.showGhostPath();
            volumeSlider.setValue(volume);
            volumeLabel.setText(percentText(volume));
            fullscreenToggle.setChecked(fullscreen);
            vsyncToggle.setChecked(vsync);
            ghostToggle.setChecked(showGhostPath);
            refreshWindowSize.run();
            refreshSpeed.run();
            apply(game);
        }
    }

    /**
     * Builds one of the three columns: a teal uppercase heading over its rows, each rows' gap matching the mockup.
     *
     * @param ui      the widget kit
     * @param heading the column's heading
     * @param rows    the rows, top to bottom
     * @return the column
     */
    private static Table column(UiKit ui, String heading, Table... rows) {
        Table column = new Table();
        column.top().left();
        column.add(ui.label(heading, Theme.TextStyle.LABEL, Theme.ACCENT)).left().padBottom(16f).row();
        for (Table row : rows) {
            column.add(row).left().padBottom(16f).row();
        }
        return column;
    }

    /**
     * Builds the single volume slider that stands in for the mockup's separate Music and Sound effects sliders.
     *
     * @param ui    the widget kit
     * @param game  the game, to preview the volume live while dragging
     * @param model the dialog's state
     * @return the row
     */
    private static Table volumeRow(UiKit ui, RobotRampageGame game, Model model) {
        Table row = new Table();
        row.top().left();
        Table header = new Table();
        header.add(ui.label("Volume", Theme.TextStyle.LABEL, Theme.INK)).left().expandX();
        Label percent = ui.label(percentText(model.volume), Theme.TextStyle.BODY, Theme.INK);
        header.add(percent);
        row.add(header).width(SLIDER_WIDTH).left().padBottom(8f).row();
        Slider slider = ui.slider(model.volume);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                model.volume = slider.getValue();
                ui.setText(percent, Theme.TextStyle.BODY, percentText(model.volume));
                model.apply(game);
            }
        });
        model.volumeSlider = slider;
        model.volumeLabel = percent;
        row.add(slider).size(SLIDER_WIDTH, 28f);
        return row;
    }

    /**
     * Builds a labelled on/off switch row.
     *
     * @param ui       the widget kit
     * @param game     the game, to apply and save the change at once
     * @param model    the dialog's state
     * @param label    what the switch is for
     * @param initial  whether it starts on
     * @param setter   where the switch's new value is written in {@code model}
     * @param register given the built switch, so {@code Model.resetToDefaults} can find it again; the volume slider and
     *                 ghost-path switch keep their own dedicated {@code Model} fields since only these two toggles are
     *                 built this generically
     * @return the row
     */
    private static Table toggleRow(UiKit ui, RobotRampageGame game, Model model, String label, boolean initial,
                                   Consumer<Boolean> setter, Consumer<PillToggle> register) {
        Table row = new Table();
        row.left();
        PillToggle toggle = ui.toggle();
        toggle.setChecked(initial);
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.accept(toggle.isChecked());
                model.apply(game);
            }
        });
        register.accept(toggle);
        row.add(toggle).size(52f, 28f);
        row.add(ui.label(label, Theme.TextStyle.LABEL, Theme.INK)).padLeft(12f);
        return row;
    }

    /**
     * Builds the "Show my program on the board" switch, with the mockup's explanatory line under it.
     *
     * @param ui    the widget kit
     * @param game  the game, to apply and save the change at once
     * @param model the dialog's state
     * @return the row
     */
    private static Table ghostPathRow(UiKit ui, RobotRampageGame game, Model model) {
        Table row = new Table();
        row.left();
        PillToggle toggle = ui.toggle();
        toggle.setChecked(model.showGhostPath);
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                model.showGhostPath = toggle.isChecked();
                model.apply(game);
            }
        });
        model.ghostToggle = toggle;
        row.add(toggle).size(52f, 28f).top();
        Table text = new Table();
        text.left();
        text.add(ui.label("Show my program on the board", Theme.TextStyle.LABEL, Theme.INK)).left().row();
        Label detail = ui.label("The dashed ghost path while you place cards.", Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        detail.setWrap(true);
        text.add(detail).width(300f).left();
        row.add(text).padLeft(12f);
        return row;
    }

    /**
     * Builds the "Window size" segmented control: the design's native 1920x1080 and two smaller 16:9 sizes.
     *
     * @param ui    the widget kit
     * @param game  the game, to apply and save the change at once
     * @param model the dialog's state
     * @return the row
     */
    private static Table windowSizeRow(UiKit ui, RobotRampageGame game, Model model) {
        int[][] sizes = {{1280, 720}, {1600, 900}, {1920, 1080}};
        Table column = new Table();
        column.left();
        column.add(ui.label("Window size", Theme.TextStyle.LABEL, Theme.INK)).left().padBottom(8f).row();
        Table row = new Table();
        column.add(row);
        Runnable refresh = () -> {
            row.clearChildren();
            for (int[] size : sizes) {
                boolean selected = size[0] == model.windowWidth && size[1] == model.windowHeight;
                row.add(segment(ui, size[0] + "×" + size[1], selected, () -> {
                    model.windowWidth = size[0];
                    model.windowHeight = size[1];
                    model.apply(game);
                })).height(44f + UiKit.SHAPE_RESERVE);
            }
        };
        model.refreshWindowSize = refresh;
        refresh.run();
        return column;
    }

    /**
     * Builds the "Resolution playback speed" segmented control: the same 1x/2x/4x choice the in-turn buttons offer,
     * here setting only what a turn's resolution starts at.
     *
     * @param ui    the widget kit
     * @param game  the game, to apply and save the change at once
     * @param model the dialog's state
     * @return the row
     */
    private static Table speedRow(UiKit ui, RobotRampageGame game, Model model) {
        float[] speeds = {1f, 2f, 4f};
        Table column = new Table();
        column.left();
        column.add(ui.label("Resolution playback speed", Theme.TextStyle.LABEL, Theme.INK)).left().padBottom(8f).row();
        Table row = new Table();
        column.add(row);
        Runnable refresh = () -> {
            row.clearChildren();
            for (float speedValue : speeds) {
                boolean selected = speedValue == model.resolutionSpeed;
                row.add(segment(ui, (int) speedValue + "×", selected, () -> {
                    model.resolutionSpeed = speedValue;
                    model.apply(game);
                })).height(44f + UiKit.SHAPE_RESERVE);
            }
        };
        model.refreshSpeed = refresh;
        refresh.run();
        return column;
    }

    /**
     * Rewrites a segmented row to show a newly picked value, then builds one segment of a control such as "Window
     * size" or "Resolution playback speed": a dark pill when selected, transparent otherwise, matching the mockup and
     * {@code GameScreen.refreshSpeed}'s own segmented control.
     *
     * @param ui       the widget kit
     * @param label    the segment's text
     * @param selected whether this is the picked value
     * @param onPick   what to do when this segment is clicked; the caller re-runs its own refresh afterwards
     * @return the segment
     */
    private static Table segment(UiKit ui, String label, boolean selected, Runnable onPick) {
        Table item = new Table();
        item.setBackground(selected ? ui.rounded(Theme.INK, Theme.INK, 0, 8)
            : ui.rounded(new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, 0f), 0, 8));
        item.padLeft(16f).padRight(16f).padBottom(UiKit.SHAPE_RESERVE);
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
     * Words a fraction as a whole-number percentage, for example {@code "80%"}.
     *
     * @param fraction the fraction, 0 to 1
     * @return the percentage text
     */
    private static String percentText(float fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
