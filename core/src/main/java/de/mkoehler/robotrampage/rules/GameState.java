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

    /**
     * The winner value of a game that has not been won (yet), or that ended without a winner.
     */
    public static final int NO_WINNER = -1;

    private final Board board;
    private final List<Robot> robots;
    private final Deck deck;
    private int destructionCounter;
    private boolean over;
    private int winnerId = NO_WINNER;

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
        GameState copy = new GameState(board, robotCopies, deck.copy());
        copy.destructionCounter = destructionCounter;
        copy.over = over;
        copy.winnerId = winnerId;
        return copy;
    }

    /**
     * Hands out the next destruction sequence number, so robots destroyed earlier can be told
     * apart from those destroyed later.
     *
     * @return a number larger than every one handed out before
     */
    public int nextDestructionOrder() {
        return destructionCounter++;
    }

    /**
     * Returns whether the game has ended.
     *
     * @return {@code true} once a robot has won or no robot is left to play
     */
    public boolean isOver() {
        return over;
    }

    /**
     * Returns the id of the robot that won.
     *
     * @return the winner's id, or {@link #NO_WINNER} if the game is still running or ended
     *         without a winner
     */
    public int winnerId() {
        return winnerId;
    }

    /**
     * Ends the game.
     *
     * @param winnerId the id of the winning robot, or {@link #NO_WINNER} if nobody won
     */
    public void endGame(int winnerId) {
        this.over = true;
        this.winnerId = winnerId;
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
