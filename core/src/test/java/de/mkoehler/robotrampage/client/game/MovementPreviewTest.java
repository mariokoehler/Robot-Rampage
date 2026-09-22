package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.game.MovementPreview.Step;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link MovementPreview} against small boards: it plays a robot's own cards the way the real rules do
 * (design.md 2.4), except that another robot blocks instead of being pushed, and the preview simply stops instead of
 * modelling destruction.
 *
 * @author Mario Koehler
 */
class MovementPreviewTest {

    private static final Set<Position> NO_OBSTACLES = Set.of();

    /**
     * Builds a card with an arbitrary priority; the preview never looks at it.
     *
     * @param type the card type
     * @return the card
     */
    private static Card card(CardType type) {
        return new Card(type, 500);
    }

    /**
     * A move card walks forward and reports one step per card, matching how far it actually got.
     */
    @Test
    void moveCardsWalkForwardOneStepPerCard() {
        Board board = AsciiBoard.board(". . . . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_2), card(CardType.MOVE_1)), NO_OBSTACLES);

        assertEquals(List.of(new Step(new Position(2, 0), Direction.EAST), new Step(new Position(3, 0), Direction.EAST)),
            path);
    }

    /**
     * A wall stops that card's movement early, but the robot survives and the next card still plays out from where it
     * stopped.
     */
    @Test
    void aWallStopsOneCardButTheNextCardStillPlays() {
        // Wall on the east side of the second square (x=1): one successful step from x=0, then blocked.
        Board board = AsciiBoard.board(". . | . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_3), card(CardType.ROTATE_RIGHT)), NO_OBSTACLES);

        assertEquals(List.of(new Step(new Position(1, 0), Direction.EAST), new Step(new Position(1, 0), Direction.SOUTH)),
            path);
    }

    /**
     * Rotate cards turn the robot in place: the position does not change, only the facing.
     */
    @Test
    void rotateCardsTurnInPlace() {
        Board board = AsciiBoard.board(". . .");

        List<Step> path = MovementPreview.path(board, new Position(1, 0), Direction.NORTH,
            List.of(card(CardType.ROTATE_LEFT), card(CardType.U_TURN)), NO_OBSTACLES);

        assertEquals(List.of(new Step(new Position(1, 0), Direction.WEST), new Step(new Position(1, 0), Direction.EAST)),
            path);
    }

    /**
     * Back up moves opposite the robot's facing, without turning it.
     */
    @Test
    void backUpMovesOppositeTheFacing() {
        Board board = AsciiBoard.board(". . .");

        List<Step> path = MovementPreview.path(board, new Position(1, 0), Direction.EAST,
            List.of(card(CardType.BACK_UP)), NO_OBSTACLES);

        assertEquals(List.of(new Step(new Position(0, 0), Direction.EAST)), path);
    }

    /**
     * A pit ends the preview for good: no step is added for the destroying card, and nothing after it runs either,
     * exactly as a destroyed robot plays no more cards in a real turn.
     */
    @Test
    void aPitEndsThePreviewWithNoFurtherSteps() {
        Board board = AsciiBoard.board(". . o . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_2), card(CardType.ROTATE_LEFT)), NO_OBSTACLES);

        assertTrue(path.isEmpty());
    }

    /**
     * Walking off the edge of the board is treated exactly like a pit: the preview ends with no further steps.
     */
    @Test
    void theEdgeOfTheBoardEndsThePreview() {
        Board board = AsciiBoard.board(". . .");

        List<Step> path = MovementPreview.path(board, new Position(2, 0), Direction.EAST,
            List.of(card(CardType.MOVE_1)), NO_OBSTACLES);

        assertTrue(path.isEmpty());
    }

    /**
     * Another robot's square blocks a move exactly like a wall would: the robot stops one square short, survives, and
     * later cards keep playing — the deliberate divergence from the real rules, which would push a robot that can be
     * pushed instead of stopping (design.md 3.5: this preview is never authoritative, since the other robot's own
     * program is unknown).
     */
    @Test
    void anotherRobotBlocksInsteadOfBeingPushed() {
        Board board = AsciiBoard.board(". . . . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_3), card(CardType.MOVE_1)), Set.of(new Position(2, 0)));

        assertEquals(List.of(new Step(new Position(1, 0), Direction.EAST), new Step(new Position(1, 0), Direction.EAST)),
            path, "blocked by the obstacle at (2,0) on both cards, one square short of it each time");
    }

    /**
     * No cards means no steps.
     */
    @Test
    void noCardsGivesAnEmptyPath() {
        Board board = AsciiBoard.board(". . .");

        assertTrue(MovementPreview.path(board, new Position(1, 0), Direction.NORTH, List.of(), NO_OBSTACLES).isEmpty());
    }
}
