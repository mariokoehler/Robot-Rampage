package de.mkoehler.robotrampage.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import de.mkoehler.robotrampage.net.NetworkConstants;

import java.security.SecureRandom;

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
     * @param args optionally the TCP port to listen on as the first argument (the default is
     *             {@link NetworkConstants#TCP_PORT}) and the game seed as the second (the default is a random one,
     *             which is logged at startup so a game can be reproduced)
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : NetworkConstants.TCP_PORT;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : new SecureRandom().nextLong();
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        // The game is turn-based: this loop only drains network events and advances timers, so a modest rate is plenty and
        // keeps reaction to a player's move well under a tenth of a second.
        config.updatesPerSecond = 20;
        new HeadlessApplication(new GameServer(port, seed), config);
    }
}
