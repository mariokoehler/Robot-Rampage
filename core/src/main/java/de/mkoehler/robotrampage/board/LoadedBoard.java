package de.mkoehler.robotrampage.board;

import java.util.List;

/**
 * A board that was read and passed validation: its metadata and structure as a definition, the
 * runtime board built from it, and any warnings the validator had.
 *
 * @param definition the definition the board was built from
 * @param board      the runtime board
 * @param warnings   legal but suspicious things the validator noticed
 * @author Mario Koehler
 */
public record LoadedBoard(BoardDefinition definition, Board board, List<String> warnings) {

    /**
     * Creates a loaded board, copying the warnings.
     *
     * @param definition the definition
     * @param board      the runtime board
     * @param warnings   the validator's warnings
     */
    public LoadedBoard {
        warnings = List.copyOf(warnings);
    }
}
