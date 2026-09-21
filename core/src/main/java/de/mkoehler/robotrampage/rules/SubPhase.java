package de.mkoehler.robotrampage.rules;

/**
 * The sub-phases every register is resolved in, in the order they happen
 * (design.md 2.4), plus {@link #CLEANUP} for everything after the fifth register.
 * <p>
 * Every {@link LoggedEvent} records the sub-phase it happened in, so a client can
 * pace its animation at phase boundaries and tests can assert what happened in
 * which phase.
 *
 * @author Mario Koehler
 */
public enum SubPhase {

    /**
     * Start of a turn, before any register: destroyed robots re-enter the board. Not part of
     * any register.
     */
    RESPAWN,

    /**
     * Every robot's card for the register is revealed.
     */
    REVEAL,

    /**
     * Robots execute their cards in descending priority order.
     */
    ROBOT_MOVEMENT,

    /**
     * Express belts move robots one square.
     */
    EXPRESS_BELTS,

    /**
     * Express and normal belts move robots one square.
     */
    ALL_BELTS,

    /**
     * Active pushers push the robots on their squares.
     */
    PUSHERS,

    /**
     * Gears rotate the robots on them.
     */
    GEARS,

    /**
     * Board and robot lasers fire.
     */
    LASERS,

    /**
     * Active crushers destroy the robots on them.
     */
    CRUSHERS,

    /**
     * Robots standing on their next flag touch it.
     */
    CHECKPOINTS,

    /**
     * End of turn: repairs, power-down, discarding. Not part of any register.
     */
    CLEANUP
}
