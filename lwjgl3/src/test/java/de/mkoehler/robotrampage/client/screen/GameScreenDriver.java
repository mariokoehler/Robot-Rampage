package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.game.GameModel;
import de.mkoehler.robotrampage.client.ui.FacingPicker;
import de.mkoehler.robotrampage.client.ui.PillToggle;
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
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.lwjgl3.BoardSnapshot;
import de.mkoehler.robotrampage.lwjgl3.SampleTurn;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.RobotStatus;

import java.io.File;
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
 * the working directory. With an output folder as the argument, it also saves a PNG of the states that can only be reached
 * by clicking, such as the power-down confirmation, which {@code ScreenSnapshot} cannot open on its own.
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
     * @param args the output folder for the power-down dialog's PNG, or none to skip saving it
     */
    public static void main(String[] args) {
        File folder = args.length > 0 ? new File(args[0]) : null;
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setInitialVisible(false);
        configuration.setWindowedMode(WIDTH, HEIGHT);
        new Lwjgl3Application(new RobotRampageGame() {
            @Override
            public void create() {
                super.create();
                drive(this, folder);
                driveResolution(this);
                driveRespawn(this);
                driveEliminated(this);
                driveStayPoweredDown(this);
                driveReconnect(this);
                System.out.println("GameScreenDriver: all checks passed");
                Gdx.app.exit();
            }
        }, configuration);
    }

    /**
     * Plays through a turn and the end of the game on the screen.
     *
     * @param game   the game
     * @param folder where to save the power-down dialog's PNG, or {@code null} to skip it
     */
    private static void drive(RobotRampageGame game, File folder) {
        ScriptedLink link = new ScriptedLink();
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            hand.add(new Card(CardType.MOVE_1, 100 + i));
        }
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        ConnectedServer connected = new ConnectedServer(link, new HandshakeResponse("Welcome", ME, "token", "test", 600),
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

        Actor toggle = findActor(screen.stage.getRoot(), PillToggle.class);
        check(toggle != null, "the power-down switch should be on screen");
        float[] toggleAt = centerOf(toggle);
        int actorsBeforeDialog = screen.stage.getActors().size;
        click(screen, toggleAt[0], toggleAt[1]);
        check(screen.stage.getActors().size == actorsBeforeDialog + 1, "turning the switch on should open the power-down dialog");
        check(!((PillToggle) toggle).isChecked(), "the switch should revert until the dialog is confirmed");
        check(!model.powerDownNext(), "nothing should be announced until confirmed");
        if (folder != null) {
            snapshot(screen, folder, "dialog-powerdown.png");
        }

        TextButton notNow = findButton(screen.stage.getRoot(), "NOT NOW");
        check(notNow != null, "the dialog should have a Not now button");
        click(screen, centerOf(notNow)[0], centerOf(notNow)[1]);
        check(screen.stage.getActors().size == actorsBeforeDialog, "Not now should close the dialog");
        check(!model.powerDownNext(), "Not now should not announce a power-down");

        click(screen, toggleAt[0], toggleAt[1]);
        TextButton powerDown = findButton(screen.stage.getRoot(), "POWER DOWN");
        check(powerDown != null, "the dialog should have a Power down button");
        click(screen, centerOf(powerDown)[0], centerOf(powerDown)[1]);
        check(screen.stage.getActors().size == actorsBeforeDialog, "Power down should close the dialog");
        check(model.powerDownNext(), "Power down should announce it");
        check(((PillToggle) toggle).isChecked(), "the switch should reflect the announcement");

        click(screen, toggleAt[0], toggleAt[1]);
        check(screen.stage.getActors().size == actorsBeforeDialog, "turning the switch back off needs no dialog");
        check(!model.powerDownNext(), "turning it off should cancel the announcement");

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

        link.incoming.add(new GameOver(0, model.robots(), 15));
        frame(screen);
        check(screen.stage.getActors().size == actors, "the end of the game should not open a dialog");
        check(!screen.stage.getActors().get(0).isVisible() && screen.stage.getActors().get(2).isVisible(),
            "the end of the game should show the game over layout in place of the game");
        click(screen, 1696f, 1006f);
        check(game.getScreen() == screen, "the lobby button of the game over screen is only a countdown");
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
        ConnectedServer connected = new ConnectedServer(new ScriptedLink(), new HandshakeResponse("Welcome", ME, "token", "test", 600),
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
     * Checks the respawn-facing dialog: it opens by itself once the hand arrives, pre-selects the robot's own facing,
     * follows a click on one of the direction buttons, and applies the picked facing only once "Go" is pressed.
     *
     * @param game the game
     */
    private static void driveRespawn(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            hand.add(new Card(CardType.MOVE_1, 200 + i));
        }
        List<RobotState> before = List.of(new RobotState(ME, new Position(4, 4), Direction.NORTH, 0, 2, 0,
            new Position(3, 0), RobotStatus.ACTIVE, false, false));
        List<Object> messages = List.of(new StateSnapshot(2, before, false, -1),
            new TurnStarted(3, List.of(), List.of(0, 1, 2), 90), new HandDealt(3, hand, List.of(), true, false));
        ConnectedServer connected = new ConnectedServer(new ScriptedLink(),
            new HandshakeResponse("Welcome", ME, "token", "test", 600), List.of(), false);
        GameScreen screen = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(board, players, ME), messages);
        game.setScreen(screen);
        screen.resize(WIDTH, HEIGHT);
        GameModel model = screen.model();
        frame(screen);

        check(findLabel(screen.stage.getRoot(), "BACK IN THE GAME") != null,
            "the respawn dialog should open by itself once the hand arrives");
        int actorsWithDialog = screen.stage.getActors().size;
        FacingPicker picker = (FacingPicker) findActor(screen.stage.getRoot(), FacingPicker.class);
        check(picker != null, "the dialog should show the facing picker");
        check(picker.facing() == Direction.NORTH, "the picker should start on the robot's own facing");
        check(model.respawnFacing() == null, "no facing is chosen before Go is pressed");

        Vector2 westButton = picker.localToStageCoordinates(new Vector2(30f, 220f));
        click(screen, westButton.x, HEIGHT - westButton.y);
        check(picker.facing() == Direction.WEST, "a click on the west button should pick west");
        check(model.respawnFacing() == null, "picking in the widget alone must not change the model yet");

        TextButton go = findButton(screen.stage.getRoot(), "GO");
        check(go != null, "the dialog should have a Go button");
        click(screen, centerOf(go)[0], centerOf(go)[1]);
        check(model.respawnFacing() == Direction.WEST, "Go should apply the picked facing");
        check(screen.stage.getActors().size == actorsWithDialog - 1, "Go should close the dialog");

        screen.onMessage(new PlayerConfirmed(0));
        frame(screen);
        check(findLabel(screen.stage.getRoot(), "BACK IN THE GAME") == null,
            "the dialog must not reopen for a turn it was already offered and closed for");
    }

    /**
     * Checks that a player is told once their own robot has lost its last life, and that "Keep watching" dismisses it.
     *
     * @param game the game
     */
    private static void driveEliminated(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        RobotState eliminated = new RobotState(ME, null, Direction.NORTH, 9, 0, 0, new Position(3, 0),
            RobotStatus.ELIMINATED, false, false);
        List<Object> messages = List.of(new TurnResolved(6, List.of()), new StateSnapshot(6, List.of(eliminated), false, -1));
        ConnectedServer connected = new ConnectedServer(new ScriptedLink(),
            new HandshakeResponse("Welcome", ME, "token", "test", 600), List.of(), false);
        GameScreen screen = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(board, players, ME), messages);
        game.setScreen(screen);
        screen.resize(WIDTH, HEIGHT);
        int actorsBefore = screen.stage.getActors().size;
        frame(screen);
        check(screen.model().myEliminationJustSeen(), "the model should have seen the elimination");
        check(findLabel(screen.stage.getRoot(), "YOU'RE OUT") != null, "the eliminated dialog should open by itself");
        check(screen.stage.getActors().size == actorsBefore + 1, "the dialog should be the only new actor");

        TextButton keepWatching = findButton(screen.stage.getRoot(), "KEEP WATCHING");
        check(keepWatching != null, "the dialog should have a Keep watching button");
        click(screen, centerOf(keepWatching)[0], centerOf(keepWatching)[1]);
        check(screen.stage.getActors().size == actorsBefore, "Keep watching should close the dialog");
    }

    /**
     * Checks the power-down switch of a robot that is already powered down: announcing another power-down, and changing
     * one's mind about it, are each sent to the server at once, since there is no Confirm button left to send them with.
     *
     * @param game the game
     */
    private static void driveStayPoweredDown(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        List<Object> messages = List.of(new TurnStarted(2, List.of(), List.of(0, 2), 90),
            new HandDealt(2, List.of(), List.of(), false, true));
        ScriptedLink link = new ScriptedLink();
        ConnectedServer connected = new ConnectedServer(link, new HandshakeResponse("Welcome", ME, "token", "test", 600),
            List.of(), false);
        GameScreen screen = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(board, players, ME), messages);
        game.setScreen(screen);
        screen.resize(WIDTH, HEIGHT);
        GameModel model = screen.model();
        frame(screen);
        check(model.stage() == GameModel.Stage.SITTING_OUT && model.isPoweredDownThisTurn(),
            "the robot should already be powered down this turn");

        Actor toggle = findActor(screen.stage.getRoot(), PillToggle.class);
        check(toggle != null, "the power-down switch should be on screen while sitting out");
        float[] toggleAt = centerOf(toggle);
        click(screen, toggleAt[0], toggleAt[1]);
        TextButton stayDown = findButton(screen.stage.getRoot(), "POWER DOWN");
        check(stayDown != null, "the dialog should have a Power down button");
        click(screen, centerOf(stayDown)[0], centerOf(stayDown)[1]);
        check(!link.sent.isEmpty() && link.sent.get(link.sent.size() - 1) instanceof SubmitProgram sent && sent.powerDown(),
            "announcing another power-down while already down should be sent at once");

        click(screen, toggleAt[0], toggleAt[1]);
        check(link.sent.get(link.sent.size() - 1) instanceof SubmitProgram sent && !sent.powerDown(),
            "changing one's mind should also be sent at once, with no dialog needed to turn it off");
    }

    /**
     * Checks the reconnect flow, the one part of it that needs a real widget: a dropped connection opens the "Connection
     * lost" dialog by itself, and "Leave game" gives up the attempt and returns to the connect screen. The retry state
     * machine itself (retries, the grace countdown, a successful or refused retry) has no window to click and is covered by
     * {@code ReconnectorTest} instead.
     *
     * @param game the game
     */
    private static void driveReconnect(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        List<Object> messages = List.of(new TurnStarted(2, List.of(), List.of(0, 1, 2), 90));
        ConnectedServer connected = new ConnectedServer(new ScriptedLink(),
            new HandshakeResponse("Welcome", ME, "token-x", "test", 5), List.of(), false);
        GameScreen screen = new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(board, players, ME), messages);
        game.setScreen(screen);
        screen.resize(WIDTH, HEIGHT);
        frame(screen);

        int actorsBefore = screen.stage.getActors().size;
        screen.onDisconnect();
        frame(screen);
        check(findLabel(screen.stage.getRoot(), "CONNECTION LOST") != null,
            "a dropped connection should open the connection-lost dialog by itself");
        check(screen.stage.getActors().size == actorsBefore + 1, "the dialog should be the only new actor");

        TextButton leave = findButton(screen.stage.getRoot(), "LEAVE GAME");
        check(leave != null, "the dialog should have a Leave game button");
        click(screen, centerOf(leave)[0], centerOf(leave)[1]);
        check(game.getScreen() instanceof ConnectScreen, "Leave game should give up the attempt and go to the connect screen");
    }

    /**
     * Finds the first descendant of a group that is an instance of a type.
     *
     * @param group the group to search
     * @param type  the type
     * @return the actor, or {@code null} if none is found
     */
    private static Actor findActor(Group group, Class<?> type) {
        for (Actor child : group.getChildren()) {
            if (type.isInstance(child)) {
                return child;
            }
            if (child instanceof Group nested) {
                Actor found = findActor(nested, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds the first button with an exact label, such as the design's capitalised button text.
     *
     * @param group the group to search
     * @param label the label
     * @return the button, or {@code null} if none is found
     */
    private static TextButton findButton(Group group, String label) {
        for (Actor child : group.getChildren()) {
            if (child instanceof TextButton button && label.contentEquals(button.getText())) {
                return button;
            }
            if (child instanceof Group nested) {
                TextButton found = findButton(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds the first label with an exact text, such as a dialog's title.
     *
     * @param group the group to search
     * @param text  the text
     * @return the label, or {@code null} if none is found
     */
    private static Label findLabel(Group group, String text) {
        for (Actor child : group.getChildren()) {
            if (child instanceof Label label && text.contentEquals(label.getText())) {
                return label;
            }
            if (child instanceof Group nested) {
                Label found = findLabel(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Returns the screen position of the centre of an actor, counted from the top left as {@link #click} expects.
     *
     * @param actor the actor
     * @return the position, as x and y
     */
    private static float[] centerOf(Actor actor) {
        Vector2 stage = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        return new float[] {stage.x, HEIGHT - stage.y};
    }

    /**
     * Saves a PNG of the screen as it stands, without disposing it: the driver keeps clicking on the same screen afterward.
     *
     * @param screen the screen
     * @param folder where to write
     * @param name   the file name
     */
    private static void snapshot(GameScreen screen, File folder, String name) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        buffer.begin();
        frame(screen);
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        buffer.end();
        Pixmap upright = BoardSnapshot.flipped(pixmap);
        PixmapIO.writePNG(Gdx.files.absolute(new File(folder, name).getAbsolutePath()), upright);
        upright.dispose();
        pixmap.dispose();
        buffer.dispose();
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
