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
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeResponse() {
    }

    /**
     * Creates a handshake response.
     *
     * @param accepted whether the client's handshake was accepted
     * @param message  a human-readable message accompanying the result
     */
    public HandshakeResponse(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
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
}
