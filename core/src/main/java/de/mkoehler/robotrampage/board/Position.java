package de.mkoehler.robotrampage.board;

/**
 * A square on the board, identified by its integer coordinates (design.md 2.1).
 * The origin is the south-west corner; {@code x} grows east and {@code y} grows
 * north.
 * <p>
 * A {@code Position} is not tied to a particular {@link Board} and may lie
 * outside of it &mdash; for example the target of a step that leaves the board;
 * use {@link Board#inBounds(Position)} to tell.
 *
 * @param x the column, growing east
 * @param y the row, growing north
 * @author Mario Koehler
 */
public record Position(int x, int y) {

    /**
     * Returns the square one step away in the given direction.
     *
     * @param direction the direction to step in
     * @return the neighbouring position, which may be off the board
     */
    public Position step(Direction direction) {
        return new Position(x + direction.dx(), y + direction.dy());
    }

    /**
     * Formats this position as {@code (x,y)}, which keeps test failure messages
     * and logs short.
     *
     * @return the coordinates in parentheses
     */
    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
