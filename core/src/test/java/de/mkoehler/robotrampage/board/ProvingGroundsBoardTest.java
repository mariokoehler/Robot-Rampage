package de.mkoehler.robotrampage.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the first shipped board, {@code assets/boards/proving-grounds.json} (design.md 2.11):
 * that it loads and passes validation with no errors and no warnings, and has the promised shape.
 *
 * @author Mario Koehler
 */
class ProvingGroundsBoardTest {

    private static final String RESOURCE = "boards/proving-grounds.json";

    /**
     * The board loads, is valid and produces no warnings: it has no accidental death traps.
     */
    @Test
    void loadsAndValidatesCleanly() {
        LoadedBoard loaded = BoardLoader.loadResource(RESOURCE);

        assertEquals("proving-grounds", loaded.definition().id());
        assertTrue(loaded.warnings().isEmpty(), loaded.warnings().toString());
    }

    /**
     * It is a 12x12 board with three flags and eight start squares, all facing north on the south
     * edge, so it can host the maximum of eight players.
     */
    @Test
    void hasTheDocumentedShape() {
        Board board = BoardLoader.loadResource(RESOURCE).board();

        assertEquals(12, board.width());
        assertEquals(12, board.height());
        assertEquals(3, board.flags().size());
        assertEquals(8, board.startSquares().size());
        assertTrue(board.startSquares().stream()
            .allMatch(start -> start.position().y() == 0 && start.facing() == Direction.NORTH));
    }

    /**
     * It uses the v1 element set: belts of both kinds, a gear, pits, repair sites, walls and board
     * lasers.
     */
    @Test
    void usesTheVersionOneElementSet() {
        Board board = BoardLoader.loadResource(RESOURCE).board();

        assertTrue(board.belts().values().stream().anyMatch(Belt::express));
        assertTrue(board.belts().values().stream().anyMatch(belt -> !belt.express()));
        assertTrue(board.features().containsValue(SquareFeature.PIT));
        assertTrue(board.features().containsValue(SquareFeature.GEAR_CLOCKWISE));
        assertTrue(board.features().containsValue(SquareFeature.REPAIR));
        assertEquals(2, board.lasers().size());
        assertTrue(board.walls().values().stream().anyMatch(sides -> !sides.isEmpty()));
    }

    /**
     * Exporting the loaded board and loading the export gives the same board again.
     */
    @Test
    void survivesAnExportAndReload() {
        LoadedBoard loaded = BoardLoader.loadResource(RESOURCE);
        BoardDefinition exported = BoardConverter.toDefinition(loaded.board(), "proving-grounds", "Proving Grounds",
            loaded.definition().author(), null, null);

        assertEquals(exported, BoardLoader.parse(BoardLoader.toJson(exported)).definition());
        assertEquals(loaded.definition().squares(), exported.squares());
        assertEquals(loaded.definition().edges(), exported.edges());
    }
}
