package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host, in the lobby, to move a computer-controlled robot's difficulty to the next one in the cycle
 * (Easy to Normal to Hard and back to Easy, design.md 2.14). Refused with {@link RequestRejected} for anybody else,
 * outside the lobby, or for a seat that has no bot on it.
 *
 * @param seat the bot's seat
 * @author Mario Koehler
 */
public record SetBotDifficulty(int seat) {
}
