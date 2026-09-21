package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves the crusher sub-phase of a register (design.md 2.4, 2.9): every robot
 * standing on a crusher that is active in the register is destroyed.
 *
 * @author Mario Koehler
 */
final class CrusherResolver {

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    CrusherResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Destroys every robot on a crusher that is active in a register. Crushers are
     * processed in a fixed order (west to east, then south to north) so the event
     * order is deterministic.
     *
     * @param register the register being resolved, 1 to 5
     */
    void crush(int register) {
        List<Position> crushers = state.board().crusherPositions().stream()
            .sorted(Comparator.comparingInt(Position::x).thenComparingInt(Position::y))
            .toList();
        for (Position position : crushers) {
            if (state.board().isCrusherActive(position, register)) {
                state.robotAt(position).ifPresent(robot ->
                    Destruction.destroy(state, robot, DestructionCause.CRUSHER, log));
            }
        }
    }
}
