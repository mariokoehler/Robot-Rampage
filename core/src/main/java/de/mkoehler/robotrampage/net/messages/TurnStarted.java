package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.rules.LoggedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to everybody at the start of a turn, after destroyed robots have re-entered the board.
 *
 * @param turn the turn number, starting at 1
 * @param respawnEvents the re-entries that just happened
 * @param awaitedRobotIds the robots whose players must program this turn
 * @param programmingSeconds how long the players have
 * @author Mario Koehler
 */
public record TurnStarted(int turn, List<LoggedEvent> respawnEvents, List<Integer> awaitedRobotIds, int programmingSeconds) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param turn the turn number, starting at 1
     * @param respawnEvents the re-entries that just happened
     * @param awaitedRobotIds the robots whose players must program this turn
     * @param programmingSeconds how long the players have
     */
    public TurnStarted {
        respawnEvents = new ArrayList<>(respawnEvents);
        awaitedRobotIds = new ArrayList<>(awaitedRobotIds);
    }
}
