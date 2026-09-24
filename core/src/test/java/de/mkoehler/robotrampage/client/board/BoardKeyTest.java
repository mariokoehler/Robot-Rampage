package de.mkoehler.robotrampage.client.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every entry of the {@link BoardKey} can be drawn and explained.
 *
 * @author Mario Koehler
 */
class BoardKeyTest {

    /**
     * Every picture an entry names exists in {@code assets}, so no key icon silently draws nothing.
     */
    @Test
    void everyPictureExists() {
        for (BoardKey entry : BoardKey.values()) {
            assertFalse(entry.pictures().isEmpty(), entry + " has no picture");
            for (String path : entry.pictures()) {
                assertNotNull(getClass().getClassLoader().getResource(path), entry + ": missing " + path);
            }
        }
    }

    /**
     * Every entry has a name and an explanation for its tooltip.
     */
    @Test
    void everyEntryIsExplained() {
        for (BoardKey entry : BoardKey.values()) {
            assertFalse(entry.title().isBlank(), entry + " has no name");
            assertTrue(entry.explanation().length() > 40, entry + " has no real explanation");
        }
    }
}
