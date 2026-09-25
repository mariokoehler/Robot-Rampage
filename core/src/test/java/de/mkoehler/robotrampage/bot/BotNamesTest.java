package de.mkoehler.robotrampage.bot;

import de.mkoehler.robotrampage.net.NetworkConstants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BotNames}.
 *
 * @author Mario Koehler
 */
class BotNamesTest {

    /**
     * Every name in the pool is different from the others, ignoring case, and fits as a display name.
     */
    @Test
    void thePoolHoldsUsableDistinctNames() {
        Set<String> seen = new HashSet<>();
        for (String name : BotNames.POOL) {
            assertTrue(seen.add(name.toLowerCase(Locale.ROOT)), "twice: " + name);
            assertTrue(!name.isBlank() && name.length() <= NetworkConstants.MAX_DISPLAY_NAME_LENGTH, name);
        }
        assertTrue(BotNames.POOL.size() >= 15);
    }

    /**
     * A name already seated is never picked again, whatever its case; once the pool is used up, bots are numbered.
     */
    @Test
    void takenNamesAreSkipped() {
        Set<String> taken = new HashSet<>();
        Random random = new Random(1);
        for (int i = 0; i < BotNames.POOL.size(); i++) {
            String name = BotNames.pick(taken, random);
            assertTrue(taken.add(name.toUpperCase(Locale.ROOT)), "picked a taken name: " + name);
        }
        assertEquals("Bot 1", BotNames.pick(taken, random));
        taken.add("bot 1");
        assertEquals("Bot 2", BotNames.pick(taken, random));
    }
}
