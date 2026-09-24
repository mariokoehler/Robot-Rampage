package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;

/**
 * Explanations that appear next to the mouse pointer while it rests on a widget: a panel with a heading and a wrapped
 * paragraph, shown at once and without the zoom animation of Scene2D's default tooltips. Every tooltip attached through
 * one instance shares its manager, so {@link #hideAll()} clears all of them, which a screen must do before it switches
 * layout or opens a dialog, since a shown tooltip sits on the stage root above everything else.
 *
 * @author Mario Koehler
 */
public final class InfoTooltips {

    /** The width the paragraph wraps at. */
    public static final float TEXT_WIDTH = 420f;

    private static final float FADE_SECONDS = 0.1f;

    private final UiKit ui;
    private final TooltipManager manager = new TooltipManager() {

        /**
         * Fades the tooltip in quickly instead of zooming it up from a dot.
         *
         * @param tooltip the tooltip being shown
         */
        @Override
        protected void showAction(Tooltip tooltip) {
            tooltip.getContainer().setTransform(false);
            tooltip.getContainer().getColor().a = 0f;
            tooltip.getContainer().addAction(Actions.fadeIn(FADE_SECONDS));
        }

        /**
         * Takes the tooltip away at once, so it never lingers over the next thing the pointer rests on.
         *
         * @param tooltip the tooltip being hidden
         */
        @Override
        protected void hideAction(Tooltip tooltip) {
            tooltip.getContainer().clearActions();
            tooltip.getContainer().remove();
        }
    };

    /**
     * Creates a set of tooltips drawn with a widget kit.
     *
     * @param ui the widget kit
     */
    public InfoTooltips(UiKit ui) {
        this.ui = ui;
        manager.initialTime = 0f;
        manager.subsequentTime = 0f;
    }

    /**
     * Gives a widget an explanation that appears while the pointer rests anywhere on it. The widget is made touchable,
     * so a {@link Table} reacts in the gaps between its children too, not only on them.
     *
     * @param target the widget
     * @param title  the heading of the explanation
     * @param text   the explanation, one paragraph
     * @return the tooltip, already attached
     */
    public Tooltip<Table> attach(Actor target, String title, String text) {
        Table content = ui.panel();
        content.pad(Theme.SPACE_4).padBottom(Theme.SPACE_4 + UiKit.SHAPE_RESERVE);
        content.add(ui.label(title, Theme.TextStyle.NAME, Theme.INK)).left().row();
        Label paragraph = ui.label(text, Theme.TextStyle.BODY, Theme.INK);
        paragraph.setWrap(true);
        content.add(paragraph).width(TEXT_WIDTH).left().padTop(Theme.SPACE_2);
        content.pack();
        Tooltip<Table> tooltip = new Tooltip<>(content, manager);
        tooltip.setInstant(true);
        target.setTouchable(Touchable.enabled);
        target.addListener(tooltip);
        return tooltip;
    }

    /**
     * Takes away every tooltip that is showing.
     */
    public void hideAll() {
        manager.hideAll();
    }
}
