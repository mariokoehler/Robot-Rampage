package de.mkoehler.robotrampage.net.messages;

/**
 * Sent to everybody when the time left to program changes: when the last unconfirmed player is
 * put under time pressure.
 *
 * @param secondsRemaining the seconds left, rounded up
 * @author Mario Koehler
 */
public record TimerUpdate(int secondsRemaining) {
}
