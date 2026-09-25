package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardValidator;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.board.ValidationResult;
import de.mkoehler.robotrampage.board.WalkingDistances;
import de.mkoehler.robotrampage.devtools.analysis.BoardMetrics;
import de.mkoehler.robotrampage.devtools.analysis.MetricsText;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How good a generated board is (design.md 3.14). A board that breaks a hard rule — the validator refuses it, a flag
 * cannot be reached, or a start square is unsafe (on a belt, a crusher or in a laser's line, or facing a wall, a pit or
 * the edge right in front of it; robots also re-enter there) — counts its {@link #violations()}; the generator's second population
 * works those down. A board without violations gets a {@link #score()}: every element is held to the range the two
 * hand-made boards span, the seats should be about equally far from flag 1, the flags well apart, the validator's
 * warnings (belts running into walls, off the board or into pits) few, belts in runs rather than stubs and never
 * pointing head-on at each other, and — the part that makes a board interesting —
 * its hazards and moving parts should lie on the routes robots actually walk, not in empty corners.
 *
 * @param violations how many hard rules the board breaks
 * @param score      the quality of a board without violations; higher is better, 0 at best apart from the route bonus
 * @author Mario Koehler
 */
public record Rating(int violations, double score) {

    /** How far a square may be off the shortest way between two points and still count as on the route. */
    private static final int ROUTE_SLACK = 2;
    /** Consecutive flags closer than this many steps are too close. */
    private static final int MIN_FLAG_LEG = 6;
    /** How strongly, inside the ranges, a board is drawn towards their middle, so the search keeps refining. */
    private static final double TYPICAL = 0.3;
    /** A run of connected belts shorter than this is a stub. */
    private static final int MIN_BELT_RUN = 3;

    /**
     * Returns whether the board breaks no hard rule.
     *
     * @return {@code true} if it may be offered
     */
    public boolean feasible() {
        return violations == 0;
    }

    /**
     * Returns whether this rating is better than another: fewer violations first, then the higher score.
     *
     * @param other the other rating
     * @return {@code true} if this one is better
     */
    public boolean betterThan(Rating other) {
        return violations != other.violations ? violations < other.violations : score > other.score;
    }

    /**
     * Rates a draft.
     *
     * @param draft the draft
     * @return the rating
     */
    public static Rating of(BoardDraft draft) {
        return of(draft, true);
    }

    /**
     * Rates a draft for the suggestion grid (design.md 3.14): the same rules, but nothing pulls the board towards the
     * middle of the ranges or holds its route to a length, since how deadly, how long and how moving a board is are the
     * very axes the grid spreads suggestions along.
     *
     * @param draft the draft
     * @return the rating
     */
    public static Rating forSuggestions(BoardDraft draft) {
        return of(draft, false);
    }

    /**
     * Rates a draft.
     *
     * @param draft   the draft
     * @param typical whether to pull the board towards the middle of the ranges and hold its route to a length
     * @return the rating
     */
    private static Rating of(BoardDraft draft, boolean typical) {
        Board board = draft.toBoard();
        ValidationResult validation = BoardValidator.validate(board);
        BoardMetrics metrics = BoardMetrics.of(board);
        int violations = validation.errors().size() + (metrics.allReachable() ? 0 : 1) + unsafeStarts(board, metrics);
        if (violations > 0) {
            return new Rating(violations, 0);
        }
        double score = 0;
        score -= 10 * outside(metrics.pits(), 4, 8);
        score -= 2 * outside(metrics.belts(), 18, 32);
        score -= 2 * outside(metrics.expressBelts(), 4, 16);
        score -= 8 * outside(metrics.gears(), 1, 4);
        score -= 8 * outside(metrics.pushers(), 1, 4);
        score -= 8 * outside(metrics.crushers(), 1, 3);
        score -= 8 * outside(metrics.repairSites(), 1, 3);
        score -= 10 * outside(metrics.lasers(), 2, 4);
        score -= outside(metrics.laserSquares(), 12, 28);
        int walls = board.walls().values().stream().mapToInt(Set::size).sum();
        score -= 2 * outside(walls, 10, 24);
        score -= (typical ? TYPICAL : 0) * (offMiddle(metrics.pits(), 4, 8) + offMiddle(metrics.belts(), 18, 32)
            + offMiddle(metrics.laserSquares(), 12, 28) + offMiddle(walls, 10, 24));
        score -= 6 * beltStubs(board);
        score -= 10 * headOnBelts(board);
        score -= 20 * mountsOnPits(board);
        score -= 15 * validation.warnings().size();
        score -= 15 * Math.max(0, metrics.firstFlagSpread() - MetricsText.UNFAIR_SPREAD);
        score -= typical ? 3 * outside(metrics.flagRoute(), 20, 30) : 0;
        int nearest = metrics.seats().stream().mapToInt(BoardMetrics.SeatRoute::toFirstFlag).min().orElse(0);
        score -= 3 * outside(nearest, 5, 10);
        List<Position> flags = board.flags();
        for (int leg = 1; leg < flags.size(); leg++) {
            Position from = flags.get(leg - 1);
            score -= 10 * Math.max(0, MIN_FLAG_LEG - WalkingDistances.to(board, flags.get(leg))[from.x()][from.y()]);
        }
        score += 40 * onRouteShare(board);
        return new Rating(0, score);
    }

    /**
     * Counts the start squares a robot is not safe on: on a belt or crusher, in a laser's line of fire, or facing a wall,
     * a pit or the edge of the board right in front of it.
     *
     * @param board   the board
     * @param metrics the board's metrics
     * @return the number of unsafe start squares
     */
    private static int unsafeStarts(Board board, BoardMetrics metrics) {
        Set<Position> lasered = BoardMetrics.laserSquares(board);
        int unsafe = 0;
        for (StartSquare start : board.startSquares()) {
            Position at = start.position();
            Position ahead = at.step(start.facing());
            if (board.beltAt(at).isPresent() || board.featureAt(at) == SquareFeature.CRUSHER || lasered.contains(at)
                || board.hasWall(at, start.facing()) || !board.inBounds(ahead) || board.isPit(ahead)) {
                unsafe++;
            }
        }
        return unsafe;
    }

    /**
     * Returns the share of the board's hazards and moving parts that lie on or next to a route: a square no more than
     * {@link #ROUTE_SLACK} steps off the shortest walk from a start square to flag 1, or from one flag to the next.
     *
     * @param board the board
     * @return the share, 0 to 1; 0 for a board without such squares
     */
    static double onRouteShare(Board board) {
        List<Position> flags = board.flags();
        Set<Position> route = new HashSet<>();
        int[][] toFirst = WalkingDistances.to(board, flags.get(0));
        for (StartSquare start : board.startSquares()) {
            addCorridor(board, route, WalkingDistances.to(board, start.position()), toFirst);
        }
        int[][] previous = toFirst;
        for (int leg = 1; leg < flags.size(); leg++) {
            int[][] next = WalkingDistances.to(board, flags.get(leg));
            addCorridor(board, route, previous, next);
            previous = next;
        }
        Set<Position> interesting = new HashSet<>(board.belts().keySet());
        board.features().forEach((position, feature) -> {
            if (feature != SquareFeature.REPAIR && feature != SquareFeature.NONE) {
                interesting.add(position);
            }
        });
        interesting.addAll(BoardMetrics.laserSquares(board));
        board.pushers().forEach(pusher -> interesting.add(pusher.position()));
        if (interesting.isEmpty()) {
            return 0;
        }
        int near = 0;
        for (Position square : interesting) {
            boolean close = route.contains(square);
            for (Direction direction : Direction.values()) {
                close |= route.contains(square.step(direction));
            }
            near += close ? 1 : 0;
        }
        return (double) near / interesting.size();
    }

    /**
     * Adds the squares close to the shortest walk between two points: those whose distance from one plus the distance to
     * the other is at most the shortest walk plus {@link #ROUTE_SLACK}.
     *
     * @param board the board
     * @param route receives the squares
     * @param from  the walking distances from the first point
     * @param to    the walking distances to the second point
     */
    private static void addCorridor(Board board, Set<Position> route, int[][] from, int[][] to) {
        int shortest = Integer.MAX_VALUE;
        for (int x = 0; x < board.width(); x++) {
            for (int y = 0; y < board.height(); y++) {
                shortest = Math.min(shortest, from[x][y] + to[x][y]);
            }
        }
        for (int x = 0; x < board.width(); x++) {
            for (int y = 0; y < board.height(); y++) {
                if (from[x][y] + to[x][y] <= shortest + ROUTE_SLACK) {
                    route.add(new Position(x, y));
                }
            }
        }
    }

    /**
     * Counts the belt squares that belong to a run of connected belts (belts that feed one another) shorter than
     * {@link #MIN_BELT_RUN}: stubs left over when a later change cut a belt.
     *
     * @param board the board
     * @return the number of belt squares in stubs
     */
    static int beltStubs(Board board) {
        Map<Position, Belt> belts = board.belts();
        Set<Position> seen = new HashSet<>();
        int stubs = 0;
        for (Position first : belts.keySet()) {
            if (!seen.add(first)) {
                continue;
            }
            Deque<Position> queue = new ArrayDeque<>(List.of(first));
            int size = 0;
            while (!queue.isEmpty()) {
                Position at = queue.removeFirst();
                size++;
                for (Direction direction : Direction.values()) {
                    Position next = at.step(direction);
                    boolean connected = belts.containsKey(next) && !board.hasWall(at, direction)
                        && (belts.get(at).direction() == direction || belts.get(next).direction() == direction.opposite());
                    if (connected && seen.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            stubs += size < MIN_BELT_RUN ? size : 0;
        }
        return stubs;
    }

    /**
     * Counts the lasers and pushers mounted on a pit square, which a pit dug after them can leave behind.
     *
     * @param board the board
     * @return the number of such mounts
     */
    static int mountsOnPits(Board board) {
        return (int) (board.lasers().stream().filter(laser -> board.isPit(laser.position())).count()
            + board.pushers().stream().filter(pusher -> board.isPit(pusher.position())).count());
    }

    /**
     * Counts pairs of neighbouring belts that point straight at each other, which just bounce robots back and forth.
     *
     * @param board the board
     * @return the number of such pairs
     */
    static int headOnBelts(Board board) {
        int pairs = 0;
        for (Map.Entry<Position, Belt> entry : board.belts().entrySet()) {
            Direction heading = entry.getValue().direction();
            Belt next = board.belts().get(entry.getKey().step(heading));
            if (next != null && next.direction() == heading.opposite()) {
                pairs++;
            }
        }
        return pairs / 2;
    }

    /**
     * Returns how far a value lies from the middle of a range, as a share of half the range, so every element weighs the
     * same.
     *
     * @param value the value
     * @param low   the lowest value in the range
     * @param high  the highest value in the range
     * @return 0 in the middle, 1 at either end, more outside
     */
    private static double offMiddle(int value, int low, int high) {
        double middle = (low + high) / 2.0;
        return Math.abs(value - middle) / ((high - low) / 2.0);
    }

    /**
     * Returns how far a value lies outside a range.
     *
     * @param value the value
     * @param low   the lowest value in the range
     * @param high  the highest value in the range
     * @return 0 inside the range, otherwise the distance to it
     */
    private static int outside(int value, int low, int high) {
        return value < low ? low - value : Math.max(0, value - high);
    }
}
