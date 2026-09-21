package de.mkoehler.robotrampage.testsupport;

import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Robot;

/**
 * Test helper for putting cards into a robot's registers without going through dealing.
 *
 * @author Mario Koehler
 */
public final class Programs {

    /**
     * Not instantiable; this class only exposes static helpers.
     */
    private Programs() {
    }

    /**
     * Programs the first registers of a robot with cards of the given types. Register
     * {@code i} gets priority {@code priorityBase + i}, so give different robots different
     * bases (100 apart is plenty) to keep every priority unique. Registers beyond the given
     * cards are left as they are.
     *
     * @param state        the game state
     * @param robotId      the robot to program
     * @param priorityBase the priority of the card in register 1
     * @param types        the card types for registers 1, 2, ...
     */
    public static void program(GameState state, int robotId, int priorityBase, CardType... types) {
        Robot robot = state.robot(robotId);
        for (int index = 0; index < types.length; index++) {
            robot.setRegister(index, new Card(types[index], priorityBase + index));
        }
    }

    /**
     * Counts every card that exists in a game state: both deck piles plus the cards sitting in
     * robots' registers. In a state without undealt or held hands this must always be 84.
     *
     * @param state the game state
     * @return the number of cards in the deck piles and in registers
     */
    public static int cardsInPlay(GameState state) {
        int inRegisters = 0;
        for (Robot robot : state.robots()) {
            for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
                if (robot.register(index) != null) {
                    inRegisters++;
                }
            }
        }
        return state.deck().drawPileSize() + state.deck().discardPileSize() + inRegisters;
    }
}
