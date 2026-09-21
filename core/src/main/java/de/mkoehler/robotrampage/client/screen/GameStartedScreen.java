package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.render.BoardActor;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the game screen, which is built after the lobby. It shows that the game has started and who plays, keeps
 * the connection open, and lets the player leave. The messages that came with the start are kept for the game screen; what
 * the server sends afterwards is read and dropped.
 *
 * @author Mario Koehler
 */
public final class GameStartedScreen extends StageScreen implements NetworkClient.Handler {

    private static final float MAX_TILE = 50f;
    private static final float BOARD_AREA = 700f;
    private static final float BOARD_LEFT = 1000f;

    private final ConnectedServer server;
    private final List<Object> carried;
    private boolean closed;

    /**
     * Builds the screen.
     *
     * @param game    the game showing the screen
     * @param server  the connection, handed over by the lobby
     * @param started the message that started the game
     * @param carried the messages that arrived behind it, which the game screen will have to handle first
     */
    public GameStartedScreen(RobotRampageGame game, ConnectedServer server, GameStarted started, List<Object> carried) {
        super(game);
        this.server = server;
        this.carried = List.copyOf(carried);
        Label statusLabel = ui.label("You play " + RobotLook.name(started.yourRobotId()) + " against "
            + (started.players().size() - 1) + " others.", Theme.TextStyle.LEAD, Theme.INK_MUTED);
        TextButton leave = ui.button("Leave", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closed = true;
                server.link().disconnect();
                game.setScreen(new ConnectScreen(game));
            }
        });

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().padLeft(160f).padTop(200f).padRight(Theme.VIEW_WIDTH - BOARD_LEFT + 40f);
        root.add(ui.label("Game started", Theme.TextStyle.TITLE, Theme.INK)).left().row();
        root.add(statusLabel).left().padTop(Theme.SPACE_6).row();
        root.add(ui.label("The game screen is the next thing to be built.", Theme.TextStyle.BODY_LARGE,
            Theme.INK_MUTED)).left().padTop(Theme.SPACE_3).row();
        root.add(leave).size(180f, 52f).left().padTop(Theme.SPACE_8);
        stage.addActor(root);
        stage.addActor(boardOf(started));
    }

    /**
     * Builds the picture of the board with every robot on its start square.
     *
     * @param started the message that started the game
     * @return the actor, placed on the right of the screen
     */
    private BoardActor boardOf(GameStarted started) {
        Board board = BoardLoader.parse(started.boardJson()).board();
        float tile = Math.min(MAX_TILE, BOARD_AREA / Math.max(board.width(), board.height()));
        BoardActor actor = new BoardActor(ui, board, tile);
        List<RobotPose> robots = new ArrayList<>();
        for (PlayerInfo player : started.players()) {
            StartSquare start = board.startSquares().get(player.seat());
            robots.add(RobotPose.at(player.seat(), start.position(), start.facing()));
        }
        actor.setRobots(robots);
        actor.setPosition(BOARD_LEFT, (Theme.VIEW_HEIGHT - actor.getHeight()) / 2f);
        return actor;
    }

    /**
     * Returns the messages that arrived behind the start message and have not been handled by anyone yet, in order. The
     * game screen takes them over before it polls the connection.
     *
     * @return the messages
     */
    public List<Object> carriedMessages() {
        return carried;
    }

    /**
     * Reads and drops what the server sent since the last frame.
     *
     * @param delta seconds since the previous frame, unused
     */
    @Override
    protected void update(float delta) {
        if (!closed) {
            server.link().poll(this);
        }
    }

    /**
     * Drops a message; the game screen will handle them.
     *
     * @param message a message from the server
     */
    @Override
    public void onMessage(Object message) {
    }

    /**
     * Returns to the connect screen with a note when the server closes the connection.
     */
    @Override
    public void onDisconnect() {
        if (!closed) {
            closed = true;
            game.setScreen(new ConnectScreen(game, "The server closed the connection."));
        }
    }
}
