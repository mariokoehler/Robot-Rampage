package de.mkoehler.robotrampage.net.messages;

/**
 * One board the host can choose in the lobby, as listed in {@link LobbyState#boards()}.
 *
 * @param id         the board's identifier, sent back in {@link SelectBoard}
 * @param name       the board's name
 * @param maxPlayers how many start squares it has, the most players it seats
 * @author Mario Koehler
 */
public record BoardChoice(String id, String name, int maxPlayers) {
}
