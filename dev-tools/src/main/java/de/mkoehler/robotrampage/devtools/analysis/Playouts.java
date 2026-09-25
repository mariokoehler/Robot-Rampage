package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.bot.BotBrain;
import de.mkoehler.robotrampage.bot.BotDecision;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Plays whole games on a board with a bot on every start square (design.md 2.14, 3.13), to find out what the board is
 * like to play: whether games end, how long they take, what kills robots, and whether some seats win more than others.
 * Everything is derived from a seed, so the same board and seed always give the same report.
 *
 * @author Mario Koehler
 */
public final class Playouts {

    /** A game that is still running after this many turns counts as not finished. */
    public static final int TURN_LIMIT = 60;

    /**
     * What the games played so far showed.
     *
     * @param games      how many games were played
     * @param finished   how many of them somebody won within {@link #TURN_LIMIT} turns
     * @param turns      the turns the finished games took, added up
     * @param flags      the flags touched in all games, added up
     * @param deaths     the robots destroyed in all games, by cause
     * @param winsBySeat how many games each seat won, by seat
     * @param flagsBySeat how many flags each seat had touched when its games ended, added up, by seat: a denser measure of
     *                    how well a seat does than wins, since every seat counts in every game
     */
    public record Report(int games, int finished, int turns, int flags, Map<DestructionCause, Integer> deaths,
                         int[] winsBySeat, int[] flagsBySeat) {

        /**
         * Returns the average length of a finished game.
         *
         * @return the turns, or 0 if no game finished
         */
        public double averageTurns() {
            return finished == 0 ? 0 : (double) turns / finished;
        }

        /**
         * Returns how many robots were destroyed per game, whatever the cause.
         *
         * @return the average
         */
        public double deathsPerGame() {
            return games == 0 ? 0 : deaths.values().stream().mapToInt(Integer::intValue).sum() / (double) games;
        }

        /**
         * Returns how far apart the seats are in flags touched per game: the best seat's average minus the worst's. The
         * smaller, the fairer the board.
         *
         * @return the gap in flags per game, 0 before any game
         */
        public double flagGap() {
            if (games == 0 || flagsBySeat.length == 0) {
                return 0;
            }
            int most = Arrays.stream(flagsBySeat).max().orElse(0);
            int least = Arrays.stream(flagsBySeat).min().orElse(0);
            return (most - least) / (double) games;
        }
    }

    /**
     * Not instantiated.
     */
    private Playouts() {
    }

    /**
     * Plays games one after the other and reports after each.
     *
     * @param board     the board; must be valid, with at least one flag and start square
     * @param games     how many games to play
     * @param seed      the seed every game's deck and the bots' tie-breaks are derived from
     * @param progress  receives the report after every game, the last one being the final report
     * @param cancelled asked before every turn; once it answers {@code true} the run stops without another report
     */
    public static void run(Board board, int games, long seed, Consumer<Report> progress, BooleanSupplier cancelled) {
        int seats = board.startSquares().size();
        int finished = 0;
        int turns = 0;
        int flags = 0;
        Map<DestructionCause, Integer> deaths = new EnumMap<>(DestructionCause.class);
        int[] wins = new int[seats];
        int[] flagsBySeat = new int[seats];
        for (int game = 0; game < games; game++) {
            GameState state = newGame(board, seed + game);
            Random random = new Random(seed * 31 + game);
            int turn = 0;
            while (!state.isOver() && turn < TURN_LIMIT) {
                if (cancelled.getAsBoolean()) {
                    return;
                }
                turn++;
                state = playTurn(state, random, deaths);
            }
            if (state.isOver()) {
                finished++;
                turns += turn;
                if (state.winnerId() >= 0) {
                    wins[state.winnerId()]++;
                }
            }
            flags += state.robots().stream().mapToInt(Robot::flagsTouched).sum();
            for (Robot robot : state.robots()) {
                flagsBySeat[robot.id()] += robot.flagsTouched();
            }
            progress.accept(new Report(game + 1, finished, turns, flags, Map.copyOf(deaths), wins.clone(),
                flagsBySeat.clone()));
        }
    }

    /**
     * Sets up a game with a robot on every start square.
     *
     * @param board the board
     * @param seed  the deck's seed
     * @return the state
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
     * Plays one turn the way the server does: robots re-enter, every bot programs, the turn is resolved.
     *
     * @param state  the state before the turn
     * @param random the bots' tie-breaks
     * @param deaths counts the robots destroyed, by cause
     * @return the state after the turn
     */
    private static GameState playTurn(GameState state, Random random, Map<DestructionCause, Integer> deaths) {
        EventLog respawns = new EventLog();
        Respawner.respawn(state, Map.of(), respawns);
        Set<Integer> respawned = new HashSet<>();
        for (LoggedEvent entry : respawns.entries()) {
            if (entry.event() instanceof GameEvent.RobotRespawned back) {
                respawned.add(back.robotId());
            }
        }
        for (Map.Entry<Integer, List<Card>> hand : Programming.deal(state).entrySet()) {
            int id = hand.getKey();
            BotDecision decision = BotBrain.decide(state, id, hand.getValue(), respawned.contains(id), random);
            if (decision.facing() != null) {
                state.robot(id).setFacing(decision.facing());
            }
            Programming.submit(state, id, hand.getValue(), decision.program(), decision.powerDown());
        }
        TurnResult result = TurnResolver.resolve(state);
        for (LoggedEvent entry : result.events()) {
            if (entry.event() instanceof GameEvent.RobotDestroyed destroyed) {
                deaths.merge(destroyed.cause(), 1, Integer::sum);
            }
        }
        return result.state();
    }
}
