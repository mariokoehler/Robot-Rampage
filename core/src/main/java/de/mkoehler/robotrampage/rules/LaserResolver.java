package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Laser;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the laser sub-phase of a register (design.md 2.4, 2.9).
 * <p>
 * All lasers &mdash; every board laser and the forward laser of every active,
 * powered-up robot &mdash; fire <em>simultaneously</em>. That is why the work is split
 * into three steps: first every beam is traced against the positions as they stand at
 * the start of the volley, then all resulting damage is applied, and only then are
 * robots that reached 10 damage destroyed. So two robots can destroy each other, and a
 * robot in the line of fire stays a shield for the robots behind it even if it is
 * destroyed by that very volley.
 * <p>
 * A beam travels until it meets a wall, the edge of the board or the first robot in its
 * path, and does not push. A robot never hits itself; a board laser does hit a robot
 * standing on its emitter square. Powered-down robots do not fire, but are hit like any
 * other robot.
 *
 * @author Mario Koehler
 */
final class LaserResolver {

    /**
     * Where a traced beam ended.
     *
     * @param end the last square the beam reached
     * @param hit the robot it hit, or {@code null} if it hit none
     */
    private record Trace(Position end, Robot hit) {
    }

    /**
     * Damage to apply once all beams of the volley have been traced.
     *
     * @param robot  the robot that was hit
     * @param amount the damage, i.e. the number of beams
     * @param source the kind of laser that hit it
     */
    private record Hit(Robot robot, int amount, LaserSource source) {
    }

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    LaserResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Fires all lasers once: traces every beam, applies all damage, then destroys the
     * robots that are now at 10 damage or more (in robot id order).
     */
    void fire() {
        Board board = state.board();
        List<Hit> hits = new ArrayList<>();

        for (Laser laser : board.lasers()) {
            Trace trace = trace(laser.position(), laser.firingDirection(), true);
            log.add(new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, laser.position(),
                laser.firingDirection(), trace.end(), idOf(trace.hit()), laser.beams()));
            if (trace.hit() != null) {
                hits.add(new Hit(trace.hit(), laser.beams(), LaserSource.BOARD));
            }
        }

        List<Robot> shooters = state.robots().stream()
            .filter(robot -> robot.isActive() && !robot.isPoweredDown())
            .sorted(Comparator.comparingInt(Robot::id))
            .toList();
        for (Robot shooter : shooters) {
            Trace trace = trace(shooter.position(), shooter.facing(), false);
            log.add(new GameEvent.LaserFired(LaserSource.ROBOT, shooter.id(), shooter.position(), shooter.facing(),
                trace.end(), idOf(trace.hit()), 1));
            if (trace.hit() != null) {
                hits.add(new Hit(trace.hit(), 1, LaserSource.ROBOT));
            }
        }

        for (Hit hit : hits) {
            Robot robot = hit.robot();
            robot.setDamage(robot.damage() + hit.amount());
            log.add(new GameEvent.RobotDamaged(robot.id(), hit.amount(), robot.damage(), hit.source()));
        }

        for (Robot robot : state.robots()) {
            if (robot.isActive() && robot.damage() >= Robot.DESTRUCTION_DAMAGE) {
                Destruction.destroy(state, robot, DestructionCause.DAMAGE, log);
            }
        }
    }

    /**
     * Follows a beam from a square until it ends. Nothing is modified.
     *
     * @param from               the square the beam starts on
     * @param direction          the direction it travels in
     * @param includeStartSquare whether a robot standing on {@code from} is hit (true for
     *                           a board laser's emitter square, false for a robot's own
     *                           laser, which must not hit its owner)
     * @return where the beam ended and which robot, if any, it hit
     */
    private Trace trace(Position from, Direction direction, boolean includeStartSquare) {
        Board board = state.board();
        Position end = from;
        if (includeStartSquare) {
            Optional<Robot> robot = state.robotAt(end);
            if (robot.isPresent()) {
                return new Trace(end, robot.get());
            }
        }
        while (true) {
            if (board.hasWall(end, direction)) {
                return new Trace(end, null);
            }
            Position next = end.step(direction);
            if (!board.inBounds(next)) {
                return new Trace(end, null);
            }
            end = next;
            Optional<Robot> robot = state.robotAt(end);
            if (robot.isPresent()) {
                return new Trace(end, robot.get());
            }
        }
    }

    /**
     * Returns the id to log for a possibly missing robot.
     *
     * @param robot the robot, or {@code null}
     * @return its id, or {@link GameEvent#NO_ROBOT}
     */
    private static int idOf(Robot robot) {
        return robot == null ? GameEvent.NO_ROBOT : robot.id();
    }
}
