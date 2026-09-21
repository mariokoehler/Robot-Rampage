package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Executes programming cards and single steps, including pushing (design.md 2.6).
 * <p>
 * A step in a direction fails if a wall is in the way. If the square ahead holds
 * another robot, that robot is pushed one square along, recursively; if any robot in
 * the resulting chain is blocked by a wall, <em>nobody</em> moves. The last robot of
 * a chain may be pushed onto a pit or off the board, which destroys it. A destroyed
 * robot loses whatever it still had to do, but the robot that pushed it carries on.
 *
 * @author Mario Koehler
 */
final class MovementResolver {

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    MovementResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Executes one programming card for a robot.
     *
     * @param robot the robot playing the card
     * @param card  the card to execute
     */
    void executeCard(Robot robot, Card card) {
        switch (card.type()) {
            case MOVE_1 -> walk(robot, robot.facing(), 1);
            case MOVE_2 -> walk(robot, robot.facing(), 2);
            case MOVE_3 -> walk(robot, robot.facing(), 3);
            case BACK_UP -> walk(robot, robot.facing().opposite(), 1);
            case ROTATE_LEFT -> rotate(robot, robot.facing().rotateLeft(), RotationCause.CARD);
            case ROTATE_RIGHT -> rotate(robot, robot.facing().rotateRight(), RotationCause.CARD);
            case U_TURN -> rotate(robot, robot.facing().opposite(), RotationCause.CARD);
        }
    }

    /**
     * Changes a robot's facing and logs it.
     *
     * @param robot  the robot to turn
     * @param facing the new facing
     * @param cause  why it turns
     */
    void rotate(Robot robot, Direction facing, RotationCause cause) {
        log.add(new GameEvent.RobotRotated(robot.id(), robot.facing(), facing, cause));
        robot.setFacing(facing);
    }

    /**
     * Moves a robot up to a number of single steps in a direction, without
     * changing its facing. Stops early if a step is blocked by a wall or once the
     * robot itself has been destroyed.
     *
     * @param robot     the moving robot
     * @param direction the direction to walk in
     * @param steps     the maximum number of steps
     */
    private void walk(Robot robot, Direction direction, int steps) {
        for (int step = 0; step < steps && robot.isActive(); step++) {
            if (!step(robot, direction, MoveCause.CARD)) {
                return;
            }
        }
    }

    /**
     * Moves a robot one square in a direction, pushing whatever stands in the way.
     * Used both for card movement and for pushers, which shove a robot exactly the
     * same way.
     *
     * @param robot     the robot to move
     * @param direction the direction of the step
     * @param cause     the cause to log for the moving robot itself; pushed robots
     *                  are always logged as {@link MoveCause#PUSHED}
     * @return {@code true} if the robot moved, {@code false} if a wall blocked it or
     *         a robot in the push chain
     */
    boolean step(Robot robot, Direction direction, MoveCause cause) {
        Board board = state.board();
        if (board.hasWall(robot.position(), direction)) {
            return false;
        }
        List<Robot> chain = new ArrayList<>();
        Position cursor = robot.position().step(direction);
        while (board.inBounds(cursor)) {
            Optional<Robot> occupant = state.robotAt(cursor);
            if (occupant.isEmpty()) {
                break;
            }
            if (board.hasWall(cursor, direction)) {
                return false;
            }
            chain.add(occupant.get());
            cursor = cursor.step(direction);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            Robot pushed = chain.get(i);
            moveTo(pushed, pushed.position().step(direction), MoveCause.PUSHED);
        }
        moveTo(robot, robot.position().step(direction), cause);
        return true;
    }

    /**
     * Puts a robot on a square, logs the move, and destroys the robot if that
     * square is a pit or off the board.
     *
     * @param robot  the robot to move
     * @param target the square it moves to; may be off the board
     * @param cause  why it moves
     */
    private void moveTo(Robot robot, Position target, MoveCause cause) {
        log.add(new GameEvent.RobotMoved(robot.id(), robot.position(), target, cause));
        robot.setPosition(target);
        Destruction.destroyIfOnHazard(state, robot, log);
    }
}
