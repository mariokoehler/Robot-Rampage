package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

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
    private final Map<String, Texture> images = new HashMap<>();
    private final Map<String, NinePatchDrawable> shapeCache = new HashMap<>();

    /**
     * Generates the fonts and builds the shared shapes.
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
     * Creates a button in one of the design's three kinds.
     *
     * @param text  the label, turned into capitals
     * @param kind  the kind of button
     * @param style the type style of the label, normally {@link Theme.TextStyle#BUTTON}
     * @return the button; give it a size with the layout
     */
    public TextButton button(String text, Theme.ButtonKind kind, Theme.TextStyle style) {
        return new TextButton(text.toUpperCase(Locale.ROOT), shapes.button(kind, fonts.get(style)));
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
        SUCCESS
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
     * Creates the on/off switch of the design: a pill with a round knob.
     *
     * @return the switch, off
     */
    public PillToggle toggle() {
        return new PillToggle(shapes);
    }

    /**
     * Returns a picture from the asset folder, loaded on first use and kept until this kit is disposed. Pictures are
     * drawn smaller than they are stored, so they are filtered with mipmaps.
     *
     * @param path the path relative to the asset folder, for example {@code robots/robot-1-bolt.png}
     * @return the picture as a drawable, which can also be drawn turned
     */
    public TextureRegionDrawable image(String path) {
        Texture texture = images.computeIfAbsent(path, key -> {
            Texture loaded = new Texture(Gdx.files.internal(key), true);
            loaded.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            return loaded;
        });
        return new TextureRegionDrawable(new TextureRegion(texture));
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
     * Releases the fonts, the shapes and the shared pixel.
     */
    @Override
    public void dispose() {
        fonts.dispose();
        shapes.dispose();
        pixel.dispose();
        images.values().forEach(Texture::dispose);
        images.clear();
        shapeCache.clear();
    }
}
