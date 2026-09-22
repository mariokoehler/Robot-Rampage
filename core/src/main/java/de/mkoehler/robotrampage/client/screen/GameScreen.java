package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.board.BoardGeometry;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.Reconnector;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.game.CardLook;
import de.mkoehler.robotrampage.client.game.GameModel;
import de.mkoehler.robotrampage.client.game.MovementPreview;
import de.mkoehler.robotrampage.client.game.ProgramDraft;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.render.BoardActor;
import de.mkoehler.robotrampage.client.replay.TurnReplay;
import de.mkoehler.robotrampage.client.ui.CardView;
import de.mkoehler.robotrampage.client.ui.FacingPicker;
import de.mkoehler.robotrampage.client.ui.ModalDialog;
import de.mkoehler.robotrampage.client.ui.PillToggle;
import de.mkoehler.robotrampage.client.ui.ProgressPill;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.client.ui.UiKit;
import de.mkoehler.robotrampage.net.AppVersion;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.SubPhase;
import de.mkoehler.robotrampage.rules.RobotStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The screen of a running game: the players, the board, this player's robot, and below them the five registers and the hand
 * of cards to program the turn with. It follows a {@link GameModel} that the messages of the server keep up to date, and
 * draws it in the layout of the mockups (1920 by 1080).
 * <p>
 * This is the programming half of a turn. When the server plays the turn out, the screen shows the new state without
 * animating it yet. Cards are placed and taken back with clicks.
 * <p>
 * Like the lobby, the screen owns the connection but never closes it in {@code dispose()}, only when the player leaves.
 * When the game is over and the server returns to the lobby, the connection moves back to the lobby screen together with the
 * messages that arrived behind the lobby state.
 *
 * @author Mario Koehler
 */
public final class GameScreen extends StageScreen implements NetworkClient.Handler {

    private static final float PANEL_TOP = 78f;
    private static final float PANEL_HEIGHT = 600f;
    private static final float ROW_HEIGHT = 68f;
    private static final float ROW_GAP = 8f;
    private static final float BOARD_LEFT = 660f;
    private static final float MAX_TILE = 50f;
    private static final float KEY_ICON = 40f;
    private static final float GHOST_ALPHA = 0.4f;
    private static final String HAND_HINT = "Click a card to put it in the next free register. Click a filled register to take "
        + "its card back. If the timer runs out, empty registers are filled at random.";

    private final ConnectedServer server;
    private final ServerAddress address;
    private final GameModel model;
    private final CardView cards;
    private final BoardActor boardActor;
    private final Table headerLeft = new Table();
    private final Table playersBody = new Table();
    private final Table robotBody = new Table();
    private final Table programBody = new Table();
    private final Label timeLabel;
    private final Label timeCaption;
    private TextButton timerButton;
    private boolean timerButtonPaused;
    private final ProgressPill timeBar;
    private final PillToggle powerToggle;
    private final Group programmingGroup = new Group();
    private final Group resolutionGroup = new Group();
    private final Group gameOverGroup = new Group();
    private GameOverView gameOverView;
    private Group activeGroup;
    private BoardActor resolutionBoard;
    private final Table resolutionTitle = new Table();
    private final Table cardsBody = new Table();
    private final Table feedBody = new Table();
    private final Table registersRibbon = new Table();
    private final Table stepsRibbon = new Table();
    private final Table speedBox = new Table();
    private final Label cardsHeading;
    private Button pauseButton;
    private Image pauseIcon;
    private TurnReplay replay;
    private boolean showingResolution;
    private boolean replayCompleted;
    private boolean paused;
    private float speed = 1f;
    private int shownBeat = -1;
    private boolean shownDone;
    private final List<Object> returnToLobby = new ArrayList<>();
    private LobbyState lobbyState;
    private ModalDialog dialog;
    private boolean overShown;
    private boolean closed;
    private int shownRevision = -1;
    private int respawnDialogTurn = -1;
    private boolean eliminatedDialogShown;
    private Reconnector reconnector;
    private Label reconnectDetail;
    private Label reconnectCountdown;

