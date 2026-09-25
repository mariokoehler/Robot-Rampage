package de.mkoehler.robotrampage.bot;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Chooses a computer-controlled robot's program (design.md 2.14): it plays every distinct program its hand allows through
 * the real rules engine and keeps the one that ends best — flags touched, closer to the next flag by the walking distance
 * around walls and pits, alive, little damage.
 * <p>
 * <b>Fair play:</b> the bot never sees another player's program. Every other robot's registers are emptied in the copy it
 * simulates on, so the others stand still in its imagination, whatever the server already knows about them. Since only
 * the bot's own robot acts in the simulation, card priorities cannot change the outcome, and programs are told apart by
 * their sequence of card types alone.
 *
 * @author Mario Koehler
 */
public final class BotBrain {

    /** Walking distance used for a square from which the next flag cannot be reached. */
    private static final int UNREACHABLE = 200;
    private static final double WIN = 1_000_000;
    private static final double LOST = -10_000;
    private static final double PER_FLAG = 1_000;
    private static final double PER_STEP = -20;
    private static final double PER_DAMAGE = -8;
    /** From how much damage (at the end of the turn) a surviving bot powers down to repair. */
    static final int POWER_DOWN_DAMAGE = 6;

    /**
     * Not instantiated.
     */
    private BotBrain() {
    }

    /**
     * Decides a bot's turn.
     *
     * @param state           the game as it stands, hands dealt; not changed
     * @param robotId         the bot's robot
     * @param hand            the cards dealt to it
     * @param mayChooseFacing whether the robot re-entered this turn and may pick its facing
     * @param random          breaks ties between equally good programs
     * @return the decision; its program always fits the robot's unlocked registers and hand
     */
    public static BotDecision decide(GameState state, int robotId, List<Card> hand, boolean mayChooseFacing,
                                     Random random) {
        GameState base = state.copy();
        for (Robot other : base.robots()) {
            if (other.id() != robotId) {
                for (int register = 0; register < Robot.REGISTER_COUNT; register++) {
                    other.setRegister(register, null);
                }
            }
        }
        Robot me = base.robot(robotId);
        int free = Robot.REGISTER_COUNT - me.lockedRegisterCount();
        List<List<Card>> programs = new ArrayList<>();
        distinctPrograms(hand, free, new boolean[hand.size()], new ArrayList<>(), programs);
        int[][] distances = distances(base.board(), me.flagsTouched());
        List<Direction> facings = mayChooseFacing ? List.of(Direction.values()) : Arrays.asList((Direction) null);

        Direction originalFacing = me.facing();
        double bestScore = Double.NEGATIVE_INFINITY;
        List<Card> bestProgram = programs.get(0);
        Direction bestFacing = null;
        Robot bestEnd = null;
        for (Direction facing : facings) {
            me.setFacing(facing == null ? originalFacing : facing);
            for (List<Card> program : programs) {
                for (int register = 0; register < free; register++) {
                    me.setRegister(register, program.get(register));
                }
                GameState end = TurnResolver.resolve(base).state();
                double score = score(end, me, robotId, distances) + random.nextDouble();
                if (score > bestScore) {
                    bestScore = score;
                    bestProgram = program;
                    bestFacing = facing;
                    bestEnd = end.robot(robotId);
                }
            }
        }
        boolean powerDown = bestEnd != null && bestEnd.isActive() && bestEnd.damage() >= POWER_DOWN_DAMAGE;
        return new BotDecision(bestProgram, bestFacing, powerDown);
    }

    /**
     * Collects every program the hand allows that differs from the others in its sequence of card types, keeping the first
     * card of each type it meets.
     *
     * @param hand     the hand
     * @param length   how many registers to fill
     * @param used     which cards of the hand the program being built already uses
     * @param building the program being built
     * @param into     receives the finished programs
     */
    private static void distinctPrograms(List<Card> hand, int length, boolean[] used, List<Card> building,
                                         List<List<Card>> into) {
        if (building.size() == length) {
            into.add(List.copyOf(building));
            return;
        }
        Set<CardType> tried = new HashSet<>();
        for (int index = 0; index < hand.size(); index++) {
            Card card = hand.get(index);
            if (used[index] || !tried.add(card.type())) {
                continue;
            }
            used[index] = true;
            building.add(card);
            distinctPrograms(hand, length, used, building, into);
            building.removeLast();
            used[index] = false;
        }
    }

    /**
     * Rates how the bot's robot ends the simulated turn.
     *
     * @param end       the state after the turn
     * @param before    the robot before the turn
     * @param robotId   the robot
     * @param distances the walking distances to the flag it was heading for
     * @return the score; higher is better
     */
    private static double score(GameState end, Robot before, int robotId, int[][] distances) {
        if (end.isOver() && end.winnerId() == robotId) {
            return WIN;
        }
        Robot after = end.robot(robotId);
        double score = PER_FLAG * (after.flagsTouched() - before.flagsTouched());
        if (!after.isActive() || after.lives() < before.lives()) {
            return score + LOST;
        }
        if (distances != null && after.flagsTouched() == before.flagsTouched()) {
            Position at = after.position();
            score += PER_STEP * distances[at.x()][at.y()];
        }
        return score + PER_DAMAGE * after.damage();
    }

    /**
     * Works out, for every square, how many steps it takes to walk to a flag, around walls and pits.
     *
     * @param board        the board
     * @param flagsTouched how many flags the robot has touched; the next one is the target
     * @return the steps by {@code [x][y]}, {@link #UNREACHABLE} where the flag cannot be reached, or {@code null} if every
     *         flag has been touched
     */
    static int[][] distances(Board board, int flagsTouched) {
        if (flagsTouched >= board.flags().size()) {
            return null;
        }
        int[][] steps = new int[board.width()][board.height()];
        for (int[] column : steps) {
            Arrays.fill(column, UNREACHABLE);
        }
        Position flag = board.flags().get(flagsTouched);
        steps[flag.x()][flag.y()] = 0;
        Deque<Position> queue = new ArrayDeque<>(List.of(flag));
        while (!queue.isEmpty()) {
            Position at = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                Position next = at.step(direction);
                if (board.inBounds(next) && !board.hasWall(at, direction) && !board.isPit(next)
                    && steps[next.x()][next.y()] > steps[at.x()][at.y()] + 1) {
                    steps[next.x()][next.y()] = steps[at.x()][at.y()] + 1;
                    queue.addLast(next);
                }
            }
        }
        return steps;
    }
}
