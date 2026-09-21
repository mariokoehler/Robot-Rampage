package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
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

    private static final String TOAST_NAME = "toast";
    private static final float TOAST_BOTTOM = 140f;
    private static final float TOAST_SECONDS = 4f;
    private static final float TOAST_FADE = 0.2f;

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
     * Shows a short message at the bottom of the screen that fades away by itself, for example why the server refused a
     * request. A new message replaces the one still showing.
     *
     * @param message the message
     */
    protected void toast(String message) {
        Actor showing = stage.getRoot().findActor(TOAST_NAME);
        if (showing != null) {
            showing.remove();
        }
        Table toast = ui.panel();
        toast.setName(TOAST_NAME);
        toast.padTop(Theme.SPACE_3).padLeft(Theme.SPACE_6).padRight(Theme.SPACE_6).padBottom(Theme.SPACE_3 + UiKit.SHAPE_RESERVE);
        toast.add(ui.label(message, Theme.TextStyle.BODY_LARGE, Theme.INK));
        toast.pack();
        toast.setPosition((Theme.VIEW_WIDTH - toast.getWidth()) / 2f, TOAST_BOTTOM);
        toast.getColor().a = 0f;
        toast.addAction(Actions.sequence(Actions.fadeIn(TOAST_FADE), Actions.delay(TOAST_SECONDS),
            Actions.fadeOut(TOAST_FADE), Actions.removeActor()));
        stage.addActor(toast);
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
