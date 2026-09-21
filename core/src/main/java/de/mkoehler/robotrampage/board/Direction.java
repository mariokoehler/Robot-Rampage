package de.mkoehler.robotrampage.board;

/**
 * One of the four compass directions a robot can face or a belt, wall or laser
 * can point in (design.md 2.1).
 * <p>
 * The board's {@code y} axis grows northwards, so {@link #NORTH} has a
 * {@code dy} of {@code +1}. Turning right (clockwise) runs
 * {@code NORTH -> EAST -> SOUTH -> WEST -> NORTH}.
 *
 * @author Mario Koehler
 */
public enum Direction {

    /**
     * Towards increasing {@code y}.
     */
    NORTH(0, 1),

    /**
     * Towards increasing {@code x}.
     */
    EAST(1, 0),

    /**
     * Towards decreasing {@code y}.
     */
    SOUTH(0, -1),

    /**
     * Towards decreasing {@code x}.
     */
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    /**
     * Creates a direction with the given unit step.
     *
     * @param dx the change in {@code x} of one step in this direction
     * @param dy the change in {@code y} of one step in this direction
     */
    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * Returns the change in {@code x} of one step in this direction.
     *
     * @return {@code -1}, {@code 0} or {@code 1}
     */
    public int dx() {
        return dx;
    }

    /**
     * Returns the change in {@code y} of one step in this direction.
     *
     * @return {@code -1}, {@code 0} or {@code 1}
     */
    public int dy() {
        return dy;
    }

    /**
     * Returns this direction rotated 90 degrees clockwise.
     *
     * @return the direction to the right of this one
     */
    public Direction rotateRight() {
        return values()[(ordinal() + 1) % 4];
    }

    /**
     * Returns this direction rotated 90 degrees counter-clockwise.
     *
     * @return the direction to the left of this one
     */
    public Direction rotateLeft() {
        return values()[(ordinal() + 3) % 4];
    }

    /**
     * Returns the direction pointing the opposite way (a 180 degree turn).
     *
     * @return the opposite direction
     */
    public Direction opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    /**
     * Returns the number of 90 degree clockwise turns needed to get from this
     * direction to {@code target}.
     *
     * @param target the direction to turn towards
     * @return {@code 0} for the same direction, {@code 1} if {@code target} is
     *         to the right, {@code 2} if it is opposite, {@code 3} if it is to
     *         the left
     */
    public int clockwiseQuarterTurnsTo(Direction target) {
        return (target.ordinal() - ordinal() + 4) % 4;
    }
}
