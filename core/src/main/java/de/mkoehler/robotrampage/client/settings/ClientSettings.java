package de.mkoehler.robotrampage.client.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * What the client remembers between runs: the server and the name last used on the connect screen, the session
 * token of the last game joined, and the Settings dialog's choices. Properties it does not know yet are ignored
 * when loading, so a file written by a newer version can still be read.
 *
 * @param serverAddress    the text of the address field, as typed
 * @param displayName      the text of the name field, as typed
 * @param sessionToken     the token of the last game this client joined, so relaunching after the app was closed (not
 *                         just a dropped connection, which {@code Reconnector} already covers in memory) can still try
 *                         to take the seat back; {@code null} before any game has been joined. A token the server no
 *                         longer recognises (a different game, or its own grace period already ran out) is always
 *                         harmless to send: {@code GameSession.join} simply falls back to an ordinary join by name.
 * @param volume           the master volume every sound effect is played at, 0 to 1
 * @param fullscreen       whether the window runs full screen
 * @param vsync            whether the window waits for the monitor's refresh
 * @param windowWidth      the windowed-mode width to restore when not full screen
 * @param windowHeight     the windowed-mode height to restore when not full screen
 * @param resolutionSpeed  the playback speed a turn's resolution starts at (1, 2 or 4); the in-turn buttons may still
 *                         change it for that turn without changing this
 * @param showGhostPath    whether the programming screen draws the dashed preview of the program being placed
 * @author Mario Koehler
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientSettings(String serverAddress, String displayName, String sessionToken, float volume,
                             boolean fullscreen, boolean vsync, int windowWidth, int windowHeight,
                             float resolutionSpeed, boolean showGhostPath) {

    /**
     * Replaces missing address/name values with empty text, so a hand-edited file cannot cause a {@code null} on the
     * connect screen. A missing session token stays {@code null}, its normal "no token yet" value.
     */
    public ClientSettings {
        serverAddress = serverAddress == null ? "" : serverAddress;
        displayName = displayName == null ? "" : displayName;
    }

    /**
     * The settings of a first run: the owner's own dedicated server (design.md 3.12) rather than a local dev
     * server, the name of the person logged in to this computer, no session token yet, and the Settings dialog's
     * own defaults (mockup 4.6): 80% volume, windowed at 1920x1080, vsync on, 2x resolution playback, the ghost
     * path shown.
     *
     * @return the default settings
     */
    public static ClientSettings defaults() {
        String user = System.getProperty("user.name", "");
        if (user.length() > NetworkConstants.MAX_DISPLAY_NAME_LENGTH) {
            user = user.substring(0, NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
        }
        return new ClientSettings("NAS5714.myqnapcloud.com:" + NetworkConstants.TCP_PORT, user, null, 0.8f, false,
            true, 1920, 1080, 2f, true);
    }

    /**
     * Returns a copy with another server address.
     *
     * @param address the new address text
     * @return the changed settings
     */
    public ClientSettings withServerAddress(String address) {
        return new ClientSettings(address, displayName, sessionToken, volume, fullscreen, vsync, windowWidth,
            windowHeight, resolutionSpeed, showGhostPath);
    }

    /**
     * Returns a copy with another display name.
     *
     * @param name the new name
     * @return the changed settings
     */
    public ClientSettings withDisplayName(String name) {
        return new ClientSettings(serverAddress, name, sessionToken, volume, fullscreen, vsync, windowWidth,
            windowHeight, resolutionSpeed, showGhostPath);
    }

    /**
     * Returns a copy with another session token, remembered after a successful handshake so a later run can present it
     * again.
     *
     * @param token the new token
     * @return the changed settings
     */
    public ClientSettings withSessionToken(String token) {
        return new ClientSettings(serverAddress, displayName, token, volume, fullscreen, vsync, windowWidth,
            windowHeight, resolutionSpeed, showGhostPath);
    }

    /**
     * Returns a copy with the Settings dialog's choices replaced. The rest (server, name, token) is carried over
     * unchanged, since the dialog never touches those.
     *
     * @param volume          the new master volume, 0 to 1
     * @param fullscreen      whether the window should run full screen
     * @param vsync           whether the window should wait for the monitor's refresh
     * @param windowWidth     the windowed-mode width to restore when not full screen
     * @param windowHeight    the windowed-mode height to restore when not full screen
     * @param resolutionSpeed the playback speed a turn's resolution should start at
     * @param showGhostPath   whether the programming screen should draw the ghost path
     * @return the changed settings
     */
    public ClientSettings withPreferences(float volume, boolean fullscreen, boolean vsync, int windowWidth,
                                          int windowHeight, float resolutionSpeed, boolean showGhostPath) {
        return new ClientSettings(serverAddress, displayName, sessionToken, volume, fullscreen, vsync, windowWidth,
            windowHeight, resolutionSpeed, showGhostPath);
    }
}
