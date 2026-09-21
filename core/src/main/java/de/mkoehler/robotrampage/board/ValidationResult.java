package de.mkoehler.robotrampage.board;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@link BoardValidator} found out about a board: problems that make it unusable
 * (errors) and things that are legal but suspicious (warnings).
 *
 * @param errors   the problems that make the board unusable; empty for a valid board
 * @param warnings the suspicious but legal things worth a look
 * @author Mario Koehler
 */
public record ValidationResult(List<String> errors, List<String> warnings) {

    /**
     * Creates a result, copying both lists.
     *
     * @param errors   the errors
     * @param warnings the warnings
     */
    public ValidationResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    /**
     * Returns whether the board has no errors. Warnings do not matter.
     *
     * @return {@code true} if there are no errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Combines two results into one holding all errors and all warnings of both.
     *
     * @param other the result to add
     * @return the combined result
     */
    public ValidationResult plus(ValidationResult other) {
        List<String> allErrors = new ArrayList<>(errors);
        allErrors.addAll(other.errors);
        List<String> allWarnings = new ArrayList<>(warnings);
        allWarnings.addAll(other.warnings);
        return new ValidationResult(allErrors, allWarnings);
    }
}
