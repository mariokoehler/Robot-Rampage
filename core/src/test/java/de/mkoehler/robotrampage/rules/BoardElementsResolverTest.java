package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.mkoehler.robotrampage.testsupport.AsciiBoard.assertRobots;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the gear, pusher and crusher sub-phases of a register (design.md 2.4, 2.9).
 *
 * @author Mario Koehler
 */
class BoardElementsResolverTest {

    /**
     * Rotates gears once and returns what was logged.
     *
     * @param state the game state to mutate
     * @return the events of the phase
     */
    private static EventLog gears(GameState state) {
        EventLog log = new EventLog();
        new GearResolver(state, log).rotate();
        return log;
    }

    /**
     * Fires the pushers of one register and returns what was logged.
     *
     * @param state    the game state to mutate
     * @param register the register, 1 to 5
     * @return the events of the phase
     */
    private static EventLog pushers(GameState state, int register) {
        EventLog log = new EventLog();
        new PusherResolver(state, log).fire(register);
        return log;
    }

    /**
     * Fires the crushers of one register and returns what was logged.
     *
     * @param state    the game state to mutate
     * @param register the register, 1 to 5
     * @return the events of the phase
     */
    private static EventLog crushers(GameState state, int register) {
        EventLog log = new EventLog();
        new CrusherResolver(state, log).crush(register);
        return log;
    }

    /**
     * A clockwise gear turns a robot right, a counter-clockwise gear turns it left, and both
     * log the rotation with the gear as its cause.
     */
    @Test
    void gearsTurnTheRobotsOnThem() {
        GameState state = AsciiBoard.state("c a .", "0 1 2");

        EventLog log = gears(state);

        assertEquals(Direction.EAST, state.robot(0).facing());
        assertEquals(Direction.WEST, state.robot(1).facing());
        assertEquals(Direction.NORTH, state.robot(2).facing());
        assertEquals(List.of(
            new GameEvent.RobotRotated(0, Direction.NORTH, Direction.EAST, RotationCause.GEAR),
            new GameEvent.RobotRotated(1, Direction.NORTH, Direction.WEST, RotationCause.GEAR)), log.events());
    }

    /**
     * A gear turns a powered-down robot as well.
     */
    @Test
    void gearsTurnPoweredDownRobots() {
        GameState state = AsciiBoard.state("c", "0");
        state.robot(0).setPoweredDown(true);

        gears(state);

        assertEquals(Direction.EAST, state.robot(0).facing());
    }

    /**
     * A pusher shoves the robot on its square away from its wall, but only in the registers it
     * is active in.
     */
    @Test
    void pushersPushOnlyInTheirActiveRegisters() {
        GameState state = AsciiBoard.state(". . . .", ". 0 . .",
            builder -> builder.pusher(new Position(1, 0), Direction.WEST, 2, 4));

        EventLog first = pushers(state, 1);
        assertRobots(state, ". 0 . .");
        assertTrue(first.events().isEmpty());

        EventLog second = pushers(state, 2);
        assertRobots(state, ". . 0 .");
        assertEquals(List.of(new GameEvent.RobotMoved(0, new Position(1, 0), new Position(2, 0), MoveCause.PUSHER)),
            second.events());
    }

    /**
     * A pusher uses the ordinary push rules: robots ahead are pushed along, and a wall stops
     * everybody.
     */
    @Test
    void pushersFollowTheNormalPushRules() {
        GameState chain = AsciiBoard.state(". . . .", ". 0 1 .",
            builder -> builder.pusher(new Position(1, 0), Direction.WEST, 1));
        pushers(chain, 1);
        assertRobots(chain, ". . 0 1");

        GameState blocked = AsciiBoard.state(". . . . |", ". 0 1 2",
            builder -> builder.pusher(new Position(1, 0), Direction.WEST, 1));
        pushers(blocked, 1);
        assertRobots(blocked, ". 0 1 2");
    }

    /**
     * A pusher can shove a robot into a pit.
     */
    @Test
    void pushersCanPushRobotsIntoPits() {
        GameState state = AsciiBoard.state(". . o", ". 0 .",
            builder -> builder.pusher(new Position(1, 0), Direction.WEST, 1));

        pushers(state, 1);

        assertEquals(RobotStatus.DESTROYED, state.robot(0).status());
    }

    /**
     * A robot that one pusher shoves onto another active pusher's square is not pushed a
     * second time in the same register: who is pushed is decided at the start of the phase.
     * Here the west pusher moves the robot east onto a square whose own pusher would shove
     * it north; that second push must wait for a later register.
     */
    @Test
    void aRobotIsPushedOncePerRegisterEvenOntoAnotherPusher() {
        GameState state = AsciiBoard.state("""
            . .
            . .
            """, """
            . .
            0 .
            """, builder -> builder
            .pusher(new Position(0, 0), Direction.WEST, 1)
            .pusher(new Position(1, 0), Direction.SOUTH, 1));

        pushers(state, 1);

        assertRobots(state, """
            . .
            . 0
            """);
    }

    /**
     * A robot that another robot's push chain has shoved off a pusher's square is no longer
     * pushed by that pusher: robot 0 pushes robot 1 east off the square of a north-pushing
     * pusher, and that pusher must then find nobody to push.
     */
    @Test
    void aPusherIgnoresARobotThatWasPushedAwayFromItsSquare() {
        GameState state = AsciiBoard.state("""
            . . .
            . . .
            """, """
            . . .
            0 1 .
            """, builder -> builder
            .pusher(new Position(0, 0), Direction.WEST, 1)
            .pusher(new Position(1, 0), Direction.SOUTH, 1));

        pushers(state, 1);

        assertRobots(state, """
            . . .
            . 0 1
            """);
    }

    /**
     * A crusher destroys the robot on it only in a register it is active in.
     */
    @Test
    void crushersDestroyOnlyInTheirActiveRegisters() {
        GameState state = AsciiBoard.state(". . .", ". 0 .",
            builder -> builder.crusher(new Position(1, 0), 2, 3));

        assertTrue(crushers(state, 1).events().isEmpty());
        assertEquals(RobotStatus.ACTIVE, state.robot(0).status());

        EventLog log = crushers(state, 3);

        assertEquals(List.of(new GameEvent.RobotDestroyed(0, DestructionCause.CRUSHER)), log.events());
        assertEquals(RobotStatus.DESTROYED, state.robot(0).status());
    }

    /**
     * Crushers spare robots on other squares.
     */
    @Test
    void crushersSpareRobotsElsewhere() {
        GameState state = AsciiBoard.state("x .", ". 0");

        crushers(state, 1);

        assertEquals(RobotStatus.ACTIVE, state.robot(0).status());
    }
}
