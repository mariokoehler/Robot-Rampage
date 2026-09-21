package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.rules.LoggedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to everybody when a player has been away for longer than the reconnect grace period and
 * their robot is removed.
 *
 * @param robotId the removed robot
 * @param events what happened to the robot
 * @author Mario Koehler
 */
public record PlayerLeft(int robotId, List<LoggedEvent> events) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param robotId the removed robot
     * @param events what happened to the robot
     */
    public PlayerLeft {
        events = new ArrayList<>(events);
    }
}
