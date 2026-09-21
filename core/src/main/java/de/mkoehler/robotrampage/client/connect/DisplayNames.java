package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * The rules for the name a player plays under, checked on the connect screen before anything is sent. They are the same
 * rules the server applies to the name it receives, so the name shown in the lobby is the name that was typed.
 *
 * @author Mario Koehler
 */
public final class DisplayNames {

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private DisplayNames() {
    }

    /**
     * Removes control characters and surrounding blanks.
     *
     * @param raw what was typed, may be {@code null}
     * @return the cleaned name, possibly empty
     */
    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").trim();
    }

    /**
     * Says what is wrong with a name.
     *
     * @param raw what was typed, may be {@code null}
     * @return a message that can be shown to the player, or {@code null} if the name is fine
     */
    public static String problem(String raw) {
        String cleaned = clean(raw);
        if (cleaned.isEmpty()) {
            return "Enter a name.";
        }
        if (cleaned.length() > NetworkConstants.MAX_DISPLAY_NAME_LENGTH) {
            return "Use at most " + NetworkConstants.MAX_DISPLAY_NAME_LENGTH + " characters.";
        }
        return null;
    }
}
