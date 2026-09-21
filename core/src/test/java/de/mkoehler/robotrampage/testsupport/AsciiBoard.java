package de.mkoehler.robotrampage.testsupport;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Robot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test helper that builds a {@link Board} (and a {@link GameState} with robots)
 * from a small ASCII picture, so a rules test reads like the situation it
 * describes.
 * <p>
 * <b>Terrain picture.</b> One text row per board row, <em>north at the top</em>.
 * Every non-space character in a row is one square, left to right, except
 * {@code |}, which puts a wall on the east side of the square before it. A line
 * made only of spaces and {@code -} sits <em>between</em> two square rows: each
 * {@code -} must stand directly under (or above) a square character and puts a wall
 * between that square and its neighbour on the other side of the line. A wall line
 * before the first or after the last square row is an outer wall. A blank line
 * between two rows is a wall line without walls. Square characters:
 * <pre>
 *   .  plain floor            o  pit
 *   &gt; &lt; ^ v  normal belt heading east/west/north/south
 *   E W N S  express belt heading east/west/north/south
 *   c  clockwise gear         a  counter-clockwise gear
 *   +  repair site            x  crusher active in every register
 *   1-9 flag with that number (must be numbered 1..n without gaps)
 * </pre>
 * Example &mdash; a 4x2 board with a wall between the two top-left squares, a wall
 * between the top row's third square and the square below it, and an express belt:
 * <pre>
 *   . | . . .
 *       -
 *   . E E .
 * </pre>
 * <p>
 * <b>Robot picture.</b> The same grid shape, with {@code .} for an empty square
 * and a digit {@code 0}-{@code 7} for the robot with that id. Robots start facing
 * north; tests change that with {@link Robot#setFacing(Direction)}.
 *
 * @author Mario Koehler
 */
public final class AsciiBoard {

    /**
     * One row of square characters, with where each sits in the source line and
     * which squares have a wall on their east side.
     *
     * @param squares    the square characters, west to east
     * @param columns    for each square, its character index in the source line
     * @param wallsEast  for each square, whether a {@code |} follows it
     * @param sourceLine the original line, for error messages
     */
    private record Row(List<Character> squares, List<Integer> columns, List<Boolean> wallsEast, String sourceLine) {

        /**
         * Splits a source line into squares and wall markers.
         *
         * @param line the source line
         * @return the parsed row
         * @throws IllegalArgumentException if the line starts with a wall marker
         */
        static Row parse(String line) {
            List<Character> squares = new ArrayList<>();
            List<Integer> columns = new ArrayList<>();
            List<Boolean> wallsEast = new ArrayList<>();
            for (int index = 0; index < line.length(); index++) {
                char c = line.charAt(index);
                if (c == ' ') {
                    continue;
                }
                if (c == '|') {
                    if (squares.isEmpty()) {
                        throw new IllegalArgumentException("Wall marker before the first square in '" + line + "'");
                    }
                    wallsEast.set(wallsEast.size() - 1, true);
                } else {
                    squares.add(c);
                    columns.add(index);
                    wallsEast.add(false);
                }
            }
            return new Row(squares, columns, wallsEast, line);
        }

        /**
         * Finds the square standing at a character index of the source line.
         *
         * @param index the character index
         * @return the square's column number
         * @throws IllegalArgumentException if no square stands at that index
         */
        int columnAt(int index) {
            int column = columns.indexOf(index);
            if (column < 0) {
                throw new IllegalArgumentException("No square at character " + index + " in '" + sourceLine + "'");
            }
            return column;
        }
    }

    /**
     * Not instantiable; this class only exposes static helpers.
     */
    private AsciiBoard() {
    }

    /**
     * Builds a board from a terrain picture.
     *
     * @param terrain the terrain picture, see the class documentation
     * @return the board
     * @throws IllegalArgumentException if the picture is malformed
     */
    public static Board board(String terrain) {
        return board(terrain, builder -> {
        });
    }

    /**
     * Builds a board from a terrain picture and then lets the caller add whatever has no
     * picture character, such as lasers, pushers or crushers active in specific registers.
     *
     * @param terrain the terrain picture, see the class documentation
     * @param extras  called with the builder after the picture has been applied
     * @return the board
     * @throws IllegalArgumentException if the picture is malformed
     */
    public static Board board(String terrain, Consumer<Board.Builder> extras) {
        List<Row> rows = new ArrayList<>();
        List<String> wallLineAfterRow = new ArrayList<>();
        String wallLineBeforeFirstRow = null;
        String pendingWallLine = null;
        for (String line : trimBlankEnds(terrain)) {
            if (isWallLine(line)) {
                if (pendingWallLine != null) {
                    throw new IllegalArgumentException("Two wall lines in a row: '" + pendingWallLine + "' and '" + line + "'");
                }
                pendingWallLine = line;
            } else {
                if (rows.isEmpty()) {
                    wallLineBeforeFirstRow = pendingWallLine;
                } else {
                    wallLineAfterRow.add(pendingWallLine);
                }
                pendingWallLine = null;
                rows.add(Row.parse(line));
            }
        }
        wallLineAfterRow.add(pendingWallLine);

        int height = rows.size();
        int width = rows.stream().mapToInt(row -> row.squares().size()).max().orElse(0);
        if (height == 0 || width == 0) {
            throw new IllegalArgumentException("Terrain picture is empty");
        }

        Board.Builder builder = new Board.Builder(width, height);
        Map<Integer, Position> flags = new TreeMap<>();
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            Row row = rows.get(rowIndex);
            if (row.squares().size() != width) {
                throw new IllegalArgumentException("Row has " + row.squares().size() + " squares, expected " + width
                    + ": '" + row.sourceLine() + "'");
            }
            int y = height - 1 - rowIndex;
            for (int column = 0; column < width; column++) {
                Position position = new Position(column, y);
                placeSquare(builder, flags, position, row.squares().get(column));
                if (row.wallsEast().get(column)) {
                    builder.wall(position, Direction.EAST);
                }
            }
        }
        addHorizontalWalls(builder, wallLineBeforeFirstRow, rows.get(0), height - 1, Direction.NORTH);
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            addHorizontalWalls(builder, wallLineAfterRow.get(rowIndex), rows.get(rowIndex), height - 1 - rowIndex, Direction.SOUTH);
        }

        int expected = 1;
        for (Map.Entry<Integer, Position> flag : flags.entrySet()) {
            if (flag.getKey() != expected++) {
                throw new IllegalArgumentException("Flags must be numbered 1..n without gaps, found " + flags.keySet());
            }
            builder.flag(flag.getValue());
        }
        extras.accept(builder);
        return builder.build();
    }

    /**
     * Builds a game state from a terrain picture and a robot picture, with a
     * standard shuffled deck (seed 1).
     *
     * @param terrain the terrain picture, see the class documentation
     * @param robots  the robot picture, see the class documentation
     * @return the game state; robots are ordered by id and face north
     * @throws IllegalArgumentException if either picture is malformed
     */
    public static GameState state(String terrain, String robots) {
        return state(terrain, robots, builder -> {
        });
    }

    /**
     * Like {@link #state(String, String)}, but lets the caller add board elements that have
     * no picture character (see {@link #board(String, Consumer)}).
     *
     * @param terrain the terrain picture, see the class documentation
     * @param robots  the robot picture, see the class documentation
     * @param extras  called with the builder after the terrain picture has been applied
     * @return the game state; robots are ordered by id and face north
     * @throws IllegalArgumentException if either picture is malformed
     */
    public static GameState state(String terrain, String robots, Consumer<Board.Builder> extras) {
        Board board = board(terrain, extras);
        List<String> lines = trimBlankEnds(robots).stream().filter(line -> !line.isBlank()).toList();
        if (lines.size() != board.height()) {
            throw new IllegalArgumentException("Robot picture has " + lines.size() + " rows, board has " + board.height());
        }
        Map<Integer, Position> positions = new TreeMap<>();
        for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
            Row row = Row.parse(lines.get(rowIndex));
            if (row.squares().size() != board.width()) {
                throw new IllegalArgumentException("Robot row has " + row.squares().size() + " squares, board is "
                    + board.width() + " wide: '" + row.sourceLine() + "'");
            }
            for (int column = 0; column < board.width(); column++) {
                char c = row.squares().get(column);
                if (c == '.') {
                    continue;
                }
                if (c < '0' || c > '7') {
                    throw new IllegalArgumentException("Bad robot character '" + c + "' in '" + row.sourceLine() + "'");
                }
                positions.put(c - '0', new Position(column, board.height() - 1 - rowIndex));
            }
        }
        List<Robot> robotList = new ArrayList<>();
        positions.forEach((id, position) -> robotList.add(new Robot(id, position, Direction.NORTH)));
        return new GameState(board, robotList, Deck.standard(1L));
    }

    /**
     * Asserts that the active robots stand exactly where a robot picture says.
     * Robots that are not active (destroyed, eliminated) count as absent.
     *
     * @param state    the game state to check
     * @param expected the expected robot picture, same grid shape as the board
     */
    public static void assertRobots(GameState state, String expected) {
        assertEquals(normalizeRobotPicture(expected), renderRobots(state));
    }

    /**
     * Renders the active robots of a state as a robot picture, one space between
     * squares and no trailing whitespace.
     *
     * @param state the game state
     * @return the picture, rows separated by newlines, north at the top
     */
    public static String renderRobots(GameState state) {
        Board board = state.board();
        StringBuilder out = new StringBuilder();
        for (int y = board.height() - 1; y >= 0; y--) {
            if (out.length() > 0) {
                out.append('\n');
            }
            for (int x = 0; x < board.width(); x++) {
                if (x > 0) {
                    out.append(' ');
                }
                Position position = new Position(x, y);
                out.append(state.robotAt(position).map(robot -> (char) ('0' + robot.id())).orElse('.'));
            }
        }
        return out.toString();
    }

    /**
     * Re-renders a robot picture in canonical form (single spaces, no blank lines)
     * so it can be compared with {@link #renderRobots(GameState)}.
     *
     * @param picture a robot picture as written in a test
     * @return the canonical form
     */
    private static String normalizeRobotPicture(String picture) {
        List<String> lines = new ArrayList<>();
        for (String line : trimBlankEnds(picture)) {
            if (line.isBlank()) {
                continue;
            }
            StringBuilder canonical = new StringBuilder();
            for (char square : Row.parse(line).squares()) {
                if (canonical.length() > 0) {
                    canonical.append(' ');
                }
                canonical.append(square);
            }
            lines.add(canonical.toString());
        }
        return String.join("\n", lines);
    }

    /**
     * Splits a picture into lines and drops blank lines from both ends.
     *
     * @param picture the picture text
     * @return the remaining lines
     */
    private static List<String> trimBlankEnds(String picture) {
        List<String> lines = new ArrayList<>(List.of(picture.split("\n", -1)));
        while (!lines.isEmpty() && lines.get(0).isBlank()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * Returns whether a line is a wall line, i.e. made only of spaces and dashes.
     * A blank line between two square rows is a wall line without any wall.
     *
     * @param line the line to classify
     * @return {@code true} for a wall line
     */
    private static boolean isWallLine(String line) {
        return line.chars().allMatch(c -> c == ' ' || c == '-');
    }

    /**
     * Adds walls to the builder for every dash in a wall line.
     *
     * @param builder  the board under construction
     * @param wallLine the wall line, or {@code null} if there is none
     * @param row      the square row the dashes are aligned with
     * @param y        the {@code y} of the square row the wall line belongs to
     * @param side     the side of that row's squares the walls go on
     * @throws IllegalArgumentException if a dash is not aligned with a square
     */
    private static void addHorizontalWalls(Board.Builder builder, String wallLine, Row row, int y, Direction side) {
        if (wallLine == null) {
            return;
        }
        for (int index = 0; index < wallLine.length(); index++) {
            if (wallLine.charAt(index) == '-') {
                builder.wall(new Position(row.columnAt(index), y), side);
            }
        }
    }

    /**
     * Puts the element a picture character stands for onto a square.
     *
     * @param builder  the board under construction
     * @param flags    collects flag numbers and their squares
     * @param position the square
     * @param c        the picture character
     * @throws IllegalArgumentException if the character is unknown
     */
    private static void placeSquare(Board.Builder builder, Map<Integer, Position> flags, Position position, char c) {
        switch (c) {
            case '.' -> {
            }
            case '>' -> builder.belt(position, Direction.EAST, false);
            case '<' -> builder.belt(position, Direction.WEST, false);
            case '^' -> builder.belt(position, Direction.NORTH, false);
            case 'v' -> builder.belt(position, Direction.SOUTH, false);
            case 'E' -> builder.belt(position, Direction.EAST, true);
            case 'W' -> builder.belt(position, Direction.WEST, true);
            case 'N' -> builder.belt(position, Direction.NORTH, true);
            case 'S' -> builder.belt(position, Direction.SOUTH, true);
            case 'o' -> builder.feature(position, SquareFeature.PIT);
            case 'c' -> builder.feature(position, SquareFeature.GEAR_CLOCKWISE);
            case 'a' -> builder.feature(position, SquareFeature.GEAR_COUNTERCLOCKWISE);
            case '+' -> builder.feature(position, SquareFeature.REPAIR);
            case 'x' -> builder.crusher(position, 1, 2, 3, 4, 5);
            default -> {
                if (c >= '1' && c <= '9') {
                    flags.put(c - '0', position);
                } else {
                    throw new IllegalArgumentException("Unknown terrain character '" + c + "' at " + position);
                }
            }
        }
    }
}
