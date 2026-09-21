package de.mkoehler.robotrampage.net.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * The public state of every robot, sent after each turn and when a player reconnects. Hands and
 * programs are not part of it.
 *
 * @param turn the turn number this state is after
 * @param robots every robot of the game, ordered by id
 * @param over whether the game has ended
 * @param winnerRobotId the winning robot, or -1 if there is none (yet)
 * @author Mario Koehler
 */
public record StateSnapshot(int turn, List<RobotState> robots, boolean over, int winnerRobotId) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param turn the turn number this state is after
     * @param robots every robot of the game, ordered by id
     * @param over whether the game has ended
     * @param winnerRobotId the winning robot, or -1 if there is none (yet)
     */
    public StateSnapshot {
        robots = new ArrayList<>(robots);
    }
}
