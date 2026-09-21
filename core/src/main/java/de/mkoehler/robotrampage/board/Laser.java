package de.mkoehler.robotrampage.board;

/**
 * A wall-mounted board laser (design.md 2.9).
 * <p>
 * The laser is mounted on one side of its square and fires across the board
 * from there: a laser on the {@code WEST} side of a square fires {@code EAST}.
 * Adding a laser to a {@link Board} also puts a wall on that side of the square.
 * The beam starts on the emitter's own square, so a robot standing there is hit.
 *
 * @param position the square the laser is mounted on
 * @param side     the side of that square the laser is mounted on
 * @param beams    the number of beams, i.e. the damage per hit (1 to 3)
 * @author Mario Koehler
 */
public record Laser(Position position, Direction side, int beams) {

    /**
     * Returns the direction the beam travels in.
     *
     * @return the direction opposite to the side the laser is mounted on
     */
    public Direction firingDirection() {
        return side.opposite();
    }
}
