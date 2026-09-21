package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

/**
 * One atomic change to the game, recorded while a turn is resolved so clients can
 * animate it (design.md 3.4).
 * <p>
 * Events refer to robots by their stable {@link Robot#id() id}, never by index or
 * object identity, and carry everything a client needs to animate them so it never
 * has to re-derive rules. Which register and sub-phase an event belongs to is not
 * part of the event itself; the {@link LoggedEvent} wrapper carries that.
 *
 * @author Mario Koehler
 */
public sealed interface GameEvent {

    /**
     * A robot moved from one square to another. When the destination lies off the
     * board or is a pit the robot is destroyed right afterwards, which is reported
     * by a following {@link RobotDestroyed}.
     *
     * @param robotId the moving robot
     * @param from    the square it left
     * @param to      the square it entered; may be off the board
     * @param cause   why it moved
     */
    record RobotMoved(int robotId, Position from, Position to, MoveCause cause) implements GameEvent {
    }

    /**
     * A robot changed facing.
     *
     * @param robotId the rotating robot
     * @param from    its facing before
     * @param to      its facing after
     * @param cause   why it turned
     */
    record RobotRotated(int robotId, Direction from, Direction to, RotationCause cause) implements GameEvent {
    }

    /**
     * A robot was destroyed and removed from the board.
     *
     * @param robotId the destroyed robot
     * @param cause   why it was destroyed
     */
    record RobotDestroyed(int robotId, DestructionCause cause) implements GameEvent {
    }
}
