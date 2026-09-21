package de.mkoehler.robotrampage.board;

import java.util.List;

/**
 * Thrown when a board file cannot be used: it is not valid JSON of the right shape, or it
 * fails {@link BoardValidator}. Carries every problem found, not just the first.
 *
 * @author Mario Koehler
 */
public class InvalidBoardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    /**
     * Creates an exception listing everything that is wrong with a board.
     *
     * @param errors the problems, at least one
     */
    public InvalidBoardException(List<String> errors) {
        super("Invalid board:\n - " + String.join("\n - ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns every problem found.
     *
     * @return the error messages
     */
    public List<String> errors() {
        return errors;
    }
}
