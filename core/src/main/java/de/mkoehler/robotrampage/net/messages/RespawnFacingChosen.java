package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.board.Direction;

/**
 * Sent to everybody the moment a re-entering robot's player picks its facing (design.md 2.13), so every client can show
 * the robot turned the right way on the board right away, rather than only once the turn resolves. The facing is public
 * table state, not a card, so unlike {@link PlayerConfirmed} this says exactly what was chosen.
 *
 * @param robotId the robot that was turned
 * @param facing  the facing it was turned to
 * @author Mario Koehler
 */
public record RespawnFacingChosen(int robotId, Direction facing) {
}
