package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies removing a robot whose player has left (design.md 2.13) and the last-robot-standing check
 * that follows it.
 *
 * @author Mario Koehler
 */
class ForfeitTest {

    /**
     * A robot on the board is destroyed with the forfeit cause, its cards return to the discard pile and it is
     * eliminated even though it still had lives.
     */
    @Test
    void forfeitRemovesAnActiveRobotForGood() {
        GameState state = AsciiBoard.state(". .", "0 1");
        Programs.program(state, 0, 100, CardType.MOVE_1, CardType.MOVE_1);
        EventLog log = new EventLog();

        Forfeit.forfeit(state, 0, log);

        Robot robot = state.robot(0);
        assertEquals(RobotStatus.ELIMINATED, robot.status());
        assertEquals(0, robot.lives());
        assertNull(robot.position());
        assertEquals(2, state.deck().discardPileSize());
        assertEquals(List.of(new GameEvent.RobotDestroyed(0, DestructionCause.FORFEIT)), log.events());
    }

    /**
     * A robot that was waiting to re-enter is eliminated too, with the same event, so every client learns about it.
     */
    @Test
    void forfeitEliminatesARobotWaitingToRespawn() {
        GameState state = AsciiBoard.state(". .", "0 1");
        Destruction.destroy(state, state.robot(0), DestructionCause.PIT, new EventLog());
        EventLog log = new EventLog();

        Forfeit.forfeit(state, 0, log);

        assertEquals(RobotStatus.ELIMINATED, state.robot(0).status());
        assertEquals(List.of(new GameEvent.RobotDestroyed(0, DestructionCause.FORFEIT)), log.events());
    }

    /**
     * Forfeiting an already eliminated robot changes nothing and logs nothing.
     */
    @Test
    void forfeitingTwiceDoesNothingTheSecondTime() {
        GameState state = AsciiBoard.state(". .", "0 1");
        Forfeit.forfeit(state, 0, new EventLog());
        EventLog log = new EventLog();

        Forfeit.forfeit(state, 0, log);

        assertTrue(log.events().isEmpty());
    }

    /**
     * When only one robot is left after a forfeit, it wins; with two or more left, the game goes on.
     */
    @Test
    void lastRobotStandingWinsAfterAForfeit() {
        GameState three = AsciiBoard.state(". . .", "0 1 2");
        Forfeit.forfeit(three, 0, new EventLog());
        GameOutcome.endIfOneRobotIsLeft(three, new EventLog());
        assertTrue(!three.isOver());

        Forfeit.forfeit(three, 1, new EventLog());
        EventLog log = new EventLog();
        GameOutcome.endIfOneRobotIsLeft(three, log);

        assertTrue(three.isOver());
        assertEquals(2, three.winnerId());
        assertEquals(List.of(new GameEvent.GameEnded(2)), log.events());
    }

    /**
     * If nobody is left the game ends without a winner, and a game that is already over is not ended again.
     */
    @Test
    void noRobotsLeftMeansNoWinnerAndTheGameEndsOnlyOnce() {
        GameState state = AsciiBoard.state(". .", "0 1");
        Forfeit.forfeit(state, 0, new EventLog());
        Forfeit.forfeit(state, 1, new EventLog());
        EventLog log = new EventLog();

        GameOutcome.endIfOneRobotIsLeft(state, log);
        GameOutcome.endIfOneRobotIsLeft(state, log);

        assertTrue(state.isOver());
        assertEquals(GameState.NO_WINNER, state.winnerId());
        assertEquals(List.of(new GameEvent.GameEnded(GameEvent.NO_ROBOT)), log.events());
    }
}
