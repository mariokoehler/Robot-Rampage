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
 * @param rotation the heading in degrees, counter-clockwise, 0 for north, as {@link BoardGeometry#rotation} returns; it may
 *                 run past 360 or below 0 while a robot turns
 * @param alpha     how opaque the robot is, from 0 (gone) to 1
 * @param tag       the damage taken in the moment being shown, drawn as a tag next to the robot, or 0 for none
 * @param showBadge whether the seat-number badge is drawn; the wedge (facing) is always drawn regardless. Turned off
 *                  for the programming screen's "ghost path" (design.md 3.5, 4.3): several ghosts of the *same* robot
 *                  can be on screen together, where the same repeated number adds nothing a live robot's badge does
 *                  (telling two different players' robots apart) and is only noise.
 * @author Mario Koehler
 */
public record RobotPose(int seat, float x, float y, float rotation, float alpha, int tag, boolean showBadge) {

    /**
     * Creates the pose of a fully visible robot without a damage tag, with its badge shown.
     *
     * @param seat     the seat of the robot's player
     * @param x        the horizontal position in squares
     * @param y        the vertical position in squares
     * @param rotation the heading in degrees, counter-clockwise, 0 for north
     */
    public RobotPose(int seat, float x, float y, float rotation) {
        this(seat, x, y, rotation, 1f, 0, true);
    }

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
