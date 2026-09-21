package de.mkoehler.robotrampage.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the execution phase of a turn: five registers, each in the sub-phase order of
 * design.md 2.4, followed by the cleanup phase (design.md 2.3).
 * <p>
 * The resolver is <em>deterministic</em>: it contains no randomness, so the same state
 * always produces the same result. It never modifies the state it is given; it works on a
 * {@link GameState#copy() copy} and returns that together with the ordered event log.
 * Registers must already hold the programmed cards (see
 * {@link Programming#submit(GameState, int, List, List, boolean)}); a robot with an empty
 * register, or one that is powered down, simply does nothing in that register.
 * <p>
 * If a robot touches the final flag, or all robots but one are eliminated, the game ends
 * at the end of that register: the remaining registers and the cleanup phase are skipped.
 * A finished game is terminal &mdash; its state is only good for display, and resolving it
 * again is refused.
 *
 * @author Mario Koehler
 */
public final class TurnResolver {

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private TurnResolver() {
    }

    /**
     * Plays out one turn.
     *
     * @param input the game state with every robot's registers programmed
     * @return the state after the turn and the events that led there
     * @throws IllegalStateException if the game is already over
     */
    public static TurnResult resolve(GameState input) {
        if (input.isOver()) {
            throw new IllegalStateException("The game is over; there is no turn left to resolve");
        }
        GameState state = input.copy();
        EventLog log = new EventLog();
        MovementResolver movement = new MovementResolver(state, log);
        BeltResolver belts = new BeltResolver(state, log);
        PusherResolver pushers = new PusherResolver(state, log);
        GearResolver gears = new GearResolver(state, log);
        LaserResolver lasers = new LaserResolver(state, log);
        CrusherResolver crushers = new CrusherResolver(state, log);
        CheckpointResolver checkpoints = new CheckpointResolver(state, log);

        for (int register = 1; register <= Robot.REGISTER_COUNT; register++) {
            log.enter(register, SubPhase.REVEAL);
            Map<Robot, Card> cards = new HashMap<>();
            for (Robot robot : state.robots()) {
                Card card = robot.register(register - 1);
                if (robot.isActive() && !robot.isPoweredDown() && card != null) {
                    cards.put(robot, card);
                    log.add(new GameEvent.RegisterRevealed(robot.id(), card));
                }
            }
            List<Robot> byPriority = new ArrayList<>(cards.keySet());
            byPriority.sort(Comparator.comparingInt((Robot robot) -> cards.get(robot).priority()).reversed());

            log.enter(register, SubPhase.ROBOT_MOVEMENT);
            for (Robot robot : byPriority) {
                if (robot.isActive()) {
                    movement.executeCard(robot, cards.get(robot));
                }
            }
            log.enter(register, SubPhase.EXPRESS_BELTS);
            belts.pass(true);
            log.enter(register, SubPhase.ALL_BELTS);
            belts.pass(false);
            log.enter(register, SubPhase.PUSHERS);
            pushers.fire(register);
            log.enter(register, SubPhase.GEARS);
            gears.rotate();
            log.enter(register, SubPhase.LASERS);
            lasers.fire();
            log.enter(register, SubPhase.CRUSHERS);
            crushers.crush(register);
            log.enter(register, SubPhase.CHECKPOINTS);
            checkpoints.touch();
            GameOutcome.endIfOneRobotIsLeft(state, log);

            if (state.isOver()) {
                return new TurnResult(state, log.entries());
            }
        }

        new CleanupResolver(state, log).run();
        return new TurnResult(state, log.entries());
    }
}
