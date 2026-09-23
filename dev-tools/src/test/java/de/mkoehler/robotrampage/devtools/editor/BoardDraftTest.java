package de.mkoehler.robotrampage.devtools.editor;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BoardDraft}: that it round-trips the real board, and the rules that keep an edited board sensible.
 *
 * @author Mario Koehler
 */
class BoardDraftTest {

    private static final Path PROVING_GROUNDS = Path.of("..", "assets", "boards", "proving-grounds.json");

    /**
     * Returns a valid minimal board: one start square and one flag.
     *
     * @return the draft
     */
    private static BoardDraft minimal() {
        BoardDraft draft = new BoardDraft("test-board", "Test board", null);
        draft.addStart(new Position(0, 0), Direction.NORTH);
        draft.addFlag(new Position(5, 5));
        return draft;
    }

    @Test
    void theRealBoardSurvivesADraftUnchanged() throws IOException {
        BoardDefinition loaded = BoardLoader.parse(Files.readString(PROVING_GROUNDS)).definition();

        assertEquals(loaded, BoardDraft.of(loaded).toDefinition());
    }

    @Test
    void savingTheRealBoardWritesTheCheckedInFileByteForByte() throws IOException {
        String text = Files.readString(PROVING_GROUNDS).replace("\r\n", "\n");
        BoardDefinition loaded = BoardLoader.parse(text).definition();

        assertEquals(text, BoardFiles.format(BoardDraft.of(loaded).toDefinition()));
    }

    @Test
    void anEmptyBoardIsInvalidButStillExportsAndDraws() {
        BoardDraft draft = new BoardDraft("empty", "Empty", null);

        ValidationResult result = draft.validate();

        assertFalse(result.isValid());
        assertEquals(12, draft.toBoard().width());
        assertEquals(1, result.errors().stream().filter(e -> e.contains("at least one flag")).count(),
            "a problem both validator levels find is listed once");
    }

    @Test
    void aMinimalBoardIsValid() {
        assertTrue(minimal().validate().isValid());
    }

    @Test
    void removingALaserLeavesNoWallBehind() {
        BoardDraft draft = minimal();
        Position square = new Position(3, 4);
        draft.mountLaser(square, Direction.EAST, 2);
        assertTrue(draft.toBoard().hasWall(square, Direction.EAST));

        draft.removeMount(square, Direction.EAST);

        assertFalse(draft.toBoard().hasWall(square, Direction.EAST));
        assertTrue(draft.toBoard().lasers().isEmpty());
    }

    @Test
    void aLaserLoadedFromAFileAlsoLeavesNoWallWhenRemoved() {
        BoardDraft draft = minimal();
        draft.mountPusher(new Position(3, 4), Direction.NORTH, Set.of(1, 3));
        BoardDraft reloaded = BoardDraft.of(draft.toDefinition());

        reloaded.removeMount(new Position(3, 4), Direction.NORTH);

        assertFalse(reloaded.toBoard().hasWall(new Position(3, 4), Direction.NORTH));
    }

    @Test
    void theSideALaserIsMountedOnDecidesWhichWayItFires() {
        BoardDraft draft = minimal();
        draft.mountLaser(new Position(3, 4), Direction.EAST, 1);
        draft.mountLaser(new Position(4, 4), Direction.WEST, 1);

        List<Direction> firing = draft.toBoard().lasers().stream().map(laser -> laser.firingDirection()).toList();

        assertEquals(2, firing.size());
        assertTrue(firing.containsAll(List.of(Direction.EAST, Direction.WEST)));
    }

    @Test
    void aLaserReplacesAPusherOnTheSameSide() {
        BoardDraft draft = minimal();
        Position square = new Position(3, 4);
        draft.mountPusher(square, Direction.SOUTH, Set.of(2));

        draft.mountLaser(square, Direction.SOUTH, 1);

        Board board = draft.toBoard();
        assertTrue(board.pushers().isEmpty());
        assertEquals(1, board.lasers().size());
    }

    @Test
    void aWallIsTheSameFromBothSidesAndSkippedWhereAMountAlreadyWallsTheEdgeOff() {
        BoardDraft draft = minimal();
        assertTrue(draft.addWall(new Position(3, 4), Direction.EAST));
        assertFalse(draft.addWall(new Position(4, 4), Direction.WEST), "the same edge from the other side");
        assertTrue(draft.removeWall(new Position(4, 4), Direction.WEST));

        draft.mountLaser(new Position(6, 6), Direction.NORTH, 1);

        assertFalse(draft.addWall(new Position(6, 7), Direction.SOUTH));
    }

