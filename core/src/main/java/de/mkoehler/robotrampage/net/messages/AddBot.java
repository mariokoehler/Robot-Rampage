package de.mkoehler.robotrampage.net.messages;

/**
 * Sent by the host, in the lobby, to seat a computer-controlled robot on the lowest free seat (design.md 2.14). Refused
 * with {@link RequestRejected} for anybody else, outside the lobby, or when every seat is taken.
 *
 * @author Mario Koehler
 */
public record AddBot() {
}
