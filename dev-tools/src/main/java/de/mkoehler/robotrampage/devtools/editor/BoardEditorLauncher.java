package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

/**
 * Starts the board editor (design.md 3.13), a developer tool that is not part of the game. Run it with the
 * {@code assets} folder as the working directory, for example with {@code mvn -pl dev-tools compile exec:exec} or
 * {@code start_board_editor.cmd}; it opens and saves boards in {@code assets/boards}.
 *
 * @author Mario Koehler
 */
public final class BoardEditorLauncher {

    private static final float SCREEN_SHARE = 0.9f;

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private BoardEditorLauncher() {
    }

    /**
     * Opens the editor window.
     *
     * @param args optionally the id of a board in {@code assets/boards} to open, such as {@code proving-grounds}
     */
    public static void main(String[] args) {
        BoardEditorApp app = new BoardEditorApp(args.length > 0 ? args[0] : null);
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Board Editor");
        configuration.useVsync(true);
        Graphics.DisplayMode display = Lwjgl3ApplicationConfiguration.getDisplayMode();
        configuration.setForegroundFPS(display.refreshRate + 1);
        int width = Math.round(Math.min(display.width * SCREEN_SHARE, display.height * SCREEN_SHARE * 16f / 9f));
        configuration.setWindowedMode(width, Math.round(width * 9f / 16f));
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            /**
             * Lets the editor ask about unsaved changes before the window closes.
             *
             * @return {@code true} if the window may close
             */
            @Override
            public boolean closeRequested() {
                return app.closeRequested();
            }
        });
        new Lwjgl3Application(app, configuration);
    }
}
