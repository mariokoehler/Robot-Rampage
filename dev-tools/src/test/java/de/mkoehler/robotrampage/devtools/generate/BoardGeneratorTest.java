package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.BoardValidator;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.devtools.analysis.BoardMetrics;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BoardGenerator} and {@link Mutations}: the changes never break a board's fixed parts, and a run gives the
 * same valid board for the same canvas and seed.
 *
 * @author Mario Koehler
 */
class BoardGeneratorTest {

    /**
     * Reads the first board as a draft.
     *
     * @return the draft
     */
    private static BoardDraft provingGrounds() {
        try {
            return BoardDraft.of(BoardLoader.parse(Files.readString(Path.of("..", "assets", "boards",
                "proving-grounds.json"))).definition());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Thousands of random changes, from an empty board and from a real one, never throw, never move a start square and
     * never change how many flags there are; the result can always be built and checked.
     */
    @Test
    void mutationsNeverThrowOrTouchTheFixedParts() {
        Random random = new Random(11);
        for (BoardDraft start : List.of(BoardGenerator.prepare(new BoardDraft("e", "E", null), random),
            provingGrounds())) {
            BoardDraft draft = start.copy();
            List<StartSquare> starts = List.copyOf(draft.starts());
            int flags = draft.flags().size();
            for (int step = 0; step < 3000; step++) {
                assertDoesNotThrow(() -> Mutations.mutate(draft, random));
                assertEquals(starts, draft.starts());
                assertEquals(flags, draft.flags().size());
            }
            assertDoesNotThrow(() -> BoardValidator.validate(draft.toBoard()));
            assertDoesNotThrow(() -> Rating.of(draft));
        }
    }

    /**
     * An empty canvas gets eight start squares along the bottom row and three flags, and comes back as a valid board
     * whose flags can all be reached; the same seed gives the same board.
     */
    @Test
    void anEmptyCanvasBecomesAValidBoardTheSameWayEveryTime() {
        BoardDraft canvas = new BoardDraft("e", "E", null);

        BoardGenerator.Candidate first = BoardGenerator.generate(canvas, 7L, () -> false);
        BoardGenerator.Candidate second = BoardGenerator.generate(canvas, 7L, () -> false);

        assertTrue(first.rating().feasible());
        assertTrue(first.draft().validate().isValid(), first.draft().validate().errors().toString());
        assertTrue(BoardMetrics.of(first.draft().toBoard()).allReachable());
        assertEquals(8, first.draft().starts().size());
        assertEquals(new StartSquare(new Position(2, 0), Direction.NORTH), first.draft().starts().get(0));
        assertEquals(3, first.draft().flags().size());
        assertEquals(first.draft().toDefinition(), second.draft().toDefinition());
        assertTrue(canvas.starts().isEmpty(), "the canvas itself is not changed");
    }

    /**
     * Starting from a real board, its start squares and number of flags are kept, and the result rates at least as well
     * as the board it started from.
     */
    @Test
    void aRealCanvasKeepsItsStartsAndGetsNoWorse() {
        BoardDraft canvas = provingGrounds();

        BoardGenerator.Candidate result = BoardGenerator.generate(canvas, 3L, () -> false);

        assertEquals(canvas.starts(), result.draft().starts());
        assertEquals(canvas.flags().size(), result.draft().flags().size());
        assertFalse(Rating.of(canvas).betterThan(result.rating()));
    }

    /**
     * A cancelled run stops at once and returns the canvas it started from.
     */
    @Test
    void aCancelledRunReturnsTheStart() {
        BoardDraft canvas = provingGrounds();

        BoardGenerator.Candidate result = BoardGenerator.generate(canvas, 3L, () -> true);

        assertEquals(canvas.toDefinition(), result.draft().toDefinition());
    }
}
