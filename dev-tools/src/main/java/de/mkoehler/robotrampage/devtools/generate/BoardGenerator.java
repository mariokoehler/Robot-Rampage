package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Generates a board from the one on the editor's canvas (design.md 3.14): an evolutionary search that only ever makes the
 * small, sensible changes of {@link Mutations} and keeps what {@link Rating} likes best. It runs two populations side by
 * side, as the FI2Pop method does: boards that break a hard rule compete on how few rules they break, valid boards on
 * their score, and children of either kind land in whichever population they belong to. The search is spent by number
 * of boards rated, never by time, so the same canvas and seed give the same board on any machine.
 *
 * @author Mario Koehler
 */
public final class BoardGenerator {

    /** How many boards a run rates. */
    public static final int EVALUATIONS = 4000;
    private static final int POPULATION = 30;
    private static final int TOURNAMENT = 3;
    private static final int MAX_MUTATIONS = 3;
    private static final int FLAGS_ON_EMPTY = 3;

    /**
     * A board and how good it is.
     *
     * @param draft  the board
     * @param rating its rating
     */
    public record Candidate(BoardDraft draft, Rating rating) {
    }

    /**
     * Not instantiated.
     */
    private BoardGenerator() {
    }

    /**
     * Generates a board.
     *
     * @param canvas    the board to start from; not changed. Start squares are kept exactly; a canvas without any gets
     *                  eight along the bottom row facing north, one without flags gets three placed at random
     * @param seed      the seed of every random choice
     * @param cancelled asked once per generation; once it answers {@code true} the run stops and returns the best so far
     * @return the best board found, with its rating; a valid one whenever the search found one
     */
    public static Candidate generate(BoardDraft canvas, long seed, BooleanSupplier cancelled) {
        Random random = new Random(seed);
        BoardDraft start = prepare(canvas, random);
        List<Candidate> feasible = new ArrayList<>();
        List<Candidate> infeasible = new ArrayList<>();
        Candidate best = new Candidate(start, Rating.of(start));
        place(best, feasible, infeasible);
        int evaluations = 1;
        while (evaluations < EVALUATIONS && !cancelled.getAsBoolean()) {
            List<Candidate> children = new ArrayList<>();
            for (int child = 0; child < POPULATION && evaluations < EVALUATIONS; child++) {
                List<Candidate> pool = infeasible.isEmpty() || !feasible.isEmpty() && random.nextInt(4) != 0
                    ? feasible : infeasible;
                BoardDraft draft = pick(pool, random).draft().copy();
                int changes = 1 + random.nextInt(MAX_MUTATIONS);
                for (int change = 0; change < changes; change++) {
                    Mutations.mutate(draft, random);
                }
                Candidate candidate = new Candidate(draft, Rating.of(draft));
                evaluations++;
                children.add(candidate);
                if (candidate.rating().betterThan(best.rating())) {
                    best = candidate;
                }
            }
            children.forEach(candidate -> place(candidate, feasible, infeasible));
            trim(feasible);
            trim(infeasible);
        }
        return best;
    }

    /**
     * Copies the canvas and gives it what every board needs: start squares and flags, if it has none.
     *
     * @param canvas the canvas
     * @param random places the flags
     * @return the board the search starts from
     */
    static BoardDraft prepare(BoardDraft canvas, Random random) {
        BoardDraft draft = canvas.copy();
        if (draft.starts().isEmpty()) {
            for (int x = 2; x <= 9; x++) {
                draft.addStart(new Position(x, 0), Direction.NORTH);
            }
        }
        while (draft.flags().isEmpty() || draft.flags().size() < FLAGS_ON_EMPTY && canvas.flags().isEmpty()) {
            Position square = new Position(random.nextInt(BoardDraft.SIZE), 3 + random.nextInt(BoardDraft.SIZE - 3));
            if (draft.startAt(square).isEmpty()) {
                draft.clearSquare(square);
                draft.addFlag(square);
            }
        }
        return draft;
    }

    /**
     * Picks a parent by tournament: the best of a few picked at random.
     *
     * @param pool   the population, not empty
     * @param random the randomness
     * @return the parent
     */
    private static Candidate pick(List<Candidate> pool, Random random) {
        Candidate winner = pool.get(random.nextInt(pool.size()));
        for (int round = 1; round < TOURNAMENT; round++) {
            Candidate rival = pool.get(random.nextInt(pool.size()));
            if (rival.rating().betterThan(winner.rating())) {
                winner = rival;
            }
        }
        return winner;
    }

    /**
     * Puts a candidate into the population it belongs to.
     *
     * @param candidate  the candidate
     * @param feasible   the valid boards
     * @param infeasible the boards that break a hard rule
     */
    private static void place(Candidate candidate, List<Candidate> feasible, List<Candidate> infeasible) {
        (candidate.rating().feasible() ? feasible : infeasible).add(candidate);
    }

    /**
     * Keeps only the best {@link #POPULATION} of a population.
     *
     * @param population the population
     */
    private static void trim(List<Candidate> population) {
        population.sort(Comparator.comparing(Candidate::rating, (a, b) -> a.betterThan(b) ? -1 : b.betterThan(a) ? 1 : 0));
        while (population.size() > POPULATION) {
            population.removeLast();
        }
    }
}
