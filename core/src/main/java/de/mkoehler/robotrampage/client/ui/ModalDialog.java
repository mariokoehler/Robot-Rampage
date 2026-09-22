package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import java.util.Arrays;

/**
 * A dialog on top of a screen: the screen dims behind a scrim, a panel with a title, some content and a row of buttons
 * sits in the middle, and nothing behind it can be clicked while it is open.
 * <p>
 * Dialogs are built with a fixed width, filled from top to bottom, and then {@linkplain #show shown}. A dialog that
 * reports an error, a warning or something else worth a glance can carry a coloured bar at the top of its panel.
 *
 * @author Mario Koehler
 */
public final class ModalDialog {

    private static final float STRIPE_HEIGHT = 6f;
    private static final float ROW_GAP = 20f;

    private final UiKit kit;
    private final Table root = new Table();
    private final Table panel;
    private final float contentWidth;
    private Runnable onEscape;

    /**
     * Creates an empty dialog.
     *
     * @param kit    the widget kit
     * @param width  the width of the panel in layout pixels
     * @param stripe the colour of the bar at the top of the panel, or {@code null} for none
     */
    public ModalDialog(UiKit kit, float width, Color stripe) {
        this.kit = kit;
        this.contentWidth = width - 2 * Theme.SPACE_8;
        root.setFillParent(true);
        root.setBackground(kit.solid(Theme.SCRIM));
        root.setTouchable(Touchable.enabled);
        root.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE && onEscape != null) {
                    onEscape.run();
                }
                return true;
            }
        });
        panel = kit.panel();
        panel.pad(Theme.SPACE_8).top();
        if (stripe != null) {
            panel.add(new Image(kit.solid(stripe))).width(contentWidth).height(STRIPE_HEIGHT).padBottom(ROW_GAP).row();
        }
        root.add(panel).width(width);
    }

    /**
     * Returns the width of the area inside the panel's padding.
     *
     * @return the width in layout pixels
     */
    public float contentWidth() {
        return contentWidth;
    }

    /**
     * Adds the title, in the display face.
     *
     * @param text the title
     * @return this dialog
     */
    public ModalDialog title(String text) {
        Label label = kit.label(text, Theme.TextStyle.SUBTITLE, Theme.INK);
        label.setWrap(true);
        panel.add(label).width(contentWidth).left().padBottom(ROW_GAP).row();
        return this;
    }

    /**
     * Adds a paragraph of text that wraps at the width of the dialog.
     *
     * @param text  the text
     * @param style the type style
     * @param color the text color
     * @return this dialog
     */
    public ModalDialog text(String text, Theme.TextStyle style, Color color) {
        Label label = kit.label(text, style, color);
        label.setWrap(true);
        panel.add(label).width(contentWidth).left().padBottom(ROW_GAP).row();
        return this;
    }

    /**
     * Adds any widget as a row of its own, as wide as the content area.
     *
     * @param actor the widget
     * @return this dialog
     */
    public ModalDialog row(Actor actor) {
        panel.add(actor).width(contentWidth).left().padBottom(ROW_GAP).row();
        return this;
    }

    /**
     * Adds the row of buttons at the bottom, right-aligned and 16 px apart, all the same width.
     *
     * @param width   the width of each button
     * @param buttons the buttons, the main action last
     * @return this dialog
     */
    public ModalDialog buttons(float width, TextButton... buttons) {
        float[] widths = new float[buttons.length];
        Arrays.fill(widths, width);
        return buttons(widths, buttons);
    }

    /**
     * Adds the row of buttons at the bottom, right-aligned and 16 px apart, each with its own width.
     *
     * @param widths  the width of each button, same length and order as {@code buttons}
     * @param buttons the buttons, the main action last
     * @return this dialog
     */
    public ModalDialog buttons(float[] widths, TextButton... buttons) {
        Table row = new Table();
        row.right();
        for (int i = 0; i < buttons.length; i++) {
            row.add(buttons[i]).size(widths[i], 52f).padLeft(i == 0 ? 0 : Theme.SPACE_4);
        }
        panel.add(row).width(contentWidth).right().padTop(Theme.SPACE_2);
        return this;
    }

    /**
     * Sets what happens when the player presses Escape while the dialog is open.
     *
     * @param action what to do, for example closing the dialog
     * @return this dialog
     */
    public ModalDialog onEscape(Runnable action) {
        this.onEscape = action;
        return this;
    }

    /**
     * Opens the dialog on a stage and gives it the keyboard.
     *
     * @param stage the stage of the screen behind it
     */
    public void show(Stage stage) {
        stage.addActor(root);
        stage.setKeyboardFocus(root);
    }

    /**
     * Closes the dialog.
     */
    public void hide() {
        root.remove();
    }
}
