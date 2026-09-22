package de.mkoehler.robotrampage.lwjgl3;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code assets/textures/game.atlas} (built by the {@code AtlasPacker} dev tool) has exactly one region
 * for every PNG under {@code assets/board}, {@code assets/cards}, {@code assets/icons}, {@code assets/robots} and
 * {@code assets/tiles} — the folders {@code UiKit.image} draws pictures from.
 * <p>
 * A region {@code UiKit.image} cannot find is silently invisible on screen, not a crash or a failed assertion
 * anywhere else — none of {@code ScreenSnapshot}, {@code BoardSnapshot} or {@code GameScreenDriver} would catch a
 * missing region on their own, since a blank square looks like a passing check to all three. This test is deliberately
 * a plain-text parse of the {@code .atlas} file, not a real {@code TextureAtlas} (which needs a GL context to load its
 * page as a texture), so it runs as an ordinary, fast surefire test.
 *
 * @author Mario Koehler
 */
class AtlasCoverageTest {

    private static final String[] PICTURE_FOLDERS = {"board", "cards", "icons", "robots", "tiles"};

    /**
     * Every PNG under the picture folders has a matching atlas region, and the atlas has no region left over for a
     * picture that no longer exists (which would just be dead weight on the page).
     */
    @Test
    void everyPictureHasExactlyOneRegion() {
        Set<String> expected = new TreeSet<>();
        for (String folder : PICTURE_FOLDERS) {
            File directory = new File("../assets/" + folder);
            assertTrue(directory.isDirectory(), directory + " should exist");
            File[] pngs = directory.listFiles((dir, name) -> name.endsWith(".png"));
            assertFalse(pngs == null || pngs.length == 0, folder + " should have at least one picture");
            for (File png : pngs) {
                expected.add(folder + "/" + png.getName().substring(0, png.getName().length() - ".png".length()));
            }
        }

        Set<String> actual = regionNames(new File("../assets/textures/game.atlas"));

        assertEquals(expected, actual, "run AtlasPacker (see its Javadoc) to bring assets/textures/game.atlas up to date");
    }

    /**
     * Reads the region names out of a libGDX {@code .atlas} file, without loading its page as a texture.
     * <p>
     * The format is one or more pages, each: a blank line (before every page but the first), the page's file name,
     * four {@code key: value} header lines, then one unindented line per region (its name) followed by its indented
     * attribute lines ({@code rotate}, {@code xy}, {@code size}, ...). Parsed as a small state machine rather than
     * "skip the first line", since a blank line before the very first page (as libGDX's packer writes) would
     * otherwise shift everything and leave the real page name looking like a region.
     *
     * @param atlasFile the {@code .atlas} file
     * @return the region names it declares
     */
    private static Set<String> regionNames(File atlasFile) {
        assertTrue(atlasFile.isFile(), atlasFile + " should exist");
        List<String> lines;
        try {
            lines = Files.readAllLines(atlasFile.toPath());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Set<String> names = new TreeSet<>();
        boolean expectingPageName = true;
        boolean inPageHeader = false;
        int headerLinesLeft = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                expectingPageName = true;
                continue;
            }
            boolean indented = line.startsWith(" ") || line.startsWith("\t");
            if (expectingPageName) {
                expectingPageName = false;
                inPageHeader = true;
                headerLinesLeft = 4; // size, format, filter, repeat
                continue;
            }
            if (inPageHeader) {
                headerLinesLeft--;
                if (headerLinesLeft <= 0) {
                    inPageHeader = false;
                }
                continue;
            }
            if (!indented) {
                names.add(line.trim());
            }
        }
        return names;
    }
}
