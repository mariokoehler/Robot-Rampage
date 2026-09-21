package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

import java.util.Arrays;

/**
 * The mutable state of one robot (design.md 2.2).
 * <p>
 * A robot is identified by its {@link #id()}, which stays the same for the whole
 * game: destroyed robots keep their id while they wait to re-enter, and events
 * always refer to robots by id, never by index or object identity.
 * <p>
 * The rules engine mutates robots while resolving a turn, which is why this is a
 * plain mutable class; resolution always works on a {@link GameState#copy() copy}.
 *
 * @author Mario Koehler
 */
public final class Robot {

    /**
     * Damage at which a robot is destroyed.
     */
    public static final int DESTRUCTION_DAMAGE = 10;

    /**
     * Number of registers in a program.
     */
    public static final int REGISTER_COUNT = 5;

    /**
     * Number of cards dealt to an undamaged robot.
     */
    public static final int FULL_HAND_SIZE = 9;

    /**
     * Number of lives a robot starts the game with.
     */
    public static final int STARTING_LIVES = 3;

    private final int id;
    private Position position;
    private Direction facing;
    private int damage;
    private int lives = STARTING_LIVES;
    private int flagsTouched;
    private Position archiveMarker;
    private final Card[] registers = new Card[REGISTER_COUNT];
    private boolean poweredDown;
    private boolean powerDownAnnounced;
    private RobotStatus status = RobotStatus.ACTIVE;
    private int destructionOrder;

    /**
     * Creates an undamaged robot with full lives, standing on its own archive
     * marker.
     *
     * @param id       the robot's stable id
     * @param position the square it starts on, which is also its first archive marker
     * @param facing   the direction it starts facing
     */
    public Robot(int id, Position position, Direction facing) {
        this.id = id;
        this.position = position;
        this.facing = facing;
        this.archiveMarker = position;
    }

    /**
     * Returns a deep copy of this robot.
     *
     * @return an independent copy with identical state
     */
    public Robot copy() {
        Robot copy = new Robot(id, position, facing);
        copy.damage = damage;
        copy.lives = lives;
        copy.flagsTouched = flagsTouched;
        copy.archiveMarker = archiveMarker;
        System.arraycopy(registers, 0, copy.registers, 0, REGISTER_COUNT);
        copy.poweredDown = poweredDown;
        copy.powerDownAnnounced = powerDownAnnounced;
        copy.status = status;
        copy.destructionOrder = destructionOrder;
        return copy;
    }

    /**
     * Returns the robot's stable id.
     *
     * @return the id
     */
    public int id() {
        return id;
    }

    /**
     * Returns the square the robot is on.
     *
     * @return the position, or {@code null} while the robot is not on the board
     */
    public Position position() {
        return position;
    }

    /**
     * Places the robot on a square, or takes it off the board.
     *
     * @param position the new position, or {@code null} to remove it from the board
     */
    public void setPosition(Position position) {
        this.position = position;
    }

    /**
     * Returns the direction the robot faces.
     *
     * @return the facing
     */
    public Direction facing() {
        return facing;
    }

    /**
     * Sets the direction the robot faces.
     *
     * @param facing the new facing
     */
    public void setFacing(Direction facing) {
        this.facing = facing;
    }

    /**
     * Returns the robot's damage.
     *
     * @return the number of damage tokens, 0 to 9 for a living robot
     */
    public int damage() {
        return damage;
    }

    /**
     * Sets the robot's damage.
     *
     * @param damage the new number of damage tokens
     */
    public void setDamage(int damage) {
        this.damage = damage;
    }

    /**
     * Returns the robot's remaining lives.
     *
     * @return the number of life tokens left
     */
    public int lives() {
        return lives;
    }

    /**
     * Sets the robot's remaining lives.
     *
     * @param lives the new number of life tokens
     */
    public void setLives(int lives) {
        this.lives = lives;
    }

    /**
     * Returns the number of the highest flag this robot has touched.
     *
     * @return {@code 0} if none, otherwise the flag number; the next flag to
     *         touch is {@code flagsTouched() + 1}
     */
    public int flagsTouched() {
        return flagsTouched;
    }

    /**
     * Sets the number of the highest flag touched.
     *
     * @param flagsTouched the flag number, 0 for none
     */
    public void setFlagsTouched(int flagsTouched) {
        this.flagsTouched = flagsTouched;
    }

