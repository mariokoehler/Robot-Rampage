package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.devtools.editor.BoardDraft;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Suggestions}.
 *
 * @author Mario Koehler
 */
class SuggestionsTest {

    /**
     * From an empty canvas the search returns six valid boards, each in a different cell and truly in the cell it
     * claims; the same seed gives the same six.
     */
    @Test
    void sixDifferentValidBoardsTheSameWayEveryTime() {
        BoardDraft canvas = new BoardDraft("e", "E", null);

        List<Suggestions.Suggestion> first = Suggestions.suggest(canvas, 4L, 6, () -> false);
        List<Suggestions.Suggestion> second = Suggestions.suggest(canvas, 4L, 6, () -> false);

        assertEquals(6, first.size());
        Set<Suggestions.Cell> cells = new HashSet<>();
        for (Suggestions.Suggestion suggestion : first) {
            assertTrue(suggestion.rating().feasible());
            assertTrue(suggestion.draft().validate().isValid());
            assertEquals(suggestion.cell(), Suggestions.describe(suggestion.draft(), suggestion.rating()).cell());
            assertTrue(cells.add(suggestion.cell()), "each suggestion comes from its own cell");
        }
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).draft().toDefinition(), second.get(index).draft().toDefinition());
        }
    }

    /**
     * The first suggestion is the best board of all, and the six come from six separate searches, so no two are the same
     * board.
     */
    @Test
    void theBestComesFirstAndNoTwoAreTheSame() {
        List<Suggestions.Suggestion> six = Suggestions.suggest(new BoardDraft("e", "E", null), 4L, 6, () -> false);

        for (Suggestions.Suggestion suggestion : six) {
            assertTrue(six.get(0).rating().score() >= suggestion.rating().score());
        }
        Set<Object> boards = new HashSet<>();
        six.forEach(suggestion -> boards.add(suggestion.draft().toDefinition()));
        assertEquals(six.size(), boards.size());
    }

    /**
     * A cancelled search stops at once and suggests nothing.
     */
    @Test
    void aCancelledSearchStops() {
        assertTrue(Suggestions.suggest(new BoardDraft("e", "E", null), 4L, 6, () -> true).isEmpty());
    }

    /**
     * Cells are described in words along all three axes.
     */
    @Test
    void cellsAreDescribedInWords() {
        assertEquals("Deadly, short route, lots of movement", new Suggestions.Cell(2, 0, 2).words());
    }
}
