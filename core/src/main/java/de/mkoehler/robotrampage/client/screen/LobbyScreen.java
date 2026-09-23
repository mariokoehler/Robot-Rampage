package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.audio.AudioKit;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.lobby.LobbyView;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.ui.PillToggle;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.SetReady;
import de.mkoehler.robotrampage.net.messages.StartGameRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The waiting room: who has joined, who is ready, the facts of the game, and the buttons to get ready, leave or, for the
 * host, start the game. Everything shown comes from the server's latest {@link LobbyState}; the screen keeps no state of
 * its own, so it looks the same whether it was reached by joining or by the end of a game.
 * <p>
 * The screen owns the connection to the server until the game starts, and then hands it, with the messages that arrived
 * behind the start message, to the next screen. The connection is closed only when the player leaves or the server closes
 * it, never when the screen is disposed, since disposal also happens when the connection moves on.
 *
 * @author Mario Koehler
 */
public final class LobbyScreen extends StageScreen implements NetworkClient.Handler {

    private static final float ROW_HEIGHT = 72f;
    private static final float ROW_GAP = 10f;
    private static final float PREVIEW_SIZE = 480f;
    private static final float FACT_LABEL_WIDTH = 170f;

    private final ConnectedServer server;
    private final ServerAddress address;
    private final int mySeat;
    private final Table playerRows = new Table();
    private final Label countLabel;
    private final Table boardFacts = new Table();
    private final Label hintLabel;
    private final PillToggle readyToggle;
    private final TextButton startButton;
    private final Label startLabel;
    private GameStarted started;
    private LobbyState lastLobby;
    private final List<Object> afterStart = new ArrayList<>();
    private boolean closed;

    /**
     * Builds the screen and takes over the messages that arrived with the acceptance, among them the first lobby state.
     * Every path that reaches the lobby (a first join, a reconnect, or the game handing the connection back) passes
     * through here, so this is also where the session token is remembered to disk for next time (design.md 5.1).
     *
     * @param game    the game showing the screen
     * @param server  the accepted connection
     * @param address the address that was joined
     */
    public LobbyScreen(RobotRampageGame game, ConnectedServer server, ServerAddress address) {
        super(game);
        this.server = server;
        this.address = address;
        this.mySeat = server.welcome().getSeat();
        countLabel = ui.label("", Theme.TextStyle.CHIP, Theme.ON_PRIMARY);
        hintLabel = ui.label("", Theme.TextStyle.BODY, Theme.INK_MUTED);
        hintLabel.setWrap(true);
        readyToggle = ui.toggle();
        startButton = ui.button("Start game", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        startLabel = startButton.getLabel();

        stage.addActor(headerTable());
        stage.addActor(contentTable());
        stage.addActor(actionTable());

        String token = server.welcome().getSessionToken();
        if (token != null && !token.equals(game.settings().sessionToken())) {
            game.saveSettings(game.settings().withSessionToken(token));
        }
        server.earlyMessages().forEach(this::onMessage);
        if (server.closedAlready()) {
            onDisconnect();
        }
    }

    /**
     * Builds the big title with the address of the server next to it.
     *
     * @return the table
     */
    private Table headerTable() {
        Table table = new Table();
        table.setFillParent(true);
        table.top().left().padLeft(64f).padTop(48f);
        table.add(ui.label("Lobby", Theme.TextStyle.TITLE, Theme.INK)).left();
        table.add(ui.chip(address.toString(), UiKit.ChipKind.OUTLINE)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_8);
        return table;
    }

    /**
     * Builds the two panels: the players and the game.
     *
     * @return the table
     */
    private Table contentTable() {
        Table players = ui.panel();
        players.pad(28f).top().left();
        Table heading = new Table();
        heading.add(ui.label("Players", Theme.TextStyle.SUBTITLE, Theme.INK));
        Table count = ui.chip("", UiKit.ChipKind.INK);
        count.clearChildren();
        count.add(countLabel).padLeft(Theme.SPACE_3).padRight(Theme.SPACE_3);
        heading.add(count).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_4);
        players.add(heading).left().row();
        players.add(playerRows).growX().padTop(20f);

        Table board = ui.panel();
        board.pad(28f).top().left();
        board.add(ui.label("The board", Theme.TextStyle.SUBTITLE, Theme.INK)).left().row();
        Table preview = ui.well();
        preview.add(ui.label("The board is shown when the game starts.", Theme.TextStyle.BODY, Theme.INK_MUTED));
        board.add(preview).size(PREVIEW_SIZE).left().padTop(20f).row();
        board.add(boardFacts).growX().padTop(20f).row();
        board.add(hintLabel).width(820f - 56f).left().padTop(Theme.SPACE_3);

        Table table = new Table();
        table.setFillParent(true);
        table.top().left().padLeft(64f).padTop(160f);
        table.add(players).width(940f).top();
        table.add(board).width(820f).top().padLeft(Theme.SPACE_8);
        return table;
    }

