package de.mkoehler.robotrampage.board;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Checks boards for sense (design.md 3.6). Validation is a first-class step: a board
 * from any source &mdash; a hand-authored file, a composition of sections, a generator
 * &mdash; must pass exactly the same checks before the game plays on it.
 * <p>
 * There are two levels. {@link #validate(BoardDefinition)} checks the <em>structure</em> of
 * a JSON definition (coordinates on the grid, no duplicates, sensible numbers) and must pass
 * before the definition can be turned into a {@link Board}. {@link #validate(Board)} then
 * checks the <em>playability</em> of the finished board, including that every flag and every
 * start square can be reached from every other.
 * <p>
 * Legal but suspicious things, such as a belt that carries robots off the board, are reported
 * as warnings, not errors: a death trap is a legitimate part of a board.
 *
 * @author Mario Koehler
 */
public final class BoardValidator {

    /**
     * The largest width or height a board may have.
     */
    public static final int MAX_SIZE = 64;

    /**
     * The largest number of start squares, which is the largest number of players.
     */
    public static final int MAX_START_SQUARES = 8;

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private BoardValidator() {
    }

    /**
     * Checks the structure of a board definition.
     *
     * @param definition the definition to check
     * @return the errors and warnings found; a definition without errors can be converted to a
     *         {@link Board}
     */
    public static ValidationResult validate(BoardDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (definition.formatVersion() != BoardDefinition.FORMAT_VERSION) {
            errors.add("Unsupported formatVersion " + definition.formatVersion() + " (this version reads "
                + BoardDefinition.FORMAT_VERSION + ")");
        }
        if (isBlank(definition.id())) {
            errors.add("The board needs an id");
        }
        if (isBlank(definition.name())) {
            errors.add("The board needs a name");
        }
        int width = definition.width();
        int height = definition.height();
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) {
            errors.add("Board size must be between 1x1 and " + MAX_SIZE + "x" + MAX_SIZE + ", was " + width + "x" + height);
            return new ValidationResult(errors, warnings);
        }

        Set<Position> seenSquares = new HashSet<>();
        for (BoardDefinition.Square square : definition.squares()) {
            Position position = new Position(square.x(), square.y());
            checkInBounds(position, width, height, "Square", errors);
            if (!seenSquares.add(position)) {
                errors.add("Square " + position + " is listed more than once");
            }
            if (square.belt() != null && square.belt().dir() == null) {
                errors.add("The belt on " + position + " has no direction");
            }
            if (square.feature() == SquareFeature.NONE) {
                errors.add("Square " + position + " has the feature NONE; leave the feature out for plain floor");
            }
            if (square.belt() == null && square.feature() == null) {
                warnings.add("Square " + position + " has neither a belt nor a feature");
            }
            if (square.feature() == SquareFeature.CRUSHER) {
                checkRegisters(square.registers(), "The crusher on " + position, errors);
            } else if (!square.registers().isEmpty()) {
                errors.add("Square " + position + " lists registers but has no crusher");
            }
        }

        Set<String> mounts = new HashSet<>();
        for (BoardDefinition.Edge edge : definition.edges()) {
            Position position = new Position(edge.x(), edge.y());
            String where = "Edge " + position + " " + edge.side();
            checkInBounds(position, width, height, "Edge", errors);
            if (edge.side() == null) {
                errors.add("The edge on " + position + " has no side");
                continue;
            }
            if (!edge.wall() && edge.laser() == null && edge.pusher() == null) {
                errors.add(where + " has no wall, laser or pusher");
            }
            if (edge.laser() != null && edge.pusher() != null) {
                errors.add(where + " has both a laser and a pusher");
            }
            if (edge.laser() != null && (edge.laser().beams() < 1 || edge.laser().beams() > 3)) {
                errors.add("The laser on " + where + " must have 1 to 3 beams, has " + edge.laser().beams());
            }
            if (edge.pusher() != null) {
                checkRegisters(edge.pusher().registers(), "The pusher on " + where, errors);
            }
            if ((edge.laser() != null || edge.pusher() != null) && !mounts.add(position + "" + edge.side())) {
                errors.add(where + " has more than one laser or pusher");
            }
        }

        if (definition.flags().isEmpty()) {
            errors.add("The board needs at least one flag");
        }
        Set<Position> seenFlags = new HashSet<>();
        for (int index = 0; index < definition.flags().size(); index++) {
            BoardDefinition.Flag flag = definition.flags().get(index);
            Position position = new Position(flag.x(), flag.y());
            checkInBounds(position, width, height, "Flag " + (index + 1), errors);
            if (!seenFlags.add(position)) {
                errors.add("Flag " + (index + 1) + " shares its square " + position + " with another flag");
            }
        }

        if (definition.startSquares().isEmpty()) {
            errors.add("The board needs at least one start square");
        }
        if (definition.startSquares().size() > MAX_START_SQUARES) {
            errors.add("A board has at most " + MAX_START_SQUARES + " start squares, this one has "
                + definition.startSquares().size());
        }
        Set<Position> seenStarts = new HashSet<>();
        for (BoardDefinition.Start start : definition.startSquares()) {
            Position position = new Position(start.x(), start.y());
            checkInBounds(position, width, height, "Start square", errors);
            if (start.facing() == null) {
                errors.add("The start square " + position + " has no facing");
            }
            if (!seenStarts.add(position)) {
                errors.add("Start square " + position + " is listed more than once");
            }
        }
        return new ValidationResult(errors, warnings);
    }

    /**
     * Checks whether a finished board is playable.
     * <p>
     * Errors: no flag or no start square, too many start squares, two flags or two start squares
     * on one square, a flag or start square on a pit, and any flag or start square that cannot be
     * reached from the first start square by walking over squares that are not pits and are
     * not separated by walls. Because walls and pits work the same in both directions, that is the
     * same as every flag being reachable from every start square.
     * <p>
     * Warnings: a flag or start square on a belt or a crusher, a start square on a flag, and
     * every belt that leads off the board, into a pit or into a wall.
     *
     * @param board the board to check
     * @return the errors and warnings found
     */
    public static ValidationResult validate(Board board) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Position> flags = board.flags();
        List<StartSquare> starts = board.startSquares();

        if (flags.isEmpty()) {
            errors.add("The board needs at least one flag");
        }
        if (starts.isEmpty()) {
            errors.add("The board needs at least one start square");
        }
        if (starts.size() > MAX_START_SQUARES) {
            errors.add("A board has at most " + MAX_START_SQUARES + " start squares, this one has " + starts.size());
        }
        if (new HashSet<>(flags).size() != flags.size()) {
            errors.add("Two flags share a square");
        }
        Set<Position> startPositions = new HashSet<>();
        for (StartSquare start : starts) {
            if (!startPositions.add(start.position())) {
                errors.add("Two start squares share the square " + start.position());
            }
        }

        for (int index = 0; index < flags.size(); index++) {
            Position flag = flags.get(index);
            String label = "Flag " + (index + 1) + " on " + flag;
            if (board.isPit(flag)) {
                errors.add(label + " is on a pit");
            }
            checkSpecialSquare(board, flag, label, warnings);
        }
        for (StartSquare start : starts) {
            String label = "The start square " + start.position();
            if (board.isPit(start.position())) {
                errors.add(label + " is on a pit");
            }
            if (flags.contains(start.position())) {
                warnings.add(label + " is on a flag");
            }
            checkSpecialSquare(board, start.position(), label, warnings);
        }

        board.belts().forEach((position, belt) -> {
            Position target = position.step(belt.direction());
            String label = "The belt on " + position + " pointing " + belt.direction();
            if (board.hasWall(position, belt.direction())) {
                warnings.add(label + " runs into a wall");
            } else if (!board.inBounds(target)) {
                warnings.add(label + " carries robots off the board");
            } else if (board.isPit(target)) {
                warnings.add(label + " carries robots into a pit");
            }
        });

        if (!starts.isEmpty() && !board.isPit(starts.get(0).position())) {
            Set<Position> reachable = reachableFrom(board, starts.get(0).position());
            for (int index = 0; index < flags.size(); index++) {
                if (!reachable.contains(flags.get(index))) {
                    errors.add("Flag " + (index + 1) + " on " + flags.get(index) + " cannot be reached from the start squares");
                }
            }
            for (StartSquare start : starts) {
                if (!reachable.contains(start.position())) {
                    errors.add("The start square " + start.position() + " is cut off from the first start square");
                }
            }
        }
        return new ValidationResult(errors, warnings);
    }

    /**
     * Finds every square that can be walked to from a square: any neighbour that is on the board,
     * not a pit and not behind a wall.
     *
     * @param board the board
     * @param start the square to start from
     * @return the reachable squares, including the start
     */
    private static Set<Position> reachableFrom(Board board, Position start) {
        Set<Position> seen = new HashSet<>();
        Queue<Position> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (Direction direction : Direction.values()) {
                Position next = current.step(direction);
                if (board.inBounds(next) && !board.isPit(next) && !board.hasWall(current, direction) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * Warns if a flag or start square sits on a belt or a crusher.
     *
     * @param board    the board
     * @param position the square
     * @param label    how to name the square in a message
     * @param warnings receives the warnings
     */
    private static void checkSpecialSquare(Board board, Position position, String label, List<String> warnings) {
        if (board.beltAt(position).isPresent()) {
            warnings.add(label + " is on a belt");
        }
        if (board.featureAt(position) == SquareFeature.CRUSHER) {
            warnings.add(label + " is on a crusher");
        }
    }

    /**
     * Reports a position that lies outside the grid.
     *
     * @param position the position
     * @param width    the board width
     * @param height   the board height
     * @param what     what the position belongs to, for the message
     * @param errors   receives the error
     */
    private static void checkInBounds(Position position, int width, int height, String what, List<String> errors) {
        if (position.x() < 0 || position.x() >= width || position.y() < 0 || position.y() >= height) {
            errors.add(what + " " + position + " is outside the " + width + "x" + height + " board");
        }
    }

    /**
     * Reports a register list that is empty or holds a number outside 1 to 5.
     *
     * @param registers the registers
     * @param what      what the registers belong to, for the message
     * @param errors    receives the errors
     */
    private static void checkRegisters(List<Integer> registers, String what, List<String> errors) {
        if (registers.isEmpty()) {
            errors.add(what + " is active in no register");
        }
        for (Integer register : registers) {
            if (register == null || register < 1 || register > 5) {
                errors.add(what + " lists the invalid register " + register);
            }
        }
    }

    /**
     * Returns whether a text is missing or blank.
     *
     * @param text the text
     * @return {@code true} for {@code null} or whitespace only
     */
    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
