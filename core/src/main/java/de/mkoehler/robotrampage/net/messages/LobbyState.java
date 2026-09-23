package de.mkoehler.robotrampage.net.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * The state of the lobby, sent to everybody whenever it changes (someone joins, leaves, gets
 * ready, or the host changes).
 *
 * @param players            everyone in the lobby, ordered by seat
 * @param boardName          the name of the board that will be played
 * @param maxPlayers         how many players the board has start squares for
 * @param minPlayers         how many players are needed before the host can start the game
 * @param boardWidth         the width of the board in squares
 * @param boardHeight        the height of the board in squares
 * @param flagCount          how many flags the board has, to be touched in order
 * @param lives              how many lives each robot starts with
 * @param programmingSeconds the most time players get to program a turn
 * @param boardId            the id of the board that will be played
 * @param boardJson          that board as JSON, for the lobby's preview
 * @param boards             every board the host can choose from, in the server's order
 * @author Mario Koehler
 */
public record LobbyState(List<PlayerInfo> players, String boardName, int maxPlayers, int minPlayers, int boardWidth,
                         int boardHeight, int flagCount, int lives, int programmingSeconds, String boardId,
                         String boardJson, List<BoardChoice> boards) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param players            everyone in the lobby, ordered by seat
     * @param boardName          the name of the board that will be played
     * @param maxPlayers         how many players the board has start squares for
     * @param minPlayers         how many players are needed before the host can start the game
     * @param boardWidth         the width of the board in squares
     * @param boardHeight        the height of the board in squares
     * @param flagCount          how many flags the board has, to be touched in order
     * @param lives              how many lives each robot starts with
     * @param programmingSeconds the most time players get to program a turn
     * @param boardId            the id of the board that will be played
     * @param boardJson          that board as JSON, for the lobby's preview
     * @param boards             every board the host can choose from, in the server's order
     */
    public LobbyState {
        players = new ArrayList<>(players);
        boards = new ArrayList<>(boards);
    }
}
