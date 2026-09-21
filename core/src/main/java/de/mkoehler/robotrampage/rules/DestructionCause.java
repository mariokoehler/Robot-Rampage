package de.mkoehler.robotrampage.rules;

/**
 * Why a robot was destroyed (design.md 2.9).
 *
 * @author Mario Koehler
 */
public enum DestructionCause {

    /**
     * It entered a pit.
     */
    PIT,

    /**
     * It left the board over an open edge.
     */
    LEFT_BOARD,

    /**
     * It reached 10 damage.
     */
    DAMAGE,

    /**
     * It stood on an active crusher.
     */
    CRUSHER,

    /**
     * Its player left the game for good, for example because they stayed disconnected past the
     * reconnect grace period (design.md 2.13).
     */
    FORFEIT
}
