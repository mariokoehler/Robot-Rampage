package de.mkoehler.robotrampage.board;

import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies both levels of {@link BoardValidator}: the structure of a JSON definition and the
 * playability of a finished board (design.md 3.6).
 *
 * @author Mario Koehler
 */
class BoardValidatorTest {

    private static final BoardDefinition.Flag FLAG = new BoardDefinition.Flag(2, 2);
    private static final BoardDefinition.Start START = new BoardDefinition.Start(0, 0, Direction.NORTH);

    /**
     * Builds a 3x3 definition around the given parts.
     *
     * @param squares the squares
     * @param edges   the edges
     * @param flags   the flags
     * @param starts  the start squares
     * @return the definition
     */
    private static BoardDefinition definition(List<BoardDefinition.Square> squares, List<BoardDefinition.Edge> edges,
                                              List<BoardDefinition.Flag> flags, List<BoardDefinition.Start> starts) {
        return new BoardDefinition(1, "id", "Name", null, null, null, 3, 3, squares, edges, flags, starts);
    }

    /**
     * Builds a valid definition with the given squares and edges.
     *
     * @param squares the squares
     * @param edges   the edges
     * @return the definition
     */
    private static BoardDefinition withElements(List<BoardDefinition.Square> squares, List<BoardDefinition.Edge> edges) {
        return definition(squares, edges, List.of(FLAG), List.of(START));
    }

    /**
     * Asserts that a definition has an error mentioning a text.
     *
     * @param definition the definition
     * @param text       a fragment of the expected error message
     */
    private static void assertDefinitionError(BoardDefinition definition, String text) {
        ValidationResult result = BoardValidator.validate(definition);
        assertFalse(result.isValid(), "expected an error about: " + text);
        assertTrue(result.errors().stream().anyMatch(error -> error.contains(text)),
            "no error contains '" + text + "' in " + result.errors());
    }

    /**
     * A minimal well-formed definition is valid.
     */
    @Test
    void aMinimalDefinitionIsValid() {
        assertTrue(BoardValidator.validate(definition(List.of(), List.of(), List.of(FLAG), List.of(START))).isValid());
    }

    /**
     * Wrong format version, missing id or name and impossible sizes are errors.
     */
    @Test
    void metadataAndSizeAreChecked() {
        assertDefinitionError(new BoardDefinition(2, "id", "N", null, null, null, 3, 3, null, null, List.of(FLAG),
            List.of(START)), "formatVersion");
        assertDefinitionError(new BoardDefinition(1, " ", "N", null, null, null, 3, 3, null, null, List.of(FLAG),
            List.of(START)), "id");
        assertDefinitionError(new BoardDefinition(1, "id", null, null, null, null, 3, 3, null, null, List.of(FLAG),
            List.of(START)), "name");
        assertDefinitionError(new BoardDefinition(1, "id", "N", null, null, null, 0, 3, null, null, List.of(FLAG),
            List.of(START)), "size");
        assertDefinitionError(new BoardDefinition(1, "id", "N", null, null, null, 3, 65, null, null, List.of(FLAG),
            List.of(START)), "size");
    }

    /**
     * Squares must lie on the grid and be listed only once.
     */
    @Test
    void squaresMustBeInBoundsAndUnique() {
        BoardDefinition.Square pit = new BoardDefinition.Square(1, 1, null, SquareFeature.PIT, null);
        assertDefinitionError(withElements(List.of(new BoardDefinition.Square(3, 0, null, SquareFeature.PIT, null)),
            List.of()), "outside");
        assertDefinitionError(withElements(List.of(pit, pit), List.of()), "more than once");
    }

    /**
     * The feature {@code NONE} is not written in a file: plain floor is simply left out.
     */
    @Test
    void anExplicitNoneFeatureIsRejected() {
        assertDefinitionError(withElements(List.of(new BoardDefinition.Square(1, 1, null, SquareFeature.NONE, null)),
            List.of()), "NONE");
    }

