package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies laser fire (design.md 2.4 step 7, 2.9): beams stopped by walls and robots,
 * board lasers versus robot lasers, and above all that a volley is simultaneous.
 *
 * @author Mario Koehler
 */
class LaserResolverTest {

    /**
     * Fires one volley and returns what was logged.
     *
     * @param state the game state to mutate
     * @return the events of the volley
     */
    private static EventLog fire(GameState state) {
        EventLog log = new EventLog();
        new LaserResolver(state, log).fire();
        return log;
    }

    /**
     * Makes a robot face east, the direction of most of these scenarios.
     *
     * @param state the game state
     * @param id    the robot to turn
     */
    private static void faceEast(GameState state, int id) {
        state.robot(id).setFacing(Direction.EAST);
    }

    /**
     * A board laser hits the first robot in its line and no robot behind it, dealing one
     * damage per beam.
     */
    @Test
    void boardLaserHitsOnlyTheFirstRobotInLine() {
        GameState state = AsciiBoard.state(". . . . .", ". . 0 . 1",
            builder -> builder.laser(new Position(0, 0), Direction.WEST, 2));
        state.robot(0).setPoweredDown(true);
        state.robot(1).setPoweredDown(true);

        EventLog log = fire(state);

        assertEquals(2, state.robot(0).damage());
        assertEquals(0, state.robot(1).damage());
        assertEquals(List.of(
            new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 0), Direction.EAST,
                new Position(2, 0), 0, 2),
            new GameEvent.RobotDamaged(0, 2, 2, LaserSource.BOARD)), log.events());
    }

    /**
     * A wall stops a beam, so a robot behind it is safe.
     */
    @Test
    void wallsBlockBoardLasers() {
        GameState state = AsciiBoard.state(". . | . .", ". . . 0",
            builder -> builder.laser(new Position(0, 0), Direction.WEST, 1));
        state.robot(0).setPoweredDown(true);

        EventLog log = fire(state);

        assertEquals(0, state.robot(0).damage());
        assertEquals(new Position(1, 0), ((GameEvent.LaserFired) log.events().get(0)).to());
        assertEquals(1, log.events().size());
    }

    /**
     * A board laser hits a robot standing on the emitter's own square.
     */
    @Test
    void boardLaserHitsARobotOnItsEmitterSquare() {
        GameState state = AsciiBoard.state(". . .", "0 . .",
            builder -> builder.laser(new Position(0, 0), Direction.WEST, 1));
        state.robot(0).setPoweredDown(true);

        fire(state);

        assertEquals(1, state.robot(0).damage());
    }

    /**
     * A beam that meets nothing runs to the board edge and is still reported.
     */
    @Test
    void beamsThatHitNothingRunToTheEdge() {
        GameState state = AsciiBoard.state(". . . .", ". . . .",
            builder -> builder.laser(new Position(0, 0), Direction.WEST, 1));

        EventLog log = fire(state);

        assertEquals(new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 0), Direction.EAST,
            new Position(3, 0), GameEvent.NO_ROBOT, 1), log.events().get(0));
        assertEquals(1, log.events().size());
    }

    /**
     * A robot's laser hits the first robot ahead of it, and never the robot itself.
     */
    @Test
    void robotLaserHitsTheFirstRobotAhead() {
        GameState state = AsciiBoard.state(". . . . .", "0 . 1 . 2");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.NORTH);
        state.robot(2).setFacing(Direction.NORTH);

        EventLog log = fire(state);

        assertEquals(0, state.robot(0).damage());
        assertEquals(1, state.robot(1).damage());
        assertEquals(0, state.robot(2).damage());
        assertTrue(log.events().contains(new GameEvent.LaserFired(LaserSource.ROBOT, 0, new Position(0, 0),
            Direction.EAST, new Position(2, 0), 1, 1)));
    }

    /**
     * A wall directly in front of a robot swallows its shot.
     */
    @Test
    void wallsBlockRobotLasers() {
        GameState state = AsciiBoard.state(". | . .", "0 . 1");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.NORTH);

        fire(state);

        assertEquals(0, state.robot(1).damage());
    }

    /**
     * Two robots facing each other shoot each other at the same time.
     */
    @Test
    void facingRobotsShootEachOther() {
        GameState state = AsciiBoard.state(". . . .", "0 . . 1");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.WEST);

        fire(state);

        assertEquals(1, state.robot(0).damage());
        assertEquals(1, state.robot(1).damage());
    }

    /**
     * A powered-down robot fires nothing, but can be hit.
     */
    @Test
    void poweredDownRobotsDoNotFireButCanBeHit() {
        GameState state = AsciiBoard.state(". . . .", "0 . . 1");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.WEST);
        state.robot(1).setPoweredDown(true);

        fire(state);

        assertEquals(0, state.robot(0).damage());
        assertEquals(1, state.robot(1).damage());
    }

    /**
     * Robots that reach 10 damage are destroyed only after the whole volley has been applied,
     * so two robots at 9 damage facing each other destroy each other.
     */
    @Test
    void robotsCanDestroyEachOtherInOneVolley() {
        GameState state = AsciiBoard.state(". . . .", "0 . . 1");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.WEST);
        state.robot(0).setDamage(9);
        state.robot(1).setDamage(9);

        EventLog log = fire(state);

        assertEquals(RobotStatus.DESTROYED, state.robot(0).status());
        assertEquals(RobotStatus.DESTROYED, state.robot(1).status());
        assertTrue(log.events().contains(new GameEvent.RobotDestroyed(0, DestructionCause.DAMAGE)));
        assertTrue(log.events().contains(new GameEvent.RobotDestroyed(1, DestructionCause.DAMAGE)));
    }

    /**
     * A robot destroyed by the volley still fires in it: robot 1 dies to robot 0's shot but its
     * own shot at robot 2 was already on its way.
     */
    @Test
    void aRobotDestroyedByTheVolleyStillFiresInIt() {
        GameState state = AsciiBoard.state(". . . .", "0 1 2 .");
        faceEast(state, 0);
        faceEast(state, 1);
        state.robot(2).setFacing(Direction.NORTH);
        state.robot(1).setDamage(9);

        fire(state);

        assertEquals(RobotStatus.DESTROYED, state.robot(1).status());
        assertEquals(1, state.robot(2).damage());
    }

    /**
     * A robot in the line of fire shields the robots behind it, even when that robot is destroyed
     * by the very same volley: the beam is traced against the positions at the start.
     */
    @Test
    void aRobotDestroyedByTheVolleyStillShieldsTheOnesBehindIt() {
        GameState state = AsciiBoard.state(". . . .", ". 0 1 .",
            builder -> builder.laser(new Position(0, 0), Direction.WEST, 1));
        state.robot(0).setDamage(9);
        state.robot(0).setPoweredDown(true);
        state.robot(1).setPoweredDown(true);

        EventLog log = fire(state);

        assertEquals(RobotStatus.DESTROYED, state.robot(0).status());
        assertEquals(0, state.robot(1).damage());
        assertTrue(log.events().contains(new GameEvent.RobotDestroyed(0, DestructionCause.DAMAGE)));
    }

    /**
     * Two hits in one volley add up: a robot at 8 damage hit by two lasers reaches exactly 10, is
     * destroyed once, and both damage events come before the single destruction event.
     */
    @Test
    void twoHitsInOneVolleyDestroyARobotOnce() {
        GameState state = AsciiBoard.state(". . .", ". 0 .", builder -> builder
            .laser(new Position(0, 0), Direction.WEST, 1)
            .laser(new Position(2, 0), Direction.EAST, 1));
        state.robot(0).setDamage(8);
        state.robot(0).setPoweredDown(true);

        EventLog log = fire(state);

        assertEquals(List.of(
            new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 0), Direction.EAST,
                new Position(1, 0), 0, 1),
            new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(2, 0), Direction.WEST,
                new Position(1, 0), 0, 1),
            new GameEvent.RobotDamaged(0, 1, 9, LaserSource.BOARD),
            new GameEvent.RobotDamaged(0, 1, 10, LaserSource.BOARD),
            new GameEvent.RobotDestroyed(0, DestructionCause.DAMAGE)), log.events());
    }

    /**
     * The order of the log is: all beams, then all damage, then destruction.
     */
    @Test
    void volleyLogsBeamsThenDamageThenDestruction() {
        GameState state = AsciiBoard.state(". . .", "0 . 1");
        faceEast(state, 0);
        state.robot(1).setFacing(Direction.WEST);
        state.robot(1).setDamage(9);

        EventLog log = fire(state);

        List<GameEvent> events = log.events();
        assertTrue(events.get(0) instanceof GameEvent.LaserFired);
        assertTrue(events.get(1) instanceof GameEvent.LaserFired);
        assertTrue(events.get(2) instanceof GameEvent.RobotDamaged);
        assertTrue(events.get(3) instanceof GameEvent.RobotDamaged);
        assertTrue(events.get(4) instanceof GameEvent.RobotDestroyed);
        assertEquals(5, events.size());
    }

    /**
     * Robots that are not on the board are neither shot at nor do they shoot.
     */
    @Test
    void destroyedRobotsTakePartInNothing() {
        GameState state = AsciiBoard.state(". . .", "0 . 1");
        faceEast(state, 0);
        state.robot(1).setStatus(RobotStatus.DESTROYED);
        state.robot(1).setPosition(null);

        EventLog log = fire(state);

        assertEquals(1, log.events().size());
        assertEquals(0, state.robot(1).damage());
    }
}