    /**
     * Returns the square this robot returns to when destroyed.
     *
     * @return the archive marker position
     */
    public Position archiveMarker() {
        return archiveMarker;
    }

    /**
     * Moves the archive marker.
     *
     * @param archiveMarker the new archive marker position
     */
    public void setArchiveMarker(Position archiveMarker) {
        this.archiveMarker = archiveMarker;
    }

    /**
     * Returns the card programmed into a register.
     *
     * @param index the register index, 0 to 4 (register 1 is index 0)
     * @return the card, or {@code null} if the register is empty
     */
    public Card register(int index) {
        return registers[index];
    }

    /**
     * Programs a register.
     *
     * @param index the register index, 0 to 4 (register 1 is index 0)
     * @param card  the card to place there, or {@code null} to empty it
     */
    public void setRegister(int index, Card card) {
        registers[index] = card;
    }

    /**
     * Returns whether the robot is shut down this turn.
     *
     * @return {@code true} if powered down
     */
    public boolean isPoweredDown() {
        return poweredDown;
    }

    /**
     * Sets whether the robot is shut down this turn.
     *
     * @param poweredDown {@code true} to power the robot down
     */
    public void setPoweredDown(boolean poweredDown) {
        this.poweredDown = poweredDown;
    }

    /**
     * Returns whether the player announced a power-down for the next turn.
     *
     * @return {@code true} if a power-down is announced
     */
    public boolean isPowerDownAnnounced() {
        return powerDownAnnounced;
    }

    /**
     * Sets whether a power-down is announced for the next turn.
     *
     * @param powerDownAnnounced {@code true} if announced
     */
    public void setPowerDownAnnounced(boolean powerDownAnnounced) {
        this.powerDownAnnounced = powerDownAnnounced;
    }

    /**
     * Returns the robot's life-cycle status.
     *
     * @return the status
     */
    public RobotStatus status() {
        return status;
    }

    /**
     * Sets the robot's life-cycle status.
     *
     * @param status the new status
     */
    public void setStatus(RobotStatus status) {
        this.status = status;
    }

    /**
     * Returns when this robot was last destroyed, relative to all other destructions of the
     * game: a smaller number means destroyed earlier. Decides who gets the archive square
     * first when several robots re-enter at once (design.md 2.9).
     *
     * @return the destruction sequence number, meaningful only while the robot is
     *         {@link RobotStatus#DESTROYED}
     */
    public int destructionOrder() {
        return destructionOrder;
    }

    /**
     * Records when this robot was destroyed.
     *
     * @param destructionOrder the sequence number from {@link GameState#nextDestructionOrder()}
     */
    public void setDestructionOrder(int destructionOrder) {
        this.destructionOrder = destructionOrder;
    }

    /**
     * Returns whether the robot is on the board and playing.
     *
     * @return {@code true} if {@link RobotStatus#ACTIVE}
     */
    public boolean isActive() {
        return status == RobotStatus.ACTIVE;
    }

    /**
     * Returns how many registers are locked by damage (design.md 2.5): none up to
     * 4 damage, then one more per damage point, all five at 9 damage.
     * <p>
     * The locked registers are always the highest-numbered ones, i.e. register 5
     * down to register {@code 6 - lockedRegisterCount()}.
     *
     * @return the number of locked registers, 0 to 5
     */
    public int lockedRegisterCount() {
        return Math.min(REGISTER_COUNT, Math.max(0, damage - 4));
    }

    /**
     * Returns whether a register is locked by damage.
     *
     * @param index the register index, 0 to 4 (register 1 is index 0)
     * @return {@code true} if that register keeps its card between turns
     */
    public boolean isRegisterLocked(int index) {
        return index >= REGISTER_COUNT - lockedRegisterCount();
    }

    /**
     * Returns how many cards this robot is dealt at the start of a turn
     * (design.md 2.5): nine minus its damage.
     *
     * @return the hand size, never negative
     */
    public int handSize() {
        return Math.max(0, FULL_HAND_SIZE - damage);
    }

    /**
     * Formats the robot for test failure messages and logs.
     *
     * @return a short description of id, position, facing and damage
     */
    @Override
    public String toString() {
        return "Robot#" + id + "@" + position + " " + facing + " dmg=" + damage + " " + status
            + " regs=" + Arrays.toString(registers);
    }
}
