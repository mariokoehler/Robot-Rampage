package de.mkoehler.robotrampage.devtools.editor;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.BoardLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The folder the editor opens boards from and saves them to, normally {@code assets/boards}, where the game finds them.
 * A board is stored as {@code <id>.json} in a compact layout of its own (see {@link #format}): one square, edge, flag or
 * start square per line, in the canonical order of the export, so a saved board reads well and changes diff line by
 * line. {@link BoardLoader} reads it like any other JSON.
 *
 * @author Mario Koehler
 */
public final class BoardFiles {

    private static final String SUFFIX = ".json";

    /**
     * The index of the boards a server offers, in the same folder.
     */
    public static final String INDEX_FILE = "boards.txt";

    private final Path folder;

    /**
     * Creates the store for a folder.
     *
     * @param folder the folder holding the board files
     */
    public BoardFiles(Path folder) {
        this.folder = folder;
    }

    /**
     * Returns the folder.
     *
     * @return the folder
     */
    public Path folder() {
        return folder;
    }

    /**
     * Lists the boards in the folder.
     *
     * @return the identifiers, sorted
     * @throws UncheckedIOException if the folder cannot be read
     */
    public List<String> list() {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                .filter(fileName -> fileName.endsWith(SUFFIX))
                .map(fileName -> fileName.substring(0, fileName.length() - SUFFIX.length()))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns whether a board file exists.
     *
     * @param id the board's identifier
     * @return {@code true} if there is a file for it
     */
    public boolean exists(String id) {
        return Files.exists(file(id));
    }

    /**
     * Reads a board, checked by the same loader the game uses.
     *
     * @param id the board's identifier
     * @return the board
     * @throws UncheckedIOException if the file cannot be read
     * @throws de.mkoehler.robotrampage.board.InvalidBoardException if it is not a valid board
     */
    public BoardDefinition read(String id) {
        try {
            return BoardLoader.parse(Files.readString(file(id), StandardCharsets.UTF_8)).definition();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes a board to {@code <id>.json}, replacing the file if there is one, and adds a new board to the end of the
     * index {@value #INDEX_FILE} that lists the boards a server offers (see {@code BoardCatalog}), creating the index if
     * there is none. A board already listed keeps its place.
     *
     * @param definition the board
     * @throws UncheckedIOException if a file cannot be written
     */
    public void write(BoardDefinition definition) {
        try {
            Files.createDirectories(folder);
            Files.writeString(file(definition.id()), format(definition), StandardCharsets.UTF_8);
            register(definition.id());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds a board id to the end of the index unless it is listed already.
     *
     * @param id the board's identifier
     * @throws IOException if the index cannot be read or written
     */
    private void register(String id) throws IOException {
        Path index = folder.resolve(INDEX_FILE);
        String text = Files.exists(index) ? Files.readString(index, StandardCharsets.UTF_8) : "";
        boolean listed = text.lines().map(String::trim).anyMatch(id::equals);
        if (!listed) {
            String separator = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
            Files.writeString(index, text + separator + id + "\n", StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes a board as JSON text in the compact layout of the board files: the metadata one property per line, then
     * each list with one entry per line, lines ending in a line feed. Absent metadata is left out; an empty list is
     * written as {@code []}.
     *
     * @param definition the board
     * @return the JSON text
     */
    public static String format(BoardDefinition definition) {
        List<String> properties = new ArrayList<>();
        properties.add("\"formatVersion\": " + definition.formatVersion());
        properties.add("\"id\": " + quote(definition.id()));
        properties.add("\"name\": " + quote(definition.name()));
        if (definition.author() != null) {
            properties.add("\"author\": " + quote(definition.author()));
        }
        if (definition.generator() != null) {
            properties.add("\"generator\": " + quote(definition.generator()));
        }
        if (definition.seed() != null) {
            properties.add("\"seed\": " + definition.seed());
        }
        properties.add("\"width\": " + definition.width());
        properties.add("\"height\": " + definition.height());
        properties.add(list("squares", definition.squares(), BoardFiles::square));
        properties.add(list("edges", definition.edges(), BoardFiles::edge));
        properties.add(list("flags", definition.flags(), flag -> "{\"x\": " + flag.x() + ", \"y\": " + flag.y() + "}"));
        properties.add(list("startSquares", definition.startSquares(), start -> "{\"x\": " + start.x() + ", \"y\": "
            + start.y() + ", \"facing\": \"" + start.facing() + "\"}"));
        return "{\n  " + String.join(",\n  ", properties) + "\n}\n";
    }

    /**
     * Writes one list property with one entry per line.
     *
     * @param name    the property name
     * @param entries the entries
     * @param writer  writes one entry as JSON
     * @param <T>     the type of the entries
     * @return the property as JSON text, without a trailing comma
     */
    private static <T> String list(String name, List<T> entries, Function<T, String> writer) {
        if (entries.isEmpty()) {
            return "\"" + name + "\": []";
        }
        return "\"" + name + "\": [\n    " + entries.stream().map(writer).collect(Collectors.joining(",\n    "))
            + "\n  ]";
    }

    /**
     * Writes one square entry.
     *
     * @param square the square
     * @return the JSON object
     */
    private static String square(BoardDefinition.Square square) {
        StringBuilder text = new StringBuilder("{\"x\": " + square.x() + ", \"y\": " + square.y());
        if (square.belt() != null) {
            text.append(", \"belt\": {\"dir\": \"").append(square.belt().dir()).append("\", \"express\": ")
                .append(square.belt().express()).append('}');
        }
        if (square.feature() != null) {
            text.append(", \"feature\": \"").append(square.feature()).append('"');
        }
        if (!square.registers().isEmpty()) {
            text.append(", \"registers\": ").append(numbers(square.registers()));
        }
        return text.append('}').toString();
    }

    /**
     * Writes one edge entry.
     *
     * @param edge the edge
     * @return the JSON object
     */
    private static String edge(BoardDefinition.Edge edge) {
        StringBuilder text = new StringBuilder("{\"x\": " + edge.x() + ", \"y\": " + edge.y() + ", \"side\": \""
            + edge.side() + "\"");
        if (edge.wall()) {
            text.append(", \"wall\": true");
        }
        if (edge.laser() != null) {
            text.append(", \"laser\": {\"beams\": ").append(edge.laser().beams()).append('}');
        }
        if (edge.pusher() != null) {
            text.append(", \"pusher\": {\"registers\": ").append(numbers(edge.pusher().registers())).append('}');
        }
        return text.append('}').toString();
    }

    /**
     * Writes a list of numbers as a JSON array.
     *
     * @param numbers the numbers
     * @return the array, such as {@code [1, 3, 5]}
     */
    private static String numbers(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Writes a text as a JSON string, with quotes and escapes.
     *
     * @param text the text
     * @return the JSON string
     */
    private static String quote(String text) {
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(text)) + "\"";
    }

    /**
     * Returns the file of a board.
     *
     * @param id the board's identifier
     * @return the path
     */
    private Path file(String id) {
        return folder.resolve(id + SUFFIX);
    }
}
