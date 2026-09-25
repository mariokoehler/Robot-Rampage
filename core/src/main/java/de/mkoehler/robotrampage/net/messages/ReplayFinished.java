package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by a client once it has finished showing a turn's replay, played to the end or skipped (design.md 2.13). When every
 * connected human player has sent it for the turn just resolved, the server deals the next turn at once instead of
 * waiting out the rest of the pause. A report for any other turn, or outside the pause after a turn, is ignored.
 *
 * @param turn the turn whose replay the client finished
 * @author Mario Koehler
 */
public record ReplayFinished(int turn) {
}