    /**
     * A crusher needs registers 1 to 5; other features may not list any.
     */
    @Test
    void crusherRegistersAreChecked() {
        assertDefinitionError(withElements(List.of(new BoardDefinition.Square(1, 1, null, SquareFeature.CRUSHER, null)),
            List.of()), "no register");
        assertDefinitionError(withElements(
            List.of(new BoardDefinition.Square(1, 1, null, SquareFeature.CRUSHER, List.of(6))), List.of()), "invalid register");
        assertDefinitionError(withElements(
            List.of(new BoardDefinition.Square(1, 1, null, SquareFeature.PIT, List.of(1))), List.of()), "no crusher");
    }

    /**
     * An edge must carry something, may not carry both a laser and a pusher, and needs sensible
     * beam counts and registers.
     */
    @Test
    void edgesAreChecked() {
        assertDefinitionError(withElements(List.of(),
            List.of(new BoardDefinition.Edge(1, 1, Direction.NORTH, false, null, null))), "no wall, laser or pusher");
        assertDefinitionError(withElements(List.of(), List.of(new BoardDefinition.Edge(1, 1, Direction.NORTH, false,
            new BoardDefinition.Laser(1), new BoardDefinition.Pusher(List.of(1))))), "both a laser and a pusher");
        assertDefinitionError(withElements(List.of(), List.of(new BoardDefinition.Edge(1, 1, Direction.NORTH, false,
            new BoardDefinition.Laser(4), null))), "1 to 3 beams");
        assertDefinitionError(withElements(List.of(), List.of(new BoardDefinition.Edge(1, 1, Direction.NORTH, false, null,
            new BoardDefinition.Pusher(List.of())))), "no register");
        assertDefinitionError(withElements(List.of(), List.of(new BoardDefinition.Edge(5, 1, Direction.NORTH, true, null,
            null))), "outside");
        assertDefinitionError(withElements(List.of(), List.of(new BoardDefinition.Edge(1, 1, null, true, null, null))),
            "no side");
    }

    /**
     * Two mounts on the same side of the same square are an error.
     */
    @Test
    void aSideCarriesAtMostOneLaserOrPusher() {
        BoardDefinition.Edge laser = new BoardDefinition.Edge(1, 1, Direction.NORTH, false, new BoardDefinition.Laser(1), null);

        assertDefinitionError(withElements(List.of(), List.of(laser, laser)), "more than one");
    }

    /**
     * Flags and start squares: at least one of each, in bounds, no duplicates, at most eight starts.
     */
    @Test
    void flagsAndStartSquaresAreChecked() {
        assertDefinitionError(definition(List.of(), List.of(), List.of(), List.of(START)), "at least one flag");
        assertDefinitionError(definition(List.of(), List.of(), List.of(FLAG), List.of()), "at least one start");
        assertDefinitionError(definition(List.of(), List.of(), List.of(FLAG, FLAG), List.of(START)), "shares its square");
        assertDefinitionError(definition(List.of(), List.of(), List.of(new BoardDefinition.Flag(9, 9)), List.of(START)),
            "outside");
        assertDefinitionError(definition(List.of(), List.of(), List.of(FLAG), List.of(START, START)), "more than once");
        assertDefinitionError(definition(List.of(), List.of(), List.of(FLAG),
            List.of(new BoardDefinition.Start(0, 0, null))), "no facing");
        List<BoardDefinition.Start> nine = java.util.stream.IntStream.range(0, 9)
            .mapToObj(i -> new BoardDefinition.Start(i % 3, i / 3, Direction.NORTH)).toList();
        assertDefinitionError(definition(List.of(), List.of(), List.of(FLAG), nine), "at most 8");
    }

    /**
     * A flag on a pit or a start square on a pit is unplayable.
     */
    @Test
    void flagsAndStartSquaresMustNotBeOnPits() {
        ValidationResult flagOnPit = BoardValidator.validate(AsciiBoard.board(". o", builder -> builder
            .flag(new Position(1, 0))
            .startSquare(new Position(0, 0), Direction.NORTH)));
        assertTrue(flagOnPit.errors().stream().anyMatch(error -> error.contains("Flag 1") && error.contains("pit")),
            flagOnPit.errors().toString());
        ValidationResult startOnPit = BoardValidator.validate(AsciiBoard.board("o 1", builder -> builder
            .startSquare(new Position(0, 0), Direction.NORTH)));
        assertTrue(startOnPit.errors().stream().anyMatch(error -> error.contains("start square") && error.contains("pit")));
    }

