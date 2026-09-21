package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.board.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent by a player to lock in their program for the turn. Cards are named by their unique
 * priority.
 *
 * @param turn the turn this program is for
 * @param cardPriorities one card per unlocked register, register 1 first; each must be in the dealt hand
 * @param powerDown whether to announce a power-down after this turn
 * @param respawnFacing the facing to take after re-entering, or {@code null} to keep it; honoured only in the turn the robot re-entered
 * @author Mario Koehler
 */
public record SubmitProgram(int turn, List<Integer> cardPriorities, boolean powerDown, Direction respawnFacing) {

    /**
     * Creates the message. Lists are copied into {@link ArrayList}s, the list type the wire format registers.
     *
     * @param turn the turn this program is for
     * @param cardPriorities one card per unlocked register, register 1 first; each must be in the dealt hand
     * @param powerDown whether to announce a power-down after this turn
     * @param respawnFacing the facing to take after re-entering, or {@code null} to keep it; honoured only in the turn the robot re-entered
     */
    public SubmitProgram {
        cardPriorities = new ArrayList<>(cardPriorities);
    }
}
