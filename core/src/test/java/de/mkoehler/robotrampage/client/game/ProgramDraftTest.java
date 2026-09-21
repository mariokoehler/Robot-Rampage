package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.client.game.ProgramDraft.RegisterView;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@link ProgramDraft} places cards into registers, where locked registers sit, and what it sends.
 *
 * @author Mario Koehler
 */
class ProgramDraftTest {

    private static final Card MOVE_1 = new Card(CardType.MOVE_1, 520);
    private static final Card MOVE_2 = new Card(CardType.MOVE_2, 700);
    private static final Card LEFT = new Card(CardType.ROTATE_LEFT, 150);
    private static final Card RIGHT = new Card(CardType.ROTATE_RIGHT, 220);
    private static final Card U_TURN = new Card(CardType.U_TURN, 30);
    private static final Card BACK_UP = new Card(CardType.BACK_UP, 450);
    private static final Card MOVE_3 = new Card(CardType.MOVE_3, 810);
    private static final Card LOCKED_A = new Card(CardType.MOVE_1, 111);
    private static final Card LOCKED_B = new Card(CardType.MOVE_1, 112);

    private static List<Card> hand() {
        return List.of(MOVE_1, MOVE_2, LEFT, RIGHT, U_TURN, BACK_UP, MOVE_3, new Card(CardType.MOVE_1, 530),
            new Card(CardType.MOVE_1, 540));
    }

    /**
     * Cards go into the free registers in order, and a card cannot be placed twice or from outside the hand.
     */
    @Test
    void cardsFillTheRegistersInOrder() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());

        assertTrue(draft.place(MOVE_2));
        assertTrue(draft.place(RIGHT));

        assertEquals(new RegisterView(1, MOVE_2, false), draft.registers().get(0));
        assertEquals(new RegisterView(2, RIGHT, false), draft.registers().get(1));
        assertEquals(new RegisterView(3, null, false), draft.registers().get(2));
        assertEquals(3, draft.missing());
        assertTrue(draft.isPlaced(MOVE_2));
        assertFalse(draft.isPlaced(LEFT));
        assertFalse(draft.place(new Card(CardType.MOVE_3, 999)), "not in the hand");
    }

    /**
     * A card that is already in a register moves when it is placed again, instead of being in two registers.
     */
    @Test
    void placingAPlacedCardMovesIt() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());
        draft.place(MOVE_2);

        draft.placeAt(MOVE_2, 4);

        assertNull(draft.registers().get(0).card());
        assertEquals(MOVE_2, draft.registers().get(3).card());
        assertEquals(4, draft.missing());
    }

    /**
     * Placing a card into a register that is taken replaces the card there; the replaced card is free again.
     */
    @Test
    void placingIntoATakenRegisterReplacesTheCard() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());
        draft.place(MOVE_2);

        assertTrue(draft.placeAt(LEFT, 1));

        assertEquals(LEFT, draft.registers().get(0).card());
        assertFalse(draft.isPlaced(MOVE_2));
    }

    /**
     * Taking a card back empties the register, and taking from an empty or missing register does nothing.
     */
    @Test
    void takingACardBack() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());
        draft.place(LEFT);

        assertEquals(LEFT, draft.take(1));
        assertNull(draft.take(1));
        assertNull(draft.take(0));
        assertNull(draft.take(6));
        assertEquals(5, draft.missing());
    }

    /**
     * When every register is full nothing more is placed, and the program is complete.
     */
    @Test
    void fiveCardsCompleteTheProgram() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());
        assertFalse(draft.isComplete());

        draft.place(MOVE_1);
        draft.place(MOVE_2);
        draft.place(LEFT);
        draft.place(RIGHT);
        assertFalse(draft.isComplete());
        draft.place(U_TURN);

        assertTrue(draft.isComplete());
        assertFalse(draft.place(BACK_UP), "every register is full");
    }

    /**
     * Locked registers are the highest-numbered ones: with two locked, registers 1 to 3 are free, 4 and 5 hold the locked
     * cards in order, and they cannot be changed.
     */
    @Test
    void lockedRegistersAreTheLastOnes() {
        ProgramDraft draft = new ProgramDraft(hand().subList(0, 7), List.of(LOCKED_A, LOCKED_B));

        assertEquals(3, draft.freeRegisterCount());
        assertEquals(2, draft.lockedRegisterCount());
        assertEquals(new RegisterView(4, LOCKED_A, true), draft.registers().get(3));
        assertEquals(new RegisterView(5, LOCKED_B, true), draft.registers().get(4));
        assertFalse(draft.placeAt(MOVE_1, 4), "register 4 is locked");
        assertNull(draft.take(5));
        draft.place(MOVE_1);
        draft.place(MOVE_2);
        draft.place(LEFT);
        assertFalse(draft.place(RIGHT), "only three registers are free");
        assertTrue(draft.isComplete());
    }

    /**
     * Only the free registers are sent, register 1 first, by card priority, together with the power-down and facing.
     */
    @Test
    void onlyTheFreeRegistersAreSent() {
        ProgramDraft draft = new ProgramDraft(hand().subList(0, 7), List.of(LOCKED_A, LOCKED_B));
        draft.place(MOVE_2);
        draft.place(LEFT);
        draft.place(MOVE_1);

        SubmitProgram submit = draft.toSubmit(4, true, Direction.SOUTH);

        assertEquals(new SubmitProgram(4, List.of(700, 150, 520), true, Direction.SOUTH), submit);
    }

    /**
     * A robot with all five registers locked has nothing to place and confirms an empty program.
     */
    @Test
    void aFullyLockedRobotConfirmsAnEmptyProgram() {
        List<Card> locked = new ArrayList<>(List.of(LOCKED_A, LOCKED_B, MOVE_1, MOVE_2, LEFT));
        ProgramDraft draft = new ProgramDraft(List.of(), locked);

        assertTrue(draft.isComplete());
        assertEquals(0, draft.freeRegisterCount());
        assertEquals(List.of(), draft.toSubmit(9, false, null).cardPriorities());
        assertEquals(5, draft.registers().stream().filter(RegisterView::locked).count());
    }

    /**
     * An incomplete program cannot be turned into a message.
     */
    @Test
    void anIncompleteProgramCannotBeSent() {
        ProgramDraft draft = new ProgramDraft(hand(), List.of());
        draft.place(MOVE_1);

        assertThrows(IllegalStateException.class, () -> draft.toSubmit(1, false, null));
    }

    /**
     * More locked cards than registers is a broken message and is refused.
     */
    @Test
    void tooManyLockedCardsAreRefused() {
        List<Card> six = List.of(MOVE_1, MOVE_2, LEFT, RIGHT, U_TURN, BACK_UP);

        assertThrows(IllegalArgumentException.class, () -> new ProgramDraft(List.of(), six));
    }
}
