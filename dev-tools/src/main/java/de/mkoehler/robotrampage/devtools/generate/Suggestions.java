package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.devtools.analysis.BoardMetrics;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Finds several good boards that differ from each other, for the editor's Suggest button (design.md 3.14): MAP-Elites
 * searches on separate islands. Every valid board lands in one cell of a 3 × 3 × 3 grid spanned by how deadly it is, how
 * long its route is and how much of it moves robots, and each cell keeps only its best board; boards that break a hard
 * rule wait in a small population of their own until a change makes them valid. One search on one grid lets a single
 * strong family of boards spread into every cell, so every suggestion would share its skeleton; instead each suggestion
 * comes from its own island — its own search, started from its own scrambled copy of the canvas (and, on an empty
 * canvas, its own flags) — and the islands' boards are picked to lie far apart in the grid. Like
 * {@link BoardGenerator}, the search is spent by boards rated, never by time, so a canvas and seed always give the same
 * suggestions.
 *
 * @author Mario Koehler
 */
public final class Suggestions {

    /** How many boards each island rates. */
    public static final int EVALUATIONS_PER_ISLAND = 2000;
    /** Where the hazard share bins end: below the first is "calm", from the second on "deadly". */
    static final double[] HAZARD_EDGES = {0.17, 0.22};
    /** Where the whole-route bins end, in steps: below the first is "short", from the second on "long". */
    static final int[] ROUTE_EDGES = {31, 36};
    /** Where the moving share bins end: below the first is "still", from the second on "busy". */
    static final double[] MOVING_EDGES = {0.17, 0.21};
    private static final int BINS = 3;
    private static final int INFEASIBLE = 30;
    private static final int TOURNAMENT = 3;
    private static final int MAX_MUTATIONS = 3;
    /** How many random changes an island's copy of the canvas gets before its search starts. */
    private static final int SCRAMBLE = 20;
    /** Mixes an island's number into the seed, so every island draws its own random numbers. */
    private static final long ISLAND_STRIDE = 0x9E3779B97F4A7C15L;
    /** A cell's best board is only shown if it scores no more than this far below the best board of all. */
    private static final double QUALITY_FLOOR = 15;

    /**
     * A place in the grid.
     *
     * @param hazard 0 calm, 1 medium, 2 deadly
     * @param route  0 short, 1 medium, 2 long
     * @param moving 0 still, 1 medium, 2 busy
     */
    public record Cell(int hazard, int route, int moving) {

        /**
         * Returns the cell's position in a flat array.
         *
         * @return the index, 0 to 26
         */
        int index() {
            return (hazard * BINS + route) * BINS + moving;
        }

        /**
         * Describes the cell in words, for the suggestion's caption.
         *
         * @return for example {@code Deadly, short route, lots of movement}
         */
        public String words() {
            return List.of("Calm", "Risky", "Deadly").get(hazard) + ", "
                + List.of("short route", "medium route", "long route").get(route) + ", "
                + List.of("little movement", "some movement", "lots of movement").get(moving);
        }

        /**
         * Returns how far apart two cells are, counting steps along each axis.
         *
         * @param other the other cell
         * @return the distance
         */
        int distance(Cell other) {
            return Math.abs(hazard - other.hazard) + Math.abs(route - other.route) + Math.abs(moving - other.moving);
        }
    }

    /**
     * A suggested board.
     *
     * @param draft       the board
     * @param rating      its rating in suggestion mode
     * @param cell        where it sits in the grid
     * @param hazardShare the share of squares that are pits, crushers or in a laser's line
     * @param route       the average whole route over all seats, in steps
     * @param movingShare the share of squares that are belts or gears
     */
    public record Suggestion(BoardDraft draft, Rating rating, Cell cell, double hazardShare, double route,
                             double movingShare) {
    }

    /**
     * Not instantiated.
     */
    private Suggestions() {
    }

