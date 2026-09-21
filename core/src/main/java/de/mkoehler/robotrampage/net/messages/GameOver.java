package de.mkoehler.robotrampage.net.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to everybody when the game has ended, and again to a player who comes back while the results are up.
 *
 * @param winnerRobotId  the winning robot, or -1 if nobody won
 * @param robots         the final state of every robot
 * @param lobbyInSeconds the seconds until the server takes everybody back to the lobby, rounded up
 * @author Mario Koehler
 */
public record GameOver(int winnerRobotId, List<RobotState> robots, int lobbyInSeconds) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param winnerRobotId  the winning robot, or -1 if nobody won
     * @param robots         the final state of every robot
     * @param lobbyInSeconds the seconds until the server takes everybody back to the lobby
     */
    public GameOver {
        robots = new ArrayList<>(robots);
    }
}
