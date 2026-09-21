package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import de.mkoehler.robotrampage.client.game.Standings;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Game Over layout: who won, the podium of the best three, the standings of everybody, and the buttons that leave the
 * server or wait for the lobby. It is laid out on the {@value Theme#VIEW_WIDTH} by {@value Theme#VIEW_HEIGHT} canvas of the
 * design, so a position such as "left 64, top 56" is given exactly as the mockup has it.
 * <p>
 * The view only shows a {@link Standings}; what the lobby button says is set by {@link #setLobbySeconds(int)}, because the
 * server, not the player, takes everybody back to the lobby.
 *
 * @author Mario Koehler
 */
final class GameOverView extends Group {

    private static final float PODIUM_FLOOR_TOP = 880f;
    private static final float ROW_HEIGHT = 76f;
    private static final float ROW_GAP = 8f;
    private static final float CARD_WIDTH = 900f;
    private static final float CARD_TOP = 288f;
    private static final float CARD_PAD = 28f;
    private static final float BUTTONS_TOP = 980f;
    private static final float HEADLINE_MAX_WIDTH = 1500f;
    private static final int CONFETTI_PIECES = 102;
    private static final Color[] CONFETTI_COLORS = {
        Color.valueOf("#186878"), Color.valueOf("#23262b"), Color.valueOf("#e69f00"), Color.valueOf("#56b4e9"),
        Color.valueOf("#009e73"), Color.valueOf("#cc79a7"), Color.valueOf("#c93f0a"), Color.valueOf("#0072b2")};

    private final UiKit ui;
    private final TextButton lobbyButton;
    private int shownSeconds = -2;

    /**
     * Builds the layout.
     *
     * @param ui           the widget factory
     * @param standings    the results to show
     * @param onLeave      what the button that leaves the server does
     * @param lobbySeconds the seconds until the server opens the lobby
     */
    GameOverView(UiKit ui, Standings standings, Runnable onLeave, int lobbySeconds) {
        this.ui = ui;
        setSize(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT);
        if (standings.hasWinner()) {
            addConfetti();
            addPodium(standings.rows());
        }
        addHeader(standings);
        addStandings(standings);

        TextButton leave = ui.button("Leave server", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onLeave.run();
            }
        });
        lobbyButton = ui.button("Back to lobby", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        lobbyButton.setDisabled(true);
        float shadow = UiKit.SHAPE_RESERVE;
        float lobbyLeft = Theme.VIEW_WIDTH - 64f - 320f;
        put(lobbyButton, lobbyLeft, BUTTONS_TOP, 320f, 52f + shadow);
        put(leave, lobbyLeft - 20f - 260f, BUTTONS_TOP, 260f, 52f + shadow);
        setLobbySeconds(lobbySeconds);
    }

    /**
     * Says on the lobby button how long it is until the lobby opens. The button cannot be pressed: the server takes everybody
     * back, all at once, when the results have been shown for long enough.
     *
     * @param seconds the seconds left, 0 once the time is up
     */
    void setLobbySeconds(int seconds) {
        if (seconds == shownSeconds) {
            return;
        }
        shownSeconds = seconds;
        lobbyButton.setText((seconds > 0 ? "Lobby in " + seconds + " s" : "Opening the lobby…").toUpperCase(Locale.ROOT));
    }

    /**
     * Puts a widget at a position and size of the mockup, measured from the top left of the screen.
     *
     * @param actor  the widget
     * @param left   the distance from the left edge
     * @param top    the distance from the top edge
     * @param width  the width
     * @param height the height
     */
    private void put(Actor actor, float left, float top, float width, float height) {
        actor.setBounds(left, Theme.VIEW_HEIGHT - top - height, width, height);
        addActor(actor);
    }

    /**
     * Adds the small end-of-game line: what it is, who won, and what decided it. A name that would make the big line run off
     * the screen shrinks it.
     *
     * @param standings the results
     */
    private void addHeader(Standings standings) {
        Label headline = ui.label(standings.headline(), Theme.TextStyle.RESULT, Theme.INK);
        float width = headline.getPrefWidth();
        if (width > HEADLINE_MAX_WIDTH) {
            // The fonts carry their own scale (they are generated larger and scaled down); a label's scale replaces it.
            headline.setFontScale(ui.fonts().get(Theme.TextStyle.RESULT).getScaleX() * HEADLINE_MAX_WIDTH / width);
        }
        Label sub = ui.label(standings.subline(), Theme.TextStyle.LEAD, Theme.INK_MUTED);
        sub.setWrap(true);
        Table header = new Table();
        header.top().left();
        header.add(ui.label("Game over", Theme.TextStyle.LABEL, Theme.INK_MUTED)).left().row();
        header.add(headline).left().padTop(6f).row();
        header.add(sub).left().width(1000f).padTop(14f);
        put(header, 64f, 56f, 1400f, 230f);
    }

    /**
     * Adds the podium: the first three, with the winner in the middle on the tallest block. Only as many places as there are
     * players are built.
     *
     * @param rows the standings, best first
     */
    private void addPodium(List<Standings.Row> rows) {
        Image floor = new Image(ui.solid(Theme.INK));
        put(floor, 0f, PODIUM_FLOOR_TOP, 820f, 12f);
        // Blocks are placed second, first, third; the pictures stand 14 px into the block, as in the mockup.
        float[][] places = {{270f, 250f, 200f}, {40f, 170f, 150f}, {500f, 120f, 150f}};
        Color[] colors = {Theme.PRIMARY, Theme.ACCENT, Theme.INK};
        for (int place = 0; place < Math.min(3, rows.size()); place++) {
            Standings.Row row = rows.get(place);
            float left = places[place][0];
            float height = places[place][1];
            float picture = places[place][2];
            float top = PODIUM_FLOOR_TOP - height;

            Image robot = new Image(ui.image(RobotLook.picture(row.seat())));
            robot.setScaling(Scaling.fit);
            put(robot, left + (210f - picture) / 2f, top + 14f - picture, picture, picture);

            put(new Image(ui.rounded(colors[place], colors[place], 0, 16)), left, top, 210f, height);
            put(new Image(ui.solid(colors[place])), left, top + 24f, 210f, height - 24f);
            Table caption = new Table();
            caption.top();
            caption.add(ui.label(String.valueOf(row.rank()), Theme.TextStyle.DISPLAY, Theme.ON_PRIMARY)).row();
            Label name = ui.label(row.name(), Theme.TextStyle.NAME, Theme.ON_PRIMARY);
            name.setEllipsis(true);
            name.setAlignment(Align.center);
            caption.add(name).width(190f).center();
            put(caption, left, top + 8f, 210f, height - 8f);
        }
    }

    /**
     * Adds the card with a line for every player. The lines shrink when there are many players so that the card never runs
     * into the buttons.
     *
     * @param standings the results
     */
    private void addStandings(Standings standings) {
        List<Standings.Row> rows = standings.rows();
        int count = Math.max(1, rows.size());
        float titleHeight = 44f;
        float available = BUTTONS_TOP - 16f - CARD_TOP - 2 * CARD_PAD - titleHeight - 16f - UiKit.SHAPE_RESERVE;
        float rowHeight = Math.min(ROW_HEIGHT, (available - (count - 1) * ROW_GAP) / count);

        Table card = ui.panel();
        card.pad(CARD_PAD, CARD_PAD, CARD_PAD + UiKit.SHAPE_RESERVE, CARD_PAD).top().left();
        Table title = new Table();
        title.add(ui.label("Standings", Theme.TextStyle.SUBTITLE, Theme.INK)).left();
        title.add(ui.label(standings.rankingRule(), Theme.TextStyle.BODY, Theme.INK_MUTED)).left().bottom().padLeft(16f)
            .padBottom(4f);
        card.add(title).left().height(titleHeight).padBottom(16f).row();
        for (Standings.Row row : rows) {
            card.add(standingsRow(row)).growX().height(rowHeight + UiKit.SHAPE_RESERVE).padBottom(ROW_GAP - UiKit.SHAPE_RESERVE)
                .row();
        }
        card.pack();
        put(card, Theme.VIEW_WIDTH - 64f - CARD_WIDTH, CARD_TOP, CARD_WIDTH, card.getHeight());
    }

    /**
     * Builds the line of one player: the place, the robot, the name with what happened to it, the flags and the lives.
     *
     * @param row the player
     * @return the line
     */
    private Table standingsRow(Standings.Row row) {
        Table line = new Table();
        line.setBackground(ui.rounded(Theme.SURFACE_RAISED, row.you() ? Theme.ACCENT : Theme.LINE,
            row.you() ? Theme.BORDER_HEAVY : Theme.BORDER_HAIRLINE, 14));
        line.padLeft(20f).padRight(20f).padBottom(UiKit.SHAPE_RESERVE);
        line.add(ui.label(String.valueOf(row.rank()), Theme.TextStyle.SUBTITLE, row.winner() ? Theme.PRIMARY : Theme.INK_MUTED))
            .width(34f).center();
        Image robot = new Image(ui.image(RobotLook.picture(row.seat())));
        robot.setScaling(Scaling.fit);
        line.add(robot).size(52f).padLeft(16f);

        Table text = new Table();
        Table nameLine = new Table();
        nameLine.add(ui.label(row.name(), Theme.TextStyle.NAME, Theme.INK));
        if (row.winner()) {
            nameLine.add(ui.chip("Winner", UiKit.ChipKind.SUCCESS)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(10f);
        }
        if (row.you()) {
            nameLine.add(ui.chip("You", UiKit.ChipKind.PRIMARY)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(10f);
        }
        text.add(nameLine).left().row();
        if (!row.detail().isEmpty()) {
            text.add(ui.label(row.detail(), Theme.TextStyle.BODY, Theme.INK_MUTED)).left();
        }
        line.add(text).expandX().left().padLeft(16f);

        line.add(ui.chip(row.flags() + "/" + row.flagTotal() + " flags", UiKit.ChipKind.OUTLINE))
            .height(UiKit.CHIP_CELL_HEIGHT).padRight(16f);
        line.add(ui.pips(row.lives(), row.maxLives(), 16f, 5));
        return line;
    }

    /**
     * Scatters confetti over the top right. The pieces come from a fixed seed, so the screen always looks the same.
     */
    private void addConfetti() {
        Random random = new Random(7L);
        for (int i = 0; i < CONFETTI_PIECES; i++) {
            Color color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            float left = 1010f + random.nextFloat() * 870f;
            float top = 16f + random.nextFloat() * 244f;
            int kind = i % 3;
            float width;
            float height;
            int radius;
            if (kind == 0) {
                width = 16f + random.nextInt(19);
                height = Math.round(width * 0.37f);
                radius = 3;
            } else if (kind == 1) {
                width = 10f + random.nextInt(13);
                height = width;
                radius = 4;
            } else {
                width = 10f + 5f * random.nextInt(3);
                height = width;
                radius = (int) (width / 2f);
            }
            Image piece = new Image(ui.rounded(color, color, 0, radius));
            put(piece, left, top, width, height);
            piece.setOrigin(width / 2f, height / 2f);
            piece.setRotation(random.nextInt(360));
        }
    }
}
