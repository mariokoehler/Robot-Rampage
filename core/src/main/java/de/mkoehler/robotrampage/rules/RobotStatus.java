package de.mkoehler.robotrampage.rules;

/**
 * Where a robot is in its life cycle (design.md 2.2).
 *
 * @author Mario Koehler
 */
public enum RobotStatus {

    /**
     * On the board and taking part in the game.
     */
    ACTIVE,

    /**
     * Destroyed this game turn or earlier; off the board and waiting to re-enter
     * at the start of the next turn.
     */
    DESTROYED,

    /**
     * Out of lives; takes no further part in the game.
     */
    ELIMINATED
}
