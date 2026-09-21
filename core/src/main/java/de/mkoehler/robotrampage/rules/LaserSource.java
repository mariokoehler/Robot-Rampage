package de.mkoehler.robotrampage.rules;

/**
 * Which kind of laser fired a beam or caused damage (design.md 2.9).
 *
 * @author Mario Koehler
 */
public enum LaserSource {

    /**
     * A wall-mounted laser of the board.
     */
    BOARD,

    /**
     * The laser every active robot fires forward from its own square.
     */
    ROBOT
}
