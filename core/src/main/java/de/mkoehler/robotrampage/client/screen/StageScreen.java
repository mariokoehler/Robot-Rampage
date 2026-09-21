package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

/**
 * The base of the screens built from Scene2D widgets. It owns the {@link Stage}, whose layout is
 * {@value Theme#VIEW_WIDTH} by {@value Theme#VIEW_HEIGHT} pixels whatever the window size (letterboxed on the page color),
 * clears the window to the page color, and routes the input to the stage while the screen is shown.
 *
 * @author Mario Koehler
 */
abstract class StageScreen extends ScreenAdapter {

    /** The game that shows this screen and owns the shared resources. */
    protected final RobotRampageGame game;
    /** The widget kit of the game. */
    protected final UiKit ui;
    /** The stage every widget of this screen lives on. */
    protected final Stage stage;

    /**
     * Creates the stage of a screen.
     *
     * @param game the game showing the screen
     */
    protected StageScreen(RobotRampageGame game) {
        this.game = game;
        this.ui = game.ui();
        this.stage = new Stage(new FitViewport(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT));
    }

    /**
     * Called once a frame before the stage is updated and drawn; the place for anything a screen does on its own, such as
     * reading a network connection.
     *
     * @param delta seconds since the previous frame
     */
    protected void update(float delta) {
    }

    /**
     * Starts sending the input to the stage.
     */
    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    /**
     * Stops sending input to the stage.
     */
    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    /**
     * Updates the screen and draws it.
     *
     * @param delta seconds since the previous frame
     */
    @Override
    public void render(float delta) {
        update(delta);
        Gdx.gl.glClearColor(Theme.SURFACE.r, Theme.SURFACE.g, Theme.SURFACE.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    /**
     * Fits the layout to the new window size.
     *
     * @param width  the new window width in pixels
     * @param height the new window height in pixels
     */
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /**
     * Releases the stage.
     */
    @Override
    public void dispose() {
        stage.dispose();
    }
}
