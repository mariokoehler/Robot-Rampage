package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dealing and program submission (design.md 2.3, 2.5), and that no card is ever
 * lost or created along the way.
 *
 * @author Mario Koehler
 */
class ProgrammingTest {

    /**
     * Each robot is dealt nine cards minus its damage; robots that are destroyed or powered down
     * get none.
     */
    @Test
    void handSizeFollowsDamageAndSkipsRobotsThatCannotPlay() {
        GameState state = AsciiBoard.state(". . . .", "0 1 2 3");
        state.robot(1).setDamage(4);
        state.robot(2).setPoweredDown(true);
        state.robot(3).setStatus(RobotStatus.DESTROYED);

        Map<Integer, List<Card>> hands = Programming.deal(state);

        assertEquals(2, hands.size());
        assertEquals(9, hands.get(0).size());
        assertEquals(5, hands.get(1).size());
        assertEquals(84 - 14, state.deck().drawPileSize());
    }

    /**
     * For every amount of damage the hand is exactly as large as the number of registers the
     * player must fill (or larger while nothing is locked), so a player can always complete a
     * program.
     */
    @Test
    void handAlwaysCoversTheUnlockedRegisters() {
        Robot robot = new Robot(0, new de.mkoehler.robotrampage.board.Position(0, 0),
            de.mkoehler.robotrampage.board.Direction.NORTH);
        for (int damage = 0; damage <= 9; damage++) {
            robot.setDamage(damage);
            int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
            assertTrue(robot.handSize() >= unlocked, "damage " + damage);
            if (robot.lockedRegisterCount() > 0) {
                assertEquals(unlocked, robot.handSize(), "damage " + damage);
            }
        }
    }

    /**
     * Submitting puts the chosen cards into registers 1..5 in order, sends the rest of the hand to
     * the discard pile and records the power-down announcement.
     */
    @Test
    void submitFillsTheRegistersAndDiscardsTheRestOfTheHand() {
        GameState state = AsciiBoard.state(".", "0");
        List<Card> hand = Programming.deal(state).get(0);
        List<Card> chosen = new ArrayList<>(List.of(hand.get(8), hand.get(2), hand.get(5), hand.get(0), hand.get(7)));

        Programming.submit(state, 0, hand, chosen, true);

        for (int index = 0; index < 5; index++) {
            assertEquals(chosen.get(index), state.robot(0).register(index));
        }
        assertEquals(4, state.deck().discardPileSize());
        assertTrue(state.robot(0).isPowerDownAnnounced());
    }

    /**
     * A damaged robot only fills its unlocked registers; the locked ones keep their cards.
     */
    @Test
    void submitLeavesLockedRegistersUntouched() {
        GameState state = AsciiBoard.state(".", "0");
        Programs.program(state, 0, 100, CardType.U_TURN, CardType.U_TURN, CardType.BACK_UP, CardType.ROTATE_LEFT,
            CardType.ROTATE_RIGHT);
        Card lockedFour = state.robot(0).register(3);
        Card lockedFive = state.robot(0).register(4);
        state.robot(0).setDamage(6);
        List<Card> hand = Programming.deal(state).get(0);

        Programming.submit(state, 0, hand, hand.subList(0, 3), false);

        assertEquals(hand.get(0), state.robot(0).register(0));
        assertEquals(hand.get(2), state.robot(0).register(2));
        assertEquals(lockedFour, state.robot(0).register(3));
        assertEquals(lockedFive, state.robot(0).register(4));
    }

    /**
     * A program with the wrong number of cards, with a card that is not in the hand, or with
     * one card used twice is rejected, and nothing is changed.
     */
    @Test
    void submitRejectsInvalidPrograms() {
        GameState state = AsciiBoard.state(".", "0");
        List<Card> hand = Programming.deal(state).get(0);

        assertThrows(IllegalArgumentException.class,
            () -> Programming.submit(state, 0, hand, hand.subList(0, 4), false));
        Card foreign = new Card(CardType.MOVE_1, 9999);
        assertThrows(IllegalArgumentException.class,
            () -> Programming.submit(state, 0, hand, List.of(hand.get(0), hand.get(1), hand.get(2), hand.get(3), foreign),
                false));
        assertThrows(IllegalArgumentException.class,
            () -> Programming.submit(state, 0, hand, List.of(hand.get(0), hand.get(0), hand.get(1), hand.get(2), hand.get(3)),
                false));

        assertNull(state.robot(0).register(0));
        assertEquals(0, state.deck().discardPileSize());
    }

    /**
     * A robot at 9 damage is dealt no cards but is still part of the deal, so its player is asked
     * for a program: an empty one, with the option to announce a power-down, which is exactly when
     * it is most useful.
     */
    @Test
    void aRobotAtNineDamageStillGetsToAnnounceAPowerDown() {
        GameState state = AsciiBoard.state(".", "0");
        state.robot(0).setDamage(9);

        Map<Integer, List<Card>> hands = Programming.deal(state);
        assertTrue(hands.containsKey(0));
        assertEquals(0, hands.get(0).size());

        Programming.submit(state, 0, hands.get(0), List.of(), true);

        assertTrue(state.robot(0).isPowerDownAnnounced());
    }

    /**
     * Dealing and submitting never lose or create a card: everything is either in the deck piles
     * or in a register.
     */
    @Test
    void dealAndSubmitConserveAllEightyFourCards() {
        GameState state = AsciiBoard.state(". .", "0 1");
        Map<Integer, List<Card>> hands = Programming.deal(state);
        for (Map.Entry<Integer, List<Card>> entry : hands.entrySet()) {
            Programming.submit(state, entry.getKey(), entry.getValue(), entry.getValue().subList(0, 5), false);
        }

        assertEquals(84, Programs.cardsInPlay(state));
        assertNotNull(state.robot(0).register(4));
    }
}
