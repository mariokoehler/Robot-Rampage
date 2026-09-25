package de.mkoehler.robotrampage.session;

import de.mkoehler.robotrampage.rules.Card;

import java.util.List;

/**
 * Everything the session remembers about one seated player. Package-private and mutable: only
 * {@link GameSession} touches it, on a single thread.
 *
 * @author Mario Koehler
 */
final class SessionPlayer {

    final int seat;
    final String name;
    final String token;
    final int joinOrder;

    /**
     * Whether the seat is played by the computer (design.md 2.14). A bot never has a connection of its own, yet counts as
     * connected and always as ready, so nothing that waits for, times out or forgets absent humans ever touches it.
     */
    final boolean bot;

    /**
     * Whether the player is currently connected.
     */
    boolean connected = true;

    /**
     * When the player disconnected, in clock milliseconds; meaningful only while not connected.
     */
    long disconnectedAt;

    /**
     * Whether the player has said they are ready to start (lobby).
     */
    boolean ready;

    /**
     * Whether the player has been away too long and their robot was removed for good.
     */
    boolean left;

    /**
     * Whether the player has just come back and the others have not been told yet.
     */
    boolean reconnected;

    /**
     * Whether the player must program in the current turn.
     */
    boolean awaiting;

    /**
     * Whether the player's program for the current turn is locked in.
     */
    boolean confirmed;

    /**
     * The cards dealt to the player this turn and not yet used; empty once they have confirmed.
     */
    List<Card> hand = List.of();

    /**
     * Creates a newly seated player.
     *
     * @param seat      the seat, which is also the robot id
     * @param name      the display name
     * @param token     the session token
     * @param joinOrder when they joined, relative to the others; the earliest joiner is the host
     */
    SessionPlayer(int seat, String name, String token, int joinOrder) {
        this(seat, name, token, joinOrder, false);
    }

    /**
     * Creates a newly seated human player or bot. A bot starts ready.
     *
     * @param seat      the seat, which is also the robot id
     * @param name      the display name
     * @param token     the session token; a bot's is never handed out
     * @param joinOrder when they joined, relative to the others
     * @param bot       whether the computer plays this seat
     */
    SessionPlayer(int seat, String name, String token, int joinOrder, boolean bot) {
        this.seat = seat;
        this.name = name;
        this.token = token;
        this.joinOrder = joinOrder;
        this.bot = bot;
        this.ready = bot;
    }
}
