package de.mkoehler.robotrampage.bot;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BotBrain}: it heads for its flag, stays out of pits, never looks at other programs, always chooses a
 * legal program, and bots alone can play a real board to the end.
 *
 * @author Mario Koehler
 */
class BotBrainTest {

    /**
     * Makes a hand of cards of the given types, with distinct priorities.
     *
     * @param types the types
     * @return the hand
     */
    private static List<Card> hand(CardType... types) {
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            hand.add(new Card(types[i], 100 + 10 * i));
        }
        return hand;
    }

    /**
     * With the flag straight ahead and three squares away, the bot drives onto it.
     */
    @Test
    void drivesOntoTheFlag() {
        GameState state = AsciiBoard.state(". . . 1 . .\n. . . . . .", "0 . . . . .\n. . . . . .");
        state.robot(0).setFacing(Direction.EAST);
        List<Card> hand = hand(CardType.MOVE_3, CardType.U_TURN, CardType.U_TURN, CardType.U_TURN, CardType.U_TURN,
            CardType.BACK_UP, CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT, CardType.ROTATE_LEFT);

        BotDecision decision = BotBrain.decide(state, 0, hand, false, new Random(1));

        assertEquals(5, decision.program().size());
        Programming.submit(state, 0, hand, decision.program(), decision.powerDown());
        assertEquals(1, TurnResolver.resolve(state).state().robot(0).flagsTouched());
    }

    /**
     * A pit straight ahead is avoided even though driving is the only way closer to the flag.
     */
    @Test
    void staysOutOfAPit() {
        GameState state = AsciiBoard.state(". o . 1\n. . . .", "0 . . .\n. . . .");
        state.robot(0).setFacing(Direction.EAST);
        List<Card> hand = hand(CardType.MOVE_1, CardType.MOVE_2, CardType.MOVE_3, CardType.MOVE_1, CardType.MOVE_2,
            CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT, CardType.U_TURN, CardType.BACK_UP);

        BotDecision decision = BotBrain.decide(state, 0, hand, false, new Random(1));

        Programming.submit(state, 0, hand, decision.program(), false);
        assertTrue(TurnResolver.resolve(state).state().robot(0).isActive(), "the bot should not drive into the pit");
    }

    /**
     * The walking distance goes around walls and pits, not through them.
     */
    @Test
    void distancesGoAroundWallsAndPits() {
        Board board = AsciiBoard.board(". . | 1\n. o .\n. . .");

        int[][] steps = BotBrain.distances(board, 0);

        assertEquals(0, steps[2][2]);
        assertEquals(6, steps[0][2], "around the wall, down the left, along the bottom and up the right");
    }

    /**
     * What the other players have programmed makes no difference to the bot's choice: it never looks.
     */
    @Test
    void neverLooksAtOtherPrograms() {
        GameState state = AsciiBoard.state(". . . .\n. . . 1\n. . . .", "0 . . .\n. . . .\n. 1 . .");
        List<Card> hand = hand(CardType.MOVE_1, CardType.MOVE_2, CardType.ROTATE_RIGHT, CardType.MOVE_1, CardType.U_TURN,
            CardType.ROTATE_LEFT, CardType.BACK_UP, CardType.MOVE_3, CardType.ROTATE_RIGHT);
        GameState other = state.copy();
        for (int register = 0; register < Robot.REGISTER_COUNT; register++) {
            other.robot(1).setRegister(register, new Card(CardType.MOVE_3, 800 + register));
        }

        assertEquals(BotBrain.decide(state, 0, hand, true, new Random(7)),
            BotBrain.decide(other, 0, hand, true, new Random(7)));
    }

    /**
     * Whatever the damage and the hand, the program fits the robot's free registers and comes from its hand, so the server
     * can always submit it.
     */
    @Test
    void alwaysChoosesALegalProgram() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        Random random = new Random(3);
        for (int round = 0; round < 40; round++) {
            GameState state = newGame(board, round, 4);
            Robot robot = state.robot(round % 4);
            robot.setDamage(random.nextInt(10));
            Map<Integer, List<Card>> hands = Programming.deal(state);
            for (int register = 0; register < Robot.REGISTER_COUNT; register++) {
                if (!robot.isRegisterLocked(register)) {
                    robot.setRegister(register, null);
                } else if (robot.register(register) == null) {
                    robot.setRegister(register, new Card(CardType.MOVE_1, 900 + register));
                }
            }
            List<Card> hand = hands.get(robot.id());

            BotDecision decision = BotBrain.decide(state, robot.id(), hand, round % 3 == 0, random);

            Programming.submit(state, robot.id(), hand, decision.program(), decision.powerDown());
            assertEquals(Robot.REGISTER_COUNT - robot.lockedRegisterCount(), decision.program().size());
        }
    }

    /**
     * Four bots alone play the first board: they touch flags, and games come to an end.
     */
    @Test
    void botsPlayARealBoardToTheEnd() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        int finished = 0;
        int flags = 0;
        long slowest = 0;
        for (long seed = 1; seed <= 3; seed++) {
            GameState state = newGame(board, seed, 4);
            Random random = new Random(seed);
            for (int turn = 1; turn <= 80 && !state.isOver(); turn++) {
                Respawner.respawn(state, Map.of(), new EventLog());
                Map<Integer, List<Card>> hands = Programming.deal(state);
                for (Map.Entry<Integer, List<Card>> entry : hands.entrySet()) {
                    long start = System.nanoTime();
                    BotDecision decision = BotBrain.decide(state, entry.getKey(), entry.getValue(), false, random);
                    slowest = Math.max(slowest, System.nanoTime() - start);
                    Programming.submit(state, entry.getKey(), entry.getValue(), decision.program(), decision.powerDown());
                }
                state = TurnResolver.resolve(state).state();
            }
            finished += state.isOver() ? 1 : 0;
            flags += state.robots().stream().mapToInt(Robot::flagsTouched).sum();
        }
        System.out.println("BotBrainTest: " + finished + " of 3 games finished, " + flags + " flags, slowest decision "
            + slowest / 1_000_000 + " ms");
        assertTrue(finished >= 2, "bots should finish most games, finished " + finished);
        assertTrue(flags >= 6, "bots should touch flags, touched " + flags);
    }

    /**
     * Starts a game with robots on the first start squares.
     *
     * @param board  the board
     * @param seed   the deck's seed
     * @param robots how many robots
     * @return the state
     */
    private static GameState newGame(Board board, long seed, int robots) {
        List<Robot> list = new ArrayList<>();
        for (int id = 0; id < robots; id++) {
            StartSquare start = board.startSquares().get(id);
            list.add(new Robot(id, start.position(), start.facing()));
        }
        return new GameState(board, list, Deck.standard(seed));
    }
}
