package de.mkoehler.robotrampage.lwjgl3.tools;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;

import java.io.File;

/**
 * Developer-only utility that (re)builds the game's one texture atlas from the PNGs under {@code assets/}, writing
 * {@code assets/textures/game.atlas} and its page image. Not a JUnit test — it has a {@code main()} method and is
 * meant to be run by hand whenever an image under {@code assets/board}, {@code assets/cards}, {@code assets/icons},
 * {@code assets/robots} or {@code assets/tiles} is added, removed or replaced:
 * <pre>{@code
 * mvn -q -pl core -am install -DskipTests
 * mvn -pl lwjgl3 dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt -Dmdep.includeScope=test
 * cd lwjgl3 && java -cp "target/classes;target/test-classes;$(cat target/test-cp.txt)" \
 *     de.mkoehler.robotrampage.lwjgl3.tools.AtlasPacker
 * }</pre>
 * <p>
 * <b>Packs from {@code assets/}, not {@code assets-raw/}</b> — deliberately unlike the StarWars project's
 * {@code AtlasPacker}, whose raw folders are 1:1 with runtime art. Here {@code assets/tiles} and {@code assets/robots}
 * are themselves *generated* from the design's SVGs by {@code tools/design-import/rasterize.js}, and
 * {@code assets/board} by {@code tools/design-import/make-board-sprites.js} — except {@code robot-wedge.png}, which the
 * owner repainted by hand and the script deliberately no longer touches (CLAUDE.md). Packing the already-rasterised
 * PNGs in {@code assets/} keeps a single generation path and cannot undo that repaint; packing from
 * {@code assets-raw/design/} would mean re-deriving the whole rasterisation pipeline inside this class.
 * <p>
 * {@code assets/} also holds {@code boards/} (board JSON) and {@code fonts/} (TrueType files), which the packer simply
 * ignores — {@link TexturePacker} only picks up image files.
 * <p>
 * It lives under {@code src/test} (rather than {@code src/main}) specifically so that {@code gdx-tools} stays off the
 * shaded runtime jar's classpath.
 *
 * @author Mario Koehler
 */
public final class AtlasPacker {

    /**
     * The atlas's page size. 56 images at up to 128x128 fit one 2048x2048 page with plenty of room, keeping every
     * picture the game draws behind a single texture bind; a spillover to a second page would print in the output
     * below ("Writing ...") and should be called out in CLAUDE.md's asset pipeline notes if it ever happens.
     */
    private static final int MAX_PAGE_SIZE = 2048;

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private AtlasPacker() {
    }

    /**
     * Packs {@code assets/} into {@code assets/textures/game.atlas}.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        // exec runs with the lwjgl3 module directory as the working directory, so the repo root
        // (and the assets/ sibling) is one level up.
        File input = new File("../assets");
        File output = new File("../assets/textures");

        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = MAX_PAGE_SIZE;
        settings.maxHeight = MAX_PAGE_SIZE;
        // Pictures are drawn smaller than they are stored (design.md 4.5), so they need mipmaps, exactly like the
        // plain Texture loading this replaces (UiKit.image used new Texture(file, true) with the same two filters).
        settings.filterMin = Texture.TextureFilter.MipMapLinearLinear;
        settings.filterMag = Texture.TextureFilter.Linear;
        // Without this, every subfolder of assets/ (board, cards, icons, robots, tiles) would pack onto its own page
        // instead of sharing one — the whole point of atlasing. Region names keep their subfolder prefix regardless
        // (e.g. "tiles/floor"), verified against the generated .atlas file, so every existing ui.image("tiles/floor.png")
        // call site keeps working unchanged: UiKit.image only has to strip the ".png".
        settings.combineSubdirectories = true;
        // The default 2px padding is only safe at mip level 0. Several icons are drawn at roughly 1/6 of their
        // 128px source size (e.g. GameScreen's 20-26px icons), several mip levels down, where a plain 2px gutter has
        // already vanished and neighbouring regions bleed into each other on the page - invisible with loose
        // Textures (nothing to bleed from) but a real regression an atlas introduces. duplicatePadding mirrors each
        // region's edge pixels into its padding instead of leaving it blank, which keeps the bleed within the
        // region's own colour instead of a neighbour's. Verified by comparing ScreenSnapshot's card/icon PNGs before
        // and after.
        settings.duplicatePadding = true;
        // Verified empirically (ran the packer twice in a row, checked the region count and AtlasCoverageTest both
        // times): TexturePackerFileProcessor already skips an output directory nested inside the input directory, so
        // assets/textures/game.png does not get packed into itself on a second run. If a future libGDX upgrade ever
        // changes that, the symptom is a spurious "textures/game" region and a growing page — move the output
        // outside assets/ (and adjust UiKit.image's internal path) rather than debugging it as a one-off.
        TexturePacker.process(settings, input.getPath(), output.getPath(), "game");
    }
}
