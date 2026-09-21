package de.mkoehler.robotrampage.client.ui;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the parts of {@link Theme} that need no graphics context: the seat colors, the type scale, the character
 * set, and the pixel renderer behind every rounded panel, button and field. Building textures and fonts needs OpenGL
 * and is exercised by running the game instead.
 *
 * @author Mario Koehler
 */
class ThemeTest {

    private static final int TRANSPARENT = 0;

    /**
     * Reads one pixel of a rendered shape.
     *
     * @param shape the shape
     * @param x     the column
     * @param y     the row, counted from the top
     * @return the pixel as RGBA8888
     */
    private static int pixel(Theme.RenderedShape shape, int x, int y) {
        return shape.rgba()[y * shape.width() + x];
    }

    /**
     * Returns the alpha channel of an RGBA8888 pixel.
     *
     * @param rgba the pixel
     * @return the alpha, 0 to 255
     */
    private static int alpha(int rgba) {
        return rgba & 0xff;
    }

    /**
     * The eight robot colors are the color-blind-safe set in seat order, and seat numbers outside 1 to 8 are refused.
     */
    @Test
    void robotColorsFollowTheSeatNumbers() {
        assertEquals(8, Theme.SEATS);
        assertEquals(Color.valueOf("#e69f00"), Theme.robotColor(1));
        assertEquals(Color.valueOf("#56b4e9"), Theme.robotColor(2));
        assertEquals(Color.valueOf("#eeeeee"), Theme.robotColor(8));
        assertThrows(IllegalArgumentException.class, () -> Theme.robotColor(0));
        assertThrows(IllegalArgumentException.class, () -> Theme.robotColor(9));
    }

    /**
     * Every seat has a different color, so players can be told apart by more than a number.
     */
    @Test
    void everySeatHasItsOwnColor() {
        for (int a = 1; a <= Theme.SEATS; a++) {
            for (int b = a + 1; b <= Theme.SEATS; b++) {
                assertNotEquals(Theme.robotColor(a), Theme.robotColor(b), "seats " + a + " and " + b);
            }
        }
    }

    /**
     * The type scale matches the design system: Bungee for titles and buttons, Barlow for everything read, with the
     * documented sizes and letter spacing.
     */
    @Test
    void textStylesMatchTheDesignSystem() {
        assertEquals(56, Theme.TextStyle.DISPLAY.size());
        assertEquals(28, Theme.TextStyle.HEADING.size());
        assertEquals(18, Theme.TextStyle.BUTTON.size());
        assertEquals(0.02f, Theme.TextStyle.BUTTON.letterSpacingEm());
        assertEquals(16, Theme.TextStyle.BODY.size());
        assertEquals(14, Theme.TextStyle.LABEL.size());
        assertEquals(0.06f, Theme.TextStyle.LABEL.letterSpacingEm());
        assertEquals(13, Theme.TextStyle.CAPTION.size());
        for (Theme.TextStyle style : new Theme.TextStyle[] {Theme.TextStyle.DISPLAY, Theme.TextStyle.HEADING,
            Theme.TextStyle.BUTTON, Theme.TextStyle.TITLE, Theme.TextStyle.HERO}) {
            assertEquals(Theme.FontFile.BUNGEE, style.file(), style.name());
        }
        assertEquals(Theme.FontFile.BARLOW_BOLD, Theme.TextStyle.NAME.file());
        assertEquals(32, Theme.TextStyle.SUBTITLE.size());
        assertEquals(26, Theme.TextStyle.BUTTON_LARGE.size());
        assertEquals(22, Theme.TextStyle.BUTTON_MEDIUM.size());
        assertEquals(20, Theme.TextStyle.FIELD.size());
        assertEquals(Theme.FontFile.BARLOW_SEMIBOLD, Theme.TextStyle.FIELD.file());
        assertEquals(20, Theme.TextStyle.BODY_LARGE.size());
        assertEquals(13, Theme.TextStyle.CHIP.size());
        assertEquals(0.06f, Theme.TextStyle.CHIP.letterSpacingEm());
    }

    /**
     * The four font files carry the names the asset folder uses.
     */
    @Test
    void fontFilesHaveTheShippedNames() {
        assertEquals("Bungee-Regular.ttf", Theme.FontFile.BUNGEE.fileName());
        assertEquals("Barlow-Regular.ttf", Theme.FontFile.BARLOW_REGULAR.fileName());
        assertEquals("Barlow-SemiBold.ttf", Theme.FontFile.BARLOW_SEMIBOLD.fileName());
        assertEquals("Barlow-Bold.ttf", Theme.FontFile.BARLOW_BOLD.fileName());
    }

    /**
     * The generated character set covers plain ASCII, the letters of the names the design uses as examples, and the
     * punctuation the screens print.
     */
    @Test
    void characterSetCoversNamesAndScreenPunctuation() {
        for (char c : "AZaz09.,:!?".toCharArray()) {
            assertTrue(Theme.CHARACTERS.indexOf(c) >= 0, "missing " + c);
        }
        for (char c : "ŁódźİıșÖüÅ×–−…“”·€".toCharArray()) {
            assertTrue(Theme.CHARACTERS.indexOf(c) >= 0, "missing " + c);
        }
    }

