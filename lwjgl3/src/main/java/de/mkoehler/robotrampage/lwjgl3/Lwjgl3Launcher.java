package de.mkoehler.robotrampage.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import de.mkoehler.robotrampage.RobotRampageGame;

/**
 * Launches the desktop (LWJGL3) client.
 *
 * @author Mario Koehler
 */
public class Lwjgl3Launcher {

    /**
     * Not instantiable; this class only holds the process entry point.
     */
    private Lwjgl3Launcher() {
    }

    /**
     * Starts the client, unless {@link StartupHelper} had to relaunch the JVM
     * (macOS and some Linux setups), in which case the relaunched JVM does the
     * actual work.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return;
        }
        createApplication();
    }

    /**
     * Creates and starts the LWJGL3 application.
     *
     * @return the running application
     */
    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new RobotRampageGame(), getDefaultConfiguration());
    }

    /**
     * Builds the window configuration: vsync, a maximized 1920x1080 window
     * (the restore size if the player un-maximizes) and the window icons.
     *
     * @return the configuration for the client window
     */
    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Robot Rampage");
        configuration.useVsync(true);
        // Limits FPS to the refresh rate of the active monitor, plus 1 to match fractional refresh rates.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1920, 1080);
        configuration.setMaximized(true);
        // Placeholder libGDX icons from lwjgl3/src/main/resources/, until we have a real logo.
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
