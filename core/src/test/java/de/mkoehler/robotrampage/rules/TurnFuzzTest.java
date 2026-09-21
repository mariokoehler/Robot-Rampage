package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays many complete games with random programs on the first board and checks that the engine's
 * invariants hold after every single turn, so rules that interact in ways no hand-written test
 * anticipated show up as failures here. Games are seeded, so any failure is reproducible.
 *
 * @author Mario Koehler
 */
class TurnFuzzTest {

    private static final int GAMES = 30;
    private static final int MAX_TURNS = 80;

    /**
     * Every game must run without exceptions and keep robots, cards and statuses consistent.
     */
    @Test
    void randomGamesKeepTheEngineConsistent() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        Map<SubPhase, Integer> phasesSeen = new EnumMap<>(SubPhase.class);
        Map<String, Integer> eventsSeen = new java.util.TreeMap<>();
        int gamesFinished = 0;

        for (long seed = 1; seed <= GAMES; seed++) {
            GameState state = newGame(board, seed);
            Random random = new Random(seed);
            for (int turn = 1; turn <= MAX_TURNS && !state.isOver(); turn++) {
                String context = "game " + seed + ", turn " + turn;
                respawn(state, random);
                assertInvariants(state, context + " after respawn");

                program(state, random);
                assertEquals(84, Programs.cardsInPlay(state), context + ": cards after programming");

                TurnResult result = TurnResolver.resolve(state);
                state = result.state();
                for (LoggedEvent entry : result.events()) {
                    assertTrue(entry.register() >= 0 && entry.register() <= Robot.REGISTER_COUNT, context);
                    phasesSeen.merge(entry.phase(), 1, Integer::sum);
                    eventsSeen.merge(entry.event().getClass().getSimpleName(), 1, Integer::sum);
                }
                assertInvariants(state, context + " after resolve");
                assertEquals(84, Programs.cardsInPlay(state), context + ": cards after resolving");
            }
            if (state.isOver()) {
                gamesFinished++;
            }
        }

        // The run is only meaningful if it actually exercised the interesting rules.
        assertTrue(phasesSeen.get(SubPhase.ROBOT_MOVEMENT) > 0);
        assertTrue(phasesSeen.get(SubPhase.LASERS) > 0);
        assertTrue(phasesSeen.get(SubPhase.CLEANUP) > 0);
        for (String eventType : List.of("RobotDamaged", "RobotDestroyed", "FlagTouched", "RobotPoweredDown",
            "RobotRepaired", "GameEnded")) {
            assertTrue(eventsSeen.getOrDefault(eventType, 0) > 0, "the fuzz run never produced " + eventType);
        }
        assertTrue(gamesFinished > 0, "no game ever finished");
    }

    /**
     * Sets up a game with eight robots on the board's start squares.
     *
     * @param board the board
     * @param seed  the deck seed
     * @return the new game
     */
    private static GameState newGame(Board board, long seed) {
        List<Robot> robots = new ArrayList<>();
        for (int id = 0; id < board.startSquares().size(); id++) {
            StartSquare start = board.startSquares().get(id);
            robots.add(new Robot(id, start.position(), start.facing()));
        }
        return new GameState(board, robots, Deck.standard(seed));
    }

    /**
     * Brings destroyed robots back, each facing a random direction.
     *
     * @param state  the game state
     * @param random the source of randomness
     */
    private static void respawn(GameState state, Random random) {
        Map<Integer, Direction> facings = new java.util.HashMap<>();
        for (Robot robot : state.robots()) {
            facings.put(robot.id(), Direction.values()[random.nextInt(4)]);
        }
        Respawner.respawn(state, facings, new EventLog());
    }

    /**
     * Deals hands and programs every robot with a random selection of its cards; about one robot in
     * ten announces a power-down.
     *
     * @param state  the game state
     * @param random the source of randomness
     */
    private static void program(GameState state, Random random) {
        for (Map.Entry<Integer, List<Card>> entry : Programming.deal(state).entrySet()) {
            Robot robot = state.robot(entry.getKey());
            List<Card> shuffled = new ArrayList<>(entry.getValue());
            Collections.shuffle(shuffled, random);
            int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
            Programming.submit(state, robot.id(), entry.getValue(), shuffled.subList(0, unlocked), random.nextInt(10) == 0);
        }
    }

    /**
     * Checks everything that must be true of any game state: active robots are on distinct, in-bounds,
     * non-pit squares with fewer than 10 damage and at least one life, and robots that are not active are
     * off the board, with only eliminated robots out of lives.
     *
     * @param state   the game state
     * @param context which game and turn this is, for failure messages
     */
    private static void assertInvariants(GameState state, String context) {
        Board board = state.board();
        Set<Position> occupied = new HashSet<>();
        for (Robot robot : state.robots()) {
            if (robot.isActive()) {
                Position position = robot.position();
                assertTrue(position != null && board.inBounds(position), context + ": " + robot + " is off the board");
                assertTrue(!board.isPit(position), context + ": " + robot + " stands on a pit");
                assertTrue(occupied.add(position), context + ": two robots on " + position);
                assertTrue(robot.damage() < Robot.DESTRUCTION_DAMAGE, context + ": " + robot + " should be destroyed");
                assertTrue(robot.lives() > 0, context + ": " + robot + " has no lives");
            } else {
                assertNull(robot.position(), context + ": " + robot + " is not active but has a position");
                assertEquals(robot.lives() <= 0, robot.status() == RobotStatus.ELIMINATED, context + ": " + robot);
            }
        }
    }
}
