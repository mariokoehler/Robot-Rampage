package de.mkoehler.robotrampage.net.messages;

/**
 * Sent to everybody when a player drops off the network or comes back.
 *
 * @param robotId the player's robot
 * @param connected whether they are connected now
 * @author Mario Koehler
 */
public record PlayerConnection(int robotId, boolean connected) {
}
