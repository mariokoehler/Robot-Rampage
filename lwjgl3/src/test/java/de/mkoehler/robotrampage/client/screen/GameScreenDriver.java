package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.game.GameModel;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.lwjgl3.SampleTurn;
import de.mkoehler.robotrampage.rules.CardType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A development tool, not part of the game: puts the real {@link GameScreen} on a hidden window and drives it the way a
 * player would, with simulated mouse clicks and canned messages from the server. It checks that placing a card, taking it
 * back, confirming, a refused request, the menu, and the way back to the lobby after the game all work on the real widgets,
 * which the unit tests cannot reach because they have no window. It stops with an error at the first thing that is wrong.
 * <p>
 * It lives in the package of the screens to reach what they keep to themselves. Run it with the {@code assets} folder as
 * the working directory.
 *
 * @author Mario Koehler
 */
public final class GameScreenDriver {

    private static final int WIDTH = Theme.VIEW_WIDTH;
    private static final int HEIGHT = Theme.VIEW_HEIGHT;
    private static final int ME = 1;
    private static final float HAND_LEFT = 246f + 52f;
    private static final float HAND_Y = 955f;
    private static final float REGISTER_Y = 798f;
    private static final float CARD_STEP = 116f;

    /**
     * A connection whose incoming messages the driver queues and whose outgoing messages it records.
     */
    private static final class ScriptedLink implements ServerLink {
        private final ConcurrentLinkedQueue<Object> incoming = new ConcurrentLinkedQueue<>();
        private final List<Object> sent = new ArrayList<>();

        @Override
        public void connect(String host, int tcpPort) {
        }

        @Override
        public void poll(NetworkClient.Handler handler) {
            Object message;
            while ((message = incoming.poll()) != null) {
                handler.onMessage(message);
            }
        }

        @Override
        public void send(Object message) {
            sent.add(message);
        }

        @Override
        public void disconnect() {
        }
    }

    /**
     * Not instantiable; this class only holds the entry point.
     */
    private GameScreenDriver() {
    }

