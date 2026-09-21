package de.mkoehler.robotrampage.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.render.BoardActor;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * A development tool, not part of the game: draws a board with a robot on every start square into a PNG through a hidden
 * window, so that the board renderer can be looked at without starting a game. Run it with the {@code assets} folder as the
 * working directory; the board is a classpath resource or the path of a board file:
 * <pre>java -cp ... de.mkoehler.robotrampage.lwjgl3.BoardSnapshot [board resource] [output png] [tile size]</pre>
 *
 * @author Mario Koehler
 */
public final class BoardSnapshot {

    private static final int WIDTH = Theme.VIEW_WIDTH;
    private static final int HEIGHT = Theme.VIEW_HEIGHT;

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private BoardSnapshot() {
    }

    /**
     * Renders the board and exits.
     *
     * @param args the board resource (default {@code boards/proving-grounds.json}), the output file (default
     *             {@code board-snapshot.png}) and the size of a square in pixels (default 50)
     */
    public static void main(String[] args) {
        String resource = args.length > 0 ? args[0] : "boards/proving-grounds.json";
        String output = args.length > 1 ? args[1] : "board-snapshot.png";
        float tile = args.length > 2 ? Float.parseFloat(args[2]) : 50f;
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setInitialVisible(false);
        configuration.setWindowedMode(WIDTH, HEIGHT);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                snapshot(resource, output, tile);
                Gdx.app.exit();
            }
        }, configuration);
    }

    /**
     * Draws the board into an off-screen buffer and writes it out.
     *
     * @param resource the board resource
     * @param output   the file to write
     * @param tile     the size of a square
     */
    private static void snapshot(String resource, String output, float tile) {
        UiKit ui = new UiKit(Gdx.files.internal("fonts"), 1f);
        Board board = load(resource);
        BoardActor actor = new BoardActor(ui, board, tile);
        List<RobotPose> robots = new ArrayList<>();
        List<StartSquare> starts = board.startSquares();
        for (int seat = 0; seat < starts.size(); seat++) {
            robots.add(RobotPose.at(seat, starts.get(seat).position(), starts.get(seat).facing()));
        }
        actor.setRobots(robots);
        actor.setPosition(40f, 40f);

        Stage stage = new Stage(new FitViewport(WIDTH, HEIGHT));
        stage.addActor(actor);
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        buffer.begin();
        ScreenUtils.clear(Theme.SURFACE.r, Theme.SURFACE.g, Theme.SURFACE.b, 1f);
        Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
        stage.getViewport().setScreenBounds(0, 0, WIDTH, HEIGHT);
        stage.getViewport().apply(true);
        stage.draw();
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        buffer.end();
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(output).getAbsolutePath()), flipped(pixmap));
        pixmap.dispose();
        buffer.dispose();
        stage.dispose();
        ui.dispose();
    }

    /**
     * Loads the board, from a file if the argument names one and from the classpath otherwise.
     *
     * @param source a file path or a classpath resource
     * @return the board
     */
    private static Board load(String source) {
        java.io.File file = new java.io.File(source);
        if (file.isFile()) {
            try {
                return BoardLoader.parse(java.nio.file.Files.readString(file.toPath())).board();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read " + file, e);
            }
        }
        return BoardLoader.loadResource(source).board();
    }

    /**
     * Turns a picture read back from the graphics card, which starts at the bottom row, the right way up.
     *
     * @param source the picture as read
     * @return a new picture with the rows in reverse order, which the caller does not need to dispose
     */
    private static Pixmap flipped(Pixmap source) {
        Pixmap result = new Pixmap(source.getWidth(), source.getHeight(), source.getFormat());
        result.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < source.getHeight(); y++) {
            result.drawPixmap(source, 0, source.getHeight() - 1 - y, 0, y, source.getWidth(), 1);
        }
        return result;
    }
}
