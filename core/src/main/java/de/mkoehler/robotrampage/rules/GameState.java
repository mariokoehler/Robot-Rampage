package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything the rules need to know about a game in progress: the board, all
 * robots and the deck (design.md 3.4).
 * <p>
 * Turn resolution never mutates the state it is given: it works on a
 * {@link #copy()} and returns that as the new state.
 *
 * @author Mario Koehler
 */
public final class GameState {

    private final Board board;
    private final List<Robot> robots;
    private final Deck deck;

    /**
     * Creates a game state. The given list and deck are used as they are, not
     * copied.
     *
     * @param board  the board being played on
     * @param robots all robots of the game, active or not
     * @param deck   the shared programming deck
     */
    public GameState(Board board, List<Robot> robots, Deck deck) {
        this.board = board;
        this.robots = robots;
        this.deck = deck;
    }

    /**
     * Returns a deep copy: robots and deck are copied, the immutable board is
     * shared.
     *
     * @return an independent copy that can be mutated freely
     */
    public GameState copy() {
        List<Robot> robotCopies = new ArrayList<>(robots.size());
        for (Robot robot : robots) {
            robotCopies.add(robot.copy());
        }
        return new GameState(board, robotCopies, deck.copy());
    }

    /**
     * Returns the board.
     *
     * @return the immutable board
     */
    public Board board() {
        return board;
    }

    /**
     * Returns all robots, including destroyed and eliminated ones.
     *
     * @return the live list of robots, ordered by id
     */
    public List<Robot> robots() {
        return robots;
    }

    /**
     * Returns the shared deck.
     *
     * @return the deck
     */
    public Deck deck() {
        return deck;
    }

    /**
     * Looks a robot up by its stable id.
     *
     * @param id the robot id
     * @return the robot
     * @throws IllegalArgumentException if there is no robot with that id
     */
    public Robot robot(int id) {
        for (Robot robot : robots) {
            if (robot.id() == id) {
                return robot;
            }
        }
        throw new IllegalArgumentException("No robot with id " + id);
    }

    /**
     * Returns the active robot standing on a square.
     *
     * @param position the square
     * @return the robot, or empty if no active robot stands there
     */
    public Optional<Robot> robotAt(Position position) {
        for (Robot robot : robots) {
            if (robot.isActive() && position.equals(robot.position())) {
                return Optional.of(robot);
            }
        }
        return Optional.empty();
    }
}
