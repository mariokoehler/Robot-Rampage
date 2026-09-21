package de.mkoehler.robotrampage.board;

/**
 * A square robots start the game on, together with the direction they start
 * facing.
 *
 * @param position the starting square
 * @param facing   the direction a robot starting here faces
 * @author Mario Koehler
 */
public record StartSquare(Position position, Direction facing) {
}
