package de.mkoehler.robotrampage.board;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BoardCatalog} and keeps the real index, {@code assets/boards/boards.txt}, in step with the board files
 * next to it, in both directions: every listed board loads, and every board file is listed.
 *
 * @author Mario Koehler
 */
class BoardCatalogTest {

    private static final Path BOARDS = Path.of("..", "assets", "boards");

    @Test
    void everyListedBoardLoadsAndProvingGroundsComesFirst() {
        List<LoadedBoard> boards = BoardCatalog.loadResources();

        assertEquals("proving-grounds", boards.get(0).definition().id());
    }

    @Test
    void everyBoardFileIsListedInTheIndex() throws IOException {
        List<String> listed = BoardCatalog.parseIndex(Files.readString(BOARDS.resolve("boards.txt")));
        try (Stream<Path> files = Files.list(BOARDS)) {
            List<String> onDisk = files.map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .sorted().toList();
            assertEquals(onDisk, listed.stream().sorted().toList(),
                "assets/boards/boards.txt must list exactly the board files in assets/boards");
        }
    }

    @Test
    void theIndexIgnoresBlankLinesAndSpaces() {
        assertEquals(List.of("a", "b"), BoardCatalog.parseIndex("\n  a \r\n\nb\n"));
    }

    @Test
    void anEmptyIndexOrADuplicateIsRefused() {
        assertThrows(InvalidBoardException.class, () -> BoardCatalog.parseIndex("\n \n"));
        InvalidBoardException twice = assertThrows(InvalidBoardException.class, () -> BoardCatalog.parseIndex("a\nb\na"));
        assertTrue(twice.errors().get(0).contains("twice"));
    }
}
