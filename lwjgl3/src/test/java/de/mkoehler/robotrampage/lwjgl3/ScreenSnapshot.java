package de.mkoehler.robotrampage.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.screen.GameScreen;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.RobotStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A development tool, not part of the game: builds the game screen in each of its states from canned messages, without a
 * server, and writes a PNG of each through a hidden window. It is how the states that are hard to reach by playing (damaged
 * robots with locked registers, a powered-down robot, a program that is locked in) are looked at. Run it with the
 * {@code assets} folder as the working directory:
 * <pre>java -cp ... de.mkoehler.robotrampage.lwjgl3.ScreenSnapshot [output folder]</pre>
 * It writes {@code game-programming.png}, {@code game-ready.png}, {@code game-locked.png}, {@code game-confirmed.png},
 * {@code game-powered-down.png} and {@code game-time-up.png}.
 *
 * @author Mario Koehler
 */
public final class ScreenSnapshot {

    private static final int WIDTH = Theme.VIEW_WIDTH;
    private static final int HEIGHT = Theme.VIEW_HEIGHT;
    private static final int ME = 1;
    private static final List<String> NAMES = List.of("Sophie", "Mario", "Kenji", "Łukasz", "Amira", "Diego");

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private ScreenSnapshot() {
    }

    /**
     * A connection that goes nowhere: what the screen sends is thrown away and nothing ever arrives.
     */
    private static final class DeadLink implements ServerLink {
        @Override
        public void connect(String host, int tcpPort) {
        }

        @Override
        public void poll(NetworkClient.Handler handler) {
        }

        @Override
        public void send(Object message) {
        }

        @Override
        public void disconnect() {
        }
    }

    /**
     * Renders the states and exits.
     *
     * @param args the output folder, default the current folder
     */
    public static void main(String[] args) {
        File folder = new File(args.length > 0 ? args[0] : ".");
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setInitialVisible(false);
        configuration.setWindowedMode(WIDTH, HEIGHT);
        new Lwjgl3Application(new RobotRampageGame() {
            @Override
            public void create() {
                super.create();
                try {
                    renderAll(this, folder);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                Gdx.app.exit();
            }
        }, configuration);
    }

    /**
     * Builds and writes every state.
     *
     * @param game   the game that owns the widget kit
     * @param folder where to write
     * @throws IOException if writing fails
     */
    private static void renderAll(RobotRampageGame game, File folder) throws IOException {
        write(game, folder, "game-programming.png", state(game, 0, 2, false, false, false));
        write(game, folder, "game-ready.png", state(game, 0, 5, false, false, false));
        write(game, folder, "game-locked.png", state(game, 2, 3, false, false, false));
        write(game, folder, "game-confirmed.png", state(game, 0, 5, true, false, false));
        write(game, folder, "game-powered-down.png", state(game, 0, 0, false, true, false));
        write(game, folder, "game-time-up.png", state(game, 0, 2, false, false, true));
    }

    /**
     * Builds the game screen for a turn in one of its states.
     *
     * @param game        the game
     * @param locked      how many registers are locked by damage
     * @param place       how many cards to place, from the start of the hand
     * @param confirmed   whether the player has locked in the program
     * @param poweredDown whether the player's robot is powered down
     * @param timeUp      whether the server filled in the registers because time ran out
     * @return the screen
     */
    private static GameScreen state(RobotRampageGame game, int locked, int place, boolean confirmed, boolean poweredDown,
                                    boolean timeUp) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = new ArrayList<>();
        for (int seat = 0; seat < NAMES.size(); seat++) {
            players.add(new PlayerInfo(seat, NAMES.get(seat), true, true, seat == 0));
        }
        List<Object> messages = new ArrayList<>();
        int damage = locked == 0 ? 2 : locked + 4;
        List<RobotState> robots = new ArrayList<>();
        int[] damages = {1, damage, 3, 0, 2, 4};
        int[] lives = {3, 3, 3, 3, 3, 2};
        for (int seat = 0; seat < NAMES.size(); seat++) {
            Position start = new Position(2 + seat, 0);
            robots.add(new RobotState(seat, new Position(2 + seat * 2 % 8, 1 + seat), Direction.NORTH, damages[seat],
                lives[seat], seat == ME ? 1 : 0, start, RobotStatus.ACTIVE, false, false));
        }
        messages.add(new StateSnapshot(3, robots, false, -1));
        List<Integer> awaited = poweredDown ? List.of(0, 2, 3, 4, 5) : List.of(0, 1, 2, 3, 4, 5);
        messages.add(new TurnStarted(4, List.of(), awaited, 90));
        List<Card> hand = new ArrayList<>(List.of(new Card(CardType.MOVE_1, 520), new Card(CardType.MOVE_2, 700),
            new Card(CardType.ROTATE_RIGHT, 220), new Card(CardType.U_TURN, 30), new Card(CardType.MOVE_3, 810),
            new Card(CardType.ROTATE_LEFT, 150), new Card(CardType.BACK_UP, 450), new Card(CardType.MOVE_1, 530),
            new Card(CardType.ROTATE_LEFT, 160)));
        List<Card> lockedCards = new ArrayList<>();
        for (int i = 0; i < locked; i++) {
            lockedCards.add(new Card(CardType.MOVE_2, 300 + i));
        }
        messages.add(new HandDealt(4, poweredDown ? List.of() : hand.subList(0, 9 - damage), lockedCards, false,
            poweredDown));
        messages.add(new PlayerConfirmed(0));
        messages.add(new PlayerConfirmed(3));
        messages.add(new PlayerConnection(5, false));
        HandshakeResponse welcome = new HandshakeResponse("Welcome", ME, "token", "test");
        GameScreen screen = new GameScreen(game, new ConnectedServer(new DeadLink(), welcome, List.of(), false),
            new ServerAddress("localhost", 45725), new GameStarted(board, players, ME), messages);
        if (screen.model().draft() != null) {
            for (int i = 0; i < place && i < screen.model().draft().hand().size(); i++) {
                screen.model().draft().place(screen.model().draft().hand().get(i));
            }
            if (confirmed) {
                screen.model().submit();
                screen.model().apply(new PlayerConfirmed(ME));
            }
            if (timeUp) {
                screen.model().apply(new PlayerConfirmed(ME));
            }
            screen.refresh();
        }
        return screen;
    }

    /**
     * Draws a screen into an off-screen buffer and writes it out.
     *
     * @param game   the game
     * @param folder where to write
     * @param name   the file name
     * @param screen the screen
     * @throws IOException if writing fails
     */
    private static void write(RobotRampageGame game, File folder, String name, GameScreen screen) throws IOException {
        screen.resize(WIDTH, HEIGHT);
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        buffer.begin();
        screen.render(23f);
        screen.render(0f);
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        buffer.end();
        Pixmap upright = BoardSnapshot.flipped(pixmap);
        PixmapIO.writePNG(Gdx.files.absolute(new File(folder, name).getAbsolutePath()), upright);
        upright.dispose();
        pixmap.dispose();
        buffer.dispose();
        screen.dispose();
    }
}
