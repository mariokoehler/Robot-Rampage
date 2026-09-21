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
 * Verifies the conveyor-belt algorithm of design.md 2.12: express versus normal
 * belts, rotation on curves and merges, and every way movement can be blocked
 * (walls, collisions, swaps, stationary robots), plus trains and rings.
 *
 * @author Mario Koehler
 */
class BeltResolverTest {

    /**
     * Runs one belt pass and returns what was logged.
     *
     * @param state       the game state to mutate
     * @param expressOnly whether this is the express-only pass
     * @return the events the pass caused
     */
    private static EventLog pass(GameState state, boolean expressOnly) {
        EventLog log = new EventLog();
        new BeltResolver(state, log).pass(expressOnly);
        return log;
    }

    /**
     * A full register's belt phase: the express-only pass, then the all-belts pass.
     *
     * @param state the game state to mutate
     */
    private static void bothPasses(GameState state) {
        pass(state, true);
        pass(state, false);
    }

    /**
     * The express-only pass moves robots on express belts and leaves robots on
     * normal belts alone.
     */
    @Test
    void expressOnlyPassIgnoresNormalBelts() {
        GameState state = AsciiBoard.state("""
            E . > .
            """, """
            0 . 1 .
            """);

        pass(state, true);

        assertRobots(state, ". 0 1 .");
    }

    /**
     * Over a whole register an express belt moves a robot two squares and a normal
     * belt one.
     */
    @Test
    void expressBeltsMoveTwiceAndNormalBeltsOnce() {
        GameState express = AsciiBoard.state("E E E . .", "0 . . . .");
        bothPasses(express);
        assertRobots(express, ". . 0 . .");

        GameState normal = AsciiBoard.state("> > > . .", "0 . . . .");
        bothPasses(normal);
        assertRobots(normal, ". 0 . . .");
    }

    /**
     * A belt bends robots that travel onto it from the side: a robot carried east onto
     * a belt pointing south turns right, and onto one pointing north turns left.
     */
    @Test
    void curvedBeltsTurnTheRobotTheWayTheyBend() {
        GameState right = AsciiBoard.state("> v", "0 .");
        EventLog log = pass(right, false);
        assertRobots(right, ". 0");
        assertEquals(Direction.EAST, right.robot(0).facing());
        assertEquals(new GameEvent.RobotRotated(0, Direction.NORTH, Direction.EAST, RotationCause.BELT),
            log.events().get(1));

        GameState left = AsciiBoard.state("> ^", "0 .");
        pass(left, false);
        assertEquals(Direction.WEST, left.robot(0).facing());
    }

    /**
     * Belts that continue straight, or that point back against the direction of travel,
     * do not turn the robot.
     */
    @Test
    void straightAndOpposingBeltsDoNotTurnTheRobot() {
        GameState straight = AsciiBoard.state("> > .", "0 . .");
        pass(straight, false);
        assertEquals(Direction.NORTH, straight.robot(0).facing());

        GameState opposing = AsciiBoard.state("> <", "0 .");
        pass(opposing, false);
        assertEquals(Direction.NORTH, opposing.robot(0).facing());
    }

    /**
     * A robot is only ever turned by the belt square it enters: leaving a belt that
     * bends onto plain floor turns nothing, because there is no belt to turn it.
     */
    @Test
    void movingOntoPlainFloorDoesNotTurnTheRobot() {
        GameState state = AsciiBoard.state("v .\n. .", "0 .\n. .");

        EventLog log = pass(state, false);

        assertRobots(state, ". .\n0 .");
        assertEquals(Direction.NORTH, state.robot(0).facing());
        assertEquals(1, log.events().size());
    }

    /**
     * Entering a belt that bends away from the direction of travel turns the robot by
     * exactly that bend: south onto an east-pointing belt is a left turn.
     */
    @Test
    void theTurnFollowsTheBendNotTheRobotsOldFacing() {
        GameState state = AsciiBoard.state("v .\n> .", "0 .\n. .");

        pass(state, false);

        assertRobots(state, ". .\n0 .");
        assertEquals(Direction.WEST, state.robot(0).facing());
    }

