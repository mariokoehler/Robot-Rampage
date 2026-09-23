package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host to take everybody back to the lobby once they are done looking at the results (design.md 2.13).
 * Refused with {@link RequestRejected} for anybody else, or when the game has not ended.
 *
 * @author Mario Koehler
 */
public record ReturnToLobby() {
}
