package de.mkoehler.robotrampage.client.board;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

/**
 * Where and how a robot is drawn. The position is in squares and may lie between two squares and the angle between two
 * headings, which is what lets an animation move a robot smoothly; a robot at rest has whole numbers.
 *
 * @param seat     the seat of the robot's player, 0 to 7, which picks its picture
 * @param x        the horizontal position of the robot's square in squares from the left edge of the board
 * @param y        the vertical position of the robot's square in squares from the bottom edge of the board
 * @param rotation the heading in degrees, counter-clockwise, 0 for north, as {@link BoardGeometry#rotation} returns
 * @author Mario Koehler
 */
public record RobotPose(int seat, float x, float y, float rotation) {

    /**
     * Creates the pose of a robot standing still on a square.
     *
     * @param seat     the seat of the robot's player
     * @param position the square
     * @param facing   the direction the robot faces
     * @return the pose
     */
    public static RobotPose at(int seat, Position position, Direction facing) {
        return new RobotPose(seat, position.x(), position.y(), BoardGeometry.rotation(facing));
    }
}
