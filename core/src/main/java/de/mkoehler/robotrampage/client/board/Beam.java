package de.mkoehler.robotrampage.client.board;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;

/**
 * A laser beam to draw for a moment: where it starts, which way it goes and the last square it reaches.
 *
 * @param from      the square the beam starts on: the square of the emitter, or of the robot that fires
 * @param direction the direction the beam travels in
 * @param to        the last square the beam reaches
 * @param board     {@code true} for a laser mounted on the board, {@code false} for the laser of a robot
 * @param beams     the number of parallel beams, 1 to 3
 * @author Mario Koehler
 */
public record Beam(Position from, Direction direction, Position to, boolean board, int beams) {
}