    /**
     * Searches for suggestions.
     *
     * @param canvas    the board to start from; not changed. Start squares and the number of flags are kept, as for
     *                  {@link BoardGenerator}
     * @param seed      the seed of every random choice
     * @param count     how many suggestions to return at most: one island searches for each
     * @param cancelled asked once per board rated; once it answers {@code true} the run stops with what it has
     * @return the suggestions, one per island, the best first and then those farthest from the ones already picked; an
     *         island with no board within {@link #QUALITY_FLOOR} of the best gives none, so fewer than {@code count} may
     *         come back
     */
    public static List<Suggestion> suggest(BoardDraft canvas, long seed, int count, BooleanSupplier cancelled) {
        List<List<Suggestion>> islands = new ArrayList<>();
        for (int island = 0; island < count && !cancelled.getAsBoolean(); island++) {
            islands.add(island(canvas, new Random(seed + ISLAND_STRIDE * island), island == 0, cancelled));
        }
        return pickFromIslands(islands);
    }

    /**
     * Runs one island's search.
     *
     * @param canvas    the board to start from
     * @param random    the island's randomness
     * @param plain     whether the island keeps the canvas as it is instead of scrambling it first, so one suggestion
     *                  always stays close to the canvas
     * @param cancelled asked once per board rated
     * @return the best board of every cell the island filled, in cell order
     */
    private static List<Suggestion> island(BoardDraft canvas, Random random, boolean plain, BooleanSupplier cancelled) {
        Suggestion[] archive = new Suggestion[BINS * BINS * BINS];
        List<BoardGenerator.Candidate> infeasible = new ArrayList<>();
        BoardDraft start = BoardGenerator.prepare(canvas, random);
        consider(start.copy(), archive, infeasible);
        if (!plain && !cancelled.getAsBoolean()) {
            for (int change = 0; change < SCRAMBLE; change++) {
                Mutations.mutate(start, random);
            }
            consider(start, archive, infeasible);
        }
        for (int evaluation = 2; evaluation < EVALUATIONS_PER_ISLAND && !cancelled.getAsBoolean(); evaluation++) {
            List<Suggestion> elites = occupied(archive);
            BoardDraft child = elites.isEmpty() || !infeasible.isEmpty() && random.nextInt(4) == 0
                ? pickInfeasible(infeasible, random).draft().copy()
                : elites.get(random.nextInt(elites.size())).draft().copy();
            int changes = 1 + random.nextInt(MAX_MUTATIONS);
            for (int change = 0; change < changes; change++) {
                Mutations.mutate(child, random);
            }
            consider(child, archive, infeasible);
        }
        return occupied(archive);
    }

    /**
     * Picks one board from every island: first the best board of all, then again and again the island whose best-placed
     * board lies farthest in the grid from the boards already picked (ties go to the better score, then to the earlier
     * island or cell). A board scoring more than {@link #QUALITY_FLOOR} below the best of all is never picked.
     *
     * @param islands every island's best board per filled cell, in cell order
     * @return the picked boards, at most one per island, in the order picked
     */
    private static List<Suggestion> pickFromIslands(List<List<Suggestion>> islands) {
        double best = islands.stream().flatMap(List::stream).mapToDouble(elite -> elite.rating().score()).max()
            .orElse(Double.NaN);
        List<List<Suggestion>> left = new ArrayList<>();
        for (List<Suggestion> island : islands) {
            List<Suggestion> good = island.stream().filter(elite -> elite.rating().score() >= best - QUALITY_FLOOR)
                .toList();
            if (!good.isEmpty()) {
                left.add(good);
            }
        }
        List<Suggestion> picked = new ArrayList<>();
        while (!left.isEmpty()) {
            List<Suggestion> bestIsland = null;
            Suggestion bestPick = null;
            int bestDistance = -1;
            for (List<Suggestion> island : left) {
                for (Suggestion candidate : island) {
                    int distance = picked.stream().mapToInt(chosen -> chosen.cell().distance(candidate.cell())).min()
                        .orElse(0);
                    if (distance > bestDistance || distance == bestDistance
                        && candidate.rating().score() > bestPick.rating().score()) {
                        bestIsland = island;
                        bestPick = candidate;
                        bestDistance = distance;
                    }
                }
            }
            picked.add(bestPick);
            left.remove(bestIsland);
        }
        return picked;
    }

