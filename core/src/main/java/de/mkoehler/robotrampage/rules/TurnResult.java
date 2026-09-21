package de.mkoehler.robotrampage.rules;

import java.util.List;

/**
 * The outcome of resolving one turn (design.md 3.4).
 *
 * @param state  the game state after the turn; a new object, the state that was passed in is
 *               left untouched
 * @param events everything that happened, in order, each stamped with its register and
 *               sub-phase
 * @author Mario Koehler
 */
public record TurnResult(GameState state, List<LoggedEvent> events) {
}
