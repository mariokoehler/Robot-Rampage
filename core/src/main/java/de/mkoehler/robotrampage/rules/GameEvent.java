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
     * Stands in for a robot id where an event has no robot to name, for example the
     * source of a board laser or the target of a beam that hit nothing. Real robot ids
     * are never negative.
     */
    int NO_ROBOT = -1;

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
     * A laser fired one shot, which stopped at a wall, at the edge of the board or at
     * the first robot in its path. A board laser with several beams still produces a
     * single event; {@code beams} tells how strong it is.
     *
     * @param source        whether a board laser or a robot's laser fired
     * @param sourceRobotId the firing robot, or {@link #NO_ROBOT} for a board laser
     * @param from          the square the beam starts on: the emitter's square, or the
     *                      firing robot's square
     * @param direction     the direction the beam travels in
     * @param to            the last square the beam reached
     * @param hitRobotId    the robot that was hit, or {@link #NO_ROBOT} if the beam hit
     *                      nothing
     * @param beams         the number of beams, i.e. the damage the hit robot takes
     */
    record LaserFired(LaserSource source, int sourceRobotId, Position from, Direction direction, Position to,
                      int hitRobotId, int beams) implements GameEvent {
    }

    /**
     * A robot took damage from a laser.
     *
     * @param robotId     the damaged robot
     * @param amount      the damage taken by this hit
     * @param totalDamage the robot's total damage after the hit
     * @param source      the kind of laser that hit it
     */
    record RobotDamaged(int robotId, int amount, int totalDamage, LaserSource source) implements GameEvent {
    }

    /**
     * A robot's card for the current register was revealed. Only cards that are about to be
     * executed are revealed, in the {@link SubPhase#REVEAL} sub-phase of each register.
     *
     * @param robotId the robot whose card it is
     * @param card    the revealed card
     */
    record RegisterRevealed(int robotId, Card card) implements GameEvent {
    }

    /**
     * A robot touched its next flag.
     *
     * @param robotId    the robot
     * @param flagNumber the number of the flag, starting at 1
     * @param position   the flag's square
     */
    record FlagTouched(int robotId, int flagNumber, Position position) implements GameEvent {
    }

    /**
     * A robot's archive marker moved, because it touched a flag or ended a turn on a repair
     * site.
     *
     * @param robotId  the robot
     * @param position the new archive marker position
     */
    record ArchiveMarkerMoved(int robotId, Position position) implements GameEvent {
    }

    /**
     * A robot lost damage, from a repair site or from powering down.
     *
     * @param robotId     the repaired robot
     * @param amount      the damage removed
     * @param totalDamage its damage afterwards
     */
    record RobotRepaired(int robotId, int amount, int totalDamage) implements GameEvent {
    }

    /**
     * A robot shut down for the coming turn, fully repaired (design.md 2.8).
     *
     * @param robotId the robot
     */
    record RobotPoweredDown(int robotId) implements GameEvent {
    }

    /**
     * A robot came back on after a turn of being powered down.
     *
     * @param robotId the robot
     */
    record RobotPoweredUp(int robotId) implements GameEvent {
    }

    /**
     * A destroyed robot re-entered the board with no damage.
     *
     * @param robotId  the robot
     * @param position the square it re-entered on, normally its archive marker
     * @param facing   the direction it faces
     */
    record RobotRespawned(int robotId, Position position, Direction facing) implements GameEvent {
    }

    /**
     * The game ended.
     *
     * @param winnerRobotId the winning robot, or {@link #NO_ROBOT} if nobody won
     */
    record GameEnded(int winnerRobotId) implements GameEvent {
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
