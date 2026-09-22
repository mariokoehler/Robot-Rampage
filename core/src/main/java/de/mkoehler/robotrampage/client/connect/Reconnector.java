package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.ServerLink;

import java.util.function.Supplier;

/**
 * Tries to take a seat back after the connection to a server has dropped: it presents the session token again, on a
 * fresh connection, at intervals, until it succeeds, the server refuses outright, or the reconnect grace period the
 * server granted at the original join runs out.
 * <p>
 * It never re-simulates or guesses: it only reports its state and, once {@link Phase#SUCCEEDED}, hands over the same
 * {@link ConnectedServer} a fresh join would, for the screen to rebuild itself from. It has no libGDX dependency, so it
 * can be driven by a fake {@link ServerLink} in a test.
 *
 * @author Mario Koehler
 */
public final class Reconnector {

    /**
     * How the attempt to get back in stands.
     */
    public enum Phase {
        /** A connection try is under way, or the next one is being waited out. */
        TRYING,
        /** The seat was taken back. */
        SUCCEEDED,
        /** Nothing more will be tried: the grace period ran out, the player cancelled, or the server refused outright. */
        GAVE_UP
    }

    /**
     * How long to wait after a failed connection try before the next one, in seconds.
     */
    private static final float RETRY_DELAY_SECONDS = 3f;

    private final ServerAddress address;
    private final String displayName;
    private final String sessionToken;
    private final String clientVersion;
    private final Supplier<ServerLink> linkFactory;
    private final float totalGraceSeconds;
    private Phase phase = Phase.TRYING;
    private ConnectionAttempt current;
    private int attemptNumber;
    private float remainingSeconds;
    private float retryCountdown;
    private String giveUpReason;
    private ConnectedServer connected;

    /**
     * Starts trying to reconnect at once.
     *
     * @param address       the server to reconnect to
     * @param displayName   the player's display name, sent along on every try; the server ignores it whenever the token
     *                      still names a seat, but a token the server no longer recognises (grace already expired there,
     *                      the process restarted, or the session moved back to the lobby and forgot the seat) falls back
     *                      to an ordinary join, which needs a real name to be worth anything
     * @param sessionToken  the token that names the seat to take back
     * @param clientVersion the version of this game
     * @param graceSeconds  how long the server will hold the seat, counted from now
     * @param linkFactory   builds a fresh connection for each try; a connection that failed or was closed cannot be reused
     */
    public Reconnector(ServerAddress address, String displayName, String sessionToken, String clientVersion,
                       float graceSeconds, Supplier<ServerLink> linkFactory) {
        this.address = address;
        this.displayName = displayName;
        this.sessionToken = sessionToken;
        this.clientVersion = clientVersion;
        this.linkFactory = linkFactory;
        this.totalGraceSeconds = graceSeconds;
        this.remainingSeconds = graceSeconds;
        startAttempt();
    }

    /**
     * Returns how the attempt stands.
     *
     * @return the phase
     */
    public Phase phase() {
        return phase;
    }

    /**
     * Returns how many connection tries have been started, including the one that may still be running.
     *
     * @return the attempt number, starting at 1
     */
    public int attemptNumber() {
        return attemptNumber;
    }

    /**
     * Returns whether a try just failed for a reason worth trying again for, and the next one is waiting out the retry
     * delay.
     *
     * @return {@code true} between a retryable failure and the next try starting
     */
    public boolean isWaitingToRetry() {
        return phase == Phase.TRYING && current == null;
    }

    /**
     * Returns how long the server will still hold the seat.
     *
     * @return the seconds left, rounded up, never negative
     */
    public int secondsLeft() {
        return (int) Math.max(0, Math.ceil(remainingSeconds));
    }

    /**
     * Returns how much of the grace period is left, for a progress bar.
     *
     * @return from 0 to 1
     */
    public float fractionLeft() {
        return totalGraceSeconds <= 0f ? 0f : Math.max(0f, Math.min(1f, remainingSeconds / totalGraceSeconds));
    }

    /**
     * Returns why the attempt was given up, once it has been.
     *
     * @return the reason, worded for the player, or {@code null} while still trying or once it has succeeded
     */
    public String giveUpReason() {
        return giveUpReason;
    }

    /**
     * Returns the connection that took the seat back.
     *
     * @return the connection, or {@code null} before {@link Phase#SUCCEEDED}
     */
    public ConnectedServer connected() {
        return connected;
    }

    /**
     * Lets time pass.
     *
     * @param seconds the seconds since the last call
     */
    public void update(float seconds) {
        if (phase != Phase.TRYING) {
            return;
        }
        remainingSeconds -= seconds;
        if (current != null) {
            current.update();
            advanceCurrent();
        } else if (retryCountdown > 0f) {
            retryCountdown -= seconds;
            if (retryCountdown <= 0f && remainingSeconds > 0f) {
                startAttempt();
            }
        }
        if (phase == Phase.TRYING && current == null && remainingSeconds <= 0f) {
            giveUp("The reconnect grace period ran out.");
        }
    }

    /**
     * Gives up early, for example because the player chose to leave instead of waiting.
     */
    public void cancel() {
        if (phase != Phase.TRYING) {
            return;
        }
        if (current != null) {
            current.cancel();
            current = null;
        }
        phase = Phase.GAVE_UP;
    }

    /**
     * Starts a connection try on a fresh connection.
     */
    private void startAttempt() {
        attemptNumber++;
        current = new ConnectionAttempt(linkFactory.get(), address, displayName, sessionToken, clientVersion);
        current.start();
    }

    /**
     * Reacts to the current connection try finishing, if it has.
     */
    private void advanceCurrent() {
        ConnectFlow flow = current.flow();
        if (!flow.isFinished()) {
            return;
        }
        switch (flow.phase()) {
            case ACCEPTED -> {
                connected = current.connected();
                phase = Phase.SUCCEEDED;
                current = null;
            }
            case UNREACHABLE -> {
                current = null;
                retryCountdown = RETRY_DELAY_SECONDS;
            }
            case VERSION_MISMATCH, REFUSED -> giveUp(flow.detail().isEmpty() ? "The server would not let you back in."
                : flow.detail());
            default -> current = null;
        }
    }

    /**
     * Ends the attempt for good.
     *
     * @param reason why, worded for the player
     */
    private void giveUp(String reason) {
        current = null;
        giveUpReason = reason;
        phase = Phase.GAVE_UP;
    }
}
