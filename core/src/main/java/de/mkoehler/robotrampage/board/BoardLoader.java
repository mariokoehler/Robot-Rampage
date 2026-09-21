package de.mkoehler.robotrampage.board;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes boards as JSON (design.md 3.6).
 * <p>
 * Loading is strict and complete: unknown JSON properties are rejected (they are almost always
 * typos), and a board is only returned if it passes both levels of {@link BoardValidator}. If it
 * does not, an {@link InvalidBoardException} lists everything that is wrong.
 *
 * @author Mario Koehler
 */
public final class BoardLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private BoardLoader() {
    }

    /**
     * Reads a board from JSON text.
     *
     * @param json the JSON text of a board definition
     * @return the validated board with its definition and warnings
     * @throws InvalidBoardException if the text is not a valid board definition, or the board is
     *                               not valid
     */
    public static LoadedBoard parse(String json) {
        BoardDefinition definition;
        try {
            definition = MAPPER.readValue(json, BoardDefinition.class);
        } catch (JsonProcessingException e) {
            throw new InvalidBoardException(List.of("Not a valid board file: " + e.getOriginalMessage()));
        }
        ValidationResult structure = BoardValidator.validate(definition);
        if (!structure.isValid()) {
            throw new InvalidBoardException(structure.errors());
        }
        Board board = BoardConverter.toBoard(definition);
        ValidationResult result = structure.plus(BoardValidator.validate(board));
        if (!result.isValid()) {
            throw new InvalidBoardException(result.errors());
        }
        return new LoadedBoard(definition, board, result.warnings());
    }

    /**
     * Reads a board from a classpath resource, for example {@code boards/proving-grounds.json}
     * (the {@code assets/} folder is on the classpath of both client and server).
     *
     * @param resourcePath the resource path, without a leading slash
     * @return the validated board with its definition and warnings
     * @throws InvalidBoardException if the resource is missing or the board is not valid
     */
    public static LoadedBoard loadResource(String resourcePath) {
        try (InputStream stream = BoardLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new InvalidBoardException(List.of("Board resource not found: " + resourcePath));
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidBoardException(new ArrayList<>(List.of("Could not read " + resourcePath + ": " + e.getMessage())));
        }
    }

    /**
     * Writes a board definition as indented JSON. Absent values and empty lists are left out.
     *
     * @param definition the definition to write
     * @return the JSON text
     */
    public static String toJson(BoardDefinition definition) {
        try {
            return MAPPER.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A board definition could not be written as JSON", e);
        }
    }
}
