package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host to stop or restart the programming timer, for example for a break or a discussion of the rules. The
 * server only honours it from the host and only while players are programming.
 *
 * @param paused {@code true} to stop the timer, {@code false} to let it run on
 * @author Mario Koehler
 */
public record SetTimerPaused(boolean paused) {
}
