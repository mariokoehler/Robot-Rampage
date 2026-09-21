package de.mkoehler.robotrampage.board;

import java.util.Set;

/**
 * A wall-mounted pusher (design.md 2.4, 2.9).
 * <p>
 * The pusher is mounted on one side of its square and, in each register it is
 * active in, pushes the robot standing on that square one square away from the
 * wall it is mounted on. Adding a pusher to a {@link Board} also puts a wall on
 * that side of the square.
 *
 * @param position  the square the pusher is mounted on
 * @param side      the side of that square the pusher is mounted on
 * @param registers the registers (1 to 5) the pusher is active in
 * @author Mario Koehler
 */
public record Pusher(Position position, Direction side, Set<Integer> registers) {

    /**
     * Creates a pusher, taking an immutable copy of the register set.
     *
     * @param position  the square the pusher is mounted on
     * @param side      the side of that square the pusher is mounted on
     * @param registers the registers (1 to 5) the pusher is active in
     */
    public Pusher {
        registers = Set.copyOf(registers);
    }

    /**
     * Returns the direction robots are pushed in.
     *
     * @return the direction opposite to the side the pusher is mounted on
     */
    public Direction pushDirection() {
        return side.opposite();
    }

    /**
     * Returns whether this pusher fires in the given register.
     *
     * @param register the register number, 1 to 5
     * @return {@code true} if the pusher is active in that register
     */
    public boolean isActiveIn(int register) {
        return registers.contains(register);
    }
}
