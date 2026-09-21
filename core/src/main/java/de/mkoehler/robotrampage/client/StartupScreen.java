package de.mkoehler.robotrampage.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import de.mkoehler.robotrampage.net.AppVersion;

/**
 * Placeholder screen shown at startup: the game title and build version on a
 * dark background. Proves the client window, libGDX natives and resource
 * filtering all work end to end; replaced once real screens exist.
 *
 * @author Mario Koehler
 */
public class StartupScreen extends ScreenAdapter {

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();

    /**
     * Draws the title and version centered on the window.
     *
     * @param delta seconds since the previous frame, unused
     */
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float centerX = Gdx.graphics.getWidth() / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;
        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(3f);
        font.draw(batch, "ROBOT RAMPAGE", centerX - 150f, centerY + 20f);
        font.getData().setScale(1f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "v" + AppVersion.getVersion(), centerX - 40f, centerY - 30f);
        batch.end();
    }

    /**
     * Keeps the batch's projection in sync with the window size.
     *
     * @param width  the new window width in pixels
     * @param height the new window height in pixels
     */
    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /**
     * Releases the GPU resources owned by this screen.
     */
    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
