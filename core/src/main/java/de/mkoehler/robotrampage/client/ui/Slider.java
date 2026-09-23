package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;

/**
 * A horizontal slider from 0 to 1 of the design: a pill-shaped track, a filled portion up to a round knob that can be
 * dragged or clicked to, mirroring how {@link PillToggle} is built. Fires a {@link ChangeListener.ChangeEvent} while
 * being dragged and when clicked, not when {@link #setValue} sets it from code.
 *
 * @author Mario Koehler
 */
public final class Slider extends Actor {

    private static final float TRACK_HEIGHT = 10f;
    private static final float KNOB_SIZE = 28f;

    private final Drawable track;
    private final Drawable fill;
    private final Drawable knob;
    private float value;

    /**
     * Creates a slider at a starting value.
     *
     * @param shapes the shapes to draw the track, fill and knob with
     * @param value  the starting value, 0 to 1
     */
    Slider(Theme.Shapes shapes, float value) {
        track = shapes.rounded(Theme.LINE, Theme.LINE, 0, (int) TRACK_HEIGHT / 2, null, 0, false);
        fill = shapes.rounded(Theme.ACCENT, Theme.ACCENT, 0, (int) TRACK_HEIGHT / 2, null, 0, false);
        knob = shapes.rounded(Theme.SURFACE_RAISED, Theme.INK, 3, (int) KNOB_SIZE / 2 - 1, null, 0, false);
        this.value = MathUtils.clamp(value, 0f, 1f);
        addListener(new DragListener() {
            @Override
            public void drag(InputEvent event, float x, float y, int pointer) {
                setFromTouch(x);
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                boolean started = super.touchDown(event, x, y, pointer, button);
                if (started) {
                    setFromTouch(x);
                }
                return started;
            }
        });
    }

    /**
     * Moves the knob to wherever the width was just touched or dragged to, and fires a change if that moved it.
     *
     * @param touchX the touch position, local to this actor
     */
    private void setFromTouch(float touchX) {
        float next = MathUtils.clamp(touchX / getWidth(), 0f, 1f);
        if (next != value) {
            value = next;
            ChangeListener.ChangeEvent change = new ChangeListener.ChangeEvent();
            fire(change);
        }
    }

    /**
     * Returns the value.
     *
     * @return the value, 0 to 1
     */
    public float getValue() {
        return value;
    }

    /**
     * Sets the value without firing a change, for example to show what the server or a saved setting says.
     *
     * @param value the value, 0 to 1; clamped if out of range
     */
    public void setValue(float value) {
        this.value = MathUtils.clamp(value, 0f, 1f);
    }

    /**
     * Draws the track, the filled portion up to the knob, and the knob.
     *
     * @param batch       the batch
     * @param parentAlpha the transparency inherited from the parents
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        float reserve = UiKit.SHAPE_RESERVE;
        float trackY = getY() + (KNOB_SIZE - TRACK_HEIGHT) / 2f;
        track.draw(batch, getX(), trackY - reserve, getWidth(), TRACK_HEIGHT + reserve);
        float fillWidth = getWidth() * value;
        if (fillWidth > 0f) {
            fill.draw(batch, getX(), trackY - reserve, fillWidth, TRACK_HEIGHT + reserve);
        }
        float knobX = getX() + (getWidth() - KNOB_SIZE) * value;
        knob.draw(batch, knobX, getY() - reserve, KNOB_SIZE, KNOB_SIZE + reserve);
    }
}
