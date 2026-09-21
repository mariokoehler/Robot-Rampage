package de.mkoehler.robotrampage.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.net.AppVersion;
import de.mkoehler.robotrampage.net.NetworkServer;
import de.mkoehler.robotrampage.session.SessionConfig;

import java.io.IOException;

/**
 * Headless libGDX application hosting the dedicated server: it starts the network endpoint and the
 * {@link ServerController} and, on every {@code render()}, lets the controller handle what the network threads queued and
 * let time pass. That loop is the single thread that ever touches the game session (design.md 3.5).
 *
 * @author Mario Koehler
 */
public class GameServer extends ApplicationAdapter {

    private static final String TAG = "GameServer";
    private static final String BOARD_RESOURCE = "boards/proving-grounds.json";

    private final int tcpPort;
    private final long seed;
    private NetworkServer network;
    private ServerController controller;

    /**
     * Creates the application.
     *
     * @param tcpPort the TCP port to listen on
     * @param seed    the seed of the game's randomness (shuffles and random fills); logged at startup so that a game can be
     *                reproduced from a bug report
     */
    public GameServer(int tcpPort, long seed) {
        this.tcpPort = tcpPort;
        this.seed = seed;
    }

    /**
     * Loads the board, starts listening and announces the server.
     */
    @Override
    public void create() {
        LoadedBoard board = BoardLoader.loadResource(BOARD_RESOURCE);
        board.warnings().forEach(warning -> Gdx.app.log(TAG, "Board warning: " + warning));
        network = new NetworkServer();
        try {
            network.start(tcpPort);
        } catch (IOException e) {
            throw new IllegalStateException("Could not listen on TCP port " + tcpPort, e);
        }
        controller = new ServerController(network, board, SessionConfig.defaults(), seed, System::currentTimeMillis);
        Gdx.app.log(TAG, "Robot Rampage server v" + AppVersion.getVersion() + " listening on TCP " + tcpPort
            + ", playing \"" + board.definition().name() + "\", game seed " + seed + ".");
    }

    /**
     * Handles queued network events and lets time pass in the session.
     */
    @Override
    public void render() {
        controller.update();
    }

    /**
     * Stops the network endpoint.
     */
    @Override
    public void dispose() {
        network.stop();
    }
}
