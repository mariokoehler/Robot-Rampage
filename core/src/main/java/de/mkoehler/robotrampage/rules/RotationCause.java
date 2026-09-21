package de.mkoehler.robotrampage.rules;

/**
 * Why a robot changed facing.
 *
 * @author Mario Koehler
 */
public enum RotationCause {

    /**
     * A rotate or U-turn programming card.
     */
    CARD,

    /**
     * A curved or merging conveyor belt turned the robot as it moved onto it.
     */
    BELT,

    /**
     * A gear turned the robot.
     */
    GEAR
}
