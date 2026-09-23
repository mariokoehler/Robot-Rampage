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
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.ProgramRevealed;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.TimerPaused;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.rules.SubPhase;

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
 * {@code game-powered-down.png}, {@code game-time-up.png}, {@code game-time-up-locked.png} (a random fill together with a
 * damage-locked tail, to check the two register looks next to each other), {@code game-host*.png}/{@code game-guest-paused.png} (the host's
 * timer pause), {@code gameover-*.png}, {@code resolution-*.png} for a real turn at several moments of its replay, and
 * {@code dialog-respawn.png}/{@code dialog-eliminated.png} for the two dialogs that open by themselves from canned
 * messages, with no click needed. The power-down confirmation only opens from a click on the switch, so it is rendered by
 * {@code GameScreenDriver} instead.
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
        write(game, folder, "game-programming.png", state(game, 0, 2, false, false, false), 23f);
        write(game, folder, "game-ready.png", state(game, 0, 5, false, false, false), 23f);
        write(game, folder, "game-locked.png", state(game, 2, 3, false, false, false), 23f);
        write(game, folder, "game-confirmed.png", state(game, 0, 5, true, false, false), 23f);
        write(game, folder, "game-powered-down.png", state(game, 0, 0, false, true, false), 23f);
        write(game, folder, "game-time-up.png", state(game, 0, 2, false, false, true), 23f);
        write(game, folder, "game-time-up-locked.png", state(game, 2, 0, false, false, true), 23f);
        write(game, folder, "game-host.png", state(game, 0, 0, 2, false, false, false), 23f);
        write(game, folder, "gameover-winner.png", gameOver(game, 0, 3, true, false), 0f);
        write(game, folder, "gameover-two.png", gameOver(game, 0, 2, true, false), 0f);
        write(game, folder, "gameover-none.png", gameOver(game, -1, 6, false, false), 0f);
        write(game, folder, "gameover-long-name.png", gameOver(game, 0, 8, true, true), 0f);
        write(game, folder, "gameover-unseen.png", gameOver(game, 0, 3, true, false, false), 0f);
        GameScreen paused = state(game, 0, 0, 2, false, false, false);
        paused.model().apply(new TimerPaused(true, 67));
        paused.refresh();
        write(game, folder, "game-host-paused.png", paused, 23f);
        GameScreen guestPaused = state(game, 0, 2, false, false, false);
        guestPaused.model().apply(new TimerPaused(true, 67));
        guestPaused.refresh();
        write(game, folder, "game-guest-paused.png", guestPaused, 23f);
        float laser = SampleTurn.first(7L, NAMES.size(), NAMES).laserSecond();
        write(game, folder, "resolution-laser.png", resolution(game), laser);
        for (float seconds : new float[] {1.2f, 5f, 12f, 200f}) {
            write(game, folder, "resolution-" + (int) seconds + "s.png", resolution(game), seconds);
        }
        write(game, folder, "dialog-respawn.png", respawnDialogState(game), 0f);
        write(game, folder, "dialog-eliminated.png", eliminatedDialogState(game), 0f);
    }

    /**
     * Builds the game screen with the respawn dialog open: a turn in which this player's robot must pick its facing.
     *
     * @param game the game
     * @return the screen; the dialog opens by itself once the hand is applied, before the first frame is drawn
     */
    private static GameScreen respawnDialogState(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = new ArrayList<>();
        for (int seat = 0; seat < NAMES.size(); seat++) {
            players.add(new PlayerInfo(seat, NAMES.get(seat), true, true, seat == 0));
        }
        List<RobotState> robots = new ArrayList<>();
        for (int seat = 0; seat < NAMES.size(); seat++) {
            Position start = new Position(2 + seat, 0);
            boolean respawned = seat == ME;
            robots.add(new RobotState(seat, respawned ? new Position(4, 4) : new Position(2 + seat, 1),
                respawned ? Direction.WEST : Direction.NORTH, 0, respawned ? 2 : 3, 0, start, RobotStatus.ACTIVE, false,
                false));
        }
        List<Card> hand = List.of(new Card(CardType.MOVE_1, 520), new Card(CardType.MOVE_2, 700),
            new Card(CardType.ROTATE_RIGHT, 220), new Card(CardType.U_TURN, 30), new Card(CardType.MOVE_3, 810),
            new Card(CardType.ROTATE_LEFT, 150), new Card(CardType.BACK_UP, 450), new Card(CardType.MOVE_1, 530),
            new Card(CardType.ROTATE_LEFT, 160));
        List<Object> messages = List.of(new StateSnapshot(3, robots, false, -1),
            new TurnStarted(4, List.of(), List.of(0, 1, 2, 3, 4, 5), 90), new HandDealt(4, hand, List.of(), true, false));
        HandshakeResponse welcome = new HandshakeResponse("Welcome", ME, "token", "test", 600);
        return new GameScreen(game, new ConnectedServer(new DeadLink(), welcome, List.of(), false),
            new ServerAddress("localhost", 45725), new GameStarted(board, players, ME), messages);
    }

    /**
     * Builds the game screen right after this player's robot has been seen to lose its last life, with the "You're out"
     * dialog open.
     *
     * @param game the game
     * @return the screen; the dialog opens on the first frame, once the resolution of the eliminating turn completes
     */
    private static GameScreen eliminatedDialogState(RobotRampageGame game) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<PlayerInfo> players = new ArrayList<>();
        for (int seat = 0; seat < NAMES.size(); seat++) {
            players.add(new PlayerInfo(seat, NAMES.get(seat), true, true, seat == 0));
        }
        List<RobotState> after = new ArrayList<>();
        for (int seat = 0; seat < NAMES.size(); seat++) {
            boolean me = seat == ME;
            after.add(new RobotState(seat, me ? null : new Position(2 + seat, 1), Direction.NORTH, me ? 9 : 2, me ? 0 : 3,
                1, new Position(2 + seat, 0), me ? RobotStatus.ELIMINATED : RobotStatus.ACTIVE, false, false));
        }
        List<Object> messages = List.of(new TurnResolved(6, List.of()), new StateSnapshot(6, after, false, -1));
        HandshakeResponse welcome = new HandshakeResponse("Welcome", ME, "token", "test", 600);
        return new GameScreen(game, new ConnectedServer(new DeadLink(), welcome, List.of(), false),
            new ServerAddress("localhost", 45725), new GameStarted(board, players, ME), messages);
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
        return state(game, ME, locked, place, confirmed, poweredDown, timeUp);
    }

    /**
     * Builds the game screen for a turn in one of its states, as seen by the given player.
     *
     * @param game        the game
     * @param me          the seat of the player looking at the screen; seat 0 is the host
     * @param locked      how many registers are locked by damage
     * @param place       how many cards to place, from the start of the hand
     * @param confirmed   whether the player has locked in the program
     * @param poweredDown whether the player's robot is powered down
     * @param timeUp      whether the server filled in the registers because time ran out
     * @return the screen
     */
    private static GameScreen state(RobotRampageGame game, int me, int locked, int place, boolean confirmed,
                                    boolean poweredDown, boolean timeUp) {
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
                lives[seat], seat == me ? 1 : 0, start, RobotStatus.ACTIVE, false, false));
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
        HandshakeResponse welcome = new HandshakeResponse("Welcome", me, "token", "test", 600);
        GameScreen screen = new GameScreen(game, new ConnectedServer(new DeadLink(), welcome, List.of(), false),
            new ServerAddress("localhost", 45725), new GameStarted(board, players, me), messages);
        if (screen.model().draft() != null) {
            for (int i = 0; i < place && i < screen.model().draft().hand().size(); i++) {
                screen.model().draft().place(screen.model().draft().hand().get(i));
            }
            if (confirmed) {
                screen.model().submit();
                screen.model().apply(new PlayerConfirmed(me));
            }
            if (timeUp) {
                screen.model().apply(new PlayerConfirmed(me));
                List<Card> free = new ArrayList<>();
                for (int i = 0; i < 5 - locked; i++) {
                    free.add(new Card(CardType.MOVE_1, 900 + i));
                }
                List<Card> revealed = new ArrayList<>(free);
                revealed.addAll(lockedCards);
                screen.model().apply(new ProgramRevealed(4, revealed));
            }
            screen.refresh();
        }
        return screen;
    }

    /**
     * Builds the game screen at the end of a game, as a player who watched the whole game sees it.
     *
     * @param game     the game
     * @param winner   the winning seat, or -1 for no winner
     * @param players  how many players took part, at most eight
     * @param finished whether the winner touched every flag
     * @param longName whether the winner has the longest name a player can have
     * @return the screen, showing the results
     */
    private static GameScreen gameOver(RobotRampageGame game, int winner, int players, boolean finished, boolean longName) {
        return gameOver(game, winner, players, finished, longName, true);
    }

    /**
     * Builds the game screen at the end of a game, either as a player who watched the whole game sees it or as one who
     * missed all of its turns (a game that ends because a player dropped, or a player who came back after the last turn).
     *
     * @param game     the game
     * @param winner   the winning seat, or -1 for no winner
     * @param players  how many players took part, at most eight
     * @param finished whether the winner touched every flag
     * @param longName whether the winner has the longest name a player can have
     * @param seen     whether the turns of the game were played back on this screen
     * @return the screen, showing the results
     */
    private static GameScreen gameOver(RobotRampageGame game, int winner, int players, boolean finished, boolean longName,
                                       boolean seen) {
        String board = BoardLoader.toJson(BoardLoader.loadResource("boards/proving-grounds.json").definition());
        List<String> names = new ArrayList<>(List.of("Sophie", "Mario", "Kenji", "Łukasz", "Amira", "Diego", "Noor", "Tim"));
        if (longName) {
            names.set(0, "Maximilian-Alexander Q.");
        }
        List<PlayerInfo> infos = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            infos.add(new PlayerInfo(seat, names.get(seat), true, true, seat == 0));
        }
        int[] flags = {3, 2, 2, 1, 1, 0, 1, 0};
        int[] lives = {3, 3, 3, 2, 0, 0, 1, 0};
        List<RobotState> robots = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            boolean out = winner < 0 ? true : lives[seat] == 0;
            robots.add(new RobotState(seat, out ? null : new Position(2 + seat, 1), Direction.NORTH, 0,
                winner < 0 ? 0 : lives[seat], winner < 0 ? seat % 3 : Math.min(flags[seat], finished ? 3 : 1),
                new Position(2 + seat, 0), out ? RobotStatus.ELIMINATED : RobotStatus.ACTIVE, false, false));
        }
        List<Object> messages = new ArrayList<>();
        if (seen) {
            messages.add(new TurnStarted(11, List.of(), List.of(), 90));
            messages.add(new TurnResolved(11, winner < 0 || !finished ? List.of() : List.of(new LoggedEvent(4,
                SubPhase.CHECKPOINTS, new GameEvent.FlagTouched(winner, 3, new Position(9, 9))))));
            messages.add(new StateSnapshot(11, robots, true, winner));
        }
        messages.add(new GameOver(winner, robots));
        HandshakeResponse welcome = new HandshakeResponse("Welcome", ME, "token", "test", 600);
        GameScreen screen = new GameScreen(game, new ConnectedServer(new DeadLink(), welcome, List.of(), false),
            new ServerAddress("localhost", 45725), new GameStarted(board, infos, ME), messages);
        screen.model().completeResolution();
        return screen;
    }

    /**
     * Builds the game screen for a turn that has just been resolved, from a real turn of the rules engine.
     *
     * @param game the game
     * @return the screen, showing the replay from its start
     */
    private static GameScreen resolution(RobotRampageGame game) {
        SampleTurn.Sample sample = SampleTurn.first(7L, NAMES.size(), NAMES);
        ConnectedServer connected = new ConnectedServer(new DeadLink(), new HandshakeResponse("Welcome", ME, "token", "test", 600),
            List.of(), false);
        return new GameScreen(game, connected, new ServerAddress("localhost", 45725),
            new GameStarted(sample.boardJson(), sample.players(), ME), sample.messages());
    }

    /**
     * Draws a screen into an off-screen buffer and writes it out.
     *
     * @param game   the game
     * @param folder where to write
     * @param name   the file name
     * @param screen the screen
     * @param seconds how much time passes before the picture is taken
     * @throws IOException if writing fails
     */
    private static void write(RobotRampageGame game, File folder, String name, GameScreen screen, float seconds)
        throws IOException {
        screen.resize(WIDTH, HEIGHT);
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        buffer.begin();
        screen.render(seconds);
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
