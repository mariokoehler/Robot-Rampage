package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.Pusher;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the pusher sub-phase of a register (design.md 2.4, 2.9).
 * <p>
 * Every pusher active in the register pushes the robot standing on its square one
 * square away from the wall it is mounted on, with exactly the same push rules as a
 * robot moving by card (walls stop it, robots ahead are pushed along, pits and the
 * board edge destroy). Who is pushed is decided from the positions at the start of the
 * phase, so a robot that one pusher shoves onto another pusher's square is not pushed a
 * second time in the same register. Conversely, a pusher only fires if its robot is still
 * on the pusher's square by the time it is that pusher's turn: a robot that was pushed away
 * by another robot's push chain in the meantime is left alone.
 *
 * @author Mario Koehler
 */
final class PusherResolver {

    /**
     * A robot to push and the direction to push it in.
     *
     * @param robot     the robot standing on an active pusher
     * @param square    the pusher's square, where the robot stood at the start of the phase
     * @param direction the direction the pusher shoves it in
     */
    private record Push(Robot robot, Position square, Direction direction) {
    }

    private final GameState state;
    private final MovementResolver movement;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    PusherResolver(GameState state, EventLog log) {
        this.state = state;
        this.movement = new MovementResolver(state, log);
    }

    /**
     * Fires every pusher that is active in a register, in the order the pushers were
     * added to the board.
     *
     * @param register the register being resolved, 1 to 5
     */
    void fire(int register) {
        List<Push> pushes = new ArrayList<>();
        for (Pusher pusher : state.board().pushers()) {
            if (pusher.isActiveIn(register)) {
                state.robotAt(pusher.position())
                    .ifPresent(robot -> pushes.add(new Push(robot, pusher.position(), pusher.pushDirection())));
            }
        }
        for (Push push : pushes) {
            if (push.robot().isActive() && push.square().equals(push.robot().position())) {
                movement.step(push.robot(), push.direction(), MoveCause.PUSHER);
            }
        }
    }
}