    /**
     * Rates a board and keeps it if it is the best of its cell, or among the best boards that are not valid yet.
     *
     * @param draft      the board
     * @param archive    the grid
     * @param infeasible the boards that break a hard rule
     */
    private static void consider(BoardDraft draft, Suggestion[] archive, List<BoardGenerator.Candidate> infeasible) {
        Rating rating = Rating.forSuggestions(draft);
        if (!rating.feasible()) {
            if (infeasible.size() < INFEASIBLE || rating.betterThan(infeasible.getLast().rating())) {
                infeasible.add(new BoardGenerator.Candidate(draft, rating));
                infeasible.sort(Comparator.comparing(BoardGenerator.Candidate::rating,
                    (a, b) -> a.betterThan(b) ? -1 : b.betterThan(a) ? 1 : 0));
                while (infeasible.size() > INFEASIBLE) {
                    infeasible.removeLast();
                }
            }
            return;
        }
        Suggestion suggestion = describe(draft, rating);
        Suggestion elite = archive[suggestion.cell().index()];
        if (elite == null || rating.score() > elite.rating().score()) {
            archive[suggestion.cell().index()] = suggestion;
        }
    }

    /**
     * Measures a valid board along the grid's three axes.
     *
     * @param draft  the board
     * @param rating its rating
     * @return the suggestion, placed in its cell
     */
    static Suggestion describe(BoardDraft draft, Rating rating) {
        Board board = draft.toBoard();
        BoardMetrics metrics = BoardMetrics.of(board);
        double route = metrics.seats().stream().mapToInt(BoardMetrics.SeatRoute::route).average().orElse(0);
        Cell cell = new Cell(bin(metrics.hazardShare(), HAZARD_EDGES), bin(route, ROUTE_EDGES[0], ROUTE_EDGES[1]),
            bin(metrics.movingShare(), MOVING_EDGES));
        return new Suggestion(draft, rating, cell, metrics.hazardShare(), route, metrics.movingShare());
    }

    /**
     * Returns the filled cells' boards, in cell order.
     *
     * @param archive the grid
     * @return the boards
     */
    private static List<Suggestion> occupied(Suggestion[] archive) {
        List<Suggestion> filled = new ArrayList<>();
        for (Suggestion suggestion : archive) {
            if (suggestion != null) {
                filled.add(suggestion);
            }
        }
        return filled;
    }

    /**
     * Picks a board that is not valid yet, by tournament.
     *
     * @param infeasible the boards, not empty
     * @param random     the randomness
     * @return the board
     */
    private static BoardGenerator.Candidate pickInfeasible(List<BoardGenerator.Candidate> infeasible, Random random) {
        BoardGenerator.Candidate winner = infeasible.get(random.nextInt(infeasible.size()));
        for (int round = 1; round < TOURNAMENT; round++) {
            BoardGenerator.Candidate rival = infeasible.get(random.nextInt(infeasible.size()));
            if (rival.rating().betterThan(winner.rating())) {
                winner = rival;
            }
        }
        return winner;
    }

    /**
     * Returns the bin a value falls into.
     *
     * @param value the value
     * @param edges the two bin edges
     * @return 0 below the first edge, 1 below the second, 2 otherwise
     */
    private static int bin(double value, double[] edges) {
        return bin(value, edges[0], edges[1]);
    }

    /**
     * Returns the bin a value falls into.
     *
     * @param value the value
     * @param low   the first edge
     * @param high  the second edge
     * @return 0 below {@code low}, 1 below {@code high}, 2 otherwise
     */
    private static int bin(double value, double low, double high) {
        return value < low ? 0 : value < high ? 1 : 2;
    }
}
