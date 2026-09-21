package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by a player in the lobby to say whether they are ready to start.
 *
 * @param ready {@code true} if ready
 * @author Mario Koehler
 */
public record SetReady(boolean ready) {
}
