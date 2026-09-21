package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Brings destroyed robots back at the start of a turn (design.md 2.3, 2.9).
 * <p>
 * A returning robot appears on its archive marker with no damage, facing a direction its
 * player chose. If that square is taken, it appears on the nearest free square instead:
 * distance is counted in single steps over the grid, ignoring walls; squares are
 * explored north, east, south, west, and a pit is never free. When several robots return
 * in the same turn they are placed in the order they were destroyed, so the robot
 * destroyed first gets the best square.
 *
 * @author Mario Koehler
 */
public final class Respawner {

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private Respawner() {
    }

    /**
     * Re-enters every destroyed robot that still has lives, in the order they were
     * destroyed. Each gets a fresh, damage-free start with empty registers.
     *
     * @param state   the game state to mutate
     * @param facings the direction each returning robot's player chose, by robot id; a robot
     *                without an entry keeps the direction it faced when it was destroyed
     * @param log     receives a {@link SubPhase#RESPAWN} {@link GameEvent.RobotRespawned}
     *                event per returning robot
     */
    public static void respawn(GameState state, Map<Integer, Direction> facings, EventLog log) {
        log.enter(0, SubPhase.RESPAWN);
        List<Robot> waiting = new ArrayList<>();
        for (Robot robot : state.robots()) {
            if (robot.status() == RobotStatus.DESTROYED) {
                waiting.add(robot);
            }
        }
        waiting.sort(Comparator.comparingInt(Robot::destructionOrder));
        for (Robot robot : waiting) {
            Position spot = nearestFreeSquare(state, robot.archiveMarker());
            if (spot == null) {
                continue;
            }
            robot.setPosition(spot);
            robot.setFacing(facings.getOrDefault(robot.id(), robot.facing()));
            robot.setDamage(0);
            robot.setStatus(RobotStatus.ACTIVE);
            log.add(new GameEvent.RobotRespawned(robot.id(), spot, robot.facing()));
        }
    }

    /**
     * Finds where a robot can re-enter: the archive marker if free, otherwise the nearest free
     * square by breadth-first search.
     *
     * @param state the game state
     * @param start the archive marker
     * @return the square to place the robot on, or {@code null} if the board has no free square
     */
    private static Position nearestFreeSquare(GameState state, Position start) {
        Board board = state.board();
        Queue<Position> queue = new ArrayDeque<>();
        Set<Position> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            if (isFree(state, current)) {
                return current;
            }
            for (Direction direction : Direction.values()) {
                Position next = current.step(direction);
                if (board.inBounds(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /**
     * Returns whether a robot may be placed on a square.
     *
     * @param state    the game state
     * @param position the square
     * @return {@code true} if it is on the board, not a pit and not occupied by an active robot
     */
    private static boolean isFree(GameState state, Position position) {
        return state.board().inBounds(position) && !state.board().isPit(position) && state.robotAt(position).isEmpty();
    }
}
