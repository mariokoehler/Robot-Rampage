package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;

import java.util.List;

/**
 * Resolves the checkpoint sub-phase of a register (design.md 2.4, 2.10).
 * <p>
 * A robot touches its next flag only if it has touched all lower-numbered flags and
 * <em>ends the register</em> standing on it; passing over a flag mid-register does
 * nothing. Touching a flag moves the robot's archive marker onto it. The robot that
 * touches the final flag wins and the game ends. Two robots can never share a square, so at
 * most one robot can stand on the final flag when a register ends: there is no tie to break.
 *
 * @author Mario Koehler
 */
final class CheckpointResolver {

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    CheckpointResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Lets every active robot touch its next flag if it stands on it.
     */
    void touch() {
        List<Position> flags = state.board().flags();
        for (Robot robot : state.robots()) {
            if (!robot.isActive()) {
                continue;
            }
            int next = robot.flagsTouched() + 1;
            if (next > flags.size() || !flags.get(next - 1).equals(robot.position())) {
                continue;
            }
            robot.setFlagsTouched(next);
            log.add(new GameEvent.FlagTouched(robot.id(), next, robot.position()));
            if (!robot.position().equals(robot.archiveMarker())) {
                robot.setArchiveMarker(robot.position());
                log.add(new GameEvent.ArchiveMarkerMoved(robot.id(), robot.position()));
            }
            if (next == flags.size() && !state.isOver()) {
                state.endGame(robot.id());
                log.add(new GameEvent.GameEnded(robot.id()));
            }
        }
    }
}
