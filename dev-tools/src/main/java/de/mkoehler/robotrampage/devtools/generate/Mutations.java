package de.mkoehler.robotrampage.devtools.generate;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.devtools.editor.BoardDraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The small changes the board generator makes (design.md 3.14). Each is a sensible local edit of the kind a designer
 * makes — a belt path, a pit cluster, a laser along a corridor, a pusher aimed at a crusher — so the generator needs no
 * library of drawn pieces. None of them ever moves or removes a start square or changes how many flags there are, and
 * none ever throws: a change that does not fit simply does nothing.
 *
 * @author Mario Koehler
 */
public final class Mutations {

    /** The register sets pushers and crushers use: the classic odd and even registers, and every register. */
    private static final List<Set<Integer>> REGISTER_SETS = List.of(Set.of(1, 3, 5), Set.of(2, 4), Set.of(1, 2, 3, 4, 5));

    /**
     * One kind of change.
     */
    @FunctionalInterface
    interface Mutation {

        /**
         * Changes a draft.
         *
         * @param draft  the draft to change
         * @param random picks where and how
         * @return {@code true} if the draft changed
         */
        boolean apply(BoardDraft draft, Random random);
    }

    /** Every change, each with the same chance of being picked; deleting is listed twice to keep boards from filling up. */
    static final List<Mutation> ALL = List.of(Mutations::beltPath, Mutations::expressPath, Mutations::pitCluster,
        Mutations::gear, Mutations::wallRun, Mutations::laser, Mutations::pusherAndCrusher, Mutations::repairSite,
        Mutations::moveFlag, Mutations::clearSquare, Mutations::clearSquare, Mutations::removeWall);

    /**
     * Not instantiated.
     */
    private Mutations() {
    }

    /**
     * Applies one change picked at random.
     *
     * @param draft  the draft to change
     * @param random picks the change and how it is made
     * @return {@code true} if the draft changed
     */
    public static boolean mutate(BoardDraft draft, Random random) {
        return ALL.get(random.nextInt(ALL.size())).apply(draft, random);
    }

    /**
     * Lays a normal belt along a random walk of three to eight squares; each belt points at the next square, so corners
     * come out right.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a belt was laid
     */
    static boolean beltPath(BoardDraft draft, Random random) {
        return path(draft, random, false);
    }

    /**
     * Lays an express belt along a random walk, like {@link #beltPath}.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a belt was laid
     */
    static boolean expressPath(BoardDraft draft, Random random) {
        return path(draft, random, true);
    }

    /**
     * Lays a belt along a random walk that never visits a square twice and avoids start squares and flags.
     *
     * @param draft   the draft
     * @param random  the randomness
     * @param express whether it is an express belt
     * @return {@code true} if a belt was laid
     */
    private static boolean path(BoardDraft draft, Random random, boolean express) {
        Set<Position> blocked = keepClear(draft);
        Position at = randomSquare(random);
        if (blocked.contains(at)) {
            return false;
        }
        int length = 3 + random.nextInt(6);
        Direction heading = Direction.values()[random.nextInt(4)];
        List<Position> squares = new ArrayList<>(List.of(at));
        List<Direction> directions = new ArrayList<>();
        while (squares.size() < length) {
            if (random.nextInt(4) == 0) {
                heading = random.nextBoolean() ? heading.rotateLeft() : heading.rotateRight();
            }
            Position next = at.step(heading);
            if (!BoardDraft.inBounds(next) || blocked.contains(next) || squares.contains(next)) {
                break;
            }
            directions.add(heading);
            squares.add(next);
            at = next;
        }
        if (directions.isEmpty()) {
            return false;
        }
        directions.add(heading);
        for (int index = 0; index < squares.size(); index++) {
            draft.paintBelt(squares.get(index), directions.get(index), express);
        }
        return true;
    }

    /**
     * Makes a cluster of one to four neighbouring pits, not next to a start square or on a flag.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a pit was dug
     */
    static boolean pitCluster(BoardDraft draft, Random random) {
        Set<Position> blocked = keepClearWide(draft);
        Position at = randomSquare(random);
        boolean changed = false;
        int size = 1 + random.nextInt(4);
        for (int count = 0; count < size && BoardDraft.inBounds(at) && !blocked.contains(at); count++) {
            changed |= draft.paintFeature(at, SquareFeature.PIT);
            at = at.step(Direction.values()[random.nextInt(4)]);
        }
        return changed;
    }

    /**
     * Puts a gear, turning either way, on a square that is neither a start square nor a flag.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a gear was placed
     */
    static boolean gear(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        if (keepClear(draft).contains(at)) {
            return false;
        }
        return draft.paintFeature(at, random.nextBoolean() ? SquareFeature.GEAR_CLOCKWISE
            : SquareFeature.GEAR_COUNTERCLOCKWISE);
    }

