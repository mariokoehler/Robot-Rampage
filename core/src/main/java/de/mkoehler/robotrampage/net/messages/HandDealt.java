package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.rules.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent privately to a player at the start of a turn: their cards for this turn. Nobody else
 * ever sees them.
 *
 * @param turn the turn number
 * @param hand the dealt cards; empty for a robot that is powered down or has 9 damage
 * @param lockedCards the cards sitting in this robot's locked registers, in register order, ending with register 5
 * @param canChooseRespawnFacing whether the robot re-entered this turn, so its player may pick its facing
 * @param poweredDown whether the robot is shut down this turn; it needs no program, but may announce staying down
 * @author Mario Koehler
 */
public record HandDealt(int turn, List<Card> hand, List<Card> lockedCards, boolean canChooseRespawnFacing, boolean poweredDown) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param turn the turn number
     * @param hand the dealt cards; empty for a robot that is powered down or has 9 damage
     * @param lockedCards the cards sitting in this robot's locked registers, in register order, ending with register 5
     * @param canChooseRespawnFacing whether the robot re-entered this turn, so its player may pick its facing
     * @param poweredDown whether the robot is shut down this turn; it needs no program, but may announce staying down
     */
    public HandDealt {
        hand = new ArrayList<>(hand);
        lockedCards = new ArrayList<>(lockedCards);
    }
}
