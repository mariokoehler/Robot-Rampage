package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies flag touching, archive marker updates and victory (design.md 2.10).
 *
 * @author Mario Koehler
 */
class CheckpointResolverTest {

    /**
     * Runs the checkpoint phase over all robots in id order.
     *
     * @param state the game state to mutate
     * @return the events of the phase
     */
    private static EventLog touch(GameState state) {
        EventLog log = new EventLog();
        new CheckpointResolver(state, log).touch();
        return log;
    }

    /**
     * Standing on the next flag touches it and moves the archive marker there.
     */
    @Test
    void standingOnTheNextFlagTouchesItAndMovesTheArchiveMarker() {
        GameState state = AsciiBoard.state(". 1 2", ". 0 .");
        state.robot(0).setArchiveMarker(new Position(0, 0));

        EventLog log = touch(state);

        assertEquals(1, state.robot(0).flagsTouched());
        assertEquals(new Position(1, 0), state.robot(0).archiveMarker());
        assertEquals(List.of(
            new GameEvent.FlagTouched(0, 1, new Position(1, 0)),
            new GameEvent.ArchiveMarkerMoved(0, new Position(1, 0))), log.events());
        assertFalse(state.isOver());
    }

    /**
     * Flags must be touched in order: standing on flag 2 without flag 1 does nothing.
     */
    @Test
    void flagsCannotBeSkipped() {
        GameState state = AsciiBoard.state("1 2", ". 0");
        state.robot(0).setPosition(new Position(1, 1));

        EventLog log = touch(state);

        assertEquals(0, state.robot(0).flagsTouched());
        assertTrue(log.events().isEmpty());
    }

    /**
     * A flag that was already touched is not touched again, and standing on the same flag twice
     * does not add up.
     */
    @Test
    void aFlagIsTouchedOnlyOnce() {
        GameState state = AsciiBoard.state("1 2", "0 .");

        touch(state);
        EventLog again = touch(state);

        assertEquals(1, state.robot(0).flagsTouched());
        assertTrue(again.events().isEmpty());
    }

    /**
     * Touching the final flag wins the game.
     */
    @Test
    void touchingTheFinalFlagWinsTheGame() {
        GameState state = AsciiBoard.state("1 2", ". 0");
        state.robot(0).setFlagsTouched(1);
        state.robot(0).setPosition(new Position(1, 0));

        EventLog log = touch(state);

        assertTrue(state.isOver());
        assertEquals(0, state.winnerId());
        assertTrue(log.events().contains(new GameEvent.GameEnded(0)));
    }

    /**
     * Robots that are not on the board cannot touch flags.
     */
    @Test
    void inactiveRobotsTouchNothing() {
        GameState state = AsciiBoard.state("1", "0");
        state.robot(0).setStatus(RobotStatus.DESTROYED);

        EventLog log = touch(state);

        assertTrue(log.events().isEmpty());
    }
}
