package de.mkoehler.robotrampage.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import de.mkoehler.robotrampage.client.screen.StartupScreen;
import de.mkoehler.robotrampage.client.settings.ClientSettings;
import de.mkoehler.robotrampage.client.settings.SettingsStore;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

/**
 * The client's {@link com.badlogic.gdx.ApplicationListener}. It owns what every screen shares (the widget kit with the
 * fonts and shapes, and the remembered settings) and shows one {@link Screen} at a time, starting with the
 * {@link StartupScreen}.
 *
 * @author Mario Koehler
 */
public class RobotRampageGame extends Game {

    private static final float MAX_FONT_SCALE = 2f;

    private UiKit ui;
    private SettingsStore settingsStore;
    private ClientSettings settings;

    /**
     * Builds the shared resources and shows the startup screen once the libGDX backend is ready.
     */
    @Override
    public void create() {
        ui = new UiKit(Gdx.files.internal("fonts"), fontScale());
        settingsStore = SettingsStore.inHomeDirectory();
        settings = settingsStore.load();
        setScreen(new StartupScreen(this));
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
     * Returns what the client remembers between runs.
     *
     * @return the settings as last loaded or saved
     */
    public ClientSettings settings() {
        return settings;
    }

    /**
     * Replaces the remembered settings and writes them to disk. A failure to write is ignored: the game then simply does
     * not remember the change.
     *
     * @param changed the new settings
     */
    public void saveSettings(ClientSettings changed) {
        settings = changed;
        settingsStore.save(changed);
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
