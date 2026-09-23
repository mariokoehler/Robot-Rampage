package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * Sent by the host, in the lobby, to change how long players get to program a turn once the game starts (design.md
 * 2.13). Refused with {@link RequestRejected} for anybody else, outside the lobby, or outside
 * {@link NetworkConstants#MIN_PROGRAMMING_SECONDS} to {@link NetworkConstants#MAX_PROGRAMMING_SECONDS}.
 *
 * @param seconds the new programming time, in seconds
 * @author Mario Koehler
 */
public record SetProgrammingSeconds(int seconds) {
}
