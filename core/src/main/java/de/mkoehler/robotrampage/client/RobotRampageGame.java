package de.mkoehler.robotrampage.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Screen;
import de.mkoehler.robotrampage.client.audio.AudioKit;
import de.mkoehler.robotrampage.client.screen.StartupScreen;
import de.mkoehler.robotrampage.client.settings.ClientSettings;
import de.mkoehler.robotrampage.client.settings.SettingsStore;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

/**
 * The client's {@link com.badlogic.gdx.ApplicationListener}. It owns what every screen shares (the widget kit with the
 * fonts and shapes, the sound effects, and the remembered settings) and shows one {@link Screen} at a time, starting with
 * the {@link StartupScreen}.
 *
 * @author Mario Koehler
 */
public class RobotRampageGame extends Game {

    private static final float MAX_FONT_SCALE = 2f;

    private UiKit ui;
    private AudioKit audio;
    private SettingsStore settingsStore;
    private ClientSettings settings;

    /**
     * Builds the shared resources and shows the startup screen once the libGDX backend is ready, with a welcome jingle for
     * this one moment the application finishes loading — not played again by a later trip back to this screen, for example
     * from the Connect screen's "Back" button, which only reuses the same screen, not a fresh launch.
     */
    @Override
    public void create() {
        ui = new UiKit(Gdx.files.internal("fonts"), fontScale());
        audio = new AudioKit();
        ui.setAudio(audio);
        settingsStore = SettingsStore.inHomeDirectory();
        boolean firstRun = !settingsStore.exists();
        settings = settingsStore.load();
        if (firstRun) {
            // ClientSettings.defaults() hardcodes 1920x1080; on a smaller monitor the launcher already sized the
            // window down to fit (Lwjgl3Launcher.windowSize), so keep that instead of forcing 1920x1080 on it.
            settings = settings.withPreferences(settings.volume(), settings.fullscreen(), settings.vsync(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), settings.resolutionSpeed(), settings.showGhostPath());
        }
        applyPreferences(settings);
        setScreen(new StartupScreen(this));
        audio.play(AudioKit.Clip.WELCOME_JINGLE);
    }

    /**
     * Returns the widget kit that every screen builds its widgets from.
     *
     * @return the kit, valid from {@link #create()} until {@link #dispose()}
     */
    public UiKit ui() {
        return ui;
    }

    /**
     * Returns the sound effects every screen plays from.
     *
     * @return the kit, valid from {@link #create()} until {@link #dispose()}
     */
    public AudioKit audio() {
        return audio;
    }

    /**
     * Returns what the client remembers between runs.
     *
     * @return the settings as last loaded or saved
     */
    public ClientSettings settings() {
        return settings;
    }

    /**
     * Replaces the remembered settings and writes them to disk. A failure to write is ignored: the game then simply does
     * not remember the change. Use this for the address/name/token a screen remembers in passing; the Settings dialog's
     * own choices need {@link #saveAndApplyPreferences(ClientSettings)} instead, so they take effect at once.
     *
     * @param changed the new settings
     */
    public void saveSettings(ClientSettings changed) {
        settings = changed;
        settingsStore.save(changed);
    }

    /**
     * Replaces the remembered settings, writes them to disk and applies the Settings dialog's choices to the running
     * window and audio at once — the way to call {@link #saveSettings(ClientSettings)} whenever those particular
     * settings changed, as opposed to a screen just remembering a typed address or name.
     *
     * @param changed the new settings
     */
    public void saveAndApplyPreferences(ClientSettings changed) {
        saveSettings(changed);
        applyPreferences(changed);
    }

    /**
     * Applies the settings that have an effect beyond being remembered: the master volume, and the window's full
     * screen/windowed mode, size and vsync. Called once at startup and again whenever the Settings dialog changes one
     * of them.
     *
     * @param settings the settings to apply
     */
    private void applyPreferences(ClientSettings settings) {
        audio.setVolume(settings.volume());
        if (settings.fullscreen()) {
            Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
            if (!Gdx.graphics.isFullscreen() || Gdx.graphics.getWidth() != mode.width
                || Gdx.graphics.getHeight() != mode.height) {
                Gdx.graphics.setFullscreenMode(mode);
            }
        } else if (Gdx.graphics.isFullscreen() || Gdx.graphics.getWidth() != settings.windowWidth()
            || Gdx.graphics.getHeight() != settings.windowHeight()) {
            Gdx.graphics.setWindowedMode(settings.windowWidth(), settings.windowHeight());
        }
        Gdx.graphics.setVSync(settings.vsync());
    }

    /**
     * Shows a screen and disposes the one it replaces. {@link Game#setScreen} alone would only hide the old screen, and a
     * screen owns GPU resources. Screens change from inside their own {@code render} (a button was clicked, or a connection
     * was accepted), so the old screen is disposed after the current frame, not while it is still being drawn.
     *
     * @param screen the screen to show next
     */
    @Override
    public void setScreen(Screen screen) {
        Screen previous = getScreen();
        super.setScreen(screen);
        if (previous != null && previous != screen) {
            Gdx.app.postRunnable(previous::dispose);
        }
    }

    /**
     * Disposes the current screen and the shared resources along with the application.
     */
    @Override
    public void dispose() {
        Screen current = getScreen();
        super.dispose();
        if (current != null) {
            current.dispose();
        }
        if (ui != null) {
            ui.dispose();
        }
        if (audio != null) {
            audio.dispose();
        }
    }

    /**
     * Chooses how big the fonts are generated: sharp enough for the monitor the window is on, but never more than twice the
     * layout size, which keeps the glyph pages small.
     *
     * @return the scale for {@link Theme.Fonts#load}, at least 1
     */
    private static float fontScale() {
        float monitorHeight = Gdx.graphics.getDisplayMode().height;
        return Math.min(MAX_FONT_SCALE, Math.max(1f, monitorHeight / Theme.VIEW_HEIGHT));
    }
}
