package de.mkoehler.robotrampage.devtools.editor;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.devtools.generate.BoardGenerator;
import de.mkoehler.robotrampage.client.ui.Theme;

import java.io.File;

/**
 * A development tool, not a test: opens the board editor on a hidden window and writes PNGs of it, to look at its layout
 * without clicking through it. Run it with the {@code assets} folder as the working directory:
 * <pre>java -cp ... de.mkoehler.robotrampage.devtools.editor.EditorSnapshot [output folder]</pre>
 * It writes {@code editor-proving-grounds.png} (the real board open, the pusher tool chosen, so its register options
 * show), {@code editor-new.png} (a new board with a few things drawn by driving the editor, still missing its flag, so
 * the checks list an error) and {@code editor-hover-wall.png}. The last one comes from real pointer events sent through
 * the stage: the pointer rests near the east side of a square with the wall tool chosen, then a left click must add that
 * wall and a right click remove it again; the tool stops with an exception at the first thing that does not happen.
 *
 * @author Mario Koehler
 */
public final class EditorSnapshot {

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private EditorSnapshot() {
    }

    /**
     * Writes the pictures.
     *
     * @param args optionally the output folder, by default the working directory
     */
    public static void main(String[] args) {
        File folder = new File(args.length > 0 ? args[0] : ".");
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setInitialVisible(false);
        configuration.setWindowedMode(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT);
        new Lwjgl3Application(new ApplicationAdapter() {
            /**
             * Builds the editor twice and writes a picture of each.
             */
            @Override
            public void create() {
                BoardEditorApp opened = new BoardEditorApp("proving-grounds");
                opened.create();
                opened.selectTool(Tool.PUSHER);
                write(opened, new File(folder, "editor-proving-grounds.png"));
                waitForBotGames(opened, 5);
                write(opened, new File(folder, "editor-metrics.png"));
                opened.dispose();

                BoardEditorApp fresh = new BoardEditorApp(null);
                fresh.create();
                BoardEditor editor = fresh.editor();
                editor.setTool(Tool.BELT);
                editor.press(new Position(1, 1), Direction.NORTH, false);
                for (int x = 2; x <= 6; x++) {
                    editor.drag(new Position(x, 1));
                }
                editor.drag(new Position(6, 4));
                editor.release();
                editor.setTool(Tool.START);
                editor.press(new Position(0, 0), Direction.NORTH, false);
                editor.release();
                editor.setTool(Tool.CRUSHER);
                editor.press(new Position(6, 3), Direction.NORTH, false);
                editor.release();
                editor.setTool(Tool.LASER);
                editor.press(new Position(10, 8), Direction.EAST, false);
                editor.release();
                write(fresh, new File(folder, "editor-new.png"));
                drivePointer(fresh, new File(folder, "editor-hover-wall.png"));
                fresh.dispose();

                driveGenerateButton(new File(folder, "editor-generated-from-proving-grounds.png"));

                for (long seed = 1; seed <= 3; seed++) {
                    BoardEditorApp generated = new BoardEditorApp(null);
                    generated.create();
                    generated.editor().load(BoardGenerator.generate(generated.editor().draft(), seed, () -> false).draft());
                    generated.refresh();
                    waitForBotGames(generated, 20);
                    write(generated, new File(folder, "editor-generated-" + seed + ".png"));
                    generated.dispose();
                }
                Gdx.app.exit();
            }
        }, configuration);
    }

    /**
     * Clicks Generate on the first board the way the button does and checks the result: it replaces the board as one
     * undo step, and a hand edit made while the generator runs makes the result be dropped instead.
     *
     * @param file the file to write the generated board's picture to
     * @throws IllegalStateException if the board is not replaced, undo does not bring it back, or a late result
     *                               overwrites a hand edit
     */
    private static void driveGenerateButton(File file) {
        BoardEditorApp app = new BoardEditorApp("proving-grounds");
        app.create();
        BoardDefinition original = app.editor().draft().toDefinition();
        app.generate();
        waitForGenerator(app);
        if (app.editor().draft().toDefinition().equals(original)) {
            throw new IllegalStateException("Generate did not change the board");
        }
        write(app, file);
        app.editor().undo();
        if (!app.editor().draft().toDefinition().equals(original)) {
            throw new IllegalStateException("One undo did not bring the board from before Generate back");
        }

        app.generate();
        app.editor().setTool(Tool.PIT);
        app.editor().press(new Position(0, 11), Direction.NORTH, false);
        app.editor().release();
        BoardDefinition edited = app.editor().draft().toDefinition();
        waitForGenerator(app);
        if (!app.editor().draft().toDefinition().equals(edited)) {
            throw new IllegalStateException("A generated board overwrote a hand edit made while it was generated");
        }
        app.dispose();
    }

