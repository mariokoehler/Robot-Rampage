package de.mkoehler.robotrampage.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the {@link GameEvent}s of a turn in the order they happen, stamping
 * each with the register and sub-phase that was current when it was added.
 * <p>
 * The resolver calls {@link #enter(int, SubPhase)} whenever a new sub-phase
 * begins; everything added afterwards belongs to it.
 *
 * @author Mario Koehler
 */
public final class EventLog {

    private final List<LoggedEvent> entries = new ArrayList<>();
    private int register;
    private SubPhase phase = SubPhase.CLEANUP;

    /**
     * Starts a new sub-phase; events added from now on are recorded as belonging to it.
     *
     * @param register the register, 1 to 5, or {@code 0} outside any register
     * @param phase    the sub-phase being entered
     */
    public void enter(int register, SubPhase phase) {
        this.register = register;
        this.phase = phase;
    }

    /**
     * Records an event in the current register and sub-phase.
     *
     * @param event what happened
     */
    public void add(GameEvent event) {
        entries.add(new LoggedEvent(register, phase, event));
    }

    /**
     * Returns all recorded events with their context, in order.
     *
     * @return an unmodifiable view of the recorded events
     */
    public List<LoggedEvent> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns just the events, without their register and sub-phase; convenient
     * for assertions that do not care where in the turn something happened.
     *
     * @return the events in order
     */
    public List<GameEvent> events() {
        return entries.stream().map(LoggedEvent::event).toList();
    }
}
