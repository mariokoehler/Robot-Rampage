package de.mkoehler.robotrampage.client.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * What the client remembers between runs: the server and the name last used on the connect screen. It grows with the
 * settings screen (volumes, window size and so on); properties it does not know yet are ignored when loading, so a file
 * written by a newer version can still be read.
 *
 * @param serverAddress the text of the address field, as typed
 * @param displayName   the text of the name field, as typed
 * @author Mario Koehler
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientSettings(String serverAddress, String displayName) {

    /**
     * Replaces missing values with empty text, so a hand-edited file cannot cause a {@code null} on the connect screen.
     */
    public ClientSettings {
        serverAddress = serverAddress == null ? "" : serverAddress;
        displayName = displayName == null ? "" : displayName;
    }

    /**
     * The settings of a first run: a server on this computer, and the name of the person logged in to it.
     *
     * @return the default settings
     */
    public static ClientSettings defaults() {
        String user = System.getProperty("user.name", "");
        if (user.length() > NetworkConstants.MAX_DISPLAY_NAME_LENGTH) {
            user = user.substring(0, NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
        }
        return new ClientSettings("localhost:" + NetworkConstants.TCP_PORT, user);
    }

    /**
     * Returns a copy with another server address.
     *
     * @param address the new address text
     * @return the changed settings
     */
    public ClientSettings withServerAddress(String address) {
        return new ClientSettings(address, displayName);
    }

    /**
     * Returns a copy with another display name.
     *
     * @param name the new name
     * @return the changed settings
     */
    public ClientSettings withDisplayName(String name) {
        return new ClientSettings(serverAddress, name);
    }
}
