package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import de.mkoehler.robotrampage.client.audio.AudioKit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a screen needs to build widgets in the look of the design: the generated fonts, the stretchable shapes, and
 * ready-made labels, buttons, fields and panels made from them. There is one instance for the whole game; it owns GPU
 * resources, so it needs an OpenGL context to be created and must be disposed when the game closes.
 * <p>
 * Widgets are plain Scene2D. The screens lay them out in a {@link Theme#VIEW_WIDTH} by {@link Theme#VIEW_HEIGHT} viewport,
 * so the sizes of the mockups can be used as they are.
 *
 * @author Mario Koehler
 */
public final class UiKit implements Disposable {

    private static final int FIELD_PADDING = Theme.SPACE_4;
    private static final float CURSOR_WIDTH = 2f;

    /** The height of a chip's visible pill. */
    public static final int CHIP_HEIGHT = 28;
    /** The rows a stretchable shape keeps below its visible box for the shadow; a shape is this much taller than it looks. */
    public static final int SHAPE_RESERVE = 3;
    /** The height to give a chip in a layout: the pill plus the shape's shadow reserve. */
    public static final int CHIP_CELL_HEIGHT = CHIP_HEIGHT + SHAPE_RESERVE;
    private static final float SELECTION_ALPHA = 0.35f;

    private final Theme.Fonts fonts;
    private final Theme.Shapes shapes = new Theme.Shapes();
    private final Texture pixel;
    private final TextField.TextFieldStyle fieldStyle;
    private final TextField.TextFieldStyle fieldErrorStyle;
    private final TextureAtlas atlas;
    private final Map<String, NinePatchDrawable> shapeCache = new HashMap<>();
    private AudioKit audio;

    /**
     * Generates the fonts, builds the shared shapes and loads the picture atlas.
     *
     * @param fontDirectory the folder with the font files, for example {@code Gdx.files.internal("fonts")}
     * @param fontScale     the ratio of the real screen height to the layout height, see {@link Theme.Fonts#load}
     */
    public UiKit(FileHandle fontDirectory, float fontScale) {
        this.fonts = Theme.Fonts.load(fontDirectory, fontScale);
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.pixel = new Texture(pixmap);
        pixmap.dispose();
        this.fieldStyle = fieldStyle(false);
        this.fieldErrorStyle = fieldStyle(true);
        this.atlas = new TextureAtlas(Gdx.files.internal("textures/game.atlas"));
    }

    /**
     * Gives the kit the sound effects its buttons play on click. Optional: a tool that only needs the widgets (such as
     * {@code BoardSnapshot}) can leave this unset, and buttons simply stay silent.
     *
     * @param audio the sound effects
     */
    public void setAudio(AudioKit audio) {
        this.audio = audio;
    }

    /**
     * Returns the generated fonts.
     *
     * @return the fonts, owned by this object
     */
    public Theme.Fonts fonts() {
        return fonts;
    }

    /**
     * Returns the shapes for panels, buttons and fields.
     *
     * @return the shapes, owned by this object
     */
    public Theme.Shapes shapes() {
        return shapes;
    }

    /**
     * Returns a rectangle of one color that stretches to any size.
     *
     * @param color the color, including its transparency
     * @return the drawable
     */
    public Drawable solid(Color color) {
        return new TextureRegionDrawable(new TextureRegion(pixel)).tint(color);
    }

    /**
     * Creates a label. Styles that the design sets in capitals (the Bungee styles and {@link Theme.TextStyle#LABEL}) have
     * their text turned into capitals.
     *
     * @param text  the text
     * @param style the type style
     * @param color the text color
     * @return the label
     */
    public Label label(String text, Theme.TextStyle style, Color color) {
        Label label = new Label(capitalsIfNeeded(text, style), new Label.LabelStyle(fonts.get(style), color));
        return label;
    }

    /**
     * Changes the text of a label made with {@link #label}, keeping the capitals rule of its style.
     *
     * @param label the label
     * @param style the style the label was made with
     * @param text  the new text
     */
    public void setText(Label label, Theme.TextStyle style, String text) {
        label.setText(capitalsIfNeeded(text, style));
    }

    /**
     * Creates a button in one of the design's three kinds, that plays {@link AudioKit.Clip#BUTTON_CLICK} when clicked —
     * the default "haptic" feedback every button gets unless it already has a more specific sound of its own, in which
     * case the caller uses {@link #button(String, Theme.ButtonKind, Theme.TextStyle, boolean)} instead.
     *
     * @param text  the label, turned into capitals
     * @param kind  the kind of button
     * @param style the type style of the label, normally {@link Theme.TextStyle#BUTTON}
     * @return the button; give it a size with the layout
     */
    public TextButton button(String text, Theme.ButtonKind kind, Theme.TextStyle style) {
        return button(text, kind, style, false);
    }

    /**
     * Creates a button as {@link #button(String, Theme.ButtonKind, Theme.TextStyle)} does, but without the default click
     * sound, for the few buttons whose own action already plays a more specific one (so the two are never heard together).
     *
     * @param text   the label, turned into capitals
     * @param kind   the kind of button
     * @param style  the type style of the label, normally {@link Theme.TextStyle#BUTTON}
     * @param silent {@code true} to skip the default click sound
     * @return the button; give it a size with the layout
     */
    public TextButton button(String text, Theme.ButtonKind kind, Theme.TextStyle style, boolean silent) {
        TextButton button = new TextButton(text.toUpperCase(Locale.ROOT), shapes.button(kind, fonts.get(style)));
        if (!silent) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // A disabled button still receives touch events on a listener added this way (only the button's own
                    // internal listener checks isDisabled() before firing its ChangeEvent), so this one must check too,
                    // or a click that visibly does nothing would still play a sound.
                    if (audio != null && !button.isDisabled()) {
                        audio.play(AudioKit.Clip.BUTTON_CLICK);
                    }
                }
            });
        }
        return button;
    }

    /**
     * Creates a single-line text field in the design's look: a 2 px border, thicker and teal while focused.
     *
     * @param text      the text to start with
     * @param maxLength the most characters that can be typed or pasted
     * @return the field; give it a height of 56 with the layout
     */
    public TextField textField(String text, int maxLength) {
        TextField field = new TextField(text, fieldStyle);
        field.setMaxLength(maxLength);
        return field;
    }

    /**
     * Shows or clears the error look of a field made with {@link #textField}.
     *
     * @param field the field
     * @param error {@code true} for the red border, {@code false} for the normal one
     */
    public void setFieldError(TextField field, boolean error) {
        field.setStyle(error ? fieldErrorStyle : fieldStyle);
    }

    /**
     * Creates the panel every dialog and list sits on: raised paper with a hairline edge and a hard shadow.
     *
     * @return an empty table with the panel as its background
     */
    public Table panel() {
        Table table = new Table();
        table.setBackground(shapes.panel());
        return table;
    }

    /**
     * Creates a small inset box on the page color, as used for the versions in the version dialog and the reason in the
     * error dialogs.
     *
     * @return an empty table with a rounded, hairline-bordered background
     */
    public Table well() {
        Table table = new Table();
        table.setBackground(rounded(Theme.SURFACE, Theme.LINE, Theme.BORDER_HAIRLINE, Theme.RADIUS_MD));
        return table;
    }

    /**
     * Returns a stretchable rounded shape without a shadow. Shapes with the same parameters are built once and shared, so
     * a screen that rebuilds its widgets often does not pile up textures.
     *
     * @param fill        the fill color
     * @param border      the border color
     * @param borderWidth the border width in pixels, 0 for none
     * @param radius      the corner radius in pixels
     * @return the drawable, owned by this kit; do not change its padding
     */
    public NinePatchDrawable rounded(Color fill, Color border, int borderWidth, int radius) {
        String key = fill + "/" + border + "/" + borderWidth + "/" + radius;
        return shapeCache.computeIfAbsent(key, k -> shapes.rounded(fill, border, borderWidth, radius, null, 0, false));
    }

    /**
     * The looks of a status chip.
     */
    public enum ChipKind {
        /** Bordered, on the page color: neutral facts such as an address or "Not ready". */
        OUTLINE,
        /** Ink-colored with white text: counts and roles such as the host. */
        INK,
        /** Orange with white text: the player's own marker. */
        PRIMARY,
        /** Green with white text: something is done or ready. */
        SUCCESS,
        /** Teal with white text: the thing that is happening now. */
        ACCENT,
        /** Red with white text: something bad, such as being out of the game. */
        DANGER
    }

    /**
     * Creates a status chip: a small pill with a few capital letters.
     *
     * @param text the text, turned into capitals
     * @param kind the look
     * @return the chip; place it in a layout with the height {@link #CHIP_CELL_HEIGHT}
     */
    public Table chip(String text, ChipKind kind) {
        Color fill = switch (kind) {
            case OUTLINE -> Theme.SURFACE_RAISED;
            case INK -> Theme.INK;
            case PRIMARY -> Theme.PRIMARY;
            case SUCCESS -> Theme.SUCCESS;
            case ACCENT -> Theme.ACCENT;
            case DANGER -> Theme.DANGER;
        };
        Color border = kind == ChipKind.OUTLINE ? Theme.LINE_STRONG : fill;
        Color textColor = kind == ChipKind.OUTLINE ? Theme.INK_MUTED : Theme.ON_PRIMARY;
        Table chip = new Table();
        chip.setBackground(rounded(fill, border, kind == ChipKind.OUTLINE ? Theme.BORDER_CONTROL : 0, CHIP_HEIGHT / 2));
        chip.padBottom(SHAPE_RESERVE);
        chip.add(label(text, Theme.TextStyle.CHIP, textColor)).padLeft(Theme.SPACE_3).padRight(Theme.SPACE_3);
        return chip;
    }

    /**
     * Builds a row of small squares, the first ones filled and the rest as an outline: the lives a robot has left.
     *
     * @param filled how many squares are filled
     * @param total  how many squares there are
     * @param size   the width and height of a square
     * @param radius the corner radius of a square
     * @return the row
     */
    public Table pips(int filled, int total, float size, int radius) {
        Table row = new Table();
        for (int i = 0; i < total; i++) {
            Image pip = new Image(i < filled ? rounded(Theme.INK, Theme.INK, 0, radius)
                : rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL, radius));
            row.add(pip).size(size, size + SHAPE_RESERVE).padRight(4f);
        }
        return row;
    }

    /**
     * Creates the on/off switch of the design: a pill with a round knob.
     *
     * @return the switch, off
     */
    public PillToggle toggle() {
        return new PillToggle(shapes);
    }

    /**
     * Returns a picture from the shared atlas ({@code assets/textures/game.atlas}, built by the {@code AtlasPacker}
     * dev tool from every PNG under {@code assets/board}, {@code assets/cards}, {@code assets/icons},
     * {@code assets/robots} and {@code assets/tiles} — one texture bind for every picture the game draws, mipmapped
     * since pictures are drawn smaller than they are stored).
     *
     * @param path the path relative to the asset folder, for example {@code robots/robot-1-bolt.png}
     * @return the picture as a drawable, which can also be drawn turned
     * @throws IllegalArgumentException if the atlas has no region for the path (it is missing from {@code assets/}, or
     *                                   the atlas is stale — rerun {@code AtlasPacker}; {@code AtlasCoverageTest} guards
     *                                   against this happening unnoticed)
     */
    public TextureRegionDrawable image(String path) {
        String region = path.endsWith(".png") ? path.substring(0, path.length() - ".png".length()) : path;
        TextureRegion found = atlas.findRegion(region);
        if (found == null) {
            throw new IllegalArgumentException("No atlas region for \"" + path + "\" (looked up as \"" + region + "\")");
        }
        return new TextureRegionDrawable(found);
    }

    /**
     * Builds the style of a text field.
     *
     * @param error whether it is the style for a rejected value
     * @return the style
     */
    private TextField.TextFieldStyle fieldStyle(boolean error) {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = fonts.get(Theme.TextStyle.FIELD);
        style.fontColor = Theme.INK;
        style.messageFontColor = Theme.INK_MUTED;
        style.background = padded(shapes.field(false, error));
        style.focusedBackground = padded(shapes.field(true, error));
        Drawable cursor = solid(Theme.INK);
        cursor.setMinWidth(CURSOR_WIDTH);
        style.cursor = cursor;
        style.selection = solid(new Color(Theme.ACCENT.r, Theme.ACCENT.g, Theme.ACCENT.b, SELECTION_ALPHA));
        return style;
    }

    /**
     * Gives a field background the padding between its border and the text.
     *
     * @param background the stretchable background
     * @return the same background with padding
     */
    private static Drawable padded(NinePatchDrawable background) {
        background.setLeftWidth(FIELD_PADDING);
        background.setRightWidth(FIELD_PADDING);
        return background;
    }

    /**
     * Applies the capitals rule of a style to a text.
     *
     * @param text  the text
     * @param style the style
     * @return the text in capitals if the style is set in capitals, else unchanged
     */
    private static String capitalsIfNeeded(String text, Theme.TextStyle style) {
        boolean capitals = style.file() == Theme.FontFile.BUNGEE || style == Theme.TextStyle.LABEL
            || style == Theme.TextStyle.CHIP;
        return capitals ? text.toUpperCase(Locale.ROOT) : text;
    }

    /**
     * Releases the fonts, the shapes, the shared pixel and the picture atlas.
     */
    @Override
    public void dispose() {
        fonts.dispose();
        shapes.dispose();
        pixel.dispose();
        atlas.dispose();
        shapeCache.clear();
    }
}