    /**
     * A flag behind a wall is unreachable, and so is a flag behind a line of pits.
     */
    @Test
    void unreachableFlagsAreErrors() {
        ValidationResult walled = BoardValidator.validate(AsciiBoard.board(". | 1", builder -> builder
            .startSquare(new Position(0, 0), Direction.NORTH)));
        assertTrue(walled.errors().stream().anyMatch(error -> error.contains("Flag 1") && error.contains("reached")),
            walled.errors().toString());

        ValidationResult pitted = BoardValidator.validate(AsciiBoard.board(". o 1", builder -> builder
            .startSquare(new Position(0, 0), Direction.NORTH)));
        assertTrue(pitted.errors().stream().anyMatch(error -> error.contains("Flag 1")), pitted.errors().toString());
    }

    /**
     * A second start square cut off from the first is an error too.
     */
    @Test
    void cutOffStartSquaresAreErrors() {
        ValidationResult result = BoardValidator.validate(AsciiBoard.board(". . | . 1", builder -> builder
            .startSquare(new Position(0, 0), Direction.NORTH)
            .startSquare(new Position(2, 0), Direction.NORTH)));

        assertTrue(result.errors().stream().anyMatch(error -> error.contains("cut off")), result.errors().toString());
    }

    /**
     * A board where everything is connected is valid, and reachability walks around walls.
     */
    @Test
    void connectedBoardsAreValid() {
        ValidationResult result = BoardValidator.validate(AsciiBoard.board("""
            . . 1
              -
            . . .
            """, builder -> builder.startSquare(new Position(0, 0), Direction.NORTH)));

        assertTrue(result.isValid(), result.errors().toString());
        assertTrue(result.warnings().isEmpty());
    }

    /**
     * Belts that lead off the board, into a pit or into a wall are legal but suspicious.
     */
    @Test
    void deadEndBeltsAreWarnings() {
        ValidationResult result = BoardValidator.validate(AsciiBoard.board("""
            > o . >
            v . . .
            > | . . 1
            """, builder -> builder.startSquare(new Position(2, 0), Direction.NORTH)));

        assertTrue(result.isValid(), result.errors().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("(0,2)") && w.contains("into a pit")), result.warnings().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("(3,2)") && w.contains("off the board")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("(0,0)") && w.contains("into a wall")));
    }

    /**
     * A flag or start square on a belt or crusher, or a start square on a flag, is a warning.
     */
    @Test
    void specialSquaresUnderFlagsAndStartsAreWarnings() {
        ValidationResult result = BoardValidator.validate(AsciiBoard.board(". > x", builder -> builder
            .flag(new Position(1, 0))
            .flag(new Position(2, 0))
            .startSquare(new Position(0, 0), Direction.NORTH)
            .startSquare(new Position(1, 0), Direction.NORTH)));

        assertTrue(result.isValid(), result.errors().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Flag 1") && w.contains("belt")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Flag 2") && w.contains("crusher")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("start square") && w.contains("on a flag")));
    }

    /**
     * A board without flags or without start squares, or with two of them on one square, is invalid.
     */
    @Test
    void boardLevelCountsAndDuplicatesAreChecked() {
        assertTrue(BoardValidator.validate(new Board.Builder(2, 1).build()).errors().size() >= 2);
        ValidationResult duplicates = BoardValidator.validate(new Board.Builder(2, 1)
            .flag(new Position(1, 0)).flag(new Position(1, 0))
            .startSquare(new Position(0, 0), Direction.NORTH).startSquare(new Position(0, 0), Direction.EAST).build());
        assertEquals(2, duplicates.errors().size(), duplicates.errors().toString());
    }
}
