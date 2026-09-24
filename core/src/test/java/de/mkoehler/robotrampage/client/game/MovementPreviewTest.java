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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link MovementPreview} against small boards: it plays a robot's own cards and this register's board
 * effects the way the real rules do (design.md 2.4), except that other robots are never simulated, and marks where
 * the robot would be destroyed with a last step.
 *
 * @author Mario Koehler
 */
class MovementPreviewTest {

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
            List.of(card(CardType.MOVE_2), card(CardType.MOVE_1)));

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
            List.of(card(CardType.MOVE_3), card(CardType.ROTATE_RIGHT)));

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
            List.of(card(CardType.ROTATE_LEFT), card(CardType.U_TURN)));

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
            List.of(card(CardType.BACK_UP)));

        assertEquals(List.of(new Step(new Position(0, 0), Direction.EAST)), path);
    }

    /**
     * A pit ends the preview for good: the destroying card's step is marked destroyed and lies on the pit itself, and
     * nothing after it runs, exactly as a destroyed robot plays no more cards in a real turn.
     */
    @Test
    void aPitEndsThePreviewWithADestroyedStepOnThePit() {
        Board board = AsciiBoard.board(". . o . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_2), card(CardType.ROTATE_LEFT)));

        assertEquals(List.of(new Step(new Position(2, 0), Direction.EAST, true)), path);
    }

    /**
     * Walking off the edge of the board ends the preview like a pit, with the destroyed step on the last square the
     * robot stood on, since the square it drove onto is not on the board to be drawn.
     */
    @Test
    void theEdgeOfTheBoardEndsThePreviewWithADestroyedStepOnTheEdgeSquare() {
        Board board = AsciiBoard.board(". . .");

        List<Step> path = MovementPreview.path(board, new Position(2, 0), Direction.EAST,
            List.of(card(CardType.MOVE_1), card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(2, 0), Direction.EAST, true)), path);
    }

    /**
     * A move card that leaves the board partway marks the square the robot got to before leaving, not the one it
     * started the card on.
     */
    @Test
    void aMoveThreeThatLeavesTheBoardPartwayMarksTheLastSquareReached() {
        Board board = AsciiBoard.board(". . . .");

        List<Step> path = MovementPreview.path(board, new Position(1, 0), Direction.EAST,
            List.of(card(CardType.MOVE_1), card(CardType.MOVE_3)));

        assertEquals(List.of(new Step(new Position(2, 0), Direction.EAST),
            new Step(new Position(3, 0), Direction.EAST, true)), path);
    }

    /**
     * Only the last step is destroyed: every step before it is one the robot survives.
     */
    @Test
    void theDestroyingCardComesAfterTheSurvivedOnes() {
        Board board = AsciiBoard.board(". . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_1), card(CardType.ROTATE_RIGHT), card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(1, 0), Direction.EAST),
            new Step(new Position(1, 0), Direction.SOUTH),
            new Step(new Position(1, 0), Direction.SOUTH, true)), path);
    }

    /**
     * The preview takes no list of other robots' squares any more (unlike {@code MovementResolver}'s obstacles, which
     * only ever come from a {@link de.mkoehler.robotrampage.rules.GameState}, never a bare {@link Board}): nothing
     * about another robot's position can stop or redirect it, since their programs are secret and this player cannot
     * predict where they will actually be by the time this register runs.
     */
    @Test
    void nothingStandsInTheWayOfAStraightMove() {
        Board board = AsciiBoard.board(". . . . .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_3), card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(3, 0), Direction.EAST), new Step(new Position(4, 0), Direction.EAST)),
            path);
    }

    /**
     * A belt carries the robot one square further after its own card, in the belt's direction, and turns it to match
     * a belt it lands on that curves away from the direction it just travelled — exactly like {@code BeltResolver}.
     */
    @Test
    void aBeltCarriesTheRobotAndTurnsItOnACurve() {
        Board board = AsciiBoard.board(". > v");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(2, 0), Direction.SOUTH)), path,
            "moved by its own card onto the east belt, carried east onto the south-turning belt, and turned to match");
    }

    /**
     * A robot that does not move at all this register still rides the belt it happens to stand on: board effects run
     * every register regardless of what the robot's own card did.
     */
    @Test
    void aBeltCarriesTheRobotEvenOnARotateOnlyRegister() {
        Board board = AsciiBoard.board("> .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.NORTH, List.of(card(CardType.ROTATE_LEFT)));

        assertEquals(List.of(new Step(new Position(1, 0), Direction.WEST)), path);
    }

    /**
     * An express belt moves on both belt passes of a register, so it carries the robot two squares total.
     */
    @Test
    void anExpressBeltMovesTwiceInOneRegister() {
        Board board = AsciiBoard.board(". E E .");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(3, 0), Direction.EAST)), path);
    }

    /**
     * A gear turns the robot standing on it, after any belt has carried it there, exactly like {@code GearResolver}.
     */
    @Test
    void aGearTurnsTheRobotAfterMovement() {
        Board board = AsciiBoard.board("""
            c
            .
            """);

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.NORTH, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(0, 1), Direction.EAST)), path,
            "moved onto the clockwise gear facing north, and was turned right to face east");
    }

    /**
     * A pusher active in this register shoves the robot standing on its square one square away from the wall it is
     * mounted on, exactly like {@code PusherResolver}. The pusher is mounted on the east side of (1,1), so it pushes
     * west; the robot enters that square from the south, unaffected by the pusher's own wall.
     */
    @Test
    void aPusherActiveThisRegisterShovesTheRobot() {
        Board board = AsciiBoard.board("""
            . .
            . .
            """, builder -> builder.pusher(new Position(1, 1), Direction.EAST, 1));

        List<Step> path = MovementPreview.path(board, new Position(1, 0), Direction.NORTH, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(0, 1), Direction.NORTH)), path,
            "moved onto the pusher's square by its own card, then pushed one square west by the active pusher");
    }

    /**
     * The same pusher does nothing in a register it is not active in.
     */
    @Test
    void aPusherNotActiveThisRegisterDoesNothing() {
        Board board = AsciiBoard.board("""
            . .
            . .
            """, builder -> builder.pusher(new Position(1, 1), Direction.EAST, 2));

        List<Step> path = MovementPreview.path(board, new Position(1, 0), Direction.NORTH, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(1, 1), Direction.NORTH)), path);
    }

    /**
     * A board effect that would destroy the robot ends the preview exactly like its own card would: a destroyed step on
     * the pit, nothing after it.
     */
    @Test
    void aBeltCarryingTheRobotIntoAPitEndsThePreview() {
        Board board = AsciiBoard.board(". > o");

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.EAST,
            List.of(card(CardType.MOVE_1), card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(2, 0), Direction.EAST, true)), path);
    }

    /**
     * A pusher that shoves the robot off the board marks the pusher's square, the last one the robot stood on.
     */
    @Test
    void aPusherShovingTheRobotOffTheBoardEndsThePreview() {
        Board board = AsciiBoard.board("""
            . .
            . .
            """, builder -> builder.pusher(new Position(0, 1), Direction.EAST, 1));

        List<Step> path = MovementPreview.path(board, new Position(0, 0), Direction.NORTH, List.of(card(CardType.MOVE_1)));

        assertEquals(List.of(new Step(new Position(0, 1), Direction.NORTH, true)), path,
            "moved onto the pusher's square, then pushed west off the board");
    }

    /**
     * No cards means no steps.
     */
    @Test
    void noCardsGivesAnEmptyPath() {
        Board board = AsciiBoard.board(". . .");

        assertTrue(MovementPreview.path(board, new Position(1, 0), Direction.NORTH, List.of()).isEmpty());
    }
}
