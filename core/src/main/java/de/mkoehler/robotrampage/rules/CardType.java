package de.mkoehler.robotrampage.rules;

/**
 * The kinds of programming card in the shared 84-card deck (design.md 2.5).
 *
 * @author Mario Koehler
 */
public enum CardType {

    /**
     * Move one square forward.
     */
    MOVE_1(18),

    /**
     * Move two squares forward.
     */
    MOVE_2(12),

    /**
     * Move three squares forward.
     */
    MOVE_3(6),

    /**
     * Move one square backward without changing facing.
     */
    BACK_UP(6),

    /**
     * Rotate 90 degrees counter-clockwise in place.
     */
    ROTATE_LEFT(18),

    /**
     * Rotate 90 degrees clockwise in place.
     */
    ROTATE_RIGHT(18),

    /**
     * Rotate 180 degrees in place.
     */
    U_TURN(6);

    private final int copiesInDeck;

    /**
     * Creates a card type.
     *
     * @param copiesInDeck how many cards of this type the full deck contains
     */
    CardType(int copiesInDeck) {
        this.copiesInDeck = copiesInDeck;
    }

    /**
     * Returns how many cards of this type the full deck contains.
     *
     * @return the number of copies in the 84-card deck
     */
    public int copiesInDeck() {
        return copiesInDeck;
    }
}
