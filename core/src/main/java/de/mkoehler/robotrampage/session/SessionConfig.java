package de.mkoehler.robotrampage.session;

/**
 * The timings and limits of a game session (design.md 2.13, 3.5). All durations are in milliseconds.
 *
 * @param programmingMillis     the most time players get to program a turn, counted from the deal
 * @param lastPlayerMillis      the most time the last unconfirmed player gets once everybody else is done
 * @param reconnectGraceMillis  how long a disconnected player may take to come back before their robot is removed
 * @param pauseBaseMillis       the pause between a turn's results and the next deal, before the per-event part
 * @param pausePerEventMillis   extra pause per event of the turn, so clients can finish animating
 * @param pauseMaxMillis        the longest pause between turns
 * @param gameOverMillis        how long the final results stay up before the session returns to the lobby
 * @param minPlayers            how many players a game needs
 * @author Mario Koehler
 */
public record SessionConfig(long programmingMillis, long lastPlayerMillis, long reconnectGraceMillis,
                            long pauseBaseMillis, long pausePerEventMillis, long pauseMaxMillis,
                            long gameOverMillis, int minPlayers) {

    /**
     * The defaults for a real game: 90 s to program, 30 s for the last player, 10 minutes of reconnect grace, a
     * turn pause of 2 s plus 25 ms per event (at most 20 s), 15 s of final results, and at least two players.
     *
     * @return the default configuration
     */
    public static SessionConfig defaults() {
        return new SessionConfig(90_000, 30_000, 600_000, 2_000, 25, 20_000, 15_000, 2);
    }

    /**
     * Returns the pause to leave after a turn with the given number of events.
     *
     * @param eventCount how many events the turn produced
     * @return the pause in milliseconds, never more than {@link #pauseMaxMillis()}
     */
    public long pauseAfterTurn(int eventCount) {
        return Math.min(pauseMaxMillis, pauseBaseMillis + pausePerEventMillis * eventCount);
    }
}