    @Test
    void mountingOnAWalledEdgeDropsThePlainWall() {
        BoardDraft draft = minimal();
        draft.addWall(new Position(3, 4), Direction.EAST);
        draft.mountLaser(new Position(4, 4), Direction.WEST, 1);

        draft.removeMount(new Position(4, 4), Direction.WEST);

        assertFalse(draft.toBoard().hasWall(new Position(3, 4), Direction.EAST));
    }

    @Test
    void wallsOnTheOuterBorderExportAndReload() {
        BoardDraft draft = minimal();
        draft.addWall(new Position(0, 3), Direction.WEST);
        draft.addWall(new Position(4, 0), Direction.SOUTH);
        draft.addWall(new Position(11, 3), Direction.EAST);
        draft.addWall(new Position(4, 11), Direction.NORTH);

        BoardDefinition definition = draft.toDefinition();

        assertEquals(4, definition.edges().size());
        assertEquals(definition, BoardDraft.of(definition).toDefinition());
        assertTrue(BoardDraft.of(definition).hasWall(new Position(0, 3), Direction.WEST));
    }

    @Test
    void onlyACrusherSharesItsSquareWithABelt() {
        BoardDraft draft = minimal();
        Position square = new Position(2, 2);
        draft.paintBelt(square, Direction.EAST, false);
        draft.paintCrusher(square, Set.of(1));
        Board withCrusher = draft.toBoard();
        assertTrue(withCrusher.beltAt(square).isPresent());
        assertEquals(SquareFeature.CRUSHER, withCrusher.featureAt(square));

        draft.paintFeature(square, SquareFeature.PIT);
        assertEquals(Optional.empty(), draft.toBoard().beltAt(square));
        assertTrue(draft.crushers().isEmpty());

        draft.paintBelt(square, Direction.NORTH, true);
        assertEquals(SquareFeature.NONE, draft.toBoard().featureAt(square));
        assertEquals(Optional.of(new Belt(Direction.NORTH, true)), draft.toBoard().beltAt(square));
    }

    @Test
    void crushersAndPushersNeedARegister() {
        BoardDraft draft = minimal();

        assertThrows(IllegalArgumentException.class, () -> draft.paintCrusher(new Position(1, 1), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> draft.mountPusher(new Position(1, 1), Direction.EAST, Set.of(6)));
    }

    @Test
    void movingAFlagKeepsItsNumberAndRemovingOneRenumbersTheRest() {
        BoardDraft draft = minimal();
        draft.addFlag(new Position(6, 6));
        draft.addFlag(new Position(7, 7));

        assertTrue(draft.moveFlag(new Position(5, 5), new Position(1, 1)));
        assertFalse(draft.moveFlag(new Position(1, 1), new Position(6, 6)), "a square with a flag is taken");
        assertEquals(List.of(new Position(1, 1), new Position(6, 6), new Position(7, 7)), draft.flags());

        draft.removeFlag(new Position(6, 6));
        assertEquals(List.of(new Position(1, 1), new Position(7, 7)), draft.flags());
    }

    @Test
    void startSquaresKeepTheirSeatAndFacingWhenMovedAndStopAtEight() {
        BoardDraft draft = minimal();
        for (int x = 1; x < 8; x++) {
            assertTrue(draft.addStart(new Position(x, 0), Direction.EAST));
        }
        assertFalse(draft.addStart(new Position(9, 0), Direction.EAST));

        draft.moveStart(new Position(0, 0), new Position(0, 1));
        draft.faceStart(new Position(0, 1), Direction.WEST);

        assertEquals(new Position(0, 1), draft.starts().get(0).position());
        assertEquals(Direction.WEST, draft.starts().get(0).facing());
    }

    @Test
    void onlyTwelveByTwelveBoardsCanBeEdited() {
        BoardDefinition small = new BoardDefinition(BoardDefinition.FORMAT_VERSION, "small", "Small", null, null, null,
            5, 5, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> BoardDraft.of(small));
    }

    @Test
    void aCopyIsIndependent() {
        BoardDraft draft = minimal();
        BoardDraft copy = draft.copy();

        draft.paintCrusher(new Position(4, 4), Set.of(1, 2));
        draft.addFlag(new Position(9, 9));

        assertTrue(copy.crushers().isEmpty());
        assertEquals(1, copy.flags().size());
    }
}
