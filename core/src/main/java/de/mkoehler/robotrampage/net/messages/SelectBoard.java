package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host, in the lobby, to choose the board the game will be played on (design.md 3.6). Refused with
 * {@link RequestRejected} for anybody else, outside the lobby, for an id the server does not offer, or for a board with
 * too few start squares for the seats already taken. Choosing another board clears everybody's ready flag.
 *
 * @param boardId the id of one of the {@link LobbyState#boards()}
 * @author Mario Koehler
 */
public record SelectBoard(String boardId) {
}
