package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * A thin bar with rounded ends that is filled from the left, such as the time left to program: a pale track and a teal
 * fill of the fraction that remains.
 *
 * @author Mario Koehler
 */
public final class ProgressPill extends Actor {

    private final Drawable track;
    private final Drawable fill;
    private float fraction = 1f;

    /**
     * Creates a full bar.
     *
     * @param ui     the widget kit that provides the shapes
     * @param height the height of the bar; the ends are half circles of this height
     */
    public ProgressPill(UiKit ui, int height) {
        this.track = ui.rounded(Theme.LINE, Theme.LINE, 0, height / 2);
        this.fill = ui.rounded(Theme.ACCENT, Theme.ACCENT, 0, height / 2);
        setHeight(height);
    }

    /**
     * Sets how much of the bar is filled.
     *
     * @param value from 0 (empty) to 1 (full)
     */
    public void setFraction(float value) {
        this.fraction = Math.max(0f, Math.min(1f, value));
    }

    /**
     * Draws the track and, over it, the fill.
     *
     * @param batch       the batch
     * @param parentAlpha the transparency inherited from the parents
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        float reserve = UiKit.SHAPE_RESERVE;
        track.draw(batch, getX(), getY() - reserve, getWidth(), getHeight() + reserve);
        if (fraction > 0f) {
            float width = Math.max(getHeight(), getWidth() * fraction);
            fill.draw(batch, getX(), getY() - reserve, width, getHeight() + reserve);
        }
    }
}
