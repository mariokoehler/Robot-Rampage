package de.mkoehler.robotrampage.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies reading and writing boards as JSON, including that the export is canonical so a
 * board survives a round trip unchanged (design.md 3.6).
 *
 * @author Mario Koehler
 */
class BoardLoaderTest {

    private static final String SMALL_BOARD = """
        {
          "formatVersion": 1,
          "id": "small",
          "name": "Small",
          "width": 5,
          "height": 4,
          "squares": [
            {"x": 3, "y": 1, "belt": {"dir": "EAST", "express": true}},
            {"x": 2, "y": 2, "feature": "PIT"},
            {"x": 1, "y": 1, "feature": "GEAR_CLOCKWISE"},
            {"x": 0, "y": 3, "feature": "CRUSHER", "registers": [2, 4]},
            {"x": 0, "y": 0, "belt": {"dir": "NORTH", "express": false}, "feature": "REPAIR"}
          ],
          "edges": [
            {"x": 1, "y": 2, "side": "NORTH", "wall": true},
            {"x": 4, "y": 0, "side": "EAST", "laser": {"beams": 2}},
            {"x": 3, "y": 3, "side": "SOUTH", "pusher": {"registers": [1, 5]}}
          ],
          "flags": [{"x": 4, "y": 3}, {"x": 0, "y": 1}],
          "startSquares": [{"x": 1, "y": 0, "facing": "NORTH"}, {"x": 2, "y": 0, "facing": "EAST"}]
        }
        """;

    /**
     * A definition is turned into the matching board: belts, features, crushers with their
     * registers, walls (mirrored), lasers, pushers, flags in order and start squares.
     */
    @Test
    void parsesEveryKindOfElement() {
        LoadedBoard loaded = BoardLoader.parse(SMALL_BOARD);
        Board board = loaded.board();

        assertEquals("small", loaded.definition().id());
        assertEquals(5, board.width());
        assertEquals(4, board.height());
        assertTrue(board.beltAt(new Position(3, 1)).orElseThrow().express());
        assertTrue(board.isPit(new Position(2, 2)));
        assertEquals(SquareFeature.GEAR_CLOCKWISE, board.featureAt(new Position(1, 1)));
        assertTrue(board.isCrusherActive(new Position(0, 3), 4));
        assertFalse(board.isCrusherActive(new Position(0, 3), 3));
        assertTrue(board.hasWall(new Position(1, 2), Direction.NORTH));
        assertTrue(board.hasWall(new Position(1, 3), Direction.SOUTH));
        assertEquals(2, board.lasers().get(0).beams());
        assertEquals(Direction.SOUTH, board.pushers().get(0).side());
        assertEquals(new Position(4, 3), board.flags().get(0));
        assertEquals(new Position(0, 1), board.flags().get(1));
        assertEquals(Direction.EAST, board.startSquares().get(1).facing());
        assertEquals(SquareFeature.REPAIR, board.featureAt(new Position(0, 0)));
        assertTrue(board.beltAt(new Position(0, 0)).isPresent());
    }

    /**
     * Writing a definition as JSON and reading it back gives an equal definition.
     */
    @Test
    void jsonRoundTripKeepsTheDefinition() {
        BoardDefinition original = BoardLoader.parse(SMALL_BOARD).definition();

        BoardDefinition reread = BoardLoader.parse(BoardLoader.toJson(original)).definition();

        assertEquals(original, reread);
    }

    /**
     * The generator id and seed that make a generated board reproducible survive the round trip,
     * including a seed of zero, which a careless JSON setting could drop as a "default" value.
     */
    @Test
    void generatorAndSeedSurviveTheRoundTrip() {
        Board board = new Board.Builder(2, 1).flag(new Position(1, 0)).startSquare(new Position(0, 0), Direction.NORTH).build();
        for (long seed : new long[] {0L, 42L, -7L}) {
            BoardDefinition original = BoardConverter.toDefinition(board, "gen", "Generated", null, "maze-v1", seed);

            BoardDefinition reread = BoardLoader.parse(BoardLoader.toJson(original)).definition();

            assertEquals("maze-v1", reread.generator());
            assertEquals(seed, reread.seed());
            assertEquals(original, reread);
        }
    }

