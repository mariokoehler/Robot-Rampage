package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.rules.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A rough, purely local guess at where a robot's own cards would take it, for the "ghost path" the programming screen
 * draws while a player is placing cards (design.md 3.5, 4.3): a convenience only, never authoritative, and not a
 * substitute for the server's real turn resolution.
 * <p>
 * Deliberately simplified from the real rules (design.md 2.4): only this robot's own cards move it here — belts,
 * pushers, gears, lasers and crushers are not simulated, since they are already visible on the board as static
 * pictures and this preview is only about what the player's own choices do this register. Other robots are treated
 * as immovable obstacles, never pushed, since their programs are secret and unknown to this client: the real turn may
 * push straight through a square this preview shows as blocking, or push this robot somewhere this preview never
 * shows. The preview simply ends at the first card that would destroy the robot (a pit, or off the board) — cards
 * after that never execute for a destroyed robot either, and no waypoint is added for the destroying square itself.
 *
 * @author Mario Koehler
 */
public final class MovementPreview {

    /**
     * Where the robot would be after one card of the preview.
     *
     * @param position the square
     * @param facing   the direction the robot would face
     */
    public record Step(Position position, Direction facing) {
    }

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private MovementPreview() {
    }

    /**
     * Plays a sequence of cards against a board, one at a time, and returns where each one leaves the robot.
     *
     * @param board       the board
     * @param start       the robot's position before the first card
     * @param startFacing the robot's facing before the first card
     * @param cards       the cards to play, in the order they would run (register 1 first)
     * @param obstacles   the squares other robots currently stand on; treated as always blocked, never pushed
     * @return one step per card that ran to completion; shorter than {@code cards} if a card would destroy the robot,
     *         in which case nothing beyond that card is included
     */
    public static List<Step> path(Board board, Position start, Direction startFacing, List<Card> cards,
                                  Set<Position> obstacles) {
        List<Step> steps = new ArrayList<>();
        Position position = start;
        Direction facing = startFacing;
        for (Card card : cards) {
            Position moved = position;
            switch (card.type()) {
                case MOVE_1 -> moved = walk(board, position, facing, 1, obstacles);
                case MOVE_2 -> moved = walk(board, position, facing, 2, obstacles);
                case MOVE_3 -> moved = walk(board, position, facing, 3, obstacles);
                case BACK_UP -> moved = walk(board, position, facing.opposite(), 1, obstacles);
                case ROTATE_LEFT -> facing = facing.rotateLeft();
                case ROTATE_RIGHT -> facing = facing.rotateRight();
                case U_TURN -> facing = facing.opposite();
            }
            if (moved == null) {
                break;
            }
            position = moved;
            steps.add(new Step(position, facing));
        }
        return steps;
    }

    /**
     * Moves a robot up to a number of single steps in a direction, stopping early at a wall or an obstacle, exactly
     * like {@code MovementResolver.walk} except that an obstacle blocks instead of being pushed.
     *
     * @param board     the board
     * @param position  the robot's position before this card
     * @param direction the direction to walk in
     * @param steps     the maximum number of steps
     * @param obstacles the squares other robots stand on
     * @return the position after walking, or {@code null} if a step would destroy the robot (a pit or off the board)
     */
    private static Position walk(Board board, Position position, Direction direction, int steps, Set<Position> obstacles) {
        for (int step = 0; step < steps; step++) {
            if (board.hasWall(position, direction)) {
                return position;
            }
            Position next = position.step(direction);
            if (!board.inBounds(next) || board.featureAt(next) == SquareFeature.PIT) {
                return null;
            }
            if (obstacles.contains(next)) {
                return position;
            }
            position = next;
        }
        return position;
    }
}