    /**
     * Runs the checks and exits.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setInitialVisible(false);
        configuration.setWindowedMode(WIDTH, HEIGHT);
        new Lwjgl3Application(new RobotRampageGame() {
            @Override
            public void create() {
                super.create();
                drive(this);
                driveResolution(this);
                System.out.println("GameScreenDriver: all checks passed");
                Gdx.app.exit();
            }
        }, configuration);
    }

    /**
     * Plays through a turn and the end of the game on the screen.
     *
     * @param game the game
     */
    private static void drive(RobotRampageGame game) {
        ScriptedLink link = new ScriptedLink();
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            hand.add(new Card(CardType.MOVE_1, 100 + i));
        }
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        ConnectedServer connected = new ConnectedServer(link, new HandshakeResponse("Welcome", ME, "token", "test"),
            List.of(), false);
        GameScreen screen = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(board, players, ME),
            List.of(new TurnStarted(1, List.of(), List.of(0, 1, 2), 90), new HandDealt(1, hand, List.of(), false, false)));
        game.setScreen(screen);
        screen.resize(WIDTH, HEIGHT);
        GameModel model = screen.model();
        frame(screen);
        check(model.stage() == GameModel.Stage.PROGRAMMING, "the turn should be in the programming stage");

        click(screen, HAND_LEFT + 2 * CARD_STEP, HAND_Y);
        check(model.draft().registers().get(0).card().equals(hand.get(2)), "a click on the third card should place it in register 1");
        check(!model.canConfirm(), "one card is not a program");

        click(screen, HAND_LEFT, REGISTER_Y);
        check(model.draft().registers().get(0).card() == null, "a click on register 1 should take its card back");

        for (int i = 0; i < 5; i++) {
            click(screen, HAND_LEFT + i * CARD_STEP, HAND_Y);
        }
        check(model.canConfirm(), "five placed cards should be a program");

        click(screen, 1044f, 735f);
        check(link.sent.size() == 1 && link.sent.get(0) instanceof SubmitProgram, "confirming should send the program");
        SubmitProgram sent = (SubmitProgram) link.sent.get(0);
        check(sent.cardPriorities().equals(List.of(100, 101, 102, 103, 104)), "the program should list the five cards in order");
        click(screen, 1044f, 735f);
        check(link.sent.size() == 1, "a second click on confirm must not send again");

        link.incoming.add(new RequestRejected("Your program is already locked in."));
        frame(screen);
        check(screen.stage.getRoot().findActor("toast") != null, "a refused request should show a toast");
        check(model.canConfirm(), "a refused program can be sent again");
        click(screen, 1044f, 735f);
        check(link.sent.size() == 2, "a refused program should be sent again by another click on confirm");
        link.incoming.add(new PlayerConfirmed(ME));
        frame(screen);
        check(model.stage() == GameModel.Stage.SUBMITTED && model.programVisible(), "the confirmation should lock the program in");

        int actors = screen.stage.getActors().size;
        click(screen, 1864f, 38f);
        check(screen.stage.getActors().size == actors + 1, "the menu button should open the menu");
        screen.stage.keyDown(Input.Keys.ESCAPE);
        frame(screen);
        check(screen.stage.getActors().size == actors, "Escape should close the menu");

        link.incoming.add(new GameOver(0, model.robots()));
        frame(screen);
        check(screen.stage.getActors().size == actors + 1, "the end of the game should open the game over dialog");
        link.incoming.add(new LobbyState(List.of(new PlayerInfo(ME, "Bo", false, true, true)), "Proving Grounds", 8, 2, 12, 12,
            3, 3, 90));
        frame(screen);
        check(game.getScreen() instanceof LobbyScreen, "the lobby state after the game should hand the connection to the lobby");
    }

    /**
     * Plays back a real resolved turn and checks the replay: the board does not jump to the end state before the replay has
     * played, skipping takes the end state over, and a new turn that arrives in the middle of a replay cuts it short.
     *
     * @param game the game
     */
    private static void driveResolution(RobotRampageGame game) {
        List<String> names = List.of("Ann", "Bo", "Cy", "Di");
        SampleTurn.Sample sample = SampleTurn.first(7L, names.size(), names);
        List<RobotState> after = sample.after();
        ConnectedServer connected = new ConnectedServer(new ScriptedLink(), new HandshakeResponse("Welcome", ME, "token", "test"),
            List.of(), false);

        GameScreen skipping = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(sample.boardJson(), sample.players(), ME), sample.messages());
        game.setScreen(skipping);
        skipping.resize(WIDTH, HEIGHT);
        GameModel model = skipping.model();
        frame(skipping);
        check(model.stage() == GameModel.Stage.RESOLVING, "a resolved turn should be in the resolving stage");
        check(model.isResolutionOpen(), "the end state should be held while the replay plays");
        check(!model.robots().equals(after), "the robots must not have jumped to the end state");
        click(skipping, 1718f, 38f);
        check(!model.isResolutionOpen(), "skipping should complete the resolution");
        check(model.robots().equals(after), "skipping should show the state the server reached");

        GameScreen cut = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(sample.boardJson(), sample.players(), ME), sample.messages());
        game.setScreen(cut);
        cut.resize(WIDTH, HEIGHT);
        frame(cut);
        check(cut.model().isResolutionOpen(), "the second replay should be running");
        cut.onMessage(new TurnStarted(2, List.of(), List.of(0, 1, 2, 3), 90));
        frame(cut);
        check(!cut.model().isResolutionOpen() && cut.model().robots().equals(after),
            "a new turn should cut the replay short and show the end state");
        check(cut.model().stage() == GameModel.Stage.PROGRAMMING, "the new turn should be in the programming stage");
    }

    /**
     * Runs one frame of the screen that is showing.
     *
     * @param screen the screen
     */
    private static void frame(GameScreen screen) {
        screen.render(0f);
    }

    /**
     * Clicks at a place on the 1920 by 1080 layout, counted from the top left, and runs a frame.
     *
     * @param screen the screen
     * @param x      the horizontal position
     * @param y      the vertical position
     */
    private static void click(GameScreen screen, float x, float y) {
        frame(screen);
        screen.stage.mouseMoved((int) x, (int) y);
        screen.stage.touchDown((int) x, (int) y, 0, Input.Buttons.LEFT);
        screen.stage.touchUp((int) x, (int) y, 0, Input.Buttons.LEFT);
        frame(screen);
    }

    /**
     * Stops with an error when something is not as it should be.
     *
     * @param condition what should hold
     * @param message   what is wrong if it does not
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("GameScreenDriver: " + message);
        }
    }
}
