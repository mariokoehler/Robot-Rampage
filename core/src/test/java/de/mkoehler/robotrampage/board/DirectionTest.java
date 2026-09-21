package de.mkoehler.robotrampage.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link Direction}'s rotation arithmetic, which every movement, belt and
 * gear rule builds on.
 *
 * @author Mario Koehler
 */
class DirectionTest {

    /**
     * Turning right must walk clockwise through all four directions.
     */
    @Test
    void rotateRightWalksClockwise() {
        assertEquals(Direction.EAST, Direction.NORTH.rotateRight());
        assertEquals(Direction.SOUTH, Direction.EAST.rotateRight());
        assertEquals(Direction.WEST, Direction.SOUTH.rotateRight());
        assertEquals(Direction.NORTH, Direction.WEST.rotateRight());
    }

    /**
     * Turning left must be the inverse of turning right.
     */
    @Test
    void rotateLeftInvertsRotateRight() {
        for (Direction direction : Direction.values()) {
            assertEquals(direction, direction.rotateRight().rotateLeft());
        }
        assertEquals(Direction.WEST, Direction.NORTH.rotateLeft());
    }

    /**
     * The opposite direction is two quarter turns away and steps back to the origin.
     */
    @Test
    void oppositeIsAHalfTurnAndCancelsAStep() {
        for (Direction direction : Direction.values()) {
            assertEquals(direction.rotateRight().rotateRight(), direction.opposite());
            Position there = new Position(3, 3).step(direction);
            assertEquals(new Position(3, 3), there.step(direction.opposite()));
        }
    }

    /**
     * North must be {@code +y} and east {@code +x}, matching the board's coordinate system.
     */
    @Test
    void stepsFollowTheBoardCoordinateSystem() {
        Position origin = new Position(0, 0);
        assertEquals(new Position(0, 1), origin.step(Direction.NORTH));
        assertEquals(new Position(1, 0), origin.step(Direction.EAST));
        assertEquals(new Position(0, -1), origin.step(Direction.SOUTH));
        assertEquals(new Position(-1, 0), origin.step(Direction.WEST));
    }

    /**
     * The clockwise quarter-turn count distinguishes right (1), around (2) and left (3).
     */
    @Test
    void clockwiseQuarterTurnsCountsTurnsToTarget() {
        assertEquals(0, Direction.NORTH.clockwiseQuarterTurnsTo(Direction.NORTH));
        assertEquals(1, Direction.NORTH.clockwiseQuarterTurnsTo(Direction.EAST));
        assertEquals(2, Direction.NORTH.clockwiseQuarterTurnsTo(Direction.SOUTH));
        assertEquals(3, Direction.NORTH.clockwiseQuarterTurnsTo(Direction.WEST));
        assertEquals(1, Direction.WEST.clockwiseQuarterTurnsTo(Direction.NORTH));
    }
}