    /**
     * The crusher registers a board hands out cannot be used to change the board.
     */
    @Test
    void crusherRegistersCannotBeModifiedFromOutside() {
        Board board = new Board.Builder(1, 1).crusher(new Position(0, 0), 2).build();

        assertThrows(UnsupportedOperationException.class, () -> board.crusherRegisters(new Position(0, 0)).add(3));
    }

    /**
     * Absent optional parts read as empty, and absent metadata as null.
     */
    @Test
    void absentOptionalPartsAreEmpty() {
        BoardDefinition definition = BoardLoader.parse("""
            {"formatVersion": 1, "id": "b", "name": "B", "width": 2, "height": 1,
             "flags": [{"x": 1, "y": 0}], "startSquares": [{"x": 0, "y": 0, "facing": "EAST"}]}
            """).definition();

        assertTrue(definition.squares().isEmpty());
        assertTrue(definition.edges().isEmpty());
        assertNull(definition.author());
        assertNull(definition.seed());
    }

    /**
     * The export writes a wall between two squares once, whichever side it was added from, does
     * not write the wall a laser mount implies, and is stable when repeated.
     */
    @Test
    void exportIsCanonical() {
        Board board = new Board.Builder(4, 4)
            .wall(new Position(2, 2), Direction.WEST)
            .wall(new Position(1, 2), Direction.EAST)
            .laser(new Position(3, 3), Direction.SOUTH, 1)
            .flag(new Position(0, 0))
            .startSquare(new Position(1, 0), Direction.NORTH)
            .build();

        BoardDefinition definition = BoardConverter.toDefinition(board, "c", "C", null, null, null);

        assertEquals(2, definition.edges().size());
        assertEquals(new BoardDefinition.Edge(1, 2, Direction.EAST, true, null, null), definition.edges().get(0));
        assertEquals(new BoardDefinition.Edge(3, 3, Direction.SOUTH, false, new BoardDefinition.Laser(1), null),
            definition.edges().get(1));
        assertEquals(definition, BoardConverter.toDefinition(BoardConverter.toBoard(definition), "c", "C", null, null, null));
    }

    /**
     * Outer walls on the south and west border cannot be described from an outside square, so they
     * are kept on the square inside, and survive the round trip.
     */
    @Test
    void outerWallsSurviveTheRoundTrip() {
        Board board = new Board.Builder(2, 2)
            .wall(new Position(0, 0), Direction.SOUTH)
            .wall(new Position(0, 1), Direction.WEST)
            .wall(new Position(1, 1), Direction.NORTH)
            .flag(new Position(1, 0))
            .startSquare(new Position(0, 0), Direction.NORTH)
            .build();

        Board again = BoardConverter.toBoard(BoardConverter.toDefinition(board, "o", "O", null, null, null));

        assertTrue(again.hasWall(new Position(0, 0), Direction.SOUTH));
        assertTrue(again.hasWall(new Position(0, 1), Direction.WEST));
        assertTrue(again.hasWall(new Position(1, 1), Direction.NORTH));
        assertFalse(again.hasWall(new Position(1, 0), Direction.SOUTH));
    }

    /**
     * A misspelled property is an error, not silently ignored.
     */
    @Test
    void unknownPropertiesAreRejected() {
        InvalidBoardException e = assertThrows(InvalidBoardException.class,
            () -> BoardLoader.parse(SMALL_BOARD.replace("\"width\"", "\"widht\"")));

        assertTrue(e.getMessage().contains("widht"));
    }

    /**
     * Text that is not JSON at all is reported as an invalid board, not as a raw parser exception.
     */
    @Test
    void malformedJsonIsAnInvalidBoard() {
        assertThrows(InvalidBoardException.class, () -> BoardLoader.parse("this is not json"));
    }

    /**
     * A structurally invalid board lists every problem it has, not just the first.
     */
    @Test
    void invalidBoardsReportAllErrors() {
        InvalidBoardException e = assertThrows(InvalidBoardException.class,
            () -> BoardLoader.parse(SMALL_BOARD.replace("\"id\": \"small\",", "").replace("\"flags\": [{\"x\": 4, \"y\": 3}, {\"x\": 0, \"y\": 1}],", "")));

        assertEquals(2, e.errors().size());
    }

    /**
     * A missing resource is reported as an invalid board.
     */
    @Test
    void missingResourceIsReported() {
        InvalidBoardException e = assertThrows(InvalidBoardException.class,
            () -> BoardLoader.loadResource("boards/does-not-exist.json"));

        assertTrue(e.getMessage().contains("does-not-exist"));
    }
}
