package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.rules.CardType;

import java.util.Locale;

/**
 * What a programming card is called and which picture it shows. The frame, the priority number and the name are drawn by
 * the client; only the picture of the action is a file.
 *
 * @author Mario Koehler
 */
public final class CardLook {

    /**
     * Not instantiable; this class only holds static lookups.
     */
    private CardLook() {
    }

    /**
     * Returns the name printed on a card.
     *
     * @param type the card type
     * @return for example {@code Move 2} or {@code U-Turn}
     */
    public static String name(CardType type) {
        return switch (type) {
            case MOVE_1 -> "Move 1";
            case MOVE_2 -> "Move 2";
            case MOVE_3 -> "Move 3";
            case BACK_UP -> "Back Up";
            case ROTATE_LEFT -> "Rotate Left";
            case ROTATE_RIGHT -> "Rotate Right";
            case U_TURN -> "U-Turn";
        };
    }

    /**
     * Returns the path of the picture on a card, relative to the asset folder.
     *
     * @param type the card type
     * @return for example {@code cards/move-2.png}
     */
    public static String picture(CardType type) {
        return "cards/" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".png";
    }
}