    /**
     * A rounded shape is {@code 2 * radius + 2} pixels wide and keeps three extra rows for the hard shadow.
     */
    @Test
    void shapeSizeFollowsTheRadius() {
        Theme.RenderedShape shape = Theme.renderShape(0xffffffff, 0, 0, 10, 0, 0, false);

        assertEquals(22, shape.width());
        assertEquals(25, shape.height());
        assertEquals(22 * 25, shape.rgba().length);
    }

    /**
     * Radii beyond the supported maximum are capped, so a pill-shaped request still renders.
     */
    @Test
    void hugeRadiiAreCapped() {
        Theme.RenderedShape shape = Theme.renderShape(0xffffffff, 0, 0, Theme.RADIUS_PILL, 0, 0, false);

        assertEquals(130, shape.width());
    }

    /**
     * The corners are rounded away and the middle is exactly the fill color.
     */
    @Test
    void cornersAreTransparentAndTheMiddleIsTheFill() {
        int fill = Color.rgba8888(Theme.PRIMARY);
        Theme.RenderedShape shape = Theme.renderShape(fill, fill, 0, 10, 0, 0, false);

        assertEquals(TRANSPARENT, alpha(pixel(shape, 0, 0)));
        assertEquals(TRANSPARENT, alpha(pixel(shape, shape.width() - 1, 0)));
        assertEquals(fill, pixel(shape, shape.width() / 2, shape.width() / 2));
    }

    /**
     * A border of the given width surrounds the fill: the outermost column in the middle of the shape is the border color
     * and the pixel just inside it is the fill.
     */
    @Test
    void borderSurroundsTheFill() {
        int fill = Color.rgba8888(Theme.SURFACE);
        int border = Color.rgba8888(Theme.LINE_STRONG);
        Theme.RenderedShape shape = Theme.renderShape(fill, border, 2, 10, 0, 0, false);
        int middle = shape.width() / 2;

        assertEquals(border, pixel(shape, 0, middle));
        assertEquals(border, pixel(shape, 1, middle));
        assertEquals(fill, pixel(shape, 2, middle));
    }

    /**
     * A shadow shows only in the rows below the box, in the shadow color, and not at all when there is none.
     */
    @Test
    void hardShadowSitsUnderTheBox() {
        int fill = Color.rgba8888(Theme.PRIMARY);
        int shadow = Color.rgba8888(Theme.SHADOW_PRESS);
        int core = 22;

        Theme.RenderedShape with = Theme.renderShape(fill, fill, 0, 10, shadow, Theme.SHADOW_PRESS_DY, false);
        Theme.RenderedShape without = Theme.renderShape(fill, fill, 0, 10, 0, 0, false);

        int below = pixel(with, core / 2, core + 1);
        assertTrue(alpha(below) > 0);
        assertEquals(shadow >>> 8, below >>> 8, "the shadow keeps its color");
        assertEquals(TRANSPARENT, alpha(pixel(without, core / 2, core + 1)));
    }

    /**
     * A pressed (sunk) shape is drawn three pixels lower with no shadow, and its nine-patch borders shift to match so the
     * pressed and unpressed versions have the same size.
     */
    @Test
    void sunkShapeMovesDownAndShiftsItsBorders() {
        int fill = Color.rgba8888(Theme.PRIMARY);
        int shadow = Color.rgba8888(Theme.SHADOW_PRESS);

        Theme.RenderedShape up = Theme.renderShape(fill, fill, 0, 10, shadow, 3, false);
        Theme.RenderedShape down = Theme.renderShape(fill, fill, 0, 10, shadow, 0, true);

        assertEquals(up.width(), down.width());
        assertEquals(up.height(), down.height());
        assertEquals(10, up.top());
        assertEquals(13, down.top());
        assertEquals(13, up.bottom());
        assertEquals(10, down.bottom());
        assertEquals(TRANSPARENT, alpha(pixel(down, down.width() / 2, 0)));
        assertEquals(fill, pixel(up, up.width() / 2, 0 + 11));
    }

    /**
     * The edges are anti-aliased: the pixels along a rounded corner have partial alpha, not just fully in or out.
     */
    @Test
    void cornersAreAntiAliased() {
        Theme.RenderedShape shape = Theme.renderShape(0xffffffff, 0, 0, 16, 0, 0, false);

        boolean partial = false;
        for (int y = 0; y < 16 && !partial; y++) {
            for (int x = 0; x < 16; x++) {
                int a = alpha(pixel(shape, x, y));
                if (a > 0 && a < 255) {
                    partial = true;
                    break;
                }
            }
        }
        assertTrue(partial, "a rounded corner needs partially covered pixels");
    }
}
