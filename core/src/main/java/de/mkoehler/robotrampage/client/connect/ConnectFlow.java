package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.messages.HandshakeResponse;

/**
 * The state of one attempt to join a server, from the first connection try to the server's answer. It holds no
 * threads, sockets or libGDX objects: it is told what happened and works out what the player should see, so every path
 * through it can be tested and the screens only have to draw its {@link #phase()}.
 * <p>
 * Events that arrive in a state where they no longer matter are ignored. That includes everything after the attempt was
 * {@linkplain #cancel() cancelled}, so a connection that succeeds late cannot revive a cancelled attempt.
 *
 * @author Mario Koehler
 */
public final class ConnectFlow {

    /**
     * Where the attempt stands.
     */
    public enum Phase {
        /** Nothing has been started. */
        IDLE,
        /** The connection to the server is being opened. */
        CONNECTING,
        /** The connection is up and the request has been sent; the server has not answered yet. */
        HANDSHAKING,
        /** The server accepted the player. Final. */
        ACCEPTED,
        /** The server could not be reached, or dropped the connection before answering. Final. */
        UNREACHABLE,
        /** The server refused the player because it runs another version of the game. Final. */
        VERSION_MISMATCH,
        /** The server refused the player for another reason, for example because the game is full. Final. */
        REFUSED,
        /** The player gave up. Final. */
        CANCELLED
    }

    private final String clientVersion;
    private Phase phase = Phase.IDLE;
    private String detail = "";
    private HandshakeResponse response;

    /**
     * Creates an idle flow.
     *
     * @param clientVersion the version of this game, to tell a refusal for the version from any other refusal
     */
    public ConnectFlow(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    /**
     * Returns where the attempt stands.
     *
     * @return the phase
     */
    public Phase phase() {
        return phase;
    }

    /**
     * Returns whether the attempt has reached a final phase.
     *
     * @return {@code true} once the attempt was accepted, refused, failed or cancelled
     */
    public boolean isFinished() {
        return switch (phase) {
            case IDLE, CONNECTING, HANDSHAKING -> false;
            default -> true;
        };
    }

    /**
     * Returns the text that explains a failure: what went wrong for {@link Phase#UNREACHABLE}, and the server's message for
     * {@link Phase#REFUSED} and {@link Phase#VERSION_MISMATCH}.
     *
     * @return the explanation, empty when there is nothing to explain
     */
    public String detail() {
        return detail;
    }

    /**
     * Returns the version the answering server runs.
     *
     * @return the server's version, or an empty string if there is none to show
     */
    public String serverVersion() {
        return response == null || response.getServerVersion() == null ? "" : response.getServerVersion();
    }

    /**
     * Returns the server's answer once there is one.
     *
     * @return the response, or {@code null} while the server has not answered
     */
    public HandshakeResponse response() {
        return response;
    }

    /**
     * Starts the attempt.
     *
     * @throws IllegalStateException if the flow was used before
     */
    public void begin() {
        if (phase != Phase.IDLE) {
            throw new IllegalStateException("The attempt was already started: " + phase);
        }
        phase = Phase.CONNECTING;
    }

    /**
     * The connection to the server is up.
     *
     * @return {@code true} if the request should now be sent, {@code false} if the event no longer matters
     */
    public boolean onConnected() {
        if (phase != Phase.CONNECTING) {
            return false;
        }
        phase = Phase.HANDSHAKING;
        return true;
    }

    /**
     * The connection to the server could not be opened.
     *
     * @param reason what went wrong, in words for the player
     */
    public void onConnectFailed(String reason) {
        if (phase == Phase.CONNECTING) {
            phase = Phase.UNREACHABLE;
            detail = reason;
        }
    }

    /**
     * The server answered the request.
     *
     * @param answer the server's response
     */
    public void onResponse(HandshakeResponse answer) {
        if (phase != Phase.HANDSHAKING) {
            return;
        }
        response = answer;
        if (answer.isAccepted()) {
            phase = Phase.ACCEPTED;
            return;
        }
        detail = answer.getMessage() == null ? "" : answer.getMessage();
        boolean versionsDiffer = answer.getServerVersion() != null && !answer.getServerVersion().equals(clientVersion);
        phase = versionsDiffer ? Phase.VERSION_MISMATCH : Phase.REFUSED;
    }

    /**
     * The connection ended.
     */
    public void onDisconnected() {
        if (phase == Phase.HANDSHAKING) {
            phase = Phase.UNREACHABLE;
            detail = "The server closed the connection without answering.";
        }
    }

    /**
     * The player gave up. Has no effect once the attempt has finished.
     */
    public void cancel() {
        if (!isFinished()) {
            phase = Phase.CANCELLED;
        }
    }
}
