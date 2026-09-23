package de.mkoehler.robotrampage.client.debug;

/**
 * Prints one line to standard output for every protocol message this client sends or receives, and for the client's
 * own program before it is sent (where the message alone only carries card priorities, not card types). Every line
 * starts with {@code TURNLOG}, so a playtester can filter the console down to just these lines and paste them into a
 * bug report; {@link Object#toString()} on every message here is a Java record's default, which already lists every
 * field by name, so no per-message-type formatting is needed. A temporary playtest-debugging tool (added
 * 2026-09-24), living in the unwatched, untested screen layer on purpose (design.md 3.8) rather than in
 * {@code client.game}, which stays free of I/O so it can keep being driven by tests.
 *
 * @author Mario Koehler
 */
public final class TurnLog {

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private TurnLog() {
    }

    /**
     * Logs one line, tagged so it can be found and filtered easily.
     *
     * @param line the line to log
     */
    public static void log(String line) {
        System.out.println("TURNLOG " + line);
    }
}
