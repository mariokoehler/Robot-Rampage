package de.mkoehler.robotrampage.net.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to everybody when the game has ended, and again to a player who comes back while the results are up. The
 * session stays in the results phase until the host sends {@link ReturnToLobby} (design.md 2.13); there is no
 * automatic timer.
 *
 * @param winnerRobotId the winning robot, or -1 if nobody won
 * @param robots        the final state of every robot
 * @author Mario Koehler
 */
public record GameOver(int winnerRobotId, List<RobotState> robots) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param winnerRobotId the winning robot, or -1 if nobody won
     * @param robots        the final state of every robot
     */
    public GameOver {
        robots = new ArrayList<>(robots);
    }
}
