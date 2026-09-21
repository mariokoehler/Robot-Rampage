package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Destruction} (design.md 2.9), in particular that no programming card is ever
 * lost or duplicated when a robot is destroyed.
 *
 * @author Mario Koehler
 */
class DestructionTest {

    /**
     * Destroying a robot returns every card in its registers to the discard pile exactly once and
     * empties the registers, so the 84 cards of the deck are still all accounted for.
     */
    @Test
    void destroyingARobotReturnsEachProgrammedCardToTheDiscardPileOnce() {
        GameState state = AsciiBoard.state(".", "0");
        Robot robot = state.robot(0);
        List<Card> hand = state.deck().deal(9);
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            robot.setRegister(index, hand.get(index));
        }
        int unusedInHand = hand.size() - Robot.REGISTER_COUNT;

        Destruction.destroy(state, robot, DestructionCause.DAMAGE, new EventLog());

        assertEquals(Robot.REGISTER_COUNT, state.deck().discardPileSize());
        assertEquals(84 - hand.size(), state.deck().drawPileSize());
        assertEquals(84, state.deck().drawPileSize() + state.deck().discardPileSize() + unusedInHand);
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            assertNull(robot.register(index));
        }
    }

    /**
     * Destroying a robot twice takes only one life and one set of cards: the second call finds
     * nothing left to do.
     */
    @Test
    void aRobotCanOnlyBeDestroyedOnce() {
        GameState state = AsciiBoard.state(".", "0");
        Robot robot = state.robot(0);
        robot.setRegister(0, new Card(CardType.MOVE_1, 10));
        EventLog log = new EventLog();

        Destruction.destroy(state, robot, DestructionCause.PIT, log);
        Destruction.destroy(state, robot, DestructionCause.CRUSHER, log);

        assertEquals(Robot.STARTING_LIVES - 1, robot.lives());
        assertEquals(1, state.deck().discardPileSize());
        assertEquals(1, log.events().size());
    }

    /**
     * Destruction cancels a power-down, since the robot returns as an ordinary, powered-up robot.
     */
    @Test
    void destructionCancelsPowerDown() {
        GameState state = AsciiBoard.state(".", "0");
        Robot robot = state.robot(0);
        robot.setPoweredDown(true);
        robot.setPowerDownAnnounced(true);

        Destruction.destroy(state, robot, DestructionCause.PIT, new EventLog());

        assertFalse(robot.isPoweredDown());
        assertFalse(robot.isPowerDownAnnounced());
    }

    /**
     * {@link Destruction#destroyIfOnHazard} leaves a robot on plain floor alone.
     */
    @Test
    void robotsOnPlainFloorAreNotHazardVictims() {
        GameState state = AsciiBoard.state(". o", "0 .");
        EventLog log = new EventLog();

        assertFalse(Destruction.destroyIfOnHazard(state, state.robot(0), log));
        assertTrue(log.events().isEmpty());
        assertEquals(new Position(0, 0), state.robot(0).position());
    }
}
