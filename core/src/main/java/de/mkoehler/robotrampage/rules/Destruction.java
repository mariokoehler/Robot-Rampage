package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;

/**
 * Destroys robots (design.md 2.9): removes them from the board, takes a life and
 * returns their programmed cards to the discard pile.
 * <p>
 * Re-entering the board at the start of the next turn is not handled here.
 *
 * @author Mario Koehler
 */
final class Destruction {

    /**
     * Not instantiable; this class only exposes static helpers.
     */
    private Destruction() {
    }

    /**
     * Destroys a robot: it leaves the board, loses one life (becoming
     * {@link RobotStatus#ELIMINATED} at zero lives, otherwise
     * {@link RobotStatus#DESTROYED}), every card in its registers goes to the
     * discard pile, and any power-down is cancelled. Does nothing if the robot is
     * not active, so a robot can never be destroyed twice.
     * <p>
     * The {@link GameEvent.RobotDestroyed} event is logged <em>before</em> the robot's
     * state changes.
     *
     * @param state the game being resolved
     * @param robot the robot to destroy
     * @param cause why it is destroyed
     * @param log   receives the destruction event
     */
    static void destroy(GameState state, Robot robot, DestructionCause cause, EventLog log) {
        if (!robot.isActive()) {
            return;
        }
        log.add(new GameEvent.RobotDestroyed(robot.id(), cause));
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            Card card = robot.register(index);
            if (card != null) {
                state.deck().discard(card);
                robot.setRegister(index, null);
            }
        }
        robot.setPosition(null);
        robot.setDestructionOrder(state.nextDestructionOrder());
        robot.setPoweredDown(false);
        robot.setPowerDownAnnounced(false);
        robot.setLives(robot.lives() - 1);
        robot.setStatus(robot.lives() <= 0 ? RobotStatus.ELIMINATED : RobotStatus.DESTROYED);
    }

    /**
     * Destroys a robot that stands on a pit or off the board, if it does.
     *
     * @param state the game being resolved
     * @param robot the robot to check
     * @param log   receives the destruction event
     * @return {@code true} if the robot was destroyed
     */
    static boolean destroyIfOnHazard(GameState state, Robot robot, EventLog log) {
        if (!robot.isActive()) {
            return false;
        }
        Board board = state.board();
        if (!board.inBounds(robot.position())) {
            destroy(state, robot, DestructionCause.LEFT_BOARD, log);
            return true;
        }
        if (board.isPit(robot.position())) {
            destroy(state, robot, DestructionCause.PIT, log);
            return true;
        }
        return false;
    }
}
