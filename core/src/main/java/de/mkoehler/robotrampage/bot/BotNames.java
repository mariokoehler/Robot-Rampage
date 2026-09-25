package de.mkoehler.robotrampage.bot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The names bots play under: famous robots from films, television and games, picked at random.
 *
 * @author Mario Koehler
 */
public final class BotNames {

    /**
     * The name pool. Every name is unique (ignoring case) and short enough to be a display name.
     */
    public static final List<String> POOL = List.of(
        "C-3PO", "R2-D2", "WALL-E", "EVE", "Johnny 5", "Robby", "Bender", "Marvin", "HAL 9000", "T-800", "RoboCop",
        "Baymax", "Data", "K-9", "Optimus", "Bishop", "Gort", "Chappie", "Dalek", "GLaDOS");

    /**
     * Not instantiated.
     */
    private BotNames() {
    }

    /**
     * Picks a name from the pool that nobody at the table uses yet; if every name is taken, falls back to {@code Bot N}
     * with the lowest free number.
     *
     * @param taken  the names already seated, in any case
     * @param random picks among the free names
     * @return the name
     */
    public static String pick(Set<String> taken, Random random) {
        Set<String> lower = new HashSet<>();
        taken.forEach(name -> lower.add(name.toLowerCase(Locale.ROOT)));
        List<String> free = new ArrayList<>();
        for (String name : POOL) {
            if (!lower.contains(name.toLowerCase(Locale.ROOT))) {
                free.add(name);
            }
        }
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        for (int number = 1; ; number++) {
            String name = "Bot " + number;
            if (!lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
    }
}
