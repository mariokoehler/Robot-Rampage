package de.mkoehler.robotrampage.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The programming phase of a turn (design.md 2.3, 2.5): dealing hands and turning a
 * player's choice of cards into the robot's registers.
 * <p>
 * The hand size and the number of registers a player fills always agree: a robot with
 * {@code d} damage is dealt {@code 9 - d} cards and has {@code 5 - max(0, d - 4)}
 * unlocked registers, which for every damage value is at most the hand size. Locked
 * registers keep the card they already hold.
 *
 * @author Mario Koehler
 */
public final class Programming {

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private Programming() {
    }

    /**
     * Deals a hand to every active robot that is not powered down, in robot id order, taking
     * {@code 9 - damage} cards each from the deck (which reshuffles the discard pile when
     * it runs dry).
     *
     * @param state the game state whose deck to deal from
     * @return each dealt robot's hand, by robot id; robots that are destroyed, eliminated or
     *         powered down are not in the map
     */
    public static Map<Integer, List<Card>> deal(GameState state) {
        Map<Integer, List<Card>> hands = new LinkedHashMap<>();
        for (Robot robot : state.robots()) {
            if (robot.isActive() && !robot.isPoweredDown()) {
                hands.put(robot.id(), state.deck().deal(robot.handSize()));
            }
        }
        return hands;
    }

    /**
     * Programs a robot: puts the chosen cards, in order, into its unlocked registers, sends
     * the rest of its hand to the discard pile and records whether a power-down is announced.
     *
     * @param state     the game state to mutate
     * @param robotId   the robot being programmed
     * @param hand      the hand the robot was dealt
     * @param chosen    the cards to program, register 1 first; exactly one per unlocked
     *                  register, all taken from {@code hand}
     * @param powerDown whether the player announces a power-down after this turn
     * @throws IllegalArgumentException if the number of chosen cards does not match the number
     *                                  of unlocked registers, or a chosen card is not in the
     *                                  hand (or is chosen more often than it is held)
     */
    public static void submit(GameState state, int robotId, List<Card> hand, List<Card> chosen, boolean powerDown) {
        Robot robot = state.robot(robotId);
        int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
        if (chosen.size() != unlocked) {
            throw new IllegalArgumentException("Robot " + robotId + " must program " + unlocked + " registers, got "
                + chosen.size());
        }
        List<Card> remaining = new ArrayList<>(hand);
        for (Card card : chosen) {
            if (!remaining.remove(card)) {
                throw new IllegalArgumentException("Card " + card + " is not in robot " + robotId + "'s hand");
            }
        }
        for (int index = 0; index < unlocked; index++) {
            robot.setRegister(index, chosen.get(index));
        }
        state.deck().discardAll(remaining);
        robot.setPowerDownAnnounced(powerDown);
    }
}
