package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.net.AppVersion;

/**
 * Sent by a client immediately after a connection is established, announcing
 * who is connecting and which build they run.
 * <p>
 * The server checks {@link #getVersion()} against its own
 * {@link AppVersion#getVersion()} first and rejects a mismatch (see
 * {@link HandshakeResponse}). Accounts and authentication are not designed yet
 * (design.md 7), so for now the request only carries a display name.
 *
 * @author Mario Koehler
 */
public class HandshakeRequest {

    /**
     * The name other players see for this player.
     */
    private String displayName;

    /**
     * The client's build version.
     */
    private String version;

    /**
     * The session token from an earlier connection, presented to take the same seat back, or
     * {@code null} to join as a new player.
     */
    private String sessionToken;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeRequest() {
    }

    /**
     * Creates a handshake request.
     *
     * @param displayName the name other players should see this player as
     * @param version     the client's build version, normally
     *                    {@link AppVersion#getVersion()} &mdash; callers pass it
     *                    in explicitly so this class stays a plain data holder
     */
    public HandshakeRequest(String displayName, String version) {
        this(displayName, version, null);
    }

    /**
     * Creates a handshake request that tries to resume an earlier session.
     *
     * @param displayName  the name other players should see this player as
     * @param version      the client's build version
     * @param sessionToken the token a previous {@link HandshakeResponse} handed out, or {@code null}
     *                     to join as a new player
     */
    public HandshakeRequest(String displayName, String version, String sessionToken) {
        this.displayName = displayName;
        this.version = version;
        this.sessionToken = sessionToken;
    }

    /**
     * Returns the display name supplied by the connecting client.
     *
     * @return the requested display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the client's build version, to be checked against the server's
     * own {@link AppVersion#getVersion()}.
     *
     * @return the client's build version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the session token the client presents to resume its seat.
     *
     * @return the token, or {@code null} if the client is joining as a new player
     */
    public String getSessionToken() {
        return sessionToken;
    }
}
