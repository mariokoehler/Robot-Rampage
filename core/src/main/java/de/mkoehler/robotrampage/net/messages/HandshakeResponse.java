package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the server in reply to a {@link HandshakeRequest}, indicating whether
 * the client has been accepted.
 *
 * @author Mario Koehler
 */
public class HandshakeResponse {

    /**
     * Whether the handshake was accepted.
     */
    private boolean accepted;

    /**
     * A human-readable message accompanying the result, suitable for showing
     * to the player (for example the reason for a rejection).
     */
    private String message;

    /**
     * The seat (and robot id) the player was given, or {@code -1} if the handshake was rejected.
     */
    private int seat = -1;

    /**
     * The token that lets the player take their seat back after a disconnect, or {@code null} if the
     * handshake was rejected.
     */
    private String sessionToken;

    /**
     * The version of the server that answered, so a client that is refused for its version can show both.
     */
    private String serverVersion;

    /**
     * How long a disconnected player may take to come back before their robot is removed, in whole seconds, or 0 if the
     * handshake was rejected.
     */
    private int reconnectGraceSeconds;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeResponse() {
    }

    /**
     * Creates a handshake response.
     *
     * @param accepted      whether the client's handshake was accepted
     * @param message       a human-readable message accompanying the result
     * @param serverVersion the version of the answering server
     */
    public HandshakeResponse(boolean accepted, String message, String serverVersion) {
        this.accepted = accepted;
        this.message = message;
        this.serverVersion = serverVersion;
    }

    /**
     * Creates an accepting handshake response that carries the player's seat and session token.
     *
     * @param message               a human-readable welcome
     * @param seat                  the seat, which is also the player's robot id
     * @param sessionToken          the token for resuming this seat after a disconnect
     * @param serverVersion         the version of the answering server
     * @param reconnectGraceSeconds how long a disconnected player may take to come back before their robot is removed
     */
    public HandshakeResponse(String message, int seat, String sessionToken, String serverVersion,
                             int reconnectGraceSeconds) {
        this.accepted = true;
        this.message = message;
        this.seat = seat;
        this.sessionToken = sessionToken;
        this.serverVersion = serverVersion;
        this.reconnectGraceSeconds = reconnectGraceSeconds;
    }

    /**
     * Returns whether the server accepted the handshake.
     *
     * @return {@code true} if the client was accepted
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Returns the human-readable message accompanying the result.
     *
     * @return the message, e.g. the rejection reason
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the seat the player was given.
     *
     * @return the seat, or {@code -1} if the handshake was rejected
     */
    public int getSeat() {
        return seat;
    }

    /**
     * Returns the token for resuming this seat.
     *
     * @return the token, or {@code null} if the handshake was rejected
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Returns the version of the server that answered.
     *
     * @return the server's build version
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Returns how long a disconnected player may take to come back before their robot is removed.
     *
     * @return the reconnect grace period in whole seconds, or 0 if the handshake was rejected
     */
    public int getReconnectGraceSeconds() {
        return reconnectGraceSeconds;
    }
}
