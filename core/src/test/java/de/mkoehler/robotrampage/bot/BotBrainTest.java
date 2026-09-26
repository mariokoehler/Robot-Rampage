package de.mkoehler.robotrampage.bot;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BotBrain}: it heads for its flag, stays out of pits at any difficulty, never looks at other
 * programs, powers down at a difficulty-dependent threshold, always chooses a legal program, and bots alone can play a
 * real board to the end.
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

        BotDecision decision = BotBrain.decide(state, 0, hand, false, new Random(1), BotDifficulty.HARD);

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

        BotDecision decision = BotBrain.decide(state, 0, hand, false, new Random(1), BotDifficulty.HARD);

        Programming.submit(state, 0, hand, decision.program(), false);
        assertTrue(TurnResolver.resolve(state).state().robot(0).isActive(), "the bot should not drive into the pit");
    }

    /**
     * Even at {@code EASY}, where score noise is large, a pit's penalty so dwarfs the noise that the bot still never
     * drives into it — difficulty only affects genuinely close tactical calls, never survival.
     */
    @Test
    void staysOutOfAPitEvenAtEasy() {
        GameState state = AsciiBoard.state(". o . 1\n. . . .", "0 . . .\n. . . .");
        state.robot(0).setFacing(Direction.EAST);
        List<Card> hand = hand(CardType.MOVE_1, CardType.MOVE_2, CardType.MOVE_3, CardType.MOVE_1, CardType.MOVE_2,
            CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT, CardType.U_TURN, CardType.BACK_UP);
        Random random = new Random(1);

        for (int trial = 0; trial < 20; trial++) {
            GameState trialState = state.copy();
            BotDecision decision = BotBrain.decide(trialState, 0, hand, false, random, BotDifficulty.EASY);
            Programming.submit(trialState, 0, hand, decision.program(), false);
            assertTrue(TurnResolver.resolve(trialState).state().robot(0).isActive(),
                "an easy bot should still not drive into the pit");
        }
    }

    /**
     * What the other players have programmed makes no difference to the bot's choice: it never looks. The situation is
     * built so that looking would pay off: the other robot, acting after the bot in register 1, would shove it east into
     * the pit, and only backing up in register 1 escapes that; a bot that cannot see it prefers heading for the flag.
     */
    @Test
    void neverLooksAtOtherPrograms() {
        GameState state = AsciiBoard.state(". 1 .\n. . .\n. . .\n. . o\n. . .", ". . .\n. . .\n. . .\n1 0 .\n. . .");
        state.robot(1).setFacing(Direction.EAST);
        List<Card> hand = hand(CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT, CardType.U_TURN, CardType.ROTATE_LEFT,
            CardType.ROTATE_RIGHT, CardType.BACK_UP, CardType.U_TURN, CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT);
        GameState other = state.copy();
        other.robot(1).setRegister(0, new Card(CardType.MOVE_1, 10));

        BotDecision blind = BotBrain.decide(state, 0, hand, false, new Random(7), BotDifficulty.HARD);

        assertEquals(blind, BotBrain.decide(other, 0, hand, false, new Random(7), BotDifficulty.HARD));
        assertTrue(blind.program().get(0).type() != CardType.BACK_UP, "a bot that could see would back away first");
    }

    /**
     * The three difficulties are ordered: Hard adds the least score noise (today's original tie-break-only amount) and
     * powers down the earliest (most cautious); Easy adds the most noise and powers down the latest (most careless).
     */
    @Test
    void difficultiesAreOrderedFromCautiousToCareless() {
        assertEquals(1, BotBrain.scoreNoise(BotDifficulty.HARD), "hard keeps the original tie-break-only noise");
        assertTrue(BotBrain.scoreNoise(BotDifficulty.HARD) < BotBrain.scoreNoise(BotDifficulty.NORMAL));
        assertTrue(BotBrain.scoreNoise(BotDifficulty.NORMAL) < BotBrain.scoreNoise(BotDifficulty.EASY));

        assertEquals(6, BotBrain.powerDownDamage(BotDifficulty.NORMAL), "normal keeps the original threshold");
        assertTrue(BotBrain.powerDownDamage(BotDifficulty.HARD) < BotBrain.powerDownDamage(BotDifficulty.NORMAL));
        assertTrue(BotBrain.powerDownDamage(BotDifficulty.NORMAL) < BotBrain.powerDownDamage(BotDifficulty.EASY));
    }

    /**
     * A surviving bot powers down at a difficulty-dependent damage threshold. The board is damage-neutral (no lasers,
     * so no program changes the robot's damage), so whichever program is chosen, only the threshold decides.
     */
    @Test
    void powersDownAtADifficultyDependentThreshold() {
        GameState state = AsciiBoard.state(". . .\n. . .", "0 . .\n. . .");
        state.robot(0).setDamage(5);
        List<Card> hand = hand(CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT, CardType.U_TURN, CardType.MOVE_1,
            CardType.BACK_UP);

        assertTrue(BotBrain.decide(state, 0, hand, false, new Random(1), BotDifficulty.HARD).powerDown(),
            "hard powers down from 4 damage");
        assertFalse(BotBrain.decide(state, 0, hand, false, new Random(1), BotDifficulty.NORMAL).powerDown(),
            "normal only powers down from 6 damage");
        assertFalse(BotBrain.decide(state, 0, hand, false, new Random(1), BotDifficulty.EASY).powerDown(),
            "easy only powers down from 8 damage");
    }

    /**
     * Whatever the damage, the hand and the difficulty, the program fits the robot's free registers and comes from its
     * hand, so the server can always submit it.
     */
    @Test
    void alwaysChoosesALegalProgram() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        Random random = new Random(3);
        BotDifficulty[] difficulties = BotDifficulty.values();
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

            BotDecision decision = BotBrain.decide(state, robot.id(), hand, round % 3 == 0, random,
                difficulties[round % difficulties.length]);

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
        long slowestRespawn = 0;
        for (long seed = 1; seed <= 3; seed++) {
            GameState state = newGame(board, seed, 4);
            Random random = new Random(seed);
            for (int turn = 1; turn <= 80 && !state.isOver(); turn++) {
                EventLog respawns = new EventLog();
                Respawner.respawn(state, Map.of(), respawns);
                Set<Integer> respawned = new HashSet<>();
                respawns.entries().forEach(entry -> {
                    if (entry.event() instanceof GameEvent.RobotRespawned back) {
                        respawned.add(back.robotId());
                    }
                });
                Map<Integer, List<Card>> hands = Programming.deal(state);
                for (Map.Entry<Integer, List<Card>> entry : hands.entrySet()) {
                    boolean facing = respawned.contains(entry.getKey());
                    long start = System.nanoTime();
                    BotDecision decision = BotBrain.decide(state, entry.getKey(), entry.getValue(), facing, random,
                        BotDifficulty.HARD);
                    long took = System.nanoTime() - start;
                    if (facing) {
                        slowestRespawn = Math.max(slowestRespawn, took);
                        state.robot(entry.getKey()).setFacing(decision.facing());
                    } else {
                        slowest = Math.max(slowest, took);
                    }
                    Programming.submit(state, entry.getKey(), entry.getValue(), decision.program(), decision.powerDown());
                }
                state = TurnResolver.resolve(state).state();
            }
            finished += state.isOver() ? 1 : 0;
            flags += state.robots().stream().mapToInt(Robot::flagsTouched).sum();
        }
        System.out.println("BotBrainTest: " + finished + " of 3 games finished, " + flags + " flags, slowest decision "
            + slowest / 1_000_000 + " ms, slowest with a re-entry facing " + slowestRespawn / 1_000_000 + " ms");
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
