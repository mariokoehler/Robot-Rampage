package de.mkoehler.robotrampage.devtools.editor;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the editing gestures of {@link BoardEditor}.
 *
 * @author Mario Koehler
 */
class BoardEditorTest {

    private final BoardEditor editor = new BoardEditor(new BoardDraft("test", "Test", null));

    /**
     * Clicks a square: a press and a release on it.
     *
     * @param x      the column
     * @param y      the row
     * @param side   the side nearest the pointer
     * @param remove {@code true} for the removing mouse button
     */
    private void click(int x, int y, Direction side, boolean remove) {
        editor.press(new Position(x, y), side, remove);
        editor.release();
    }

    @Test
    void draggingABeltLaysItAlongThePath() {
        editor.setTool(Tool.BELT);
        editor.press(new Position(1, 1), Direction.NORTH, false);
        editor.drag(new Position(2, 1));
        editor.drag(new Position(3, 1));
        editor.drag(new Position(3, 2));
        editor.release();

        Board board = editor.draft().toBoard();
        assertEquals(Optional.of(new Belt(Direction.EAST, false)), board.beltAt(new Position(1, 1)));
        assertEquals(Optional.of(new Belt(Direction.EAST, false)), board.beltAt(new Position(2, 1)));
        assertEquals(Optional.of(new Belt(Direction.NORTH, false)), board.beltAt(new Position(3, 1)));
        assertEquals(Optional.of(new Belt(Direction.NORTH, false)), board.beltAt(new Position(3, 2)));
        assertEquals(Direction.NORTH, editor.direction(), "the chosen direction follows the drag");
    }

    @Test
    void aFastBeltDragThatSkipsSquaresStillLaysAConnectedBelt() {
        editor.setTool(Tool.EXPRESS_BELT);
        editor.press(new Position(0, 0), Direction.NORTH, false);
        editor.drag(new Position(2, 1));
        editor.release();

        Board board = editor.draft().toBoard();
        assertEquals(4, board.belts().size());
        assertEquals(Optional.of(new Belt(Direction.NORTH, true)), board.beltAt(new Position(2, 0)));
    }

    @Test
    void aWallDragKeepsTheSideItWasPressedOn() {
        editor.setTool(Tool.WALL);
        editor.press(new Position(0, 5), Direction.NORTH, false);
        editor.drag(new Position(1, 5));
        editor.drag(new Position(2, 5));
        editor.release();

        for (int x = 0; x < 3; x++) {
            assertTrue(editor.draft().hasWall(new Position(x, 5), Direction.NORTH));
        }
    }

    @Test
    void oneGestureIsOneUndoStepAndAGestureThatChangesNothingIsNone() {
        editor.setTool(Tool.PIT);
        editor.press(new Position(1, 1), Direction.NORTH, false);
        editor.drag(new Position(2, 1));
        editor.release();
        click(5, 5, Direction.NORTH, true);

        assertTrue(editor.undo());
        assertEquals(SquareFeature.NONE, editor.draft().toBoard().featureAt(new Position(2, 1)));
        assertFalse(editor.undo());
        assertTrue(editor.redo());
        assertEquals(SquareFeature.PIT, editor.draft().toBoard().featureAt(new Position(2, 1)));
    }

    @Test
    void aFlagIsAddedThenDraggedWithItsNumber() {
        editor.setTool(Tool.FLAG);
        click(1, 1, Direction.NORTH, false);
        click(2, 2, Direction.NORTH, false);

        editor.press(new Position(1, 1), Direction.NORTH, false);
        editor.drag(new Position(1, 2));
        editor.drag(new Position(1, 3));
        editor.release();

        assertEquals(List.of(new Position(1, 3), new Position(2, 2)), editor.draft().flags());
    }

    @Test
    void clickingAStartSquareTurnsItButDraggingOneDoesNot() {
        editor.setTool(Tool.START);
        editor.setDirection(Direction.EAST);
        click(4, 0, Direction.NORTH, false);
        assertEquals(Direction.EAST, editor.draft().starts().get(0).facing());

        click(4, 0, Direction.NORTH, false);
        assertEquals(Direction.SOUTH, editor.draft().starts().get(0).facing());

        editor.press(new Position(4, 0), Direction.NORTH, false);
        editor.drag(new Position(5, 0));
        editor.release();
        assertEquals(new Position(5, 0), editor.draft().starts().get(0).position());
        assertEquals(Direction.SOUTH, editor.draft().starts().get(0).facing());
    }

    @Test
    void theRemovingButtonTakesAwayWhatTheToolPlaces() {
        editor.setTool(Tool.LASER);
        editor.setBeams(3);
        click(0, 4, Direction.WEST, false);
        assertEquals(3, editor.draft().toBoard().lasers().get(0).beams());

        click(0, 4, Direction.WEST, true);

        assertTrue(editor.draft().toBoard().lasers().isEmpty());
    }

    @Test
    void theLastRegisterCannotBeSwitchedOff() {
        assertEquals(Set.of(2, 4), editor.registers());
        assertTrue(editor.toggleRegister(2));
        assertFalse(editor.toggleRegister(4));
        assertEquals(Set.of(4), editor.registers());
    }

    @Test
    void editingMarksTheBoardUnsavedUntilSaved() {
        assertFalse(editor.isDirty());
        editor.setTool(Tool.REPAIR);
        click(3, 3, Direction.NORTH, false);
        assertTrue(editor.isDirty());

        editor.markSaved();

        assertFalse(editor.isDirty());
    }

    @Test
    void theNearestSideOfASquareIsFound() {
        assertEquals(Direction.NORTH, BoardEditor.nearestSide(0.5f, 0.9f));
        assertEquals(Direction.EAST, BoardEditor.nearestSide(0.95f, 0.4f));
        assertEquals(Direction.SOUTH, BoardEditor.nearestSide(0.5f, 0.05f));
        assertEquals(Direction.WEST, BoardEditor.nearestSide(0.1f, 0.6f));
    }

    @Test
    void boardIdsMustBeUsableFileNames() {
        assertTrue(BoardEditor.isValidId("proving-grounds"));
        assertTrue(BoardEditor.isValidId("board2"));
        assertFalse(BoardEditor.isValidId("Proving Grounds"));
        assertFalse(BoardEditor.isValidId("../escape"));
        assertFalse(BoardEditor.isValidId("-leading"));
        assertFalse(BoardEditor.isValidId(""));
    }
}
