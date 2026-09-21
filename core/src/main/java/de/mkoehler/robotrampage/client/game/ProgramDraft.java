package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.rules.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * The program a player is putting together for one turn: the dealt hand, the registers with the cards already locked in
 * them, and the cards placed in the free registers so far.
 * <p>
 * Locked registers are always the highest-numbered ones. A robot with two locked registers can fill registers 1 to 3 and
 * has registers 4 and 5 locked; a robot with all five locked has nothing to fill and confirms an empty program. Only the
 * free registers are sent to the server.
 *
 * @author Mario Koehler
 */
public final class ProgramDraft {

    /**
     * The number of registers.
     */
    public static final int REGISTERS = 5;

    /**
     * How one register looks.
     *
     * @param register the register number, 1 to 5
     * @param card     the card in it, or {@code null} if it is free and still empty
     * @param locked   whether the register is locked and its card cannot be changed
     */
    public record RegisterView(int register, Card card, boolean locked) {
    }

    private final List<Card> hand;
    private final List<Card> lockedCards;
    private final Card[] placed;

    /**
     * Creates a draft with nothing placed.
     *
     * @param hand        the dealt cards, in the order they are shown
     * @param lockedCards the cards in the locked registers, in register order, ending with register 5
     * @throws IllegalArgumentException if there are more locked cards than registers
     */
    public ProgramDraft(List<Card> hand, List<Card> lockedCards) {
        if (lockedCards.size() > REGISTERS) {
            throw new IllegalArgumentException("More locked cards than registers: " + lockedCards.size());
        }
        this.hand = List.copyOf(hand);
        this.lockedCards = List.copyOf(lockedCards);
        this.placed = new Card[REGISTERS - lockedCards.size()];
    }

    /**
     * Returns the dealt cards in the order they are shown, whether placed or not.
     *
     * @return the hand
     */
    public List<Card> hand() {
        return hand;
    }

    /**
     * Returns how many registers are free to be filled.
     *
     * @return 0 to 5
     */
    public int freeRegisterCount() {
        return placed.length;
    }

    /**
     * Returns how many locked registers there are.
     *
     * @return 0 to 5
     */
    public int lockedRegisterCount() {
        return lockedCards.size();
    }

    /**
     * Returns all five registers, in order: the free ones first, then the locked ones.
     *
     * @return the five registers
     */
    public List<RegisterView> registers() {
        List<RegisterView> views = new ArrayList<>();
        for (int index = 0; index < placed.length; index++) {
            views.add(new RegisterView(index + 1, placed[index], false));
        }
        for (int index = 0; index < lockedCards.size(); index++) {
            views.add(new RegisterView(placed.length + index + 1, lockedCards.get(index), true));
        }
        return views;
    }

    /**
     * Returns whether a card of the hand sits in a register.
     *
     * @param card the card
     * @return {@code true} if it is placed
     */
    public boolean isPlaced(Card card) {
        for (Card each : placed) {
            if (card.equals(each)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a card into the first free empty register.
     *
     * @param card a card of the hand
     * @return {@code true} if it was placed; {@code false} if it is not in the hand, is already placed, or every free
     * register is full
     */
    public boolean place(Card card) {
        for (int index = 0; index < placed.length; index++) {
            if (placed[index] == null) {
                return placeAt(card, index + 1);
            }
        }
        return false;
    }

    /**
     * Puts a card into a given free register. A card that is already in another register moves; a card that is already in
     * the register goes back to the hand.
     *
     * @param card     a card of the hand
     * @param register the register number, 1 to 5
     * @return {@code true} if it was placed; {@code false} if the card is not in the hand or the register is locked or does
     * not exist
     */
    public boolean placeAt(Card card, int register) {
        if (register < 1 || register > placed.length || !hand.contains(card)) {
            return false;
        }
        for (int index = 0; index < placed.length; index++) {
            if (card.equals(placed[index])) {
                placed[index] = null;
            }
        }
        placed[register - 1] = card;
        return true;
    }

    /**
     * Takes the card out of a register and back into the hand.
     *
     * @param register the register number, 1 to 5
     * @return the card that was taken back, or {@code null} if the register was empty, locked or does not exist
     */
    public Card take(int register) {
        if (register < 1 || register > placed.length) {
            return null;
        }
        Card card = placed[register - 1];
        placed[register - 1] = null;
        return card;
    }

    /**
     * Returns how many free registers are still empty.
     *
     * @return 0 to 5
     */
    public int missing() {
        int empty = 0;
        for (Card card : placed) {
            if (card == null) {
                empty++;
            }
        }
        return empty;
    }

    /**
     * Returns whether every free register is filled, so the program can be locked in. A robot with no free registers is
     * always complete.
     *
     * @return {@code true} if the program can be confirmed
     */
    public boolean isComplete() {
        return missing() == 0;
    }

    /**
     * Builds the message that locks the program in.
     *
     * @param turn          the turn the program is for
     * @param powerDown     whether to announce a power-down for after this turn
     * @param respawnFacing the facing to take after re-entering, or {@code null} to keep it
     * @return the message, with one card priority per free register in register order
     * @throws IllegalStateException if the program is not complete
     */
    public SubmitProgram toSubmit(int turn, boolean powerDown, Direction respawnFacing) {
        if (!isComplete()) {
            throw new IllegalStateException("The program is not complete: " + missing() + " registers are empty");
        }
        List<Integer> priorities = new ArrayList<>();
        for (Card card : placed) {
            priorities.add(card.priority());
        }
        return new SubmitProgram(turn, priorities, powerDown, respawnFacing);
    }
}
