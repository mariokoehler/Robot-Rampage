package de.mkoehler.robotrampage.board;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * How many single steps it takes to walk from every square of a board to one target square, going around walls and pits
 * and staying on the board (a breadth-first search). Belts, gears, pushers and the like are ignored: this is the plain
 * walking distance, the measure the bots head for flags by and the board editor rates boards with.
 *
 * @author Mario Koehler
 */
public final class WalkingDistances {

    /** The distance of a square from which the target cannot be reached at all. */
    public static final int UNREACHABLE = 200;

    /**
     * Not instantiated.
     */
    private WalkingDistances() {
    }

    /**
     * Works out the walking distance from every square to a target.
     *
     * @param board  the board
     * @param target the square to walk to; must be on the board
     * @return the steps by {@code [x][y]}, {@link #UNREACHABLE} where the target cannot be reached
     */
    public static int[][] to(Board board, Position target) {
        int[][] steps = new int[board.width()][board.height()];
        for (int[] column : steps) {
            Arrays.fill(column, UNREACHABLE);
        }
        steps[target.x()][target.y()] = 0;
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty()) {
            Position at = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                Position next = at.step(direction);
                if (board.inBounds(next) && !board.hasWall(at, direction) && !board.isPit(next)
                    && steps[next.x()][next.y()] > steps[at.x()][at.y()] + 1) {
                    steps[next.x()][next.y()] = steps[at.x()][at.y()] + 1;
                    queue.addLast(next);
                }
            }
        }
        return steps;
    }
}
