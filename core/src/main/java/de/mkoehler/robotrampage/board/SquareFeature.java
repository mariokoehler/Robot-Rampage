package de.mkoehler.robotrampage.board;

/**
 * A non-belt feature a square can have (design.md 2.9). A square has at most
 * one feature, and may additionally carry a {@link Belt}.
 *
 * @author Mario Koehler
 */
public enum SquareFeature {

    /**
     * Plain floor: no feature.
     */
    NONE,

    /**
     * A pit; destroys any robot that enters it.
     */
    PIT,

    /**
     * A gear that rotates robots 90 degrees clockwise each register.
     */
    GEAR_CLOCKWISE,

    /**
     * A gear that rotates robots 90 degrees counter-clockwise each register.
     */
    GEAR_COUNTERCLOCKWISE,

    /**
     * A repair site; heals a robot ending the turn on it and moves its archive
     * marker there.
     */
    REPAIR,

    /**
     * A crusher; destroys robots on it in the registers it is active in. The
     * active registers are stored separately, see
     * {@link Board#isCrusherActive(Position, int)}.
     */
    CRUSHER
}
