package de.mkoehler.robotrampage.testsupport;

import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.SquareFeature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renders a board definition as the text picture shown in design.md 2.11, so the picture in the
 * document is <em>generated from</em> the board file and can never silently drift from it (see
 * {@code DesignDocPictureTest}).
 * <p>
 * North is at the top; columns are numbered {@code 0} to {@code B} (hexadecimal style, so every
 * column is one character wide). A {@code |} between two squares is a wall, and a {@code -} on a line
 * between two rows is a wall between the square above it and the one below. Characters:
 * <pre>
 *   @  start square      1 2 3 ...  flags       L  laser mounted on that square's edge
 *   o  pit               &gt; &lt; ^ v    normal belt    E W N S    express belt
 *   c  clockwise gear    a  counter-clockwise gear     +  repair site      x  crusher
 *   P  pusher mounted on that square's edge
 * </pre>
 * Later entries in that list win when several things share a square (a laser hides the belt or
 * feature under it), which is fine for the boards so far; the picture is a reading aid, not a
 * lossless encoding — a pusher's own wall (implied, not listed separately, design.md 2.9) does not
 * get a {@code |}/{@code -} of its own either, exactly like a laser's: only the mounted square is
 * marked, not which side it is mounted on.
 *
 * @author Mario Koehler
 */
public final class BoardPicture {

    private static final String COLUMN_LABELS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Not instantiable; this class only exposes a static method.
     */
    private BoardPicture() {
    }

    /**
     * Renders a board definition.
     *
     * @param board the definition, at most 36 columns wide
     * @return the picture, lines separated by {@code \n}, without a trailing newline
     */
    public static String render(BoardDefinition board) {
        int width = board.width();
        int height = board.height();
        Map<String, Character> cells = new HashMap<>();
        for (BoardDefinition.Square square : board.squares()) {
            char c = '.';
            if (square.belt() != null) {
                c = beltCharacter(square.belt().dir(), square.belt().express());
            }
            if (square.feature() == SquareFeature.PIT) {
                c = 'o';
            } else if (square.feature() == SquareFeature.GEAR_CLOCKWISE) {
                c = 'c';
            } else if (square.feature() == SquareFeature.GEAR_COUNTERCLOCKWISE) {
                c = 'a';
            } else if (square.feature() == SquareFeature.REPAIR) {
                c = '+';
            } else if (square.feature() == SquareFeature.CRUSHER) {
                c = 'x';
            }
            cells.put(key(square.x(), square.y()), c);
        }
        for (int index = 0; index < board.flags().size(); index++) {
            BoardDefinition.Flag flag = board.flags().get(index);
            cells.put(key(flag.x(), flag.y()), Character.forDigit(index + 1, 10));
        }
        for (BoardDefinition.Start start : board.startSquares()) {
            cells.put(key(start.x(), start.y()), '@');
        }
        Set<String> eastWalls = new HashSet<>();
        Set<String> southWalls = new HashSet<>();
        for (BoardDefinition.Edge edge : board.edges()) {
            if (edge.laser() != null) {
                cells.put(key(edge.x(), edge.y()), 'L');
            }
            if (edge.pusher() != null) {
                cells.put(key(edge.x(), edge.y()), 'P');
            }
            if (!edge.wall()) {
                continue;
            }
            switch (edge.side()) {
                case EAST -> eastWalls.add(key(edge.x(), edge.y()));
                case WEST -> eastWalls.add(key(edge.x() - 1, edge.y()));
                case SOUTH -> southWalls.add(key(edge.x(), edge.y()));
                case NORTH -> southWalls.add(key(edge.x(), edge.y() + 1));
            }
        }

        StringBuilder out = new StringBuilder("      ");
        for (int x = 0; x < width; x++) {
            out.append(x > 0 ? " " : "").append(COLUMN_LABELS.charAt(x));
        }
        for (int y = height - 1; y >= 0; y--) {
            out.append('\n').append(String.format(" y%-3d ", y));
            for (int x = 0; x < width; x++) {
                out.append(cells.getOrDefault(key(x, y), '.'));
                if (x < width - 1) {
                    out.append(eastWalls.contains(key(x, y)) ? '|' : ' ');
                }
            }
            if (y > 0) {
                StringBuilder under = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    under.append(southWalls.contains(key(x, y)) ? '-' : ' ');
                    if (x < width - 1) {
                        under.append(' ');
                    }
                }
                if (!under.toString().isBlank()) {
                    out.append('\n').append("      ").append(under.toString().stripTrailing());
                }
            }
        }
        return out.toString();
    }

    /**
     * Picks the character for a belt.
     *
     * @param direction the belt's direction
     * @param express   whether it is an express belt
     * @return an arrow for a normal belt, a compass letter for an express belt
     */
    private static char beltCharacter(Direction direction, boolean express) {
        return switch (direction) {
            case NORTH -> express ? 'N' : '^';
            case EAST -> express ? 'E' : '>';
            case SOUTH -> express ? 'S' : 'v';
            case WEST -> express ? 'W' : '<';
        };
    }

    /**
     * Builds a lookup key for a square.
     *
     * @param x the column
     * @param y the row
     * @return a string identifying the square
     */
    private static String key(int x, int y) {
        return x + "," + y;
    }
}
