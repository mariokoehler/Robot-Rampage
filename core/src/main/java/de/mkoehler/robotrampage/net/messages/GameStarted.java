package de.mkoehler.robotrampage.net.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to every player when the game starts, and again when a player reconnects: everything a
 * client needs to set up the game. The random seed is never part of it.
 *
 * @param boardJson the board as JSON text, readable with {@code BoardLoader.parse}
 * @param players all players, ordered by seat
 * @param yourRobotId the receiving player's own robot id, which is also their seat
 * @author Mario Koehler
 */
public record GameStarted(String boardJson, List<PlayerInfo> players, int yourRobotId) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param boardJson the board as JSON text, readable with {@code BoardLoader.parse}
     * @param players all players, ordered by seat
     * @param yourRobotId the receiving player's own robot id, which is also their seat
     */
    public GameStarted {
        players = new ArrayList<>(players);
    }
}
