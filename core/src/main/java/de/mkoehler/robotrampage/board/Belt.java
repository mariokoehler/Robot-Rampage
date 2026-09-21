package de.mkoehler.robotrampage.board;

/**
 * A conveyor belt on a square (design.md 2.9, 2.12).
 * <p>
 * Only the belt's direction is stored: whether a belt is drawn curved or merging
 * is derived by the renderer from its neighbours, and the rotation a robot
 * receives when a belt moves it onto this square follows from the direction
 * alone (design.md 2.12).
 *
 * @param direction the direction the belt moves robots in
 * @param express   {@code true} for an express belt, which moves robots in both
 *                  belt sub-phases of a register; {@code false} for a normal belt,
 *                  which moves them in the second only
 * @author Mario Koehler
 */
public record Belt(Direction direction, boolean express) {
}
