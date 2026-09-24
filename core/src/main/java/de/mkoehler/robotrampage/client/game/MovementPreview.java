package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.Pusher;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.rules.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A rough, purely local guess at where a robot's own cards would take it, for the "ghost path" the programming screen
 * draws while a player is placing cards (design.md 3.5, 4.3): a convenience only, never authoritative, and not a
 * substitute for the server's real turn resolution.
 * <p>
 * Simplified from the real rules (design.md 2.4) in one way only: other robots are never simulated, neither as
 * movers nor as obstacles, since their programs are secret and this player cannot predict where they will be —
 * showing them as immovable would misrepresent the real turn just as much as ignoring them, so the simpler of the two
 * wrong pictures wins. After the robot's own card, this register's board effects run exactly as
 * {@code TurnResolver} orders them (express belts, then all belts, then the pusher on this square if any, then a
 * gear): a robot standing still on a belt still rides it, and a belt that curves into another belt turns the robot to
 * match, exactly like {@code BeltResolver}. The first thing, own card or board effect, that would destroy the robot (a
 * pit, or off the board) ends the preview with one last step marked {@link Step#destroyed() destroyed}, and nothing
 * after it runs, exactly as a destroyed robot plays no more of a real turn. That step always lies on the board, so it
 * can be drawn: the pit itself, or the last square the robot stood on before it left the board. Only destruction by
 * the robot's own cards and the board effects simulated here is foreseen; crushers and lasers are not simulated, and
 * neither is another robot pushing this one off the board.
 *
 * @author Mario Koehler
 */
public final class MovementPreview {

    /**
     * Where the robot would be after one register of the preview.
     *
     * @param position  the square; for a destroyed robot, the pit it fell into or the last square it stood on before
     *                  leaving the board
     * @param facing    the direction the robot would face
     * @param destroyed whether this register destroys the robot, which makes this the preview's last step
     */
    public record Step(Position position, Direction facing, boolean destroyed) {

        /**
         * Creates a step the robot survives.
         *
         * @param position the square
         * @param facing   the direction the robot would face
         */
        public Step(Position position, Direction facing) {
            this(position, facing, false);
        }
    }

    /**
     * A robot's position and facing partway through a register's board effects.
     *
     * @param position  the square; for a destroyed robot, where it was lost, as in {@link Step#position()}
     * @param facing    the direction faced
     * @param destroyed whether the robot has been destroyed, after which no later phase moves it
     */
    private record Pose(Position position, Direction facing, boolean destroyed) {
    }

    /**
     * Where a walk ended.
     *
     * @param position  the square reached; for a destroyed robot, the pit it fell into or the last square it stood on
     *                  before leaving the board
     * @param destroyed whether the walk destroyed the robot
     */
    private record Walk(Position position, boolean destroyed) {
    }

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private MovementPreview() {
    }

    /**
     * Plays a sequence of cards against a board, one register at a time, and returns where each register leaves the
     * robot once its own card and that register's board effects have both run.
     *
     * @param board       the board
     * @param start       the robot's position before the first card
     * @param startFacing the robot's facing before the first card
     * @param cards       the cards to play, in the order they would run (register 1 first)
     * @return one step per register played; if a register would destroy the robot, its step is marked
     *         {@link Step#destroyed() destroyed} and is the last one, even if cards are left
     */
    public static List<Step> path(Board board, Position start, Direction startFacing, List<Card> cards) {
        List<Step> steps = new ArrayList<>();
        Position position = start;
        Direction facing = startFacing;
        for (int index = 0; index < cards.size(); index++) {
            Card card = cards.get(index);
            int register = index + 1;
            Walk moved = new Walk(position, false);
            switch (card.type()) {
                case MOVE_1 -> moved = walk(board, position, facing, 1);
                case MOVE_2 -> moved = walk(board, position, facing, 2);
                case MOVE_3 -> moved = walk(board, position, facing, 3);
                case BACK_UP -> moved = walk(board, position, facing.opposite(), 1);
                case ROTATE_LEFT -> facing = facing.rotateLeft();
                case ROTATE_RIGHT -> facing = facing.rotateRight();
                case U_TURN -> facing = facing.opposite();
            }
            Pose pose = new Pose(moved.position(), facing, moved.destroyed());
            pose = crossBelt(board, pose, true);
            pose = crossBelt(board, pose, false);
            pose = push(board, pose, register);
            pose = turnOnGear(board, pose);
            position = pose.position();
            facing = pose.facing();
            steps.add(new Step(position, facing, pose.destroyed()));
            if (pose.destroyed()) {
                break;
            }
        }
        return steps;
    }

    /**
     * Moves a robot up to a number of single steps in a direction, stopping early at a wall.
     *
     * @param board     the board
     * @param position  the robot's position before this card
     * @param direction the direction to walk in
     * @param steps     the maximum number of steps
     * @return where the walk ended: the square reached, or, if a step would destroy the robot, the pit it fell into or
     *         the last square it stood on before leaving the board
     */
    private static Walk walk(Board board, Position position, Direction direction, int steps) {
        for (int step = 0; step < steps; step++) {
            if (board.hasWall(position, direction)) {
                return new Walk(position, false);
            }
            Position next = position.step(direction);
            if (!board.inBounds(next)) {
                return new Walk(position, true);
            }
            if (board.featureAt(next) == SquareFeature.PIT) {
                return new Walk(next, true);
            }
            position = next;
        }
        return new Walk(position, false);
    }

    /**
     * Carries a robot one square along the belt it stands on, if any, and turns it to match a belt it lands on that
     * curves away from the direction it just travelled, exactly like {@code BeltResolver}.
     *
     * @param board       the board
     * @param pose        the robot's pose before this pass
     * @param expressOnly {@code true} to move only if the belt is an express belt
     * @return the pose after the pass, marked destroyed if it would destroy the robot; a destroyed robot is returned
     *         unchanged
     */
    private static Pose crossBelt(Board board, Pose pose, boolean expressOnly) {
        if (pose.destroyed()) {
            return pose;
        }
        Optional<Belt> belt = board.beltAt(pose.position());
        if (belt.isEmpty() || (expressOnly && !belt.get().express())) {
            return pose;
        }
        Direction heading = belt.get().direction();
        Walk walked = walk(board, pose.position(), heading, 1);
        if (walked.destroyed()) {
            return new Pose(walked.position(), pose.facing(), true);
        }
        Position moved = walked.position();
        if (moved.equals(pose.position())) {
            return pose;
        }
        Direction facing = pose.facing();
        Optional<Belt> landedOn = board.beltAt(moved);
        if (landedOn.isPresent()) {
            int quarterTurns = heading.clockwiseQuarterTurnsTo(landedOn.get().direction());
            if (quarterTurns == 1) {
                facing = facing.rotateRight();
            } else if (quarterTurns == 3) {
                facing = facing.rotateLeft();
            }
        }
        return new Pose(moved, facing, false);
    }

    /**
     * Shoves a robot away from the pusher on its square, if one is active in this register, exactly like
     * {@code PusherResolver}.
     *
     * @param board    the board
     * @param pose     the robot's pose before this phase
     * @param register the register being played, 1 to 5
     * @return the pose after the phase, marked destroyed if it would destroy the robot; a destroyed robot is returned
     *         unchanged
     */
    private static Pose push(Board board, Pose pose, int register) {
        if (pose.destroyed()) {
            return pose;
        }
        for (Pusher pusher : board.pushers()) {
            if (pusher.isActiveIn(register) && pusher.position().equals(pose.position())) {
                Walk walked = walk(board, pose.position(), pusher.pushDirection(), 1);
                return new Pose(walked.position(), pose.facing(), walked.destroyed());
            }
        }
        return pose;
    }

    /**
     * Turns a robot standing on a gear, exactly like {@code GearResolver}.
     *
     * @param board the board
     * @param pose  the robot's pose before this phase
     * @return the turned pose; a destroyed robot is returned unchanged
     */
    private static Pose turnOnGear(Board board, Pose pose) {
        if (pose.destroyed()) {
            return pose;
        }
        SquareFeature feature = board.featureAt(pose.position());
        if (feature == SquareFeature.GEAR_CLOCKWISE) {
            return new Pose(pose.position(), pose.facing().rotateRight(), false);
        } else if (feature == SquareFeature.GEAR_COUNTERCLOCKWISE) {
            return new Pose(pose.position(), pose.facing().rotateLeft(), false);
        }
        return pose;
    }
}
