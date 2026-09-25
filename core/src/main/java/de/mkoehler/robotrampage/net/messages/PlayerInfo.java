package de.mkoehler.robotrampage.net.messages;

/**
 * One player as everybody sees them in the lobby and in the game. The <em>seat</em> is the
 * player's fixed position at the table: it is also the id of their robot, picks their start
 * square and colour, and never changes during a game.
 *
 * @param seat the seat, 0 to 7
 * @param name the display name
 * @param ready whether the player is ready to start (lobby)
 * @param connected whether the player is currently connected
 * @param host whether this player is the host who starts the game
 * @param bot whether the robot is computer-controlled (design.md 2.14)
 * @author Mario Koehler
 */
public record PlayerInfo(int seat, String name, boolean ready, boolean connected, boolean host, boolean bot) {

    /**
     * Describes a human player.
     *
     * @param seat      the seat, 0 to 7
     * @param name      the display name
     * @param ready     whether the player is ready to start (lobby)
     * @param connected whether the player is currently connected
     * @param host      whether this player is the host who starts the game
     */
    public PlayerInfo(int seat, String name, boolean ready, boolean connected, boolean host) {
        this(seat, name, ready, connected, host, false);
    }
}
