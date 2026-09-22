package de.mkoehler.robotrampage.lwjgl3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Verifies {@link Lwjgl3Launcher#windowSize}: the pure arithmetic that picks the window size, without needing a real
 * window or monitor.
 *
 * @author Mario Koehler
 */
class Lwjgl3LauncherTest {

    /**
     * A monitor at least as big as the design's native size starts the window at exactly that size.
     */
    @Test
    void aBigEnoughMonitorGetsTheNativeSize() {
        assertArrayEquals(new int[] {1920, 1080}, Lwjgl3Launcher.windowSize(1920, 1080));
        assertArrayEquals(new int[] {1920, 1080}, Lwjgl3Launcher.windowSize(2560, 1440));
        assertArrayEquals(new int[] {1920, 1080}, Lwjgl3Launcher.windowSize(3440, 1440), "an ultrawide is wider than tall enough");
    }

    /**
     * A monitor narrower than the design's native size is width-constrained: the window uses the full width and the
     * matching 16:9 height, even when the monitor is tall enough for more.
     */
    @Test
    void aNarrowMonitorIsWidthConstrained() {
        assertArrayEquals(new int[] {1366, 768}, Lwjgl3Launcher.windowSize(1366, 900));
        assertArrayEquals(new int[] {1280, 720}, Lwjgl3Launcher.windowSize(1280, 1024), "a 5:4 monitor is taller than 16:9 needs");
    }

    /**
     * A monitor short enough that even its full width would overshoot 16:9 is height-constrained: the window uses the
     * full height and the matching 16:9 width.
     */
    @Test
    void aShortMonitorIsHeightConstrained() {
        assertArrayEquals(new int[] {1600, 900}, Lwjgl3Launcher.windowSize(2560, 900));
    }
}