    /**
     * Two robots aimed at the same square both stay where they are.
     */
    @Test
    void collidingRobotsBothStayPut() {
        GameState state = AsciiBoard.state("> . <", "0 . 1");

        EventLog log = pass(state, false);

        assertRobots(state, "0 . 1");
        assertTrue(log.events().isEmpty());
    }

    /**
     * Two robots trying to swap squares both stay where they are.
     */
    @Test
    void swappingRobotsBothStayPut() {
        GameState state = AsciiBoard.state("> <", "0 1");

        pass(state, false);

        assertRobots(state, "0 1");
    }

    /**
     * A belt never pushes: a robot in the way that is not moving blocks the robot
     * behind it.
     */
    @Test
    void beltsDoNotPushStationaryRobots() {
        GameState state = AsciiBoard.state("> .", "0 1");

        pass(state, false);

        assertRobots(state, "0 1");
    }

    /**
     * A wall in front of a belt keeps its robot where it is.
     */
    @Test
    void wallsBlockBelts() {
        GameState state = AsciiBoard.state("> | .", "0 .");

        pass(state, false);

        assertRobots(state, "0 .");
    }

    /**
     * Robots in a row on a belt all move up together, each into the square its
     * neighbour just left.
     */
    @Test
    void trainsMoveAsOne() {
        GameState state = AsciiBoard.state("> > > .", "0 1 2 .");

        pass(state, false);

        assertRobots(state, ". 0 1 2");
    }

    /**
     * If the head of a train is blocked, everyone behind it is blocked too.
     */
    @Test
    void blockedTrainHeadBlocksTheWholeTrain() {
        GameState state = AsciiBoard.state("> > > | .", "0 1 2 .");

        pass(state, false);

        assertRobots(state, "0 1 2 .");
    }

    /**
     * Robots on a closed loop of belts all move around it at once.
     */
    @Test
    void ringOfBeltsRotatesEveryRobot() {
        GameState state = AsciiBoard.state("""
            > v
            ^ <
            """, """
            0 1
            3 2
            """);

        pass(state, false);

        assertRobots(state, """
            3 0
            2 1
            """);
        // Every robot entered a belt one quarter turn clockwise of its heading, so all
        // four turned right from north to east.
        for (int id = 0; id < 4; id++) {
            assertEquals(Direction.EAST, state.robot(id).facing());
        }
    }

    /**
     * A belt carries a robot even while it is powered down.
     */
    @Test
    void beltsMovePoweredDownRobots() {
        GameState state = AsciiBoard.state("> .", "0 .");
        state.robot(0).setPoweredDown(true);

        pass(state, false);

        assertRobots(state, ". 0");
    }

    /**
     * A belt can carry a robot into a pit or off the board, which destroys it, and the
     * log shows the move before the destruction.
     */
    @Test
    void beltsCarryRobotsToTheirDeath() {
        GameState pit = AsciiBoard.state("> o", "0 .");
        EventLog log = pass(pit, false);
        assertEquals(List.of(
            new GameEvent.RobotMoved(0, new Position(0, 0), new Position(1, 0), MoveCause.BELT),
            new GameEvent.RobotDestroyed(0, DestructionCause.PIT)), log.events());

        GameState edge = AsciiBoard.state(">", "0");
        pass(edge, false);
        assertEquals(RobotStatus.DESTROYED, edge.robot(0).status());
    }

    /**
     * Destruction is applied after the whole pass: a robot moved onto a pit does not
     * stop the other robots of the same pass from moving.
     */
    @Test
    void destructionHappensAfterAllRobotsHaveMoved() {
        GameState state = AsciiBoard.state("""
            > o
            > .
            """, """
            0 .
            1 .
            """);

        pass(state, false);

        assertEquals(RobotStatus.DESTROYED, state.robot(0).status());
        assertEquals(new Position(1, 0), state.robot(1).position());
    }
}
