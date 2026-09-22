package de.mkoehler.robotrampage.client.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * What the client remembers between runs: the server and the name last used on the connect screen, and the session
 * token of the last game joined. It grows with the settings screen (volumes, window size and so on); properties it
 * does not know yet are ignored when loading, so a file written by a newer version can still be read.
 *
 * @param serverAddress the text of the address field, as typed
 * @param displayName   the text of the name field, as typed
 * @param sessionToken  the token of the last game this client joined, so relaunching after the app was closed (not just
 *                      a dropped connection, which {@code Reconnector} already covers in memory) can still try to take
 *                      the seat back; {@code null} before any game has been joined. A token the server no longer
 *                      recognises (a different game, or its own grace period already ran out) is always harmless to
 *                      send: {@code GameSession.join} simply falls back to an ordinary join by name.
 * @author Mario Koehler
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientSettings(String serverAddress, String displayName, String sessionToken) {

    /**
     * Replaces missing address/name values with empty text, so a hand-edited file cannot cause a {@code null} on the
     * connect screen. A missing session token stays {@code null}, its normal "no token yet" value.
     */
    public ClientSettings {
        serverAddress = serverAddress == null ? "" : serverAddress;
        displayName = displayName == null ? "" : displayName;
    }

    /**
     * The settings of a first run: a server on this computer, the name of the person logged in to it, and no session
     * token yet.
     *
     * @return the default settings
     */
    public static ClientSettings defaults() {
        String user = System.getProperty("user.name", "");
        if (user.length() > NetworkConstants.MAX_DISPLAY_NAME_LENGTH) {
            user = user.substring(0, NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
        }
        return new ClientSettings("localhost:" + NetworkConstants.TCP_PORT, user, null);
    }

    /**
     * Returns a copy with another server address.
     *
     * @param address the new address text
     * @return the changed settings
     */
    public ClientSettings withServerAddress(String address) {
        return new ClientSettings(address, displayName, sessionToken);
    }

    /**
     * Returns a copy with another display name.
     *
     * @param name the new name
     * @return the changed settings
     */
    public ClientSettings withDisplayName(String name) {
        return new ClientSettings(serverAddress, name, sessionToken);
    }

    /**
     * Returns a copy with another session token, remembered after a successful handshake so a later run can present it
     * again.
     *
     * @param token the new token
     * @return the changed settings
     */
    public ClientSettings withSessionToken(String token) {
        return new ClientSettings(serverAddress, displayName, token);
    }
}
