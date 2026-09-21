package de.mkoehler.robotrampage.net.messages;

/**
 * Sent to everybody when a player has locked in their program. It says <em>that</em> they are
 * done, never what they chose.
 *
 * @param robotId the robot whose program is locked in
 * @author Mario Koehler
 */
public record PlayerConfirmed(int robotId) {
}