    /**
     * Builds the screen for a game that has just started and takes over the messages that arrived behind the start message.
     *
     * @param game    the game showing the screen
     * @param server  the connection, handed over by the lobby
     * @param address the address of the server
     * @param started the message that started the game
     * @param carried the messages that arrived behind it
     */
    public GameScreen(RobotRampageGame game, ConnectedServer server, ServerAddress address, GameStarted started,
                      List<Object> carried) {
        super(game);
        this.server = server;
        this.address = address;
        this.model = new GameModel(started);
        this.cards = new CardView(ui);
        this.timeLabel = ui.label("0:00", Theme.TextStyle.HEADING, Theme.INK);
        this.timeCaption = ui.label("Time left", Theme.TextStyle.CHIP, Theme.INK_MUTED);
        this.timeBar = new ProgressPill(ui, 10);
        this.powerToggle = ui.toggle();
        this.cardsHeading = ui.label("Cards", Theme.TextStyle.HEADING, Theme.INK);
        float tile = Math.min(MAX_TILE, PANEL_HEIGHT / Math.max(model.board().width(), model.board().height()));
        this.boardActor = new BoardActor(ui, model.board(), tile);
        boardActor.setPosition(BOARD_LEFT + (PANEL_HEIGHT - boardActor.getWidth()) / 2f,
            Theme.VIEW_HEIGHT - PANEL_TOP - PANEL_HEIGHT + (PANEL_HEIGHT - boardActor.getHeight()) / 2f);
        powerToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (powerToggle.isChecked()) {
                    powerToggle.setChecked(false);
                    showPowerDownDialog();
                } else {
                    applyPowerDownChoice(false);
                }
            }
        });

        programmingGroup.setSize(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT);
        resolutionGroup.setSize(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT);
        gameOverGroup.setSize(Theme.VIEW_WIDTH, Theme.VIEW_HEIGHT);
        stage.addActor(programmingGroup);
        stage.addActor(resolutionGroup);
        stage.addActor(gameOverGroup);
        resolutionGroup.setVisible(false);
        gameOverGroup.setVisible(false);
        activeGroup = programmingGroup;
        buildFrame();
        activeGroup = resolutionGroup;
        buildResolutionFrame();
        activeGroup = programmingGroup;
        carried.forEach(this::onMessage);
        refreshAll();
    }

    // ------------------------------------------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------------------------------------------

    /**
     * Puts the panels of the layout on the stage. The panels are built once; their contents are rebuilt when the game
     * changes.
     */
    private void buildFrame() {
        headerLeft.left();
        place(headerLeft, 32f, 14f, 1200f, 52f);
        place(timePill(), 1446f, 14f, 380f, 52f + UiKit.SHAPE_RESERVE);
        place(timerButton(), 1240f, 14f, 194f, 48f + UiKit.SHAPE_RESERVE);
        place(menuButton(), 1840f, 14f, 48f, 48f + UiKit.SHAPE_RESERVE);

        Table players = ui.panel();
        players.pad(20f).top().left();
        players.add(ui.label("Players", Theme.TextStyle.HEADING, Theme.INK)).left().row();
        playersBody.top().left();
        players.add(playersBody).growX().expandY().top().padTop(Theme.SPACE_4).row();
        players.add(ui.label("Only the cards stay secret. You see who has confirmed.", Theme.TextStyle.BODY, Theme.INK_MUTED))
            .left().padTop(Theme.SPACE_4);
        place(players, 32f, PANEL_TOP, 604f, PANEL_HEIGHT);

        activeGroup.addActor(boardActor);

        Table robot = ui.panel();
        robot.pad(20f).top().left();
        robotBody.top().left();
        robot.add(robotBody).grow();
        place(robot, 1284f, PANEL_TOP, 604f, PANEL_HEIGHT);

        Table program = ui.panel();
        program.padTop(14f).padBottom(14f + UiKit.SHAPE_RESERVE).padLeft(20f).padRight(20f).top().left();
        programBody.top().left();
        program.add(programBody).grow();
        place(program, 32f, 690f, 1856f, 348f);
    }

    /**
     * Places a widget at a position and size of the mockup, measured from the top left of the screen.
     *
     * @param content the widget
     * @param left    the distance from the left edge
     * @param top     the distance from the top edge
     * @param width   the width
     * @param height  the height
     */
    private void place(Actor content, float left, float top, float width, float height) {
        Table holder = new Table();
        holder.setFillParent(true);
        holder.top().left().padLeft(left).padTop(top);
        holder.add(content).size(width, height);
        activeGroup.addActor(holder);
    }

    /**
     * Places a widget at a position and size of the mockup, measured from the top right of the screen.
     *
     * @param content the widget
     * @param right   the distance from the right edge
     * @param top     the distance from the top edge
     * @param width   the width
     * @param height  the height
     */
    private void placeRight(Actor content, float right, float top, float width, float height) {
        Table holder = new Table();
        holder.setFillParent(true);
        holder.top().right().padRight(right).padTop(top);
        holder.add(content).size(width, height);
        activeGroup.addActor(holder);
    }

    /**
     * Builds the pill with the time left to program.
     *
     * @return the pill
     */
    private Table timePill() {
        Table pill = new Table();
        pill.setBackground(ui.rounded(Theme.SURFACE_RAISED, Theme.ACCENT, Theme.BORDER_CONTROL, 26));
        pill.padLeft(20f).padRight(20f).padBottom(UiKit.SHAPE_RESERVE);
        pill.add(timeCaption).width(76f).left();
        pill.add(timeLabel).width(84f).left().padLeft(Theme.SPACE_2);
        pill.add(timeBar).growX().height(10f).padLeft(Theme.SPACE_2);
        return pill;
    }

    /**
     * Builds the button with which the host stops and restarts the programming timer. It is only shown to the host.
     *
     * @return the button
     */
    private TextButton timerButton() {
        timerButton = ui.button("Pause timer", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        timerButton.setVisible(false);
        timerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                server.link().send(model.toggleTimerPaused());
            }
        });
        return timerButton;
    }

    /**
     * Shows the state of the timer: the caption of the time pill for everybody, and for the host the button that stops or
     * restarts it.
     */
    private void refreshTimer() {
        boolean paused = model.isTimerPaused();
        timeCaption.setText(paused ? "Paused" : "Time left");
        timerButton.setVisible(model.canPauseTimer());
        timerButton.setText((paused ? "Resume timer" : "Pause timer").toUpperCase(Locale.ROOT));
        if (paused != timerButtonPaused) {
            timerButtonPaused = paused;
            timerButton.setStyle(ui.shapes().button(paused ? Theme.ButtonKind.PRIMARY : Theme.ButtonKind.GHOST,
                ui.fonts().get(Theme.TextStyle.BUTTON)));
        }
    }

    /**
     * Builds the button that opens the menu.
     *
     * @return the button
     */
    private Button menuButton() {
        Button button = new Button(ui.shapes().button(Theme.ButtonKind.GHOST, ui.fonts().get(Theme.TextStyle.BUTTON)));
        button.add(new Image(ui.image("icons/settings.png"))).size(26f);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showMenu();
            }
        });
        return button;
    }

    // ------------------------------------------------------------------------------------------------------
    // Messages and time
    // ------------------------------------------------------------------------------------------------------

    /**
     * Reads what the server sent, keeps the clock running and redraws what changed.
     *
     * @param delta seconds since the previous frame
     */
    @Override
    protected void update(float delta) {
        if (closed) {
            return;
        }
        if (reconnector != null) {
            updateReconnect(delta);
            return;
        }
        server.link().poll(this);
        if (lobbyState != null && !closed) {
            closed = true;
            List<Object> early = new ArrayList<>();
            early.add(lobbyState);
            early.addAll(returnToLobby);
            game.setScreen(new LobbyScreen(game, new ConnectedServer(server.link(), server.welcome(), early, false),
                address));
            return;
        }
        model.tick(delta);
        timeLabel.setText(model.timeText());
        timeBar.setFraction(model.timeFraction());
        updateResolution(delta);
        if (model.revision() != shownRevision) {
            refreshAll();
        }
        if (model.stage() == GameModel.Stage.OVER && !overShown) {
            overShown = true;
            showGameOver();
        }
        if (overShown) {
            gameOverView.setLobbySeconds(model.lobbySecondsLeft());
        } else {
            maybeShowEliminatedDialog();
        }
    }

    /**
     * Takes in one message from the server. A new lobby state means the game is over and the session is back in the lobby;
     * what arrives behind it belongs to the lobby screen.
     *
     * @param message a message from the server
     */
    @Override
    public void onMessage(Object message) {
        if (lobbyState != null) {
            returnToLobby.add(message);
        } else if (message instanceof LobbyState lobby) {
            lobbyState = lobby;
        } else if (message instanceof RequestRejected rejected) {
            toast(rejected.reason());
            model.submissionRefused();
        } else {
            model.apply(message);
        }
    }

    /**
     * Returns the model the screen shows. It is meant for tools and tests that build a game state without a server; call
     * {@link #refresh()} after changing the program in it.
     *
     * @return the model
     */
    public GameModel model() {
        return model;
    }

    /**
     * Redraws everything from the model.
     */
    public void refresh() {
        refreshAll();
    }

    /**
     * The connection dropped. If the server handed out a session token to take the seat back with, tries to reconnect
     * instead of leaving at once; otherwise (a refused handshake carries no token) falls back to the connect screen.
     */
    @Override
    public void onDisconnect() {
        if (closed || reconnector != null) {
            return;
        }
        String token = server.welcome().getSessionToken();
        if (token == null) {
            closed = true;
            game.setScreen(new ConnectScreen(game, "The connection to the server was closed."));
            return;
        }
        reconnector = new Reconnector(address, model.nameOf(model.mySeat()), token, AppVersion.getVersion(),
            server.welcome().getReconnectGraceSeconds(), NetworkClient::new);
        showReconnectDialog();
    }

    /**
     * Drives the reconnect attempt instead of the running game while the connection is down, and reacts once it settles.
     *
     * @param delta seconds since the previous frame
     */
    private void updateReconnect(float delta) {
        reconnector.update(delta);
        switch (reconnector.phase()) {
            case TRYING -> refreshReconnectDialog();
            case SUCCEEDED -> {
                ConnectedServer connected = reconnector.connected();
                reconnector = null;
                closed = true;
                game.setScreen(new LobbyScreen(game, connected, address));
            }
            case GAVE_UP -> {
                String reason = reconnector.giveUpReason();
                reconnector = null;
                closed = true;
                game.setScreen(new ConnectScreen(game, reason));
            }
        }
    }

    /**
     * Opens the dialog that stays up while the seat is being taken back: no buttons to retry, since it retries by itself,
     * only the choice to give up and leave.
     */
    private void showReconnectDialog() {
        TextButton leave = ui.button("Leave game", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                giveUpReconnect();
            }
        });
        reconnectDetail = ui.label("", Theme.TextStyle.BODY_LARGE, Theme.INK);
        reconnectDetail.setWrap(true);
        reconnectCountdown = ui.label("", Theme.TextStyle.BODY, Theme.INK_MUTED);
        open(new ModalDialog(ui, 640f, Theme.DANGER)
            .title("Connection lost")
            .row(reconnectDetail)
            .row(reconnectCountdown)
            .buttons(240f, leave)
            .onEscape(this::giveUpReconnect));
        refreshReconnectDialog();
    }

    /**
     * Updates the wording of the open reconnect dialog with how the attempt stands.
     */
    private void refreshReconnectDialog() {
        reconnectDetail.setText(reconnector.isWaitingToRetry() ? "Waiting to try again …"
            : "Trying to reconnect (attempt " + reconnector.attemptNumber() + ") …");
        reconnectCountdown.setText("Your seat is held for " + formatCountdown(reconnector.secondsLeft()) + " more.");
    }

    /**
     * Formats a number of seconds as minutes and seconds, the way the programming timer's pill does (design.md 4.6: "Away
     * 9:12").
     *
     * @param totalSeconds the seconds
     * @return for example {@code 9:12}
     */
    private static String formatCountdown(int totalSeconds) {
        return totalSeconds / 60 + ":" + (totalSeconds % 60 < 10 ? "0" : "") + totalSeconds % 60;
    }

    /**
     * Gives up on reconnecting because the player chose to leave, and returns to the connect screen.
     */
    private void giveUpReconnect() {
        reconnector.cancel();
        reconnector = null;
        closed = true;
        game.setScreen(new ConnectScreen(game));
    }

    // ------------------------------------------------------------------------------------------------------
    // Redrawing
    // ------------------------------------------------------------------------------------------------------

    /**
     * Rebuilds everything that depends on the game and remembers which state was drawn.
     */
    private void refreshAll() {
        shownRevision = model.revision();
        refreshHeader();
        refreshTimer();
        refreshPlayers();
        refreshRobot();
        refreshProgram();
        refreshBoard();
        maybeShowRespawnDialog();
    }

    /**
     * Rebuilds the title with the turn and the deal.
     */
    private void refreshHeader() {
        headerLeft.clearChildren();
        headerLeft.add(ui.label(model.headline(), Theme.TextStyle.SUBTITLE, Theme.INK));
        if (model.turn() > 0) {
            headerLeft.add(ui.chip("Turn " + model.turn(), UiKit.ChipKind.INK)).height(UiKit.CHIP_CELL_HEIGHT)
                .padLeft(Theme.SPACE_4);
        }
        headerLeft.add(ui.chip("Deal: 9 − damage", UiKit.ChipKind.OUTLINE)).height(UiKit.CHIP_CELL_HEIGHT)
            .padLeft(Theme.SPACE_2);
    }

    /**
     * Puts the robots on the board, together with the "ghost path" (design.md 3.5, 4.3) of faint copies of this
     * player's own robot showing where their program placed so far would take it — drawn first, so a live robot
     * standing where a ghost would be always shows through fully opaque.
     * <p>
     * {@code model.ghostPath()} depends on the draft's registers, and {@link ProgramDraft} placing/taking a card does
     * not bump {@link GameModel#revision()} — so a mutation site that only redraws the program panel, not this one,
     * shows a stale ghost. {@link #place} and {@link #takeBack} call {@link #refreshAll} for exactly this reason;
     * route any future one (such as a {@code placeAt} for a specific register) through {@link #refreshAll} too,
     * rather than calling this method alone.
     */
    private void refreshBoard() {
        List<RobotPose> poses = new ArrayList<>();
        for (MovementPreview.Step step : model.ghostPath()) {
            poses.add(new RobotPose(model.mySeat(), step.position().x(), step.position().y(),
                BoardGeometry.rotation(step.facing()), GHOST_ALPHA, 0, false));
        }
        for (RobotState robot : model.robots()) {
            if (robot.status() == RobotStatus.ACTIVE && robot.position() != null) {
                poses.add(RobotPose.at(robot.robotId(), robot.position(), robot.facing()));
            }
        }
        boardActor.setRobots(poses);
    }

    /**
     * Rebuilds the list of players.
     */
    private void refreshPlayers() {
        playersBody.clearChildren();
        List<GameModel.PlayerRow> rows = model.playerRows();
        float available = 470f;
        float height = Math.min(ROW_HEIGHT, (available - (rows.size() - 1) * ROW_GAP) / rows.size());
        for (GameModel.PlayerRow row : rows) {
            playersBody.add(playerRow(row)).growX().height(height + UiKit.SHAPE_RESERVE)
                .padBottom(ROW_GAP - UiKit.SHAPE_RESERVE).row();
        }
    }

    /**
     * Builds the line of one player: the robot, the name, lives and damage, and what the player is doing.
     *
     * @param row the player
     * @return the line
     */
    private Table playerRow(GameModel.PlayerRow row) {
        Table line = new Table();
        line.setBackground(ui.rounded(Theme.SURFACE_RAISED, row.you() ? Theme.ACCENT : Theme.LINE,
            row.you() ? Theme.BORDER_HEAVY : Theme.BORDER_HAIRLINE, 14));
        line.padLeft(16f).padRight(16f).padBottom(UiKit.SHAPE_RESERVE);
        Image robot = new Image(ui.image(RobotLook.picture(row.seat())));
        robot.setScaling(Scaling.fit);
        line.add(robot).size(44f);

        Table text = new Table();
        text.left();
        Table nameLine = new Table();
        nameLine.add(ui.label(row.name(), Theme.TextStyle.NAME, Theme.INK));
        if (row.you()) {
            nameLine.add(ui.chip("You", UiKit.ChipKind.PRIMARY)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
        }
        text.add(nameLine).left().row();
        Table stats = new Table();
        stats.add(ui.pips(row.lives(), Robot.STARTING_LIVES, 12f, 5)).padRight(Theme.SPACE_3);
        stats.add(ui.label("Damage " + row.damage(), Theme.TextStyle.CAPTION, Theme.INK_MUTED));
        text.add(stats).left();
        line.add(text).expandX().left().padLeft(14f);

        Table status = statusChip(row.status());
        if (status != null) {
            line.add(status).height(UiKit.CHIP_CELL_HEIGHT);
        }
        return line;
    }

    /**
     * Builds the chip that says what a player is doing.
     *
     * @param status what the player is doing
     * @return the chip, or {@code null} when there is nothing to say
     */
    private Table statusChip(GameModel.PlayerStatus status) {
        return switch (status) {
            case NONE -> null;
            case THINKING -> ui.chip("Thinking", UiKit.ChipKind.OUTLINE);
            case CONFIRMED -> ui.chip("Confirmed", UiKit.ChipKind.SUCCESS);
            case AWAY -> ui.chip("Away", UiKit.ChipKind.OUTLINE);
            case POWERED_DOWN -> ui.chip("Powered down", UiKit.ChipKind.OUTLINE);
            case OUT -> ui.chip("Out", UiKit.ChipKind.INK);
        };
    }

    /**
     * Rebuilds the panel with this player's robot: lives, damage, the hand, locked registers and the board key.
     */
    private void refreshRobot() {
        robotBody.clearChildren();
        RobotState me = model.myRobot();
        robotBody.add(ui.label("Your robot", Theme.TextStyle.HEADING, Theme.INK)).left().row();

        Table who = new Table();
        Image picture = new Image(ui.image(RobotLook.picture(model.mySeat())));
        picture.setScaling(Scaling.fit);
        who.add(picture).size(88f);
        Table names = new Table();
        names.left();
        names.add(ui.label(model.nameOf(model.mySeat()), Theme.TextStyle.NAME, Theme.INK)).left().row();
        names.add(ui.label(RobotLook.name(model.mySeat()) + " robot · seat " + (model.mySeat() + 1),
            Theme.TextStyle.BODY, Theme.INK_MUTED)).left();
        who.add(names).left().padLeft(18f);
        robotBody.add(who).left().padTop(14f).row();

        Table stats = new Table();
        stats.left();
        stat(stats, "Lives", ui.pips(me.lives(), Robot.STARTING_LIVES, 18f, 5));
        Table damage = new Table();
        for (int i = 0; i < Robot.FULL_HAND_SIZE; i++) {
            Image segment = new Image(ui.rounded(i < me.damage() ? Theme.DANGER : Theme.LINE,
                i < me.damage() ? Theme.DANGER : Theme.LINE, 0, 3));
            damage.add(segment).size(14f, 20f + UiKit.SHAPE_RESERVE).padRight(3f);
        }
        damage.add(ui.label(me.damage() + " of " + Robot.FULL_HAND_SIZE, Theme.TextStyle.BODY, Theme.INK)).padLeft(7f);
        stat(stats, "Damage", damage);
        stat(stats, "Hand this turn", ui.label(model.handText(), Theme.TextStyle.BODY_LARGE, Theme.INK));
        stat(stats, "Locked registers", ui.label(model.lockedRegistersText(), Theme.TextStyle.BODY_LARGE, Theme.INK));
        Table flag = new Table();
        flag.add(new Image(ui.image("icons/flag.png"))).size(20f).padRight(Theme.SPACE_2);
        flag.add(ui.label(model.nextFlagText(), Theme.TextStyle.BODY_LARGE, Theme.INK));
        stat(stats, "Next flag", flag);
        robotBody.add(stats).left().growX().padTop(14f).row();

        robotBody.add(new Image(ui.solid(Theme.LINE))).growX().height(1f).padTop(14f).row();
        robotBody.add(ui.label("Board key", Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left().padTop(10f).row();
        robotBody.add(boardKey()).left().padTop(Theme.SPACE_2);
    }

    /**
     * Adds a line of the robot's facts: a caption and its value.
     *
     * @param table   the table of facts
     * @param caption what the fact is
     * @param value   the fact
     */
    private void stat(Table table, String caption, Actor value) {
        table.add(ui.label(caption, Theme.TextStyle.CAPTION, Theme.INK_MUTED)).width(150f).left().padBottom(6f);
        table.add(value).left().padBottom(6f).row();
    }

    /**
     * Builds the key that says what the pictures on the board mean.
     *
     * @return the key
     */
    private Table boardKey() {
        Table key = new Table();
        key.top().left();
        key.add(keyColumn(new String[] {"Belt", "Express belt", "Gear"},
            new String[][] {{"tiles/belt.png"}, {"tiles/belt-express.png"}, {"tiles/gear-clockwise.png"}})).top().padRight(16f);
        key.add(keyColumn(new String[] {"Pit", "Repair site", "Flag"},
            new String[][] {{"tiles/pit.png"}, {"tiles/repair-site.png"}, {"tiles/floor.png", "board/flag.png"}}))
            .top().padRight(16f);
        key.add(keyColumn(new String[] {"Wall", "Laser"},
            new String[][] {{"tiles/floor.png", "board/wall.png"}, {"tiles/floor.png", "board/laser-emitter.png"}})).top();
        return key;
    }

    /**
     * Builds one column of the board key.
     *
     * @param names    the names of the things
     * @param pictures for each thing the pictures it is drawn from, bottom first
     * @return the column
     */
    private Table keyColumn(String[] names, String[][] pictures) {
        Table column = new Table();
        column.top().left();
        for (int i = 0; i < names.length; i++) {
            Stack icon = new Stack();
            for (String path : pictures[i]) {
                icon.add(new Image(ui.image(path)));
            }
            column.add(icon).size(KEY_ICON).padBottom(8f);
            column.add(ui.label(names[i], Theme.TextStyle.BODY, Theme.INK)).left().padLeft(10f).padBottom(8f).row();
        }
        return column;
    }

    // ------------------------------------------------------------------------------------------------------
    // Program and hand
    // ------------------------------------------------------------------------------------------------------

    /**
     * Rebuilds the panel with the registers, the confirm button and the hand for the current stage.
     */
    private void refreshProgram() {
        programBody.clearChildren();
        GameModel.Stage stage = model.stage();
        if (stage == GameModel.Stage.PROGRAMMING || stage == GameModel.Stage.SUBMITTED) {
            boolean locked = stage == GameModel.Stage.SUBMITTED;
            programBody.add(registersRow(locked)).left().row();
            programBody.add(locked ? waitingRow() : handRow()).left().padTop(10f);
        } else {
            programBody.add(noProgramRow()).left().expand().center();
        }
        powerToggle.setChecked(model.powerDownNext());
    }

    /**
     * Builds the row with the title, the five registers, the confirm button and the power-down switch.
     *
     * @param lockedIn whether the program has been locked in and can no longer be changed
     * @return the row
     */
    private Table registersRow(boolean lockedIn) {
        Table row = new Table();
        row.top().left();
        row.add(titleColumn("Program", lockedIn ? "Locked in." : "Played in order, 1–5."))
            .top().width(170f).padRight(24f);
        Table registers = new Table();
        List<ProgramDraft.RegisterView> views = model.draft() == null ? List.of() : model.draft().registers();
        for (int register = 1; register <= ProgramDraft.REGISTERS; register++) {
            ProgramDraft.RegisterView view = register <= views.size() ? views.get(register - 1) : null;
            registers.add(registerColumn(register, view, lockedIn)).top().padRight(12f);
        }
        row.add(registers).top().padRight(48f);
        row.add(confirmColumn(lockedIn)).top();
        return row;
    }

    /**
     * Builds the column with a title and a line of explanation under it.
     *
     * @param title the title
     * @param note  the line under it
     * @return the column
     */
    private Table titleColumn(String title, String note) {
        Table column = new Table();
        column.top().left();
        column.add(ui.label(title, Theme.TextStyle.HEADING, Theme.INK)).left().row();
        Label label = ui.label(note, Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        label.setWrap(true);
        column.add(label).width(170f).left().padTop(6f);
        return column;
    }

    /**
     * Builds one register: its caption and the card in it, or its empty slot.
     *
     * @param register the register number, 1 to 5
     * @param view     what is in it, or {@code null} while the cards have not been dealt
     * @param lockedIn whether the program is locked in
     * @return the register
     */
    private Table registerColumn(int register, ProgramDraft.RegisterView view, boolean lockedIn) {
        Table column = new Table();
        boolean lockedByDamage = view != null && view.locked();
        column.add(ui.label("Register " + register, Theme.TextStyle.CHIP, lockedByDamage ? Theme.DANGER : Theme.INK_MUTED))
            .height(18f).row();
        Table slot;
        if (lockedIn && !lockedByDamage && !model.programVisible()) {
            slot = cards.hiddenSlot("Random");
        } else if (view == null && lockedIn) {
            slot = cards.hiddenSlot("Hidden");
        } else if (view == null || view.card() == null) {
            slot = cards.emptySlot(register);
        } else if (view.locked()) {
            slot = cards.card(view.card(), CardView.Look.LOCKED);
        } else {
            slot = cards.card(view.card(), CardView.Look.NORMAL);
            if (!lockedIn) {
                cards.makeClickable(slot, () -> takeBack(register));
            }
        }
        column.add(slot).size(CardView.WIDTH, CardView.CELL_HEIGHT).padTop(4f);
        return column;
    }

    /**
     * Builds the column with the confirm button, what it still needs, and the power-down switch.
     *
     * @param lockedIn whether the program is locked in
     * @return the column
     */
    private Table confirmColumn(boolean lockedIn) {
        Table column = new Table();
        column.top().left();
        TextButton confirm = ui.button(lockedIn ? "Locked in" : "Confirm program",
            Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON_LARGE);
        confirm.setDisabled(!model.canConfirm());
        confirm.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                confirm();
            }
        });
        column.add(confirm).size(340f, 64f).left().row();
        String hint = lockedIn ? model.lockedInNote() : model.confirmHint();
        Label hintLabel = ui.label(hint, Theme.TextStyle.BODY, Theme.INK_MUTED);
        hintLabel.setWrap(true);
        column.add(hintLabel).width(340f).left().padTop(10f).row();
        column.add(powerDownSwitch(lockedIn, false)).left().padTop(10f);
        return column;
    }

    /**
     * Builds the power-down switch with its explanation.
     *
     * @param lockedIn whether the program is locked in, which dims the switch and freezes it
     * @param staying  whether the robot is powered down already, so the switch keeps it down for another turn
     * @return the switch and its text
     */
    private Table powerDownSwitch(boolean lockedIn, boolean staying) {
        Table block = new Table();
        block.add(powerToggle).size(52f, 28f);
        Table text = new Table();
        text.left();
        text.add(ui.label(staying ? "Stay powered down" : "Power down next turn", Theme.TextStyle.LABEL, Theme.INK)).left()
            .row();
        Label note = ui.label(staying ? "Robot rests another turn and is repaired again."
            : "Robot rests for a turn and is fully repaired.", Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        text.add(note).left();
        block.add(text).left().padLeft(Theme.SPACE_3);
        block.setTouchable(lockedIn ? Touchable.disabled : Touchable.enabled);
        block.getColor().a = lockedIn ? 0.6f : 1f;
        return block;
    }

    /**
     * Builds the row with the hand: every dealt card, or a stand-in where the card sits in a register.
     *
     * @return the row
     */
    private Table handRow() {
        Table row = new Table();
        row.top().left();
        ProgramDraft draft = model.draft();
        int dealt = draft == null ? 0 : draft.hand().size();
        row.add(titleColumn("Hand", dealt + (dealt == 1 ? " card dealt" : " cards dealt"))).top().width(170f).padRight(24f);
        Table hand = new Table();
        if (draft != null) {
            for (Card card : draft.hand()) {
                Table view;
                if (draft.isPlaced(card)) {
                    view = cards.standIn();
                } else {
                    view = cards.card(card, CardView.Look.NORMAL);
                    cards.makeClickable(view, () -> place(card));
                }
                hand.add(view).size(CardView.WIDTH, CardView.CELL_HEIGHT).padRight(12f);
            }
        }
        row.add(hand).top();
        String extra = model.lockedHint();
        Label hint = ui.label(extra.isEmpty() ? HAND_HINT : extra + " " + HAND_HINT, Theme.TextStyle.BODY, Theme.INK_MUTED);
        hint.setWrap(true);
        row.add(hint).width(400f).top().padLeft(Theme.SPACE_4);
        return row;
    }

    /**
     * Builds the row that replaces the hand once the program is locked in: who the turn is waiting for.
     *
     * @return the row
     */
    private Table waitingRow() {
        Table row = new Table();
        row.left();
        row.add(ui.label(model.waitingForText(), Theme.TextStyle.HEADING, Theme.INK)).left().row();
        row.add(ui.label("The turn starts when everyone has confirmed or the timer runs out.", Theme.TextStyle.LEAD,
            Theme.INK_MUTED)).left().padTop(Theme.SPACE_2);
        return row;
    }

    /**
     * Builds what the panel shows when there is nothing to program: waiting, resting, out of the game, or a turn being played.
     *
     * @return the content
     */
    private Table noProgramRow() {
        Table column = new Table();
        String title;
        String note;
        switch (model.stage()) {
            case SITTING_OUT -> {
                boolean out = model.myRobot().status() == RobotStatus.ELIMINATED;
                title = out ? "You are out of the game" : "Your robot is powered down";
                note = out ? "You keep watching until the game ends."
                    : "It does not move this turn and repairs itself. You can keep it down for another turn.";
            }
            case RESOLVING -> {
                title = "Turn " + model.turn() + " has been played";
                note = "The board shows where everybody stands. Watching the turn play out comes next.";
            }
            case OVER -> {
                title = "The game is over";
                note = "You return to the lobby in a few seconds.";
            }
            default -> {
                title = "Waiting for the turn to start";
                note = "Your cards arrive when the turn begins.";
            }
        }
        column.add(ui.label(title, Theme.TextStyle.HEADING, Theme.INK)).row();
        column.add(ui.label(note, Theme.TextStyle.LEAD, Theme.INK_MUTED)).padTop(Theme.SPACE_2).row();
        if (model.stage() == GameModel.Stage.SITTING_OUT && model.isPoweredDownThisTurn()) {
            column.add(powerDownSwitch(false, true)).padTop(Theme.SPACE_6);
        }
        return column;
    }

    /**
     * Puts a card of the hand into the next free register.
     *
     * @param card the card
     */
    private void place(Card card) {
        if (model.draft() != null && model.draft().place(card)) {
            refreshAll();
        }
    }

    /**
     * Takes the card out of a register and back into the hand.
     *
     * @param register the register number
     */
    private void takeBack(int register) {
        if (model.draft() != null && model.draft().take(register) != null) {
            refreshAll();
        }
    }

    /**
     * Sends the program to the server.
     */
    private void confirm() {
        if (model.canConfirm()) {
            server.link().send(model.submit());
            refreshProgram();
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------------------------------------------

    private static final String[] STEP_NAMES = {"Reveal", "Robot movement", "Express belts", "All belts", "Pushers", "Gears",
        "Lasers", "Crushers", "Checkpoints"};
    private static final float RESOLUTION_TOP = 84f;
    private static final float RESOLUTION_HEIGHT = 768f;
    private static final int MAX_FEED_LINES = 10;

    /**
     * Puts the panels of the resolution layout on the stage: the cards played, the board, what happened, and the ribbon of
     * registers and steps below them, with the playback controls in the header.
     */
    private void buildResolutionFrame() {
        resolutionTitle.left();
        place(resolutionTitle, 32f, 14f, 800f, 52f);
        placeRight(playbackControls(), 32f, 14f, 630f, 48f + UiKit.SHAPE_RESERVE);

        Table cards = ui.panel();
        cards.pad(20f).top().left();
        cards.add(cardsHeading).left().row();
        Label hint = ui.label("Played from the highest priority to the lowest.", Theme.TextStyle.BODY, Theme.INK_MUTED);
        cards.add(hint).left().padTop(Theme.SPACE_1).row();
        cardsBody.top().left();
        cards.add(cardsBody).growX().expandY().top().padTop(Theme.SPACE_3);
        place(cards, 32f, RESOLUTION_TOP, 520f, RESOLUTION_HEIGHT);

        float tile = Math.min(64f, RESOLUTION_HEIGHT / Math.max(model.board().width(), model.board().height()));
        resolutionBoard = new BoardActor(ui, model.board(), tile);
        resolutionBoard.setStaticBeams(false);
        resolutionBoard.setPosition(576f + (RESOLUTION_HEIGHT - resolutionBoard.getWidth()) / 2f,
            Theme.VIEW_HEIGHT - RESOLUTION_TOP - RESOLUTION_HEIGHT + (RESOLUTION_HEIGHT - resolutionBoard.getHeight()) / 2f);
        activeGroup.addActor(resolutionBoard);

        Table feed = ui.panel();
        feed.pad(20f).top().left();
        feed.add(ui.label("What happened", Theme.TextStyle.HEADING, Theme.INK)).left().row();
        feedBody.top().left();
        feed.add(feedBody).growX().expandY().top().padTop(Theme.SPACE_3);
        place(feed, 1368f, RESOLUTION_TOP, 520f, RESOLUTION_HEIGHT);

        Table ribbon = ui.panel();
        ribbon.pad(16f).top().left();
        Table registers = new Table();
        registers.left();
        registers.add(ui.label("Registers", Theme.TextStyle.CHIP, Theme.INK_MUTED)).padRight(Theme.SPACE_3);
        registers.add(registersRibbon).left();
        registers.add(ui.label("Every register plays in the same nine steps.", Theme.TextStyle.BODY, Theme.INK_MUTED))
            .expandX().right();
        ribbon.add(registers).growX().row();
        stepsRibbon.left();
        ribbon.add(stepsRibbon).left().padTop(Theme.SPACE_3);
        place(ribbon, 32f, 868f, 1856f, 150f);
    }

    /**
     * Builds the playback controls: pause, the speed and skipping to the end of the turn.
     *
     * @return the controls
     */
    private Table playbackControls() {
        Theme.TextStyle font = Theme.TextStyle.BUTTON;
        pauseButton = new Button(ui.shapes().button(Theme.ButtonKind.GHOST, ui.fonts().get(font)));
        pauseIcon = new Image(ui.image("icons/pause.png"));
        pauseButton.add(pauseIcon).size(26f);
        pauseButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setPaused(!paused);
            }
        });
        TextButton skip = ui.button("Skip to end of turn", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        skip.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                skipReplay();
            }
        });
        speedBox.setBackground(ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL, Theme.RADIUS_MD));
        speedBox.pad(2f, 2f, 2f + UiKit.SHAPE_RESERVE, 2f);
        refreshSpeed();

        Table controls = new Table();
        controls.right();
        controls.add(pauseButton).size(48f, 48f + UiKit.SHAPE_RESERVE);
        controls.add(speedBox).height(48f + UiKit.SHAPE_RESERVE).padLeft(Theme.SPACE_3);
        controls.add(skip).size(340f, 48f + UiKit.SHAPE_RESERVE).padLeft(Theme.SPACE_3);
        return controls;
    }

    /**
     * Rebuilds the switch that chooses the playback speed.
     */
    private void refreshSpeed() {
        speedBox.clearChildren();
        float[] speeds = {1f, 2f, 4f};
        for (float value : speeds) {
            boolean selected = value == speed;
            Table item = new Table();
            item.setBackground(selected ? ui.rounded(Theme.INK, Theme.INK, 0, 8)
                : ui.rounded(new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, 0f), 0, 8));
            item.padLeft(16f).padRight(16f).padBottom(UiKit.SHAPE_RESERVE);
            item.add(ui.label((int) value + "\u00d7", Theme.TextStyle.BODY, selected ? Theme.ON_PRIMARY : Theme.INK));
            item.setTouchable(Touchable.enabled);
            item.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    speed = value;
                    refreshSpeed();
                }
            });
            speedBox.add(item).height(44f + UiKit.SHAPE_RESERVE);
        }
    }

    /**
     * Pauses or resumes the replay and shows the matching button.
     *
     * @param pause {@code true} to pause
     */
    private void setPaused(boolean pause) {
        paused = pause;
        pauseButton.setStyle(ui.shapes().button(paused ? Theme.ButtonKind.PRIMARY : Theme.ButtonKind.GHOST,
            ui.fonts().get(Theme.TextStyle.BUTTON)));
        pauseIcon.setDrawable(ui.image(paused ? "icons/play.png" : "icons/pause.png"));
    }

    /**
     * Jumps to the end of the turn: the board shows where everybody ended up and the state of the server is taken over.
     */
    private void skipReplay() {
        if (replay != null) {
            replay.skipToEnd();
        }
    }

    /**
     * Follows the game into the resolution layout when a turn is resolved and back out of it when the next turn begins, and
     * moves the replay along while it is showing.
     *
     * @param delta seconds since the previous frame
     */
    private void updateResolution(float delta) {
        if (overShown) {
            return;
        }
        boolean resolving = model.stage() == GameModel.Stage.RESOLVING
            || model.stage() == GameModel.Stage.OVER && replay != null;
        if (resolving != showingResolution) {
            showingResolution = resolving;
            programmingGroup.setVisible(!resolving);
            resolutionGroup.setVisible(resolving);
            if (resolving) {
                closeDialog();
                replay = new TurnReplay(model.robotsBeforeResolution(), model.lastResolved().events(), model::nameOf);
                replayCompleted = false;
                shownBeat = -1;
                setPaused(false);
            } else {
                replay = null;
                shownRevision = -1;
            }
        }
        if (!showingResolution || replay == null) {
            return;
        }
        if (!paused && !replay.isDone()) {
            replay.advance(delta * speed);
        }
        if (replay.isDone() && !replayCompleted) {
            replayCompleted = true;
            model.completeResolution();
        }
        TurnReplay.Frame frame = replay.frame();
        resolutionBoard.setRobots(frame.poses());
        resolutionBoard.setBeams(frame.beams());
        if (shownBeat != replay.beatIndex() || shownDone != replay.isDone()) {
            shownBeat = replay.beatIndex();
            shownDone = replay.isDone();
            refreshResolution();
        }
    }

    /**
     * Rebuilds the parts of the resolution layout that change from one moment to the next.
     */
    private void refreshResolution() {
        resolutionTitle.clearChildren();
        resolutionTitle.add(ui.label("Resolution", Theme.TextStyle.SUBTITLE, Theme.INK));
        resolutionTitle.add(ui.chip("Turn " + model.turn(), UiKit.ChipKind.INK)).height(UiKit.CHIP_CELL_HEIGHT)
            .padLeft(Theme.SPACE_4);
        String where = replay.isDone() ? "Turn played" : replay.register() == 0 ? "Clean-up"
            : "Register " + replay.register() + " of 5";
        resolutionTitle.add(ui.chip(where, UiKit.ChipKind.INK)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
        refreshCards();
        refreshFeed();
        refreshRibbon();
    }

    /**
     * Rebuilds the list of cards played in the register being played.
     */
    private void refreshCards() {
        cardsBody.clearChildren();
        int register = replay.register();
        ui.setText(cardsHeading, Theme.TextStyle.HEADING, register == 0 ? "Cards" : "Cards, register " + register);
        if (register == 0) {
            return;
        }
        for (TurnReplay.Play play : replay.plays()) {
            boolean you = play.robotId() == model.mySeat();
            Table row = new Table();
            row.setBackground(ui.rounded(Theme.SURFACE_RAISED, you ? Theme.ACCENT : Theme.LINE,
                you ? Theme.BORDER_HEAVY : Theme.BORDER_HAIRLINE, 14));
            row.padLeft(14f).padRight(14f).padBottom(UiKit.SHAPE_RESERVE);
            Image robot = new Image(ui.image(RobotLook.picture(play.robotId())));
            robot.setScaling(Scaling.fit);
            row.add(robot).size(42f);
            Table who = new Table();
            who.left();
            Table nameLine = new Table();
            nameLine.add(ui.label(model.nameOf(play.robotId()), Theme.TextStyle.BODY_LARGE, Theme.INK));
            if (you) {
                nameLine.add(ui.chip("You", UiKit.ChipKind.PRIMARY)).height(UiKit.CHIP_CELL_HEIGHT).padLeft(Theme.SPACE_2);
            }
            who.add(nameLine).left().row();
            who.add(ui.label(CardLook.name(play.card().type()), Theme.TextStyle.BODY, Theme.INK_MUTED)).left();
            row.add(who).expandX().left().padLeft(12f);
            Table priority = new Table();
            priority.right();
            priority.add(ui.label("Priority", Theme.TextStyle.CAPTION, Theme.INK_MUTED)).right().row();
            priority.add(ui.label(String.valueOf(play.card().priority()), Theme.TextStyle.BUTTON_MEDIUM, Theme.INK)).right();
            row.add(priority).padRight(Theme.SPACE_3);
            Image icon = new Image(ui.image(CardLook.picture(play.card().type())));
            icon.setScaling(Scaling.fit);
            row.add(icon).size(30f);
            cardsBody.add(row).growX().height(64f + UiKit.SHAPE_RESERVE).padBottom(8f - UiKit.SHAPE_RESERVE).row();
        }
    }

    /**
     * Rebuilds the list of what happened, newest first, with the moment being played marked.
     */
    private void refreshFeed() {
        feedBody.clearChildren();
        List<TurnReplay.Line> lines = replay.feed();
        for (int i = 0; i < lines.size() && i < MAX_FEED_LINES; i++) {
            feedBody.add(feedLine(lines.get(i), i == 0 && !replay.isDone())).growX().padBottom(8f).row();
        }
    }

    /**
     * Builds one line of the list of what happened.
     *
     * @param line    the line
     * @param current whether it is the moment being played, which gets a teal border
     * @return the line
     */
    private Table feedLine(TurnReplay.Line line, boolean current) {
        Table row = new Table();
        row.setBackground(ui.rounded(Theme.SURFACE_RAISED, current ? Theme.ACCENT : Theme.LINE,
            current ? Theme.BORDER_HEAVY : Theme.BORDER_HAIRLINE, 12));
        row.padTop(8f).padLeft(12f).padRight(12f).padBottom(8f + UiKit.SHAPE_RESERVE);
        Color color = switch (line.kind()) {
            case LASER, DESTROYED -> Theme.DANGER;
            case BELT -> Theme.ACCENT;
            case FLAG, REPAIR -> Theme.SUCCESS;
            case CARD, GEAR, PUSH -> Theme.INK;
        };
        row.add(new Image(ui.rounded(color, color, 0, 10))).size(36f, 36f + UiKit.SHAPE_RESERVE);
        Table text = new Table();
        text.left();
        Label title = ui.label(line.title(), Theme.TextStyle.BODY_LARGE, Theme.INK);
        text.add(title).left().row();
        if (!line.detail().isEmpty()) {
            text.add(ui.label(line.detail(), Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left();
        }
        row.add(text).left().expandX().padLeft(12f);
        return row;
    }

    /**
     * Rebuilds the ribbon with the registers and the nine steps, showing what has been played and what is being played.
     */
    private void refreshRibbon() {
        registersRibbon.clearChildren();
        int position = replay.isDone() ? 7 : replay.register() == 0 ? 6 : replay.register();
        for (int i = 1; i <= 6; i++) {
            UiKit.ChipKind kind = i < position ? UiKit.ChipKind.SUCCESS : i == position ? UiKit.ChipKind.ACCENT
                : UiKit.ChipKind.OUTLINE;
            registersRibbon.add(ui.chip(i == 6 ? "Cleanup" : String.valueOf(i), kind)).height(UiKit.CHIP_CELL_HEIGHT)
                .padRight(6f);
        }
        stepsRibbon.clearChildren();
        int current = replay.phase().ordinal() - SubPhase.REVEAL.ordinal();
        boolean all = replay.isDone() || replay.register() == 0;
        for (int i = 0; i < STEP_NAMES.length; i++) {
            int state = all || i < current ? 0 : i == current ? 1 : 2;
            stepsRibbon.add(stepChip(i + 1, STEP_NAMES[i], state)).size(190f, 48f + UiKit.SHAPE_RESERVE).padRight(6f);
        }
    }

    /**
     * Builds the chip of one step of a register.
     *
     * @param number the step number, 1 to 9
     * @param name   the name of the step
     * @param state  0 for played, 1 for being played, 2 for still to come
     * @return the chip
     */
    private Table stepChip(int number, String name, int state) {
        Color fill = state == 0 ? Theme.LINE : state == 1 ? Theme.ACCENT : new Color(0f, 0f, 0f, 0f);
        Table chip = new Table();
        chip.setBackground(state == 2 ? ui.rounded(fill, Theme.LINE_STRONG, Theme.BORDER_CONTROL, Theme.RADIUS_MD)
            : ui.rounded(fill, fill, 0, Theme.RADIUS_MD));
        chip.padLeft(12f).padRight(12f).padBottom(UiKit.SHAPE_RESERVE);
        Table circle = new Table();
        circle.setBackground(state == 1 ? ui.rounded(Color.WHITE, Color.WHITE, 0, 13)
            : state == 0 ? ui.rounded(Theme.SURFACE_RAISED, Theme.SURFACE_RAISED, 0, 13)
            : ui.rounded(new Color(0f, 0f, 0f, 0f), Theme.LINE_STRONG, Theme.BORDER_CONTROL, 13));
        circle.padBottom(UiKit.SHAPE_RESERVE);
        circle.add(ui.label(String.valueOf(number), Theme.TextStyle.BODY, state == 1 ? Theme.ACCENT
            : state == 0 ? Theme.INK : Theme.INK_MUTED));
        chip.add(circle).size(26f, 26f + UiKit.SHAPE_RESERVE);
        chip.add(ui.label(name, Theme.TextStyle.BODY, state == 1 ? Theme.ON_PRIMARY : state == 0 ? Theme.INK
            : Theme.INK_MUTED)).left().padLeft(8f);
        return chip;
    }

    // ------------------------------------------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------------------------------------------

    /**
     * Applies a power-down choice: sets it in the model, shows it on the switch, and, for a robot that is already powered
     * down, tells the server at once, whichever way the choice went, since there is nothing else left to confirm it with.
     *
     * @param announce {@code true} to power down (or stay down), {@code false} to cancel it
     */
    private void applyPowerDownChoice(boolean announce) {
        model.setPowerDownNext(announce);
        powerToggle.setChecked(announce);
        if (model.stage() == GameModel.Stage.SITTING_OUT && model.isPoweredDownThisTurn()) {
            server.link().send(model.announceStayingDown());
        }
    }

    /**
     * Explains what powering down means before it is announced. Turning the switch off needs no explanation and happens at
     * once; only turning it on opens this.
     */
    private void showPowerDownDialog() {
        TextButton notNow = ui.button("Not now", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton confirm = ui.button("Power down", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        notNow.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
            }
        });
        confirm.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
                applyPowerDownChoice(true);
            }
        });
        open(new ModalDialog(ui, 760f, Theme.ACCENT)
            .title("Power down next turn?")
            .row(infoRow(Theme.SUCCESS, "icons/tick.png", "This turn plays as normal",
                "Your five cards still run and your robot still fires."))
            .row(infoRow(Theme.ACCENT, "icons/power.png", "Next turn it rests",
                "Fully repaired, all registers unlocked. No cards, no lasers. It can still be moved and shot."))
            .row(infoRow(Theme.INK, "icons/eye.png", "Everyone sees the marker", "The cards themselves stay secret."))
            .text("It powers up again the turn after, unless you announce another power down.", Theme.TextStyle.BODY,
                Theme.INK_MUTED)
            .buttons(new float[] {200f, 280f}, notNow, confirm)
            .onEscape(this::closeDialog));
    }

    /**
     * Builds one line of an explanation: a small coloured icon and two lines of text.
     *
     * @param color the icon's background
     * @param icon  the icon's picture, relative to the asset folder
     * @param title what it is
     * @param body  what it means
     * @return the line
     */
    private Table infoRow(Color color, String icon, String title, String body) {
        Table row = new Table();
        row.top().left();
        Table mark = new Table();
        mark.setBackground(ui.rounded(color, color, 0, 10));
        mark.add(new Image(ui.image(icon))).size(20f);
        row.add(mark).size(36f, 36f + UiKit.SHAPE_RESERVE).top();
        Table text = new Table();
        text.left();
        text.add(ui.label(title, Theme.TextStyle.BODY_LARGE, Theme.INK)).left().row();
        Label bodyLabel = ui.label(body, Theme.TextStyle.BODY, Theme.INK_MUTED);
        bodyLabel.setWrap(true);
        text.add(bodyLabel).width(600f).left();
        row.add(text).left().padLeft(14f);
        return row;
    }

    /**
     * Opens the dialog that picks the facing of a robot that just re-entered, once per turn, while the player is
     * programming. Nothing happens if the choice does not apply, or it was already offered this turn.
     */
    private void maybeShowRespawnDialog() {
        if (model.stage() != GameModel.Stage.PROGRAMMING || !model.canChooseRespawnFacing()
            || respawnDialogTurn == model.turn()) {
            return;
        }
        respawnDialogTurn = model.turn();
        showRespawnDialog();
    }

    /**
     * Lets the player pick the facing of a robot that just re-entered. Dismissing without pressing "Go" keeps the facing the
     * server gave it, exactly as if "Go" had been pressed without changing the picker.
     */
    private void showRespawnDialog() {
        FacingPicker picker = new FacingPicker(ui, model.mySeat(), model.myRobot().facing());
        TextButton go = ui.button("Go", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        go.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                model.chooseRespawnFacing(picker.facing());
                closeDialog();
                refreshBoard();
            }
        });
        Table pickerRow = new Table();
        pickerRow.add(picker).size(FacingPicker.SIZE);
        open(new ModalDialog(ui, 1000f, Theme.ACCENT)
            .title("Back in the game")
            .row(ui.label("Your robot was destroyed. It returns to its archive marker with no damage.",
                Theme.TextStyle.BODY_LARGE, Theme.INK))
            .row(pickerRow)
            .row(ui.label("Pick the way it faces, then go.", Theme.TextStyle.BODY_LARGE, Theme.INK))
            .buttons(260f, go)
            .onEscape(this::closeDialog));
    }

    /**
     * Tells the player once that their robot has lost its last life, if this client watched it happen and the game has not
     * ended in the same moment (the Game Over screen covers that instead).
     */
    private void maybeShowEliminatedDialog() {
        if (eliminatedDialogShown || !model.myEliminationJustSeen()) {
            return;
        }
        eliminatedDialogShown = true;
        showEliminatedDialog();
    }

    /**
     * Tells the player they are out of lives and can only keep watching.
     */
    private void showEliminatedDialog() {
        TextButton leave = ui.button("Leave game", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton stay = ui.button("Keep watching", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leaveGame();
            }
        });
        stay.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
            }
        });
        Image robot = new Image(ui.image(RobotLook.picture(model.mySeat())));
        robot.setScaling(Scaling.fit);
        robot.getColor().a = 0.55f;
        Table text = new Table();
        text.left();
        text.add(ui.label("You have no lives left.", Theme.TextStyle.BODY_LARGE, Theme.INK)).left().row();
        text.add(ui.label("You can keep watching the rest of the game. Your robot is off the board.", Theme.TextStyle.BODY,
            Theme.INK_MUTED)).left();
        Table top = new Table();
        top.add(robot).size(104f);
        top.add(text).left().padLeft(24f);

        Table facts = new Table();
        facts.left();
        facts.add(ui.pips(0, Robot.STARTING_LIVES, 22f, 5)).padRight(14f);
        facts.add(ui.chip("Out", UiKit.ChipKind.DANGER)).height(UiKit.CHIP_CELL_HEIGHT);

        open(new ModalDialog(ui, 780f, Theme.DANGER)
            .title("You're out")
            .row(top)
            .row(facts)
            .buttons(new float[] {240f, 300f}, leave, stay)
            .onEscape(this::closeDialog));
    }

    /**
     * Opens the menu: settings (not built yet), leaving the game, and closing the menu.
     */
    private void showMenu() {
        TextButton settings = ui.button("Settings", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        settings.setDisabled(true);
        TextButton leave = ui.button("Leave game", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton back = ui.button("Resume", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showLeaveQuestion();
            }
        });
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
            }
        });
        open(new ModalDialog(ui, 700f, null)
            .title("Menu")
            .text("The game keeps running while this menu is open.", Theme.TextStyle.BODY_LARGE, Theme.INK_MUTED)
            .buttons(190f, settings, leave, back)
            .onEscape(this::closeDialog));
    }

    /**
     * Asks whether to really leave the game.
     */
    private void showLeaveQuestion() {
        TextButton stay = ui.button("Stay", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        TextButton leave = ui.button("Leave", Theme.ButtonKind.DANGER, Theme.TextStyle.BUTTON);
        stay.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
            }
        });
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leaveGame();
            }
        });
        open(new ModalDialog(ui, 700f, null)
            .title("Leave the game?")
            .text("Your robot stays on the board and plays random programs while you are away.", Theme.TextStyle.BODY_LARGE,
                Theme.INK)
            .buttons(200f, leave, stay)
            .onEscape(this::closeDialog));
    }

    /**
     * Swaps to the Game Over layout: the results of the game, until the server takes everybody back to the lobby. It is built
     * from the state the game ended with, so it waits for the end of the replay of the last turn.
     */
    private void showGameOver() {
        closeDialog();
        gameOverView = new GameOverView(ui, model.standings(), this::leaveGame, model.lobbySecondsLeft());
        gameOverGroup.clearChildren();
        gameOverGroup.addActor(gameOverView);
        programmingGroup.setVisible(false);
        resolutionGroup.setVisible(false);
        gameOverGroup.setVisible(true);
    }

    /**
     * Shows a dialog in place of the one that is open.
     *
     * @param next the dialog
     */
    private void open(ModalDialog next) {
        closeDialog();
        dialog = next;
        dialog.show(stage);
    }

    /**
     * Closes the open dialog.
     */
    private void closeDialog() {
        if (dialog != null) {
            dialog.hide();
            dialog = null;
        }
    }

    /**
     * Leaves the game: closes the connection, which the server treats as leaving, and returns to the connect screen.
     */
    private void leaveGame() {
        closed = true;
        server.link().disconnect();
        game.setScreen(new ConnectScreen(game));
    }

    /**
     * Gives up a reconnect attempt that is still under way, then releases the stage, mirroring {@link ConnectScreen}'s
     * own {@code dispose()}. The worker thread it cancels is a daemon, so this is only tidiness, not a leak fix.
     */
    @Override
    public void dispose() {
        if (reconnector != null) {
            reconnector.cancel();
            reconnector = null;
        }
        super.dispose();
    }
}
