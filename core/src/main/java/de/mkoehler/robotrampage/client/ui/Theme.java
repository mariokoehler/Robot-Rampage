package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The look of Robot Rampage in code: colors, spacing, radii, borders, shadows
 * and type styles, taken from the project's design system (the "Workshop Paper"
 * light theme with the Bungee and Barlow typefaces).
 * <p>
 * All pixel values are in the design's virtual resolution of
 * {@value #VIEW_WIDTH} by {@value #VIEW_HEIGHT}. Lay screens out in a
 * {@code FitViewport} of that size and every number here can be used as is.
 * Sizes that fall between the spacing steps are not allowed: use
 * {@code SPACE_*}.
 * <p>
 * The {@link Color} constants are shared instances. Never call a mutating
 * method such as {@code set}, {@code mul} or {@code a} on them; copy first with
 * {@code new Color(Theme.INK)}.
 * <p>
 * Text is drawn with two typefaces: {@link TextStyle#DISPLAY Bungee} for
 * titles and button labels (capitals only, never paragraphs) and Barlow for
 * everything that is read. {@link Fonts#load} generates one {@link BitmapFont}
 * per {@link TextStyle} with gdx-freetype, and {@link Shapes} builds the
 * rounded panels, buttons and fields with their hard, blur-free shadows.
 *
 * @author Mario Koehler
 */
public final class Theme {

    // ------------------------------------------------------------------ view

    /**
     * Width, in pixels, of the virtual screen the design is drawn for.
     */
    public static final int VIEW_WIDTH = 1920;

    /**
     * Height, in pixels, of the virtual screen the design is drawn for.
     */
    public static final int VIEW_HEIGHT = 1080;

    // ---------------------------------------------------------------- colors

    /**
     * Window and screen background: putty-colored blueprint paper.
     */
    public static final Color SURFACE = Color.valueOf("#e8e2d3");

    /**
     * Panels that sit on {@link #SURFACE}: the programming panel, dialogs and
     * the lobby list.
     */
    public static final Color SURFACE_RAISED = Color.valueOf("#f7f3ea");

    /**
     * Hairlines and dividers. Decorative only, never the sole edge of a
     * control.
     */
    public static final Color LINE = Color.valueOf("#c2b9a2");

    /**
     * Borders of controls and empty register slots.
     */
    public static final Color LINE_STRONG = Color.valueOf("#857c66");

    /**
     * The plain squares of the game board.
     */
    public static final Color FLOOR = Color.valueOf("#d8d1bd");

    /**
     * Conveyor-belt squares. Draw the chevrons in {@link #ACCENT}, and the
     * chevrons of express belts in {@link #PRIMARY}.
     */
    public static final Color BELT = Color.valueOf("#c3ccca");

    /**
     * The fill of pit squares, ringed in {@link #DANGER}.
     */
    public static final Color PIT = Color.valueOf("#3a3630");

    /**
     * Primary text and icons; also the color of board walls.
     */
    public static final Color INK = Color.valueOf("#23262b");

    /**
     * Secondary text: hints, versions and labels.
     */
    public static final Color INK_MUTED = Color.valueOf("#50555d");

    /**
     * Rust orange for the single main action on a screen, and for flags and
     * express belts. As text only on {@link #SURFACE_RAISED} or at 24 px and
     * up.
     */
    public static final Color PRIMARY = Color.valueOf("#c93f0a");

    /**
     * Text and icons on a {@link #PRIMARY} or {@link #DANGER} fill.
     */
    public static final Color ON_PRIMARY = Color.valueOf("#ffffff");

    /**
     * Teal for information and structure: belt chevrons, gears, the turn
     * timer and selected cards.
     */
    public static final Color ACCENT = Color.valueOf("#186878");

    /**
     * Lasers, damage, pits and destruction.
     */
    public static final Color DANGER = Color.valueOf("#b81f27");

    /**
     * Repair sites, confirmed players and other positive states.
     */
    public static final Color SUCCESS = Color.valueOf("#216c39");

    /**
     * The dark outline drawn around every robot.
     */
    public static final Color ROBOT_OUTLINE = Color.valueOf("#15161a");

    /**
     * The dimming layer drawn over a screen behind a dialog: {@link #INK} at
     * 55 percent. Not part of the design system's token file.
     */
    public static final Color SCRIM = Color.valueOf("#23262b8c");

    /**
     * The color of the hard edge under a pressed-style control: black at 35
     * percent.
     */
    public static final Color SHADOW_PRESS = Color.valueOf("#00000059");

    /**
     * The eight seat colors, an Okabe-Ito color-blind-safe set, indexed by seat
     * number minus one.
     */
    private static final Color[] ROBOT = {
        Color.valueOf("#e69f00"), Color.valueOf("#56b4e9"), Color.valueOf("#009e73"), Color.valueOf("#f0e442"),
        Color.valueOf("#0072b2"), Color.valueOf("#d55e00"), Color.valueOf("#cc79a7"), Color.valueOf("#eeeeee")
    };

    /**
     * The number of seats, and so of robot colors.
     */
    public static final int SEATS = ROBOT.length;

    // --------------------------------------------------------------- spacing

    /** Gap between an icon or number and its label; inside pips and chips. */
    public static final int SPACE_1 = 4;

    /** Gap between register slots and between hand cards; inside small controls. */
    public static final int SPACE_2 = 8;

    /** Between related rows and inside cards. */
    public static final int SPACE_3 = 12;

    /** Padding inside panels and buttons; gap between controls. */
    public static final int SPACE_4 = 16;

    /** Between panels and between sections of a screen. */
    public static final int SPACE_6 = 24;

    /** Screen margins. */
    public static final int SPACE_8 = 32;

    /** Around the title on the startup screen; the largest step. */
    public static final int SPACE_12 = 48;

    // ---------------------------------------------------------------- radii

    /** Register slots, chips, pips and cards. */
    public static final int RADIUS_SM = 6;

    /** Buttons and text fields. */
    public static final int RADIUS_MD = 10;

    /** Panels, dialogs and large blocks. */
    public static final int RADIUS_LG = 16;

    /**
     * Timers, badges and toggles: as round as the element is tall. Shapes
     * built by {@link Shapes} cap their corner radius, so use this only where
     * you draw the corner yourself.
     */
    public static final int RADIUS_PILL = 999;

    // --------------------------------------------------------------- borders

    /** Dividers and board tile edges, in {@link #LINE}. */
    public static final int BORDER_HAIRLINE = 1;

    /** Buttons, register slots and selected cards. */
    public static final int BORDER_CONTROL = 2;

    /** Robot outlines and the laser core; focus and error borders of fields. */
    public static final int BORDER_HEAVY = 3;

    /**
     * How far, in pixels, the hard shadow under buttons and hand cards sits
     * below the control. A pressed control loses it and moves down by the same
     * amount.
     */
    public static final int SHADOW_PRESS_DY = 3;

    /**
     * How far, in pixels, the hard shadow under panels sits below the panel.
     */
    public static final int SHADOW_PANEL_DY = 3;

    /**
     * Not meant to be instantiated: this class only holds constants and
     * factories.
     */
    private Theme() {
    }

    /**
     * Returns the color of a seat's robot.
     *
     * @param seat the seat number, from 1 to {@link #SEATS}
     * @return the shared seat color; never modify it
     * @throws IllegalArgumentException if the seat is out of range
     */
    public static Color robotColor(int seat) {
        if (seat < 1 || seat > SEATS) {
            throw new IllegalArgumentException("Seat must be between 1 and " + SEATS + ": " + seat);
        }
        return ROBOT[seat - 1];
    }

    // ------------------------------------------------------------------ text

    /**
     * The font files, relative to the directory passed to {@link Fonts#load}.
     */
    public enum FontFile {
        /** Bungee Regular, the display face. One weight only. */
        BUNGEE("Bungee-Regular.ttf"),
        /** Barlow Regular, weight 400. */
        BARLOW_REGULAR("Barlow-Regular.ttf"),
        /** Barlow SemiBold, weight 600. */
        BARLOW_SEMIBOLD("Barlow-SemiBold.ttf"),
        /** Barlow Bold, weight 700. */
        BARLOW_BOLD("Barlow-Bold.ttf");

        private final String fileName;

        /**
         * Creates a font file entry.
         *
         * @param fileName the file name inside the fonts directory
         */
        FontFile(String fileName) {
            this.fileName = fileName;
        }

        /**
         * Returns the file name inside the fonts directory.
         *
         * @return the file name, such as {@code Bungee-Regular.ttf}
         */
        public String fileName() {
            return fileName;
        }
    }

    /**
     * The type scale. The first six styles are the design system's; the last
     * four are the extra sizes the screen mockups use for big titles, intro
     * text and player names.
     */
    public enum TextStyle {
        /** The game title and the biggest titles (Game Over): Bungee 56. */
        DISPLAY(FontFile.BUNGEE, 56, 0f),
        /** Screen and panel headings: Bungee 28. */
        HEADING(FontFile.BUNGEE, 28, 0f),
        /** Button labels: Bungee 18, letter-spaced by 0.02 em. */
        BUTTON(FontFile.BUNGEE, 18, 0.02f),
        /** Running text, lobby entries and card descriptions: Barlow 16. */
        BODY(FontFile.BARLOW_REGULAR, 16, 0f),
        /** Small capital headings and toggle names: Barlow SemiBold 14, letter-spaced by 0.06 em. */
        LABEL(FontFile.BARLOW_SEMIBOLD, 14, 0.06f),
        /** Versions, hints and metadata: Barlow 13, the smallest size. */
        CAPTION(FontFile.BARLOW_REGULAR, 13, 0f),
        /** Screen titles such as Lobby and Connect: Bungee 72. Not in the design system's token file. */
        TITLE(FontFile.BUNGEE, 72, 0f),
        /** The title on the startup screen: Bungee 132. Not in the design system's token file. */
        HERO(FontFile.BUNGEE, 132, 0f),
        /** Intro text under a title: Barlow 24. Not in the design system's token file. */
        LEAD(FontFile.BARLOW_REGULAR, 24, 0f),
        /** Player names in lists: Barlow Bold 24. Not in the design system's token file. */
        NAME(FontFile.BARLOW_BOLD, 24, 0f);

        private final FontFile file;
        private final int size;
        private final float letterSpacingEm;

        /**
         * Creates a text style.
         *
         * @param file            the font file the style uses
         * @param size            the size in pixels at the virtual resolution
         * @param letterSpacingEm extra space between letters, as a fraction of the size
         */
        TextStyle(FontFile file, int size, float letterSpacingEm) {
            this.file = file;
            this.size = size;
            this.letterSpacingEm = letterSpacingEm;
        }

        /**
         * Returns the font file this style is generated from.
         *
         * @return the font file
         */
        public FontFile file() {
            return file;
        }

        /**
         * Returns the size in pixels at the virtual resolution.
         *
         * @return the font size
         */
        public int size() {
            return size;
        }

        /**
         * Returns the extra letter spacing as a fraction of the size.
         *
         * @return the letter spacing in em
         */
        public float letterSpacingEm() {
            return letterSpacingEm;
        }
    }

    /**
     * The characters every font is generated with: the basic set plus Latin
     * Extended letters, so names such as Köhler, Łódź and İstanbul render, and
     * the punctuation the screens use.
     */
    public static final String CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
        + "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ"
        + "ĀāĂăĄąĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋ"
        + "ŌōŎŏŐőŒœŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžȘșȚț€"
        + "×–—…‘’“”·−";

    /**
     * One {@link BitmapFont} per {@link TextStyle}, owned by this object.
     * Dispose it when the game closes.
     */
    public static final class Fonts implements Disposable {

        private final Map<TextStyle, BitmapFont> fonts = new EnumMap<>(TextStyle.class);

        /**
         * Creates an empty set; use {@link #load} to fill it.
         */
        private Fonts() {
        }

        /**
         * Generates every text style from the TrueType files in a directory.
         * Each style becomes a bitmap font sized for the given scale, so a
         * window of 2560 by 1440 can pass 1.333 to keep text sharp.
         *
         * @param directory the folder holding the four font files, for example {@code Gdx.files.internal("fonts")}
         * @param scale     the ratio of the real window height to {@value Theme#VIEW_HEIGHT}; 1 for the design size
         * @return the generated fonts
         * @throws IllegalArgumentException if the scale is not positive
         */
        public static Fonts load(FileHandle directory, float scale) {
            if (scale <= 0f) {
                throw new IllegalArgumentException("Scale must be positive: " + scale);
            }
            Fonts result = new Fonts();
            Map<FontFile, FreeTypeFontGenerator> generators = new EnumMap<>(FontFile.class);
            try {
                for (TextStyle style : TextStyle.values()) {
                    FreeTypeFontGenerator generator = generators.computeIfAbsent(style.file(),
                        file -> new FreeTypeFontGenerator(directory.child(file.fileName())));
                    result.fonts.put(style, generator.generateFont(parameterFor(style, scale)));
                }
            } finally {
                generators.values().forEach(FreeTypeFontGenerator::dispose);
            }
            return result;
        }

        /**
         * Builds the generator settings for one style.
         *
         * @param style the text style
         * @param scale the window scale
         * @return the settings: white glyphs, smooth filtering and the full character set
         */
        private static FreeTypeFontParameter parameterFor(TextStyle style, float scale) {
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = Math.max(1, Math.round(style.size() * scale));
            parameter.spaceX = Math.round(parameter.size * style.letterSpacingEm());
            parameter.color = Color.WHITE;
            parameter.characters = CHARACTERS;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            return parameter;
        }

        /**
         * Returns the font of a style. The glyphs are white, so tint them with
         * the font color of a label or button style.
         *
         * @param style the text style
         * @return the font; owned by this object
         */
        public BitmapFont get(TextStyle style) {
            return fonts.get(style);
        }

        /**
         * Releases every font and its glyph pages.
         */
        @Override
        public void dispose() {
            fonts.values().forEach(BitmapFont::dispose);
            fonts.clear();
        }
    }

    // ---------------------------------------------------------------- shapes

    /**
     * The kinds of button the design has: one orange main action per screen,
     * red for destructive actions and a bordered ghost for the rest.
     */
    public enum ButtonKind {
        /** Rust-orange fill, white label, hard shadow. */
        PRIMARY,
        /** Red fill, white label, hard shadow. */
        DANGER,
        /** No fill, a 2 px border and an ink label. */
        GHOST
    }

    /**
     * Rows of empty pixels kept under every generated shape for its hard
     * shadow, so the pressed and unpressed versions have the same size.
     */
    private static final int SHADOW_RESERVE = 3;

    /**
     * The largest corner radius a generated shape supports.
     */
    private static final int MAX_RADIUS = 64;

    /**
     * A rounded box rendered to pixels, together with the nine-patch borders
     * that let it stretch.
     *
     * @param width  the width in pixels
     * @param height the height in pixels
     * @param rgba   the pixels, row by row from the top, packed as RGBA8888
     * @param left   the fixed width of the left border
     * @param right  the fixed width of the right border
     * @param top    the fixed height of the top border
     * @param bottom the fixed height of the bottom border
     */
    record RenderedShape(int width, int height, int[] rgba, int left, int right, int top, int bottom) {
    }

    /**
     * Renders a rounded box with an optional border and a hard shadow. The
     * edges are anti-aliased with a signed distance field. The box is
     * {@code 2 * radius + 2} pixels wide, so its middle two rows and columns can
     * be stretched.
     *
     * @param fill        the fill, as RGBA8888; alpha 0 leaves the middle empty
     * @param border      the border color, as RGBA8888
     * @param borderWidth the border width in pixels, 0 for none
     * @param radius      the corner radius in pixels, capped at 64
     * @param shadow      the shadow color, as RGBA8888
     * @param shadowDy    how far below the box the shadow sits, 0 for none
     * @param sunk        whether the box is drawn {@value #SHADOW_RESERVE} pixels lower, as a pressed button is
     * @return the pixels and the nine-patch borders
     */
    static RenderedShape renderShape(int fill, int border, int borderWidth, int radius, int shadow, int shadowDy,
                                     boolean sunk) {
        int r = Math.max(1, Math.min(MAX_RADIUS, radius));
        int core = 2 * r + 2;
        int width = core;
        int height = core + SHADOW_RESERVE;
        int dy = sunk ? SHADOW_RESERVE : 0;
        int[] pixels = new int[width * height];
        double half = core / 2.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double cy = dy + half;
                double outer = coverage(roundBox(px, py, half, cy, half, half, r));
                double inner = outer;
                if (borderWidth > 0) {
                    inner = coverage(roundBox(px, py, half, cy, half - borderWidth, half - borderWidth,
                        Math.max(0, r - borderWidth)));
                }
                int out = 0;
                if (shadowDy > 0) {
                    out = over(out, shadow, coverage(roundBox(px, py, half, cy + shadowDy, half, half, r)));
                }
                if (borderWidth > 0) {
                    out = over(out, border, Math.max(0.0, outer - inner));
                }
                out = over(out, fill, inner);
                pixels[y * width + x] = out;
            }
        }
        return new RenderedShape(width, height, pixels, r, r, r + dy, r + SHADOW_RESERVE - dy);
    }

    /**
     * Returns the signed distance from a point to a rounded box: negative
     * inside, positive outside.
     *
     * @param px the point's x
     * @param py the point's y
     * @param cx the box center's x
     * @param cy the box center's y
     * @param hx half the box width
     * @param hy half the box height
     * @param r  the corner radius
     * @return the distance in pixels
     */
    private static double roundBox(double px, double py, double cx, double cy, double hx, double hy, double r) {
        double qx = Math.abs(px - cx) - (hx - r);
        double qy = Math.abs(py - cy) - (hy - r);
        return Math.hypot(Math.max(qx, 0.0), Math.max(qy, 0.0)) + Math.min(Math.max(qx, qy), 0.0) - r;
    }

    /**
     * Turns a signed distance into how much of a pixel is covered.
     *
     * @param distance the distance from the pixel center to the edge
     * @return a value from 0 (outside) to 1 (inside)
     */
    private static double coverage(double distance) {
        return Math.max(0.0, Math.min(1.0, 0.5 - distance));
    }

    /**
     * Composites a color over a pixel with the "source over" rule, on straight
     * (not premultiplied) alpha.
     *
     * @param dst      the pixel below, as RGBA8888
     * @param src      the color on top, as RGBA8888
     * @param coverage how much of the pixel the color covers, from 0 to 1
     * @return the combined pixel, as RGBA8888
     */
    private static int over(int dst, int src, double coverage) {
        double sa = (src & 0xff) / 255.0 * coverage;
        double da = (dst & 0xff) / 255.0;
        double oa = sa + da * (1.0 - sa);
        if (oa <= 0.0) {
            return 0;
        }
        int red = blend((src >>> 24) & 0xff, (dst >>> 24) & 0xff, sa, da, oa);
        int green = blend((src >>> 16) & 0xff, (dst >>> 16) & 0xff, sa, da, oa);
        int blue = blend((src >>> 8) & 0xff, (dst >>> 8) & 0xff, sa, da, oa);
        return (red << 24) | (green << 16) | (blue << 8) | (int) Math.round(oa * 255.0);
    }

    /**
     * Blends one color channel for {@link #over}.
     *
     * @param s  the top channel value, 0 to 255
     * @param d  the bottom channel value, 0 to 255
     * @param sa the top alpha, 0 to 1
     * @param da the bottom alpha, 0 to 1
     * @param oa the resulting alpha, above 0
     * @return the resulting channel value, 0 to 255
     */
    private static int blend(int s, int d, double sa, double da, double oa) {
        return (int) Math.round((s * sa + d * da * (1.0 - sa)) / oa);
    }

    /**
     * Builds and owns the stretchable textures for panels, buttons and fields.
     * They need an OpenGL context, so create this in the game's
     * {@code create()} and dispose it when the game closes.
     */
    public static final class Shapes implements Disposable {

        private final List<Texture> textures = new ArrayList<>();

        /**
         * Creates an empty set of shapes; drawables are built on request.
         */
        public Shapes() {
        }

        /**
         * Builds a stretchable rounded drawable. It has no built-in padding:
         * leave room for content with {@code SPACE_*} in your layout.
         *
         * @param fill        the fill color; use a transparent color for none
         * @param border      the border color
         * @param borderWidth the border width in pixels, 0 for none
         * @param radius      the corner radius in pixels
         * @param shadow      the hard shadow color, or {@code null} for none
         * @param shadowDy    how far below the shape the shadow sits
         * @param sunk        {@code true} for a pressed look: no shadow, {@value Theme#SHADOW_RESERVE} pixels lower
         * @return the drawable; its texture is owned by this object
         */
        public NinePatchDrawable rounded(Color fill, Color border, int borderWidth, int radius, Color shadow,
                                         int shadowDy, boolean sunk) {
            RenderedShape shape = renderShape(Color.rgba8888(fill), Color.rgba8888(border), borderWidth, radius,
                shadow == null ? 0 : Color.rgba8888(shadow), shadow == null || sunk ? 0 : shadowDy, sunk);
            Pixmap pixmap = new Pixmap(shape.width(), shape.height(), Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < shape.height(); y++) {
                for (int x = 0; x < shape.width(); x++) {
                    pixmap.drawPixel(x, y, shape.rgba()[y * shape.width() + x]);
                }
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pixmap.dispose();
            textures.add(texture);
            NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(new TextureRegion(texture),
                shape.left(), shape.right(), shape.top(), shape.bottom()));
            drawable.setLeftWidth(0f);
            drawable.setRightWidth(0f);
            drawable.setTopHeight(0f);
            drawable.setBottomHeight(0f);
            return drawable;
        }

        /**
         * Builds the panel drawable used for the programming panel, lobby
         * lists and dialogs: raised paper with a hairline edge and the hard
         * panel shadow.
         *
         * @return the panel drawable
         */
        public NinePatchDrawable panel() {
            return rounded(SURFACE_RAISED, LINE, BORDER_HAIRLINE, RADIUS_LG, LINE, SHADOW_PANEL_DY, false);
        }

        /**
         * Builds the drawable of a text field on {@link #SURFACE}: a 2 px
         * strong border, a 3 px teal border when focused, or a 3 px red
         * border when it holds an error.
         *
         * @param focused whether the field has the keyboard focus
         * @param error   whether the field's content is rejected; wins over focus
         * @return the field drawable
         */
        public NinePatchDrawable field(boolean focused, boolean error) {
            Color border = error ? DANGER : focused ? ACCENT : LINE_STRONG;
            int width = error || focused ? BORDER_HEAVY : BORDER_CONTROL;
            return rounded(SURFACE, border, width, RADIUS_MD, null, 0, false);
        }

        /**
         * Builds a button style: the up, pressed and disabled looks, the label
         * font and colors, and the 3 px sink of a pressed button. The style
         * does not own the font.
         *
         * @param kind the kind of button
         * @param font the font for the label, normally {@code fonts.get(TextStyle.BUTTON)}
         * @return the style, ready for a {@code TextButton}
         */
        public TextButtonStyle button(ButtonKind kind, BitmapFont font) {
            TextButtonStyle style = new TextButtonStyle();
            style.font = font;
            style.disabledFontColor = INK_MUTED;
            if (kind == ButtonKind.GHOST) {
                style.up = rounded(new Color(0f, 0f, 0f, 0f), LINE_STRONG, BORDER_CONTROL, RADIUS_MD, null, 0, false);
                style.down = rounded(LINE, LINE_STRONG, BORDER_CONTROL, RADIUS_MD, null, 0, false);
                style.disabled = rounded(new Color(0f, 0f, 0f, 0f), LINE, BORDER_CONTROL, RADIUS_MD, null, 0, false);
                style.fontColor = INK;
                return style;
            }
            Color fill = kind == ButtonKind.DANGER ? DANGER : PRIMARY;
            style.up = rounded(fill, fill, 0, RADIUS_MD, SHADOW_PRESS, SHADOW_PRESS_DY, false);
            style.down = rounded(fill, fill, 0, RADIUS_MD, SHADOW_PRESS, SHADOW_PRESS_DY, true);
            style.disabled = rounded(LINE, LINE, 0, RADIUS_MD, null, 0, false);
            style.fontColor = ON_PRIMARY;
            style.pressedOffsetY = -SHADOW_PRESS_DY;
            return style;
        }

        /**
         * Releases every texture built so far.
         */
        @Override
        public void dispose() {
            textures.forEach(Texture::dispose);
            textures.clear();
        }
    }
}
