package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves one conveyor-belt pass (design.md 2.12).
 * <p>
 * Every robot on an active belt gets an <em>intent</em> to move one square along
 * the belt. Intents that cannot be carried out are cancelled until the rest is
 * stable, then all remaining intents are applied simultaneously. Belts never push:
 * a robot whose target is occupied by a robot that is not itself moving stays put.
 * A robot that entered a belt square through a belt move is turned by that belt if
 * it changed direction by exactly a quarter turn. Robots that end the pass on a pit
 * or off the board are destroyed only after everything has moved.
 *
 * @author Mario Koehler
 */
final class BeltResolver {

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    BeltResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Runs one belt pass.
     *
     * @param expressOnly {@code true} for the first pass of a register, in which only
     *                    express belts move robots; {@code false} for the second, in
     *                    which express and normal belts both do
     */
    void pass(boolean expressOnly) {
        Board board = state.board();
        Map<Robot, Position> intents = new HashMap<>();
        Map<Robot, Direction> headings = new HashMap<>();
        for (Robot robot : state.robots()) {
            if (!robot.isActive()) {
                continue;
            }
            Optional<Belt> belt = board.beltAt(robot.position());
            if (belt.isPresent() && (!expressOnly || belt.get().express())
                && !board.hasWall(robot.position(), belt.get().direction())) {
                intents.put(robot, robot.position().step(belt.get().direction()));
                headings.put(robot, belt.get().direction());
            }
        }

        cancelBlockedIntents(intents);

        List<Robot> movers = new ArrayList<>(intents.keySet());
        movers.sort(Comparator.comparingInt(Robot::id));
        Map<Robot, Position> origins = new HashMap<>();
        for (Robot robot : movers) {
            origins.put(robot, robot.position());
        }
        for (Robot robot : movers) {
            log.add(new GameEvent.RobotMoved(robot.id(), origins.get(robot), intents.get(robot), MoveCause.BELT));
            robot.setPosition(intents.get(robot));
        }
        for (Robot robot : movers) {
            rotateIfTurnedByBelt(robot, headings.get(robot));
        }
        for (Robot robot : movers) {
            Destruction.destroyIfOnHazard(state, robot, log);
        }
    }

    /**
     * Repeatedly cancels intents that cannot happen until none is cancelled any
     * more. Every round judges all robots against the same snapshot, so the outcome
     * does not depend on the order robots are looked at in.
     * <p>
     * An intent is cancelled if another robot intends to enter the same square, if the
     * target holds a robot that intends to move onto this robot's square (a swap), or if
     * the target holds a robot that is not moving. A wall in the way never gives a robot
     * an intent in the first place.
     *
     * @param intents each moving robot's target square; entries are removed as intents
     *                are cancelled
     */
    private void cancelBlockedIntents(Map<Robot, Position> intents) {
        boolean changed = true;
        while (changed) {
            Set<Robot> blocked = new HashSet<>();
            for (Map.Entry<Robot, Position> entry : intents.entrySet()) {
                Robot robot = entry.getKey();
                if (isBlockedByOtherRobot(robot, entry.getValue(), intents)) {
                    blocked.add(robot);
                }
            }
            intents.keySet().removeAll(blocked);
            changed = !blocked.isEmpty();
        }
    }

    /**
     * Checks whether another robot prevents a robot from moving onto a square.
     *
     * @param robot   the robot that wants to move
     * @param target  the square it wants to enter
     * @param intents the currently standing intents of all moving robots
     * @return {@code true} on a collision, a swap, or a stationary robot on the target
     */
    private boolean isBlockedByOtherRobot(Robot robot, Position target, Map<Robot, Position> intents) {
        for (Map.Entry<Robot, Position> other : intents.entrySet()) {
            if (other.getKey() != robot && other.getValue().equals(target)) {
                return true;
            }
        }
        Optional<Robot> occupant = state.robotAt(target);
        if (occupant.isEmpty()) {
            return false;
        }
        Position occupantIntent = intents.get(occupant.get());
        return occupantIntent == null || occupantIntent.equals(robot.position());
    }

    /**
     * Turns a robot that a belt has just moved onto a belt square, if that square's
     * belt points a quarter turn away from the direction the robot travelled in.
     * This one rule covers curves and belts merging from the side.
     *
     * @param robot   the robot that has just been moved
     * @param heading the direction it was moved in
     */
    private void rotateIfTurnedByBelt(Robot robot, Direction heading) {
        if (!robot.isActive()) {
            return;
        }
        Board board = state.board();
        if (!board.inBounds(robot.position())) {
            return;
        }
        Optional<Belt> belt = board.beltAt(robot.position());
        if (belt.isEmpty()) {
            return;
        }
        int quarterTurns = heading.clockwiseQuarterTurnsTo(belt.get().direction());
        Direction newFacing;
        if (quarterTurns == 1) {
            newFacing = robot.facing().rotateRight();
        } else if (quarterTurns == 3) {
            newFacing = robot.facing().rotateLeft();
        } else {
            return;
        }
        log.add(new GameEvent.RobotRotated(robot.id(), robot.facing(), newFacing, RotationCause.BELT));
        robot.setFacing(newFacing);
    }
}