    /**
     * Builds the row of buttons at the bottom right: leave, the ready switch and start.
     *
     * @return the table
     */
    private Table actionTable() {
        TextButton leave = ui.button("Leave", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leave();
            }
        });
        readyToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                server.link().send(new SetReady(readyToggle.isChecked()));
            }
        });
        startButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                server.link().send(new StartGameRequest());
            }
        });
        Table ready = new Table();
        ready.add(readyToggle).size(52f, 28f);
        ready.add(ui.label("I am ready", Theme.TextStyle.LABEL, Theme.INK)).padLeft(Theme.SPACE_3);

        Table table = new Table();
        table.setFillParent(true);
        table.bottom().right().padRight(64f).padBottom(40f);
        table.add(leave).size(200f, 52f);
        table.add(ready).padLeft(Theme.SPACE_8);
        table.add(startButton).size(300f, 52f).padLeft(Theme.SPACE_8);
        return table;
    }

    /**
     * Reads what the server sent since the last frame, and moves on to the next screen when the game has started.
     *
     * @param delta seconds since the previous frame, unused
     */
    @Override
    protected void update(float delta) {
        if (closed) {
            return;
        }
        server.link().poll(this);
        if (started != null && !closed) {
            closed = true;
            game.setScreen(new GameScreen(game, server, address, started, afterStart));
        }
    }

    /**
     * Takes in one message: shows a new lobby state, explains a refused request, or notes that the game has started.
     * Whatever arrives after the start message belongs to the next screen and is kept for it.
     *
     * @param message a message from the server
     */
    @Override
    public void onMessage(Object message) {
        if (started != null) {
            afterStart.add(message);
        } else if (message instanceof LobbyState lobby) {
            playRosterChangeSounds(lobby);
            lastLobby = lobby;
            show(new LobbyView(lobby, mySeat));
        } else if (message instanceof RequestRejected rejected) {
            toast(rejected.reason());
        } else if (message instanceof GameStarted gameStarted) {
            started = gameStarted;
        }
    }

    /**
     * Plays a sound for every seat a new lobby state gained or lost compared to the one before it (a join, a leave, or
     * both at once if several players changed between updates). A disconnect in the lobby has no grace period, unlike
     * one during a game (2.13): {@code GameSession.disconnect} frees the seat immediately, so it always shows up here as
     * a plain leave. Nothing plays for the very first lobby state this screen ever sees: there is nothing to compare it
     * against, and everybody already seated is not "joining".
     *
     * @param lobby the newly arrived lobby state
     */
    private void playRosterChangeSounds(LobbyState lobby) {
        if (lastLobby == null) {
            return;
        }
        Set<Integer> before = lastLobby.players().stream().map(PlayerInfo::seat).collect(Collectors.toSet());
        Set<Integer> after = lobby.players().stream().map(PlayerInfo::seat).collect(Collectors.toSet());
        if (!before.containsAll(after)) {
            game.audio().play(AudioKit.Clip.PLAYER_JOINS_LOBBY);
        }
        if (!after.containsAll(before)) {
            game.audio().play(AudioKit.Clip.PLAYER_LEFT_LOBBY);
        }
    }

    /**
     * The server closed the connection. A player who drops out of the lobby loses their seat, so there is nothing to come
     * back to: the player is taken back to the connect screen and told.
     */
    @Override
    public void onDisconnect() {
        if (closed) {
            return;
        }
        closed = true;
        game.setScreen(new ConnectScreen(game, "The server closed the connection, so you are no longer in the lobby."));
    }

    /**
     * Fills the screen from a lobby state.
     *
     * @param view what to show
     */
    private void show(LobbyView view) {
        ui.setText(countLabel, Theme.TextStyle.CHIP, view.countText());
        rebuildRows(view);
        rebuildFacts(view);
        hintLabel.setText(view.hint());
        readyToggle.setChecked(view.iAmReady());
        ui.setText(startLabel, Theme.TextStyle.BUTTON, view.startLabel());
        startButton.setDisabled(!view.canStart());
    }

    /**
     * Rebuilds the list of seats.
     *
     * @param view what to show
     */
    private void rebuildRows(LobbyView view) {
        playerRows.clearChildren();
        for (LobbyView.Row row : view.rows()) {
            playerRows.add(row.occupied() ? occupiedRow(row) : freeRow(row)).growX().height(ROW_HEIGHT + UiKit.SHAPE_RESERVE)
                .padBottom(ROW_GAP - UiKit.SHAPE_RESERVE).row();
        }
    }

    /**
     * Builds the line of a seat with a player.
     *
     * @param row the seat
     * @return the line
     */
    private Table occupiedRow(LobbyView.Row row) {
        Table line = new Table();
        line.setBackground(ui.rounded(Theme.SURFACE_RAISED, row.you() ? Theme.ACCENT : Theme.LINE,
            row.you() ? Theme.BORDER_HEAVY : Theme.BORDER_HAIRLINE, Theme.RADIUS_LG));
        line.padLeft(20f).padRight(20f).padBottom(UiKit.SHAPE_RESERVE);
        line.add(ui.label(String.valueOf(row.seat() + 1), Theme.TextStyle.HEADING, Theme.INK_MUTED)).width(32f);
        Image robot = new Image(ui.image(RobotLook.picture(row.seat())));
        robot.setScaling(Scaling.fit);
        line.add(robot).size(52f).padLeft(20f);
        Table names = new Table();
        names.left();
        names.add(ui.label(row.name(), Theme.TextStyle.NAME, Theme.INK)).left().row();
        names.add(ui.label(row.robotName() + " robot", Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left();
        line.add(names).expandX().left().padLeft(20f);
        if (row.host()) {
            line.add(ui.chip("Host", UiKit.ChipKind.INK)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
        }
        if (row.you()) {
            line.add(ui.chip("You", UiKit.ChipKind.PRIMARY)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
        }
        line.add(row.ready() ? ui.chip("Ready", UiKit.ChipKind.SUCCESS) : ui.chip("Not ready", UiKit.ChipKind.OUTLINE))
            .height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
        return line;
    }

    /**
     * Builds the line of a free seat.
     *
     * @param row the seat
     * @return the line
     */
    private Table freeRow(LobbyView.Row row) {
        Table line = new Table();
        line.setBackground(ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL,
            Theme.RADIUS_LG));
        line.padLeft(20f).padRight(20f).padBottom(UiKit.SHAPE_RESERVE);
        line.add(ui.label(String.valueOf(row.seat() + 1), Theme.TextStyle.HEADING, Theme.INK_MUTED)).width(32f);
        line.add(ui.label("Waiting for a player…", Theme.TextStyle.BODY_LARGE, Theme.INK_MUTED)).expandX().left()
            .padLeft(72f);
        return line;
    }

    /**
     * Rebuilds the list of facts about the game.
     *
     * @param view what to show
     */
    private void rebuildFacts(LobbyView view) {
        boardFacts.clearChildren();
        boardFacts.left();
        fact("Board", view.boardText());
        fact("Flags", view.flagsText());
        fact("Lives", view.livesText());
        fact("Programming time", view.programmingTimeText());
        fact("Rules", "Classic 2005");
    }

    /**
     * Adds one line to the list of facts.
     *
     * @param caption what the fact is about
     * @param value   the fact
     */
    private void fact(String caption, String value) {
        boardFacts.add(ui.label(caption, Theme.TextStyle.CHIP, Theme.INK_MUTED)).width(FACT_LABEL_WIDTH).left()
            .padBottom(Theme.SPACE_2);
        boardFacts.add(ui.label(value, Theme.TextStyle.FIELD, Theme.INK)).left().padBottom(Theme.SPACE_2).row();
    }

    /**
     * Leaves the lobby: closes the connection, which frees the seat, and returns to the connect screen.
     */
    private void leave() {
        closed = true;
        server.link().disconnect();
        game.setScreen(new ConnectScreen(game));
    }
}
