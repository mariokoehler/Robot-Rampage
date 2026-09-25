package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host, in the lobby, to take a computer-controlled robot off its seat (design.md 2.14). Refused with
 * {@link RequestRejected} for anybody else, outside the lobby, or for a seat that has no bot on it — a human player can
 * never be removed this way.
 *
 * @param seat the bot's seat
 * @author Mario Koehler
 */
public record RemoveBot(int seat) {
}
