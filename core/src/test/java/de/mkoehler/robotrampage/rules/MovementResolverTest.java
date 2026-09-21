package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.mkoehler.robotrampage.testsupport.AsciiBoard.assertRobots;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies card execution, single steps and pushing (design.md 2.6), including the
 * cases where pushing meets walls, pits and the board edge.
 *
 * @author Mario Koehler
 */
class MovementResolverTest {

    /**
     * Plays one card for a robot and returns what was logged.
     *
     * @param state the game state to mutate
     * @param robot the robot playing the card
     * @param type  the type of card to play
     * @return the events the card caused
     */
    private static EventLog play(GameState state, int robot, CardType type) {
        EventLog log = new EventLog();
        new MovementResolver(state, log).executeCard(state.robot(robot), new Card(type, 500));
        return log;
    }

    /**
     * Builds a state on a single-row board with robot 0 facing east.
     *
     * @param terrain the terrain picture
     * @param robots  the robot picture
     * @return the state, with robot 0 facing east
     */
    private static GameState eastward(String terrain, String robots) {
        GameState state = AsciiBoard.state(terrain, robots);
        state.robot(0).setFacing(Direction.EAST);
        return state;
    }

    /**
     * Move 2 walks two squares and logs one move per square.
     */
    @Test
    void moveTwoWalksTwoSquares() {
        GameState state = eastward(". . . . .", "0 . . . .");

        EventLog log = play(state, 0, CardType.MOVE_2);

        assertRobots(state, ". . 0 . .");
        assertEquals(List.of(
            new GameEvent.RobotMoved(0, new Position(0, 0), new Position(1, 0), MoveCause.CARD),
            new GameEvent.RobotMoved(0, new Position(1, 0), new Position(2, 0), MoveCause.CARD)), log.events());
    }

    /**
     * A wall ends a multi-step move early; the remaining steps are simply lost.
     */
    @Test
    void wallStopsTheRestOfAMove() {
        GameState state = eastward(". . | . .", "0 . . .");

        EventLog log = play(state, 0, CardType.MOVE_3);

        assertRobots(state, ". 0 . .");
        assertEquals(1, log.events().size());
    }

