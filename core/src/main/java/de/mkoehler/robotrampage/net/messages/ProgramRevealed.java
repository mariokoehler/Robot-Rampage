package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.rules.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells a player the actual cards in their own robot's five registers, once their program for the turn is locked in
 * for a reason that left them not knowing what it contains: the server filled it in at random because the timer ran
 * out, or they are only finding out now because they were not connected when it happened. Sent only to that player;
 * a program the player locked in themselves needs no such message, since they already know what they placed.
 *
 * @param turn  the turn the cards were played in
 * @param cards all five registers, in order, whether locked by damage or by this turn's program
 * @author Mario Koehler
 */
public record ProgramRevealed(int turn, List<Card> cards) {

    /**
     * Copies the cards, so the record cannot be changed afterwards.
     */
    public ProgramRevealed {
        cards = new ArrayList<>(cards);
    }
}
