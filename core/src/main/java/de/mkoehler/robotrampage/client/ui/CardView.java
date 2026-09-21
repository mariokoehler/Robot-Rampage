package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;
import de.mkoehler.robotrampage.client.game.CardLook;
import de.mkoehler.robotrampage.rules.Card;

/**
 * Builds the widgets of the programming cards: a card with its priority, picture and name, the empty numbered slot of a
 * register, and the grey stand-in that keeps the place of a card of the hand that sits in a register.
 *
 * @author Mario Koehler
 */
public final class CardView {

    /**
     * The width of a card in layout pixels.
     */
    public static final float WIDTH = 104f;
    /**
     * The height of a card in layout pixels.
     */
    public static final float HEIGHT = 144f;
    /**
     * The height to give a card in a layout: the card plus the shape's shadow reserve.
     */
    public static final float CELL_HEIGHT = HEIGHT + UiKit.SHAPE_RESERVE;

    private static final float PICTURE = 56f;

    /**
     * The looks of a card.
     */
    public enum Look {
        /** A card that can be picked or taken back. */
        NORMAL,
        /** A card in a register that damage has locked. */
        LOCKED
    }

    private final UiKit ui;
    private final Drawable normal;
    private final Drawable hover;
    private final Drawable locked;
    private final Drawable slot;
    private final Drawable standIn;

    /**
     * Creates the factory.
     *
     * @param ui the widget kit
     */
    public CardView(UiKit ui) {
        this.ui = ui;
        this.normal = ui.rounded(Theme.SURFACE_RAISED, Theme.LINE_STRONG, Theme.BORDER_CONTROL, Theme.RADIUS_SM);
        this.hover = ui.rounded(Theme.SURFACE_RAISED, Theme.ACCENT, Theme.BORDER_HEAVY, Theme.RADIUS_SM);
        this.locked = ui.rounded(Theme.FLOOR, Theme.LINE_STRONG, Theme.BORDER_CONTROL, Theme.RADIUS_SM);
        this.slot = ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL, Theme.RADIUS_SM);
        this.standIn = ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE, Theme.BORDER_CONTROL, Theme.RADIUS_SM);
    }

    /**
     * Builds a card.
     *
     * @param card the card
     * @param look how it looks
     * @return the card; give it the size {@link #WIDTH} by {@link #CELL_HEIGHT}
     */
    public Table card(Card card, Look look) {
        Table table = new Table();
        table.setBackground(look == Look.LOCKED ? locked : normal);
        table.pad(8f, 6f, 10f + UiKit.SHAPE_RESERVE, 6f);
        table.add(ui.label(String.valueOf(card.priority()), Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left().expandX().row();
        Image picture = new Image(ui.image(CardLook.picture(card.type())));
        picture.setScaling(Scaling.fit);
        table.add(picture).size(PICTURE).expandY().row();
        table.add(ui.label(CardLook.name(card.type()), Theme.TextStyle.CAPTION, Theme.INK));
        return table;
    }

    /**
     * Builds the empty slot of a free register, showing its number large.
     *
     * @param register the register number, 1 to 5
     * @return the slot; give it the size {@link #WIDTH} by {@link #CELL_HEIGHT}
     */
    public Table emptySlot(int register) {
        Table table = new Table();
        table.setBackground(slot);
        table.padBottom(UiKit.SHAPE_RESERVE);
        table.add(ui.label(String.valueOf(register), Theme.TextStyle.DISPLAY, Theme.LINE_STRONG));
        return table;
    }

    /**
     * Builds the slot of a register whose card the player cannot see, for a program the server filled in or that was locked in
     * before the player came back.
     *
     * @param note a word under the question mark, for example {@code Hidden}
     * @return the slot; give it the size {@link #WIDTH} by {@link #CELL_HEIGHT}
     */
    public Table hiddenSlot(String note) {
        Table table = new Table();
        table.setBackground(locked);
        table.padBottom(UiKit.SHAPE_RESERVE);
        table.add(ui.label("?", Theme.TextStyle.DISPLAY, Theme.LINE_STRONG)).row();
        table.add(ui.label(note, Theme.TextStyle.CAPTION, Theme.INK_MUTED));
        return table;
    }

    /**
     * Builds the stand-in that stays in the hand where a card was that is now in a register.
     *
     * @return the stand-in; give it the size {@link #WIDTH} by {@link #CELL_HEIGHT}
     */
    public Table standIn() {
        Table table = new Table();
        table.setBackground(standIn);
        table.padBottom(UiKit.SHAPE_RESERVE);
        table.add(ui.label("In program", Theme.TextStyle.CAPTION, Theme.INK_MUTED));
        return table;
    }

    /**
     * Makes a card react to the mouse: it gets a teal border while the mouse is over it, and a click runs the action.
     *
     * @param card    a card made by {@link #card}
     * @param onClick what to do when it is clicked
     */
    public void makeClickable(Table card, Runnable onClick) {
        card.setTouchable(Touchable.enabled);
        card.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                if (pointer == -1) {
                    card.setBackground(hover);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                if (pointer == -1) {
                    card.setBackground(normal);
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClick.run();
            }
        });
    }
}
