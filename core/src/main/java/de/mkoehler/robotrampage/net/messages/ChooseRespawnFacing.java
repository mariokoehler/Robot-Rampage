package de.mkoehler.robotrampage.net.messages;

import de.mkoehler.robotrampage.board.Direction;

/**
 * Sent the moment a player presses "Go" in the respawn dialog (design.md 2.13), so the server applies and broadcasts
 * the choice right away instead of waiting for that player's full {@link SubmitProgram} — they may still be several
 * registers away from confirming, or never confirm at all if squeezed or disconnected.
 *
 * @param facing the facing picked for the re-entered robot
 * @author Mario Koehler
 */
public record ChooseRespawnFacing(Direction facing) {
}
