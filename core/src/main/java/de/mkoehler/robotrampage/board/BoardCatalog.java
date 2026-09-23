package de.mkoehler.robotrampage.board;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The boards a server offers (design.md 3.6): the board ids listed in the index file {@value #INDEX}, one per line, each
 * naming the file {@code boards/<id>.json}. Blank lines are ignored and ids are trimmed. The order of the index is the
 * order the lobby shows the boards in, and the first board is the one a new server starts with.
 * <p>
 * Every board is loaded and validated at once, and a problem with any of them is an error: a server should refuse to
 * start rather than offer a board that cannot be played. Ids come only from this index and never from a client, so no
 * client-supplied text ever becomes part of a resource path.
 *
 * @author Mario Koehler
 */
public final class BoardCatalog {

    /**
     * The index file, as a classpath resource.
     */
    public static final String INDEX = "boards/boards.txt";

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private BoardCatalog() {
    }

    /**
     * Loads every board listed in the index on the classpath.
     *
     * @return the boards, in the order of the index
     * @throws InvalidBoardException if the index is missing or empty, lists an id twice, or names a board that is
     *                               missing, not valid, or whose {@code id} differs from its file name
     */
    public static List<LoadedBoard> loadResources() {
        List<LoadedBoard> boards = new ArrayList<>();
        for (String id : parseIndex(readIndex())) {
            LoadedBoard board = BoardLoader.loadResource("boards/" + id + ".json");
            if (!id.equals(board.definition().id())) {
                throw new InvalidBoardException(List.of("boards/" + id + ".json has the id \"" + board.definition().id()
                    + "\"; a board's id must match its file name"));
            }
            boards.add(board);
        }
        return boards;
    }

    /**
     * Reads the ids from the text of an index file.
     *
     * @param text the text of the index
     * @return the ids, in order
     * @throws InvalidBoardException if the index lists no board or an id twice
     */
    public static List<String> parseIndex(String text) {
        Set<String> ids = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String id = line.trim();
            if (!id.isEmpty() && !ids.add(id)) {
                throw new InvalidBoardException(List.of(INDEX + " lists \"" + id + "\" twice"));
            }
        }
        if (ids.isEmpty()) {
            throw new InvalidBoardException(List.of(INDEX + " lists no board"));
        }
        return List.copyOf(ids);
    }

    /**
     * Reads the index file from the classpath.
     *
     * @return its text
     * @throws InvalidBoardException if it is missing or cannot be read
     */
    private static String readIndex() {
        try (InputStream stream = BoardCatalog.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (stream == null) {
                throw new InvalidBoardException(List.of("Board index not found: " + INDEX));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidBoardException(List.of("Could not read " + INDEX + ": " + e.getMessage()));
        }
    }
}