    /**
     * Waits until the editor has taken in the generator's result, the way frames passing would.
     *
     * @param app the editor, generating
     * @throws IllegalStateException if the generator takes longer than a minute
     */
    private static void waitForGenerator(BoardEditorApp app) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (app.generating()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("The generator did not finish within a minute");
            }
            app.takeGeneratedBoard();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Lets the metrics panel's bots play until some games are shown, the way frames passing would.
     *
     * @param app   the editor, with a valid board open
     * @param games how many games to wait for
     * @throws IllegalStateException if they do not arrive within a minute
     */
    private static void waitForBotGames(BoardEditorApp app, int games) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (app.metrics().gamesShown() < games) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("The bots did not play " + games + " games within a minute");
            }
            app.metrics().update(0.1f);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Sends real pointer events through the stage to check that the board area maps the pointer to the right square and
     * side: a hover with the wall tool, then a left click that must add the wall and a right click that must remove it.
     *
     * @param app  the editor, with a new board open
     * @param file the file to write the hover picture to
     * @throws IllegalStateException if the wall is not added or not removed
     */
    private static void drivePointer(BoardEditorApp app, File file) {
        Stage stage = app.stage();
        stage.getViewport().update(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT, true);
        app.selectTool(Tool.WALL);
        stage.act(0f);
        stage.draw();
        Position square = new Position(3, 5);
        float tile = app.boardArea().getWidth() / BoardDraft.SIZE;
        Vector2 point = app.boardArea().localToStageCoordinates(
            new Vector2((square.x() + 0.92f) * tile, (square.y() + 0.5f) * tile));
        int screenX = Math.round(point.x);
        int screenY = Theme.VIEW_HEIGHT - Math.round(point.y);
        stage.mouseMoved(screenX, screenY);
        write(app, file);
        stage.touchDown(screenX, screenY, 0, Input.Buttons.LEFT);
        stage.touchUp(screenX, screenY, 0, Input.Buttons.LEFT);
        if (!app.editor().draft().hasWall(square, Direction.EAST)) {
            throw new IllegalStateException("A left click near the east side of " + square + " did not add a wall there");
        }
        stage.touchDown(screenX, screenY, 0, Input.Buttons.RIGHT);
        stage.touchUp(screenX, screenY, 0, Input.Buttons.RIGHT);
        if (app.editor().draft().hasWall(square, Direction.EAST)) {
            throw new IllegalStateException("A right click near the east side of " + square + " did not remove the wall");
        }
    }

    /**
     * Draws the editor into an off-screen buffer and saves it as a PNG, rows flipped to the right way up.
     *
     * @param app  the editor
     * @param file the file to write
     */
    private static void write(BoardEditorApp app, File file) {
        int width = Theme.VIEW_WIDTH;
        int height = Theme.VIEW_HEIGHT;
        Stage stage = app.stage();
        app.refresh();
        stage.act(0f);
        stage.act(0f);
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        buffer.begin();
        ScreenUtils.clear(Theme.SURFACE.r, Theme.SURFACE.g, Theme.SURFACE.b, 1f);
        Gdx.gl.glViewport(0, 0, width, height);
        stage.getViewport().setScreenBounds(0, 0, width, height);
        stage.getViewport().apply(true);
        stage.draw();
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        buffer.end();
        Pixmap flipped = new Pixmap(width, height, pixmap.getFormat());
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            flipped.drawPixmap(pixmap, 0, height - 1 - y, 0, y, width, 1);
        }
        PixmapIO.writePNG(Gdx.files.absolute(file.getAbsolutePath()), flipped);
        flipped.dispose();
        pixmap.dispose();
        buffer.dispose();
    }
}
