package de.mkoehler.robotrampage.devtools.editor;

import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BoardFiles}: writing, listing and reading boards back.
 *
 * @author Mario Koehler
 */
class BoardFilesTest {

    @TempDir
    Path folder;

    @Test
    void aSavedBoardIsListedAndReadsBackTheSame() throws Exception {
        BoardDraft draft = new BoardDraft("my-board", "My \"quoted\" board", "Someone");
        draft.addStart(new Position(0, 0), Direction.NORTH);
        draft.addFlag(new Position(6, 6));
        draft.paintBelt(new Position(3, 3), Direction.EAST, false);
        draft.paintCrusher(new Position(3, 3), Set.of(2, 4));
        draft.mountPusher(new Position(5, 5), Direction.WEST, Set.of(1));
        BoardFiles files = new BoardFiles(folder);
        Files.writeString(folder.resolve("notes.txt"), "not a board");

        files.write(draft.toDefinition());

        assertEquals(List.of("my-board"), files.list());
        assertTrue(files.exists("my-board"));
        BoardDefinition read = files.read("my-board");
        assertEquals(draft.toDefinition(), read);
    }

    @Test
    void aMissingFolderListsNothing() {
        BoardFiles files = new BoardFiles(folder.resolve("missing"));

        assertEquals(List.of(), files.list());
        assertFalse(files.exists("anything"));
    }
}
