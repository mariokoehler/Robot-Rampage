package de.mkoehler.robotrampage.lwjgl3;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import de.mkoehler.robotrampage.client.RobotRampageGame;

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
     * Builds the window configuration: vsync, a window sized to the design's native 1920x1080 (or the largest 16:9
     * window that still fits a smaller monitor, {@link #windowSize}) and the window icons.
     *
     * @return the configuration for the client window
     */
    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Robot Rampage");
        configuration.useVsync(true);
        Graphics.DisplayMode display = Lwjgl3ApplicationConfiguration.getDisplayMode();
        // Limits FPS to the refresh rate of the active monitor, plus 1 to match fractional refresh rates.
        configuration.setForegroundFPS(display.refreshRate + 1);
        int[] size = windowSize(display.width, display.height);
        configuration.setWindowedMode(size[0], size[1]);
        // Placeholder libGDX icons from lwjgl3/src/main/resources/, until we have a real logo.
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }

    /**
     * Picks the window size to start with: the design's native 1920 by 1080 (design.md 4.2) whenever the monitor is at
     * least that big, or otherwise the largest 16:9 window that still fits it, so the layout is never scaled up and never
     * clipped.
     *
     * @param monitorWidth  the active monitor's width, in pixels
     * @param monitorHeight the active monitor's height, in pixels
     * @return the window width and height, in that order
     */
    static int[] windowSize(int monitorWidth, int monitorHeight) {
        if (monitorWidth >= 1920 && monitorHeight >= 1080) {
            return new int[] {1920, 1080};
        }
        int width = Math.min(monitorWidth, monitorHeight * 16 / 9);
        int height = width * 9 / 16;
        return new int[] {width, height};
    }
}
