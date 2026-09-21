package de.mkoehler.robotrampage.net.messages;

/**
 * Sent to everybody when the host stops or restarts the programming timer, and to a player who comes back while it is
 * stopped. The timer also restarts by itself when the turn is resolved, which is announced with this message too.
 *
 * @param paused           {@code true} while the timer is stopped
 * @param secondsRemaining the seconds left to program, rounded up
 * @author Mario Koehler
 */
public record TimerPaused(boolean paused, int secondsRemaining) {
}
