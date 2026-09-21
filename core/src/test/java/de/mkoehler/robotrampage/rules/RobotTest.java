package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how a {@link Robot}'s damage translates into hand size and locked
 * registers (design.md 2.5), and that copies are independent.
 *
 * @author Mario Koehler
 */
class RobotTest {

    /**
     * Creates an undamaged robot for a test.
     *
     * @return a robot at the origin facing north
     */
    private static Robot robot() {
        return new Robot(0, new Position(0, 0), Direction.NORTH);
    }

    /**
     * An undamaged robot gets nine cards, and each damage point takes one away.
     */
    @Test
    void handSizeIsNineMinusDamage() {
        Robot robot = robot();
        assertEquals(9, robot.handSize());
        robot.setDamage(4);
        assertEquals(5, robot.handSize());
        robot.setDamage(9);
        assertEquals(0, robot.handSize());
    }

    /**
     * No register is locked up to 4 damage; from 5 damage one more locks per point, up
     * to all five at 9 damage.
     */
    @Test
    void registersLockFromFiveDamageUpwards() {
        Robot robot = robot();
        int[][] damageToLocked = {{0, 0}, {4, 0}, {5, 1}, {6, 2}, {7, 3}, {8, 4}, {9, 5}};
        for (int[] pair : damageToLocked) {
            robot.setDamage(pair[0]);
            assertEquals(pair[1], robot.lockedRegisterCount(), "locked registers at damage " + pair[0]);
        }
    }

    /**
     * The locked registers are the highest-numbered ones: at 7 damage registers 3, 4 and 5
     * (indexes 2 to 4) are locked.
     */
    @Test
    void lockedRegistersAreTheHighestNumberedOnes() {
        Robot robot = robot();
        robot.setDamage(7);

        assertFalse(robot.isRegisterLocked(0));
        assertFalse(robot.isRegisterLocked(1));
        assertTrue(robot.isRegisterLocked(2));
        assertTrue(robot.isRegisterLocked(3));
        assertTrue(robot.isRegisterLocked(4));
    }

    /**
     * Repairing one damage point unlocks the lowest-numbered locked register first.
     */
    @Test
    void repairingUnlocksTheLowestNumberedLockedRegister() {
        Robot robot = robot();
        robot.setDamage(7);
        assertTrue(robot.isRegisterLocked(2));

        robot.setDamage(6);

        assertFalse(robot.isRegisterLocked(2));
        assertTrue(robot.isRegisterLocked(3));
        assertTrue(robot.isRegisterLocked(4));
    }

    /**
     * A new robot starts with full lives, on its own archive marker, active and undamaged.
     */
    @Test
    void newRobotStartsUndamagedOnItsArchiveMarker() {
        Robot robot = new Robot(4, new Position(2, 3), Direction.EAST);

        assertEquals(4, robot.id());
        assertEquals(Robot.STARTING_LIVES, robot.lives());
        assertEquals(0, robot.damage());
        assertEquals(new Position(2, 3), robot.archiveMarker());
        assertTrue(robot.isActive());
    }

    /**
     * A copy carries all state but changing it must not affect the original, including its
     * registers.
     */
    @Test
    void copyIsIndependentOfTheOriginal() {
        Robot original = robot();
        original.setRegister(1, new Card(CardType.MOVE_1, 10));
        original.setDamage(3);

        Robot copy = original.copy();
        copy.setRegister(1, new Card(CardType.U_TURN, 20));
        copy.setDamage(8);
        copy.setPosition(new Position(5, 5));

        assertNotSame(original, copy);
        assertEquals(new Card(CardType.MOVE_1, 10), original.register(1));
        assertEquals(3, original.damage());
        assertEquals(new Position(0, 0), original.position());
    }
}
