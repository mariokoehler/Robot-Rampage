package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.NetworkConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the name rules of {@link DisplayNames}.
 *
 * @author Mario Koehler
 */
class DisplayNamesTest {

    /**
     * Control characters and surrounding blanks are removed.
     */
    @Test
    void cleaningRemovesControlCharactersAndBlanks() {
        assertEquals("Bob", DisplayNames.clean("  Bo\u0007b\n"));
        assertEquals("", DisplayNames.clean(null));
    }

    /**
     * A normal name, including letters from other languages, has no problem, and the limit itself is allowed.
     */
    @Test
    void goodNamesHaveNoProblem() {
        assertNull(DisplayNames.problem("Mario"));
        assertNull(DisplayNames.problem("Łukasz Ödön"));
        assertNull(DisplayNames.problem("x".repeat(NetworkConstants.MAX_DISPLAY_NAME_LENGTH)));
    }

    /**
     * An empty name, one of only blanks and one that is too long are refused with a message for the player.
     */
    @Test
    void badNamesAreExplained() {
        assertEquals("Enter a name.", DisplayNames.problem(""));
        assertEquals("Enter a name.", DisplayNames.problem("  \t "));
        assertEquals("Enter a name.", DisplayNames.problem(null));
        assertNotNull(DisplayNames.problem("x".repeat(NetworkConstants.MAX_DISPLAY_NAME_LENGTH + 1)));
    }
}
