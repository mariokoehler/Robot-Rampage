package de.mkoehler.robotrampage.rules;

/**
 * Removes a robot from the game because its player has left for good (design.md 2.13): the robot
 * leaves the board, its programmed cards go back to the discard pile and it is
 * {@link RobotStatus#ELIMINATED}, whatever lives it had left.
 *
 * @author Mario Koehler
 */
public final class Forfeit {

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private Forfeit() {
    }

    /**
     * Eliminates a robot. A robot that is on the board is destroyed first (logging a
     * {@link GameEvent.RobotDestroyed} with cause {@link DestructionCause#FORFEIT}); one that is
     * waiting to re-enter gets the same event; a robot that is already eliminated is left alone.
     *
     * @param state   the game state to mutate
     * @param robotId the id of the robot whose player has left
     * @param log     receives the destruction event
     * @throws IllegalArgumentException if there is no robot with that id
     */
    public static void forfeit(GameState state, int robotId, EventLog log) {
        Robot robot = state.robot(robotId);
        if (robot.status() == RobotStatus.ELIMINATED) {
            return;
        }
        if (robot.isActive()) {
            Destruction.destroy(state, robot, DestructionCause.FORFEIT, log);
        } else {
            log.add(new GameEvent.RobotDestroyed(robotId, DestructionCause.FORFEIT));
        }
        robot.setLives(0);
        robot.setStatus(RobotStatus.ELIMINATED);
        robot.setPosition(null);
    }
}
