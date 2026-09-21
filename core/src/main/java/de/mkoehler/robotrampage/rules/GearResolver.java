package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.SquareFeature;

/**
 * Resolves the gear sub-phase of a register (design.md 2.4, 2.9): every robot standing
 * on a gear is rotated 90 degrees in the gear's direction. Powered-down robots are
 * rotated too.
 *
 * @author Mario Koehler
 */
final class GearResolver {

    private final GameState state;
    private final MovementResolver movement;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    GearResolver(GameState state, EventLog log) {
        this.state = state;
        this.movement = new MovementResolver(state, log);
    }

    /**
     * Rotates every robot that stands on a gear, in robot id order.
     */
    void rotate() {
        for (Robot robot : state.robots()) {
            if (!robot.isActive()) {
                continue;
            }
            SquareFeature feature = state.board().featureAt(robot.position());
            if (feature == SquareFeature.GEAR_CLOCKWISE) {
                movement.rotate(robot, robot.facing().rotateRight(), RotationCause.GEAR);
            } else if (feature == SquareFeature.GEAR_COUNTERCLOCKWISE) {
                movement.rotate(robot, robot.facing().rotateLeft(), RotationCause.GEAR);
            }
        }
    }
}
