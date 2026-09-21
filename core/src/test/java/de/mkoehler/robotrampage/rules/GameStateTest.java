package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link GameState}'s lookups and that a copy can be mutated without touching
 * the original, which turn resolution relies on.
 *
 * @author Mario Koehler
 */
class GameStateTest {

    /**
     * Robots are found by their stable id, and an unknown id is an error.
     */
    @Test
    void robotsAreLookedUpById() {
        GameState state = AsciiBoard.state(". . .", "5 . 2");

        assertEquals(5, state.robot(5).id());
        assertEquals(new Position(2, 0), state.robot(2).position());
        assertThrows(IllegalArgumentException.class, () -> state.robot(3));
    }

    /**
     * Only active robots occupy a square: a destroyed robot no longer blocks or is found.
     */
    @Test
    void robotAtIgnoresRobotsThatAreNotActive() {
        GameState state = AsciiBoard.state(". .", "0 1");
        assertTrue(state.robotAt(new Position(1, 0)).isPresent());

        state.robot(1).setStatus(RobotStatus.DESTROYED);
        state.robot(1).setPosition(null);

        assertTrue(state.robotAt(new Position(1, 0)).isEmpty());
        assertTrue(state.robotAt(new Position(0, 0)).isPresent());
    }

    /**
     * Mutating a copy's robots and deck leaves the original state untouched.
     */
    @Test
    void copyIsDeep() {
        GameState original = AsciiBoard.state(". .", "0 .");
        GameState copy = original.copy();

        copy.robot(0).setPosition(new Position(1, 0));
        copy.deck().deal(9);

        assertEquals(new Position(0, 0), original.robot(0).position());
        assertEquals(84, original.deck().drawPileSize());
        assertEquals(75, copy.deck().drawPileSize());
    }
}
