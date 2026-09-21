package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host to start the game. The server only starts it if there are enough players and
 * everybody else is ready.
 *
 * @author Mario Koehler
 */
public record StartGameRequest() {
}
