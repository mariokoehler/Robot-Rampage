package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.RobotStatus;

/**
 * The public state of one robot: everything every player may know about it. Cards, whether in a hand or
 * in registers, are deliberately not part of it.
 *
 * @param robotId              the robot's id, which is also its seat
 * @param position             where it stands, or {@code null} if it is not on the board
 * @param facing               the direction it faces
 * @param damage               its damage tokens
 * @param lives                its remaining lives
 * @param flagsTouched         the number of the highest flag it has touched, 0 for none
 * @param archiveMarker        the square it returns to when destroyed
 * @param status               whether it is active, destroyed or eliminated
 * @param poweredDown          whether it is shut down this turn
 * @param powerDownAnnounced   whether its player has announced a power-down for after this turn
 * @author Mario Koehler
 */
public record RobotState(int robotId, Position position, Direction facing, int damage, int lives, int flagsTouched,
                         Position archiveMarker, RobotStatus status, boolean poweredDown, boolean powerDownAnnounced) {

    /**
     * Takes a snapshot of a robot's public state.
     *
     * @param robot the robot
     * @return its public state
     */
    public static RobotState of(Robot robot) {
        return new RobotState(robot.id(), robot.position(), robot.facing(), robot.damage(), robot.lives(),
            robot.flagsTouched(), robot.archiveMarker(), robot.status(), robot.isPoweredDown(),
            robot.isPowerDownAnnounced());
    }
}
