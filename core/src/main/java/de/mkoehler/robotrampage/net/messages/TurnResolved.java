package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.rules.LoggedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent to everybody once a turn has been played out: everything that happened, in order.
 * Clients replay it as an animation; they do not re-run the rules.
 *
 * @param turn the turn number
 * @param events the events of the turn, each stamped with its register and sub-phase
 * @author Mario Koehler
 */
public record TurnResolved(int turn, List<LoggedEvent> events) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param turn the turn number
     * @param events the events of the turn, each stamped with its register and sub-phase
     */
    public TurnResolved {
        events = new ArrayList<>(events);
    }
}
