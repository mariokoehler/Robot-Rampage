package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the rules {@link Rating} counts.
 *
 * @author Mario Koehler
 */
class RatingTest {

    /**
     * A small valid draft: one start square on the bottom row facing north, one flag in the middle.
     *
     * @return the draft
     */
    private static BoardDraft minimal() {
        BoardDraft draft = new BoardDraft("t", "T", null);
        draft.addStart(new Position(5, 0), Direction.NORTH);
        draft.addFlag(new Position(5, 8));
        return draft;
    }

    /**
     * A start square on a belt, or facing a pit or a wall right in front of it, breaks a hard rule.
     */
    @Test
    void unsafeStartSquaresAreViolations() {
        BoardDraft safe = minimal();
        BoardDraft onBelt = minimal();
        onBelt.paintBelt(new Position(5, 0), Direction.EAST, false);
        BoardDraft facingPit = minimal();
        facingPit.paintFeature(new Position(5, 1), SquareFeature.PIT);
        BoardDraft facingWall = minimal();
        facingWall.addWall(new Position(5, 0), Direction.NORTH);

        assertTrue(Rating.of(safe).feasible());
        assertFalse(Rating.of(onBelt).feasible());
        assertFalse(Rating.of(facingPit).feasible());
        assertFalse(Rating.of(facingWall).feasible());
    }

    /**
     * Belts cut down to one or two squares count as stubs, a longer run does not, and two belts pointing at each other
     * count as one head-on pair.
     */
    @Test
    void beltStubsAndHeadOnBeltsAreCounted() {
        BoardDraft draft = minimal();
        for (int x = 0; x < 4; x++) {
            draft.paintBelt(new Position(x, 5), Direction.EAST, false);
        }
        draft.paintBelt(new Position(9, 3), Direction.NORTH, false);
        draft.paintBelt(new Position(0, 10), Direction.EAST, false);
        draft.paintBelt(new Position(1, 10), Direction.WEST, false);
        Board board = draft.toBoard();

        assertEquals(3, Rating.beltStubs(board), "the lone belt and the head-on pair");
        assertEquals(1, Rating.headOnBelts(board));
    }

    /**
     * A laser whose emitter square is a pit is counted.
     */
    @Test
    void mountsOnPitsAreCounted() {
        BoardDraft draft = minimal();
        draft.mountLaser(new Position(0, 6), Direction.WEST, 1);
        draft.paintFeature(new Position(0, 6), SquareFeature.PIT);

        assertEquals(1, Rating.mountsOnPits(draft.toBoard()));
    }
}