    /**
     * Runs a wall of one to three segments along the same side of neighbouring squares.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a wall was added
     */
    static boolean wallRun(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        Direction side = Direction.values()[random.nextInt(4)];
        Direction along = random.nextBoolean() ? side.rotateLeft() : side.rotateRight();
        boolean changed = false;
        int length = 1 + random.nextInt(3);
        for (int count = 0; count < length && BoardDraft.inBounds(at); count++) {
            if (!draft.hasMount(at, side)) {
                changed |= draft.addWall(at, side);
            }
            at = at.step(along);
        }
        return changed;
    }

    /**
     * Mounts a laser of one or two beams on a side of a square so that it fires across at least three squares, and never
     * along a line with a start square in it.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a laser was mounted
     */
    static boolean laser(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        Direction side = Direction.values()[random.nextInt(4)];
        Direction firing = side.opposite();
        Board board = draft.toBoard();
        if (board.isPit(at)) {
            return false;
        }
        Set<Position> starts = startSquares(draft);
        int length = 0;
        Position square = at;
        while (true) {
            if (starts.contains(square)) {
                return false;
            }
            length++;
            if (board.hasWall(square, firing) || !BoardDraft.inBounds(square.step(firing))) {
                break;
            }
            square = square.step(firing);
        }
        return length >= 3 && draft.mountLaser(at, side, 1 + random.nextInt(2));
    }

    /**
     * Mounts a pusher and puts a crusher on the square it pushes onto, both active in the same registers: the combo that
     * makes a pusher dangerous.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if they were placed
     */
    static boolean pusherAndCrusher(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        Direction side = Direction.values()[random.nextInt(4)];
        Position target = at.step(side.opposite());
        Set<Position> blocked = keepClearWide(draft);
        if (!BoardDraft.inBounds(target) || blocked.contains(at) || blocked.contains(target)) {
            return false;
        }
        Board board = draft.toBoard();
        if (board.isPit(at) || board.isPit(target)) {
            return false;
        }
        Set<Integer> registers = REGISTER_SETS.get(random.nextInt(2));
        boolean changed = draft.mountPusher(at, side, registers);
        changed |= draft.paintCrusher(target, registers);
        return changed;
    }

    /**
     * Puts a repair site on a free square.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if it was placed
     */
    static boolean repairSite(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        return !keepClear(draft).contains(at) && draft.paintFeature(at, SquareFeature.REPAIR);
    }

    /**
     * Moves a flag, keeping its number, to a square that is not a start square, a pit or another flag.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a flag moved
     */
    static boolean moveFlag(BoardDraft draft, Random random) {
        if (draft.flags().isEmpty()) {
            return false;
        }
        Position flag = draft.flags().get(random.nextInt(draft.flags().size()));
        Position to = randomSquare(random);
        Board board = draft.toBoard();
        if (startSquares(draft).contains(to) || board.isPit(to) || board.beltAt(to).isPresent()
            || board.featureAt(to) == SquareFeature.CRUSHER) {
            return false;
        }
        return draft.moveFlag(flag, to);
    }

    /**
     * Clears the belt, feature and mounts of a square that is neither a start square nor a flag.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if something was removed
     */
    static boolean clearSquare(BoardDraft draft, Random random) {
        Position at = randomSquare(random);
        return !keepClear(draft).contains(at) && draft.clearSquare(at);
    }

    /**
     * Removes a plain wall on a random side of a random square, if there is one.
     *
     * @param draft  the draft
     * @param random the randomness
     * @return {@code true} if a wall was removed
     */
    static boolean removeWall(BoardDraft draft, Random random) {
        return draft.removeWall(randomSquare(random), Direction.values()[random.nextInt(4)]);
    }

    /**
     * Picks a square of the board.
     *
     * @param random the randomness
     * @return the square
     */
    private static Position randomSquare(Random random) {
        return new Position(random.nextInt(BoardDraft.SIZE), random.nextInt(BoardDraft.SIZE));
    }

    /**
     * Returns the squares no belt, feature or mount may be put on: start squares and flags.
     *
     * @param draft the draft
     * @return the squares
     */
    private static Set<Position> keepClear(BoardDraft draft) {
        Set<Position> squares = startSquares(draft);
        squares.addAll(draft.flags());
        return squares;
    }

    /**
     * Returns the squares no pit or crusher may be put on: start squares, the squares next to them, and flags.
     *
     * @param draft the draft
     * @return the squares
     */
    private static Set<Position> keepClearWide(BoardDraft draft) {
        Set<Position> squares = keepClear(draft);
        for (StartSquare start : draft.starts()) {
            for (Direction direction : Direction.values()) {
                squares.add(start.position().step(direction));
            }
        }
        return squares;
    }

    /**
     * Returns the start squares' positions.
     *
     * @param draft the draft
     * @return the positions, in a set the caller may change
     */
    private static Set<Position> startSquares(BoardDraft draft) {
        Set<Position> squares = new HashSet<>();
        draft.starts().forEach(start -> squares.add(start.position()));
        return squares;
    }
}
