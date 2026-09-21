package de.mkoehler.robotrampage.board;

import de.mkoehler.robotrampage.testsupport.BoardPicture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the picture of the first board in design.md 2.11 honest: it is generated from
 * {@code assets/boards/proving-grounds.json} by {@link BoardPicture}, and this test fails when the
 * document no longer contains exactly that picture. When it fails, copy the picture from the failure
 * message over the fenced block in 2.11.
 *
 * @author Mario Koehler
 */
class DesignDocPictureTest {

    /**
     * design.md must contain the current rendering of the board file.
     *
     * @throws IOException if the design document cannot be read
     */
    @Test
    void designDocumentShowsTheCurrentBoard() throws IOException {
        // Surefire runs a module's tests with the module directory as working directory.
        Path designDocument = Path.of("..", "design.md");
        String document = Files.readString(designDocument, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String picture = BoardPicture.render(BoardLoader.loadResource("boards/proving-grounds.json").definition());

        assertTrue(document.contains(picture),
            "design.md 2.11 does not show the current board. Replace the picture in its fenced block with:\n" + picture);
    }
}