    /**
     * A robot facing a wall does not move and logs nothing.
     */
    @Test
    void wallDirectlyAheadBlocksTheFirstStep() {
        GameState state = eastward(". | . .", "0 . .");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, "0 . .");
        assertTrue(log.events().isEmpty());
    }

    /**
     * Moving into a robot pushes it, and the whole chain of robots ahead moves along.
     */
    @Test
    void pushingMovesTheWholeChain() {
        GameState state = eastward(". . . . .", "0 1 2 . .");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, ". 0 1 2 .");
        assertEquals(List.of(
            new GameEvent.RobotMoved(2, new Position(2, 0), new Position(3, 0), MoveCause.PUSHED),
            new GameEvent.RobotMoved(1, new Position(1, 0), new Position(2, 0), MoveCause.PUSHED),
            new GameEvent.RobotMoved(0, new Position(0, 0), new Position(1, 0), MoveCause.CARD)), log.events());
    }

    /**
     * If the last robot of a chain is blocked by a wall, nobody in the chain moves.
     */
    @Test
    void pushIntoAWallMovesNobody() {
        GameState state = eastward(". . . | .", "0 1 2 .");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, "0 1 2 .");
        assertTrue(log.events().isEmpty());
    }

    /**
     * A wall on the outer edge behind the last robot of a chain blocks the push just like an
     * interior wall: nobody moves and nobody falls off.
     */
    @Test
    void pushIntoAnOuterWallMovesNobody() {
        GameState state = eastward(". . . . |", "0 1 2 3");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, "0 1 2 3");
        assertTrue(log.events().isEmpty());
    }

    /**
     * A pushed robot keeps its facing.
     */
    @Test
    void pushedRobotsKeepTheirFacing() {
        GameState state = eastward(". . . .", "0 1 . .");
        state.robot(1).setFacing(Direction.SOUTH);

        play(state, 0, CardType.MOVE_1);

        assertRobots(state, ". 0 1 .");
        assertEquals(Direction.SOUTH, state.robot(1).facing());
    }

    /**
     * A robot pushed onto a pit is destroyed, and the robot that pushed it is not
     * affected by that.
     */
    @Test
    void pushingARobotIntoAPitDestroysIt() {
        GameState state = eastward(". . o .", "0 1 . .");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, ". 0 . .");
        assertEquals(List.of(
            new GameEvent.RobotMoved(1, new Position(1, 0), new Position(2, 0), MoveCause.PUSHED),
            new GameEvent.RobotDestroyed(1, DestructionCause.PIT),
            new GameEvent.RobotMoved(0, new Position(0, 0), new Position(1, 0), MoveCause.CARD)), log.events());
        assertEquals(RobotStatus.DESTROYED, state.robot(1).status());
        assertEquals(RobotStatus.ACTIVE, state.robot(0).status());
    }

    /**
     * A robot pushed off the board is destroyed.
     */
    @Test
    void pushingARobotOffTheBoardDestroysIt() {
        GameState state = eastward(". . .", ". 0 1");

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertRobots(state, ". . 0");
        assertTrue(log.events().contains(new GameEvent.RobotDestroyed(1, DestructionCause.LEFT_BOARD)));
    }

    /**
     * When a push destroys robots, the pusher's remaining steps still happen: two
     * robots in a row are pushed into a pit one after the other by one Move 2, and the
     * mover survives both pushes.
     */
    @Test
    void moverKeepsWalkingAfterAPushedRobotIsDestroyed() {
        GameState state = eastward(". . . o .", "0 1 2 . .");

        EventLog log = play(state, 0, CardType.MOVE_2);

        assertRobots(state, ". . 0 . .");
        assertEquals(RobotStatus.DESTROYED, state.robot(1).status());
        assertEquals(RobotStatus.DESTROYED, state.robot(2).status());
        assertEquals(RobotStatus.ACTIVE, state.robot(0).status());
        assertEquals(2, log.events().stream().filter(e -> e instanceof GameEvent.RobotDestroyed).count());
    }

    /**
     * A robot walking onto a pit is destroyed on the spot and loses the rest of its
     * move.
     */
    @Test
    void walkingIntoAPitDestroysTheRobotAndEndsTheMove() {
        GameState state = eastward(". o . .", "0 . . .");
        state.robot(0).setRegister(0, new Card(CardType.MOVE_3, 1));

        EventLog log = play(state, 0, CardType.MOVE_3);

        assertEquals(List.of(
            new GameEvent.RobotMoved(0, new Position(0, 0), new Position(1, 0), MoveCause.CARD),
            new GameEvent.RobotDestroyed(0, DestructionCause.PIT)), log.events());
        Robot robot = state.robot(0);
        assertEquals(RobotStatus.DESTROYED, robot.status());
        assertEquals(Robot.STARTING_LIVES - 1, robot.lives());
        assertNull(robot.position());
        assertNull(robot.register(0));
        assertEquals(1, state.deck().discardPileSize());
    }

    /**
     * Walking off the board edge destroys the robot, and a robot on its last life is
     * eliminated instead.
     */
    @Test
    void leavingTheBoardEliminatesARobotOnItsLastLife() {
        GameState state = AsciiBoard.state(". .", "0 .");
        state.robot(0).setFacing(Direction.WEST);
        state.robot(0).setLives(1);

        EventLog log = play(state, 0, CardType.MOVE_1);

        assertTrue(log.events().contains(new GameEvent.RobotDestroyed(0, DestructionCause.LEFT_BOARD)));
        assertEquals(RobotStatus.ELIMINATED, state.robot(0).status());
        assertEquals(0, state.robot(0).lives());
    }

    /**
     * Back Up moves one square backwards and keeps the facing.
     */
    @Test
    void backUpMovesBackwardsWithoutTurning() {
        GameState state = AsciiBoard.state("""
            .
            .
            .
            """, """
            0
            .
            .
            """);

        play(state, 0, CardType.BACK_UP);

        assertRobots(state, """
            .
            0
            .
            """);
        assertEquals(Direction.NORTH, state.robot(0).facing());
    }

    /**
     * Back Up pushes a robot standing behind, exactly like a forward move would.
     */
    @Test
    void backUpPushesTheRobotBehind() {
        GameState state = AsciiBoard.state("""
            .
            .
            .
            """, """
            0
            1
            .
            """);

        play(state, 0, CardType.BACK_UP);

        assertRobots(state, """
            .
            0
            1
            """);
    }

    /**
     * Rotate Right, Rotate Left and U-Turn change facing by 90, -90 and 180 degrees
     * and log the change.
     */
    @Test
    void rotationCardsTurnTheRobotInPlace() {
        GameState state = AsciiBoard.state(".", "0");

        EventLog right = play(state, 0, CardType.ROTATE_RIGHT);
        assertEquals(Direction.EAST, state.robot(0).facing());
        assertEquals(List.of(new GameEvent.RobotRotated(0, Direction.NORTH, Direction.EAST, RotationCause.CARD)),
            right.events());

        play(state, 0, CardType.U_TURN);
        assertEquals(Direction.WEST, state.robot(0).facing());

        play(state, 0, CardType.ROTATE_LEFT);
        assertEquals(Direction.SOUTH, state.robot(0).facing());
        assertRobots(state, "0");
    }
}
