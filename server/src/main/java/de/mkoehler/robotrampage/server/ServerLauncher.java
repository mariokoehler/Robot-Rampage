package de.mkoehler.robotrampage.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Entry point for the dedicated server process.
 *
 * @author Mario Koehler
 */
public final class ServerLauncher {

    /**
     * Not instantiable; this class only holds the process entry point.
     */
    private ServerLauncher() {
    }

    /**
     * Boots the headless libGDX application hosting {@link GameServer}.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        // The game is turn-based: there is no simulation to tick, only a loop to keep the process
        // alive and to drive turn timers later. A low rate is plenty.
        config.updatesPerSecond = 10;
        new HeadlessApplication(new GameServer(), config);
    }
}
