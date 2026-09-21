package de.mkoehler.robotrampage.rules;

import java.util.List;

/**
 * Decides when a game ends because too few robots are left (design.md 2.10). A game ends when a
 * robot touches the final flag (see {@code CheckpointResolver}), or when every robot but one has
 * been eliminated, which is what this class checks.
 *
 * @author Mario Koehler
 */
public final class GameOutcome {

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private GameOutcome() {
    }

    /**
     * Ends the game if every robot but one has been eliminated (the last one wins), or if every robot
     * has been (a game without a winner). Does nothing in a game with fewer than two robots, or if
     * the game is already over.
     *
     * @param state the game state
     * @param log   receives the {@link GameEvent.GameEnded} event
     */
    public static void endIfOneRobotIsLeft(GameState state, EventLog log) {
        if (state.isOver() || state.robots().size() < 2) {
            return;
        }
        List<Robot> alive = state.robots().stream().filter(robot -> robot.status() != RobotStatus.ELIMINATED).toList();
        if (alive.size() <= 1) {
            int winner = alive.isEmpty() ? GameState.NO_WINNER : alive.get(0).id();
            state.endGame(winner);
            log.add(new GameEvent.GameEnded(winner == GameState.NO_WINNER ? GameEvent.NO_ROBOT : winner));
        }
    }
}
