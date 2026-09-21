package de.mkoehler.robotrampage.session;

/**
 * The outcome of a player trying to join (or rejoin) the session.
 *
 * @param accepted     whether the player was let in
 * @param message      a message for the player: a welcome, or the reason they were turned away
 * @param seat         the player's seat, or -1 if they were not let in
 * @param sessionToken the token that lets them take this seat back after a disconnect, or {@code null} if they
 *                     were not let in
 * @author Mario Koehler
 */
public record JoinResult(boolean accepted, String message, int seat, String sessionToken) {

    /**
     * Creates the result for a player who was turned away.
     *
     * @param reason why
     * @return a rejecting result
     */
    static JoinResult rejected(String reason) {
        return new JoinResult(false, reason, -1, null);
    }
}
