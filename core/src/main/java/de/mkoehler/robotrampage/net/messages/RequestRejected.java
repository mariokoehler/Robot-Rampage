package de.mkoehler.robotrampage.net.messages;

/**
 * Sent privately when a request cannot be honoured: an invalid program, starting a game too
 * early, or anything else the server refuses. The player may try again.
 *
 * @param reason a message suitable for showing to the player
 * @author Mario Koehler
 */
public record RequestRejected(String reason) {
}
