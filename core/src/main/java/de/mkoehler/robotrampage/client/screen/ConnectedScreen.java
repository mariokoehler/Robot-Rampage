package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;

/**
 * The screen after a server accepted the player. It stands in for the lobby, which is built next: it shows who the server
 * says is seated, so a connection can be checked end to end, and lets the player leave again.
 *
 * @author Mario Koehler
 */
public final class ConnectedScreen extends StageScreen implements NetworkClient.Handler {

    private final ConnectedServer server;
    private final Label seatedLabel;
    private final Label statusLabel;

    /**
     * Builds the screen and takes over the messages that arrived with the acceptance.
     *
     * @param game    the game showing the screen
     * @param server  the accepted connection
     * @param address the address that was joined
     */
    public ConnectedScreen(RobotRampageGame game, ConnectedServer server, ServerAddress address) {
        super(game);
        this.server = server;
        statusLabel = ui.label("Connected to " + address + " as seat " + (server.welcome().getSeat() + 1) + ".",
            Theme.TextStyle.LEAD, Theme.INK_MUTED);
        seatedLabel = ui.label("", Theme.TextStyle.BODY_LARGE, Theme.INK);
        TextButton leave = ui.button("Leave", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        leave.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leave();
            }
        });

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().padLeft(160f).padTop(200f);
        root.add(ui.label("Connected", Theme.TextStyle.TITLE, Theme.INK)).left().row();
        root.add(statusLabel).left().padTop(Theme.SPACE_6).row();
        root.add(ui.label("The waiting room is the next screen to be built.", Theme.TextStyle.BODY_LARGE,
            Theme.INK_MUTED)).left().padTop(Theme.SPACE_3).row();
        root.add(seatedLabel).left().padTop(Theme.SPACE_8).row();
        root.add(leave).size(180f, 52f).left().padTop(Theme.SPACE_8);
        stage.addActor(root);

        server.earlyMessages().forEach(this::onMessage);
        if (server.closedAlready()) {
            onDisconnect();
        }
    }

    /**
     * Reads what the server sent since the last frame.
     *
     * @param delta seconds since the previous frame, unused
     */
    @Override
    protected void update(float delta) {
        server.link().poll(this);
    }

    /**
     * Lists the players when the server sends the state of its lobby.
     *
     * @param message a message from the server
     */
    @Override
    public void onMessage(Object message) {
        if (message instanceof LobbyState lobby) {
            StringBuilder text = new StringBuilder();
            for (PlayerInfo player : lobby.players()) {
                text.append("Seat ").append(player.seat() + 1).append(": ").append(player.name())
                    .append(player.host() ? "  (host)" : "").append(player.ready() ? "  (ready)" : "").append('\n');
            }
            seatedLabel.setText(text.toString());
        }
    }

    /**
     * Says so when the server closes the connection.
     */
    @Override
    public void onDisconnect() {
        statusLabel.setText("The connection to the server was closed.");
    }

    /**
     * Closes the connection and returns to the connect screen.
     */
    private void leave() {
        server.link().disconnect();
        game.setScreen(new ConnectScreen(game));
    }
}
