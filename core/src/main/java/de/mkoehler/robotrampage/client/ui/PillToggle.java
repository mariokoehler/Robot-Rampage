package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Pools;

/**
 * The on/off switch of the design: a 52 by 28 pill with a round knob, teal when on. Clicking it flips it and fires a
 * {@link ChangeListener.ChangeEvent}; setting it from code with {@link #setChecked} does not, so a screen can show what
 * the server says without answering it.
 *
 * @author Mario Koehler
 */
public final class PillToggle extends Actor {

    private static final float WIDTH = 52f;
    private static final float HEIGHT = 28f;
    private static final float KNOB_SIZE = 20f;
    private static final float KNOB_INSET = 4f;

    private final Drawable trackOff;
    private final Drawable trackOn;
    private final Drawable knob;
    private boolean checked;

    /**
     * Creates a switch that is off.
     *
     * @param shapes the shapes to draw the pill and the knob with
     */
    PillToggle(Theme.Shapes shapes) {
        trackOff = shapes.rounded(Theme.SURFACE, Theme.LINE_STRONG, Theme.BORDER_CONTROL, (int) HEIGHT / 2, null, 0, false);
        trackOn = shapes.rounded(Theme.ACCENT, Theme.ACCENT, Theme.BORDER_CONTROL, (int) HEIGHT / 2, null, 0, false);
        knob = shapes.rounded(Theme.SURFACE_RAISED, Theme.INK, Theme.BORDER_CONTROL, (int) KNOB_SIZE / 2 - 1, null, 0, false);
        setSize(WIDTH, HEIGHT);
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                checked = !checked;
                ChangeListener.ChangeEvent change = Pools.obtain(ChangeListener.ChangeEvent.class);
                fire(change);
                Pools.free(change);
            }
        });
    }

    /**
     * Returns whether the switch is on.
     *
     * @return {@code true} if on
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Sets the switch without firing an event.
     *
     * @param on whether it is on
     */
    public void setChecked(boolean on) {
        this.checked = on;
    }

    /**
     * Draws the pill and the knob. The stretchable shapes are three rows taller than they look, so they are drawn that much
     * lower to put their visible box on the actor.
     *
     * @param batch       the batch
     * @param parentAlpha the transparency inherited from the parents
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        float reserve = UiKit.SHAPE_RESERVE;
        (checked ? trackOn : trackOff).draw(batch, getX(), getY() - reserve, getWidth(), getHeight() + reserve);
        float knobX = getX() + (checked ? getWidth() - KNOB_INSET - KNOB_SIZE : KNOB_INSET);
        knob.draw(batch, knobX, getY() + KNOB_INSET - reserve, KNOB_SIZE, KNOB_SIZE + reserve);
    }
}
