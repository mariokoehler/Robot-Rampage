package de.mkoehler.robotrampage.rules;

/**
 * Why a robot changed square, so a client can animate it appropriately.
 *
 * @author Mario Koehler
 */
public enum MoveCause {

    /**
     * The robot walked, following its programming card.
     */
    CARD,

    /**
     * Another robot pushed it while moving.
     */
    PUSHED,

    /**
     * A conveyor belt carried it.
     */
    BELT,

    /**
     * A wall-mounted pusher shoved it.
     */
    PUSHER
}
