package de.mkoehler.robotrampage.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import de.mkoehler.robotrampage.net.AppVersion;

/**
 * Headless libGDX application hosting the dedicated server.
 * <p>
 * Currently only announces itself; the network endpoint and game sessions are
 * added as the design in design.md 3 is implemented.
 *
 * @author Mario Koehler
 */
public class GameServer extends ApplicationAdapter {

    private static final String TAG = "GameServer";

    /**
     * Logs the server's build version on startup.
     */
    @Override
    public void create() {
        Gdx.app.log(TAG, "Robot Rampage server v" + AppVersion.getVersion() + " started.");
    }
}
