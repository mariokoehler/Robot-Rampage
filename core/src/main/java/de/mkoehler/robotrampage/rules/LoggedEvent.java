package de.mkoehler.robotrampage.rules;

/**
 * A {@link GameEvent} together with where in the turn it happened.
 *
 * @param register the register the event belongs to, 1 to 5, or {@code 0} for
 *                 events outside any register (the {@link SubPhase#CLEANUP} phase)
 * @param phase    the sub-phase of that register
 * @param event    what happened
 * @author Mario Koehler
 */
public record LoggedEvent(int register, SubPhase phase, GameEvent event) {
}
