package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.rules.CardType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the names and pictures of the seven card types.
 *
 * @author Mario Koehler
 */
class CardLookTest {

    /**
     * Every card type has the name the design prints on it.
     */
    @Test
    void cardsHaveTheirDesignNames() {
        assertEquals("Move 1", CardLook.name(CardType.MOVE_1));
        assertEquals("Back Up", CardLook.name(CardType.BACK_UP));
        assertEquals("Rotate Left", CardLook.name(CardType.ROTATE_LEFT));
        assertEquals("Rotate Right", CardLook.name(CardType.ROTATE_RIGHT));
        assertEquals("U-Turn", CardLook.name(CardType.U_TURN));
    }

    /**
     * Every card type names a picture that exists in the asset folder, so a renamed file cannot go unnoticed until a hand
     * is dealt.
     */
    @Test
    void everyPictureExists() {
        for (CardType type : CardType.values()) {
            assertNotNull(getClass().getClassLoader().getResource(CardLook.picture(type)), CardLook.picture(type));
        }
        assertEquals("cards/u-turn.png", CardLook.picture(CardType.U_TURN));
        assertEquals("cards/move-3.png", CardLook.picture(CardType.MOVE_3));
    }
}
