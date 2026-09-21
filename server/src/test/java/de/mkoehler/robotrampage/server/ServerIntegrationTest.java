package de.mkoehler.robotrampage.server;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.client.connect.ConnectFlow;
import de.mkoehler.robotrampage.client.connect.ConnectedServer;
import de.mkoehler.robotrampage.client.connect.ConnectionAttempt;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.game.GameModel;
import de.mkoehler.robotrampage.client.lobby.LobbyView;
import de.mkoehler.robotrampage.client.replay.TurnReplay;
import de.mkoehler.robotrampage.net.AppVersion;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.NetworkServer;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.SetReady;
import de.mkoehler.robotrampage.net.messages.StartGameRequest;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.session.SessionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays the game end to end over real loopback sockets, and in doing so exercises the threading contract for real
 * (design.md 3.5): KryoNet's network threads, a dedicated loop thread that alone owns the session, and one test thread
 * per client role, all at the same time.
 *
 * @author Mario Koehler
 */
class ServerIntegrationTest {

    private static final long PAUSE_BETWEEN_TURNS = 200;

    private NetworkServer network;
    private ScheduledExecutorService loop;
    private final AtomicReference<Throwable> loopFailure = new AtomicReference<>();
    private final List<TestClient> clients = new ArrayList<>();
    private int port;

    /**
     * Starts a server on a free port with its own loop thread, ticking every few milliseconds like the headless render loop.
     *
     * @throws IOException if the server cannot start
     */
    @BeforeEach
    void startServer() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        LoadedBoard board = BoardLoader.loadResource("boards/proving-grounds.json");
        SessionConfig config = new SessionConfig(60_000, 30_000, 60_000, PAUSE_BETWEEN_TURNS, 0, PAUSE_BETWEEN_TURNS, 60_000, 2);
        network = new NetworkServer();
        network.start(port);
        ServerController controller = new ServerController(network, board, config, 7L, System::currentTimeMillis);
        loop = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "server-loop"));
        loop.scheduleWithFixedDelay(() -> {
            try {
                controller.update();
            } catch (Throwable t) {
                loopFailure.compareAndSet(null, t);
            }
        }, 0, 5, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops everything and fails if the loop thread ever threw.
     *
     * @throws InterruptedException if interrupted while shutting down
     */
    @AfterEach
    void stopServer() throws InterruptedException {
        clients.forEach(TestClient::close);
        loop.shutdownNow();
        loop.awaitTermination(2, TimeUnit.SECONDS);
        network.stop();
        assertNull(loopFailure.get(), "the server loop threw: " + loopFailure.get());
    }

    /**
     * Connects a new client and completes the handshake.
     *
     * @param name  the display name
     * @param token a session token to resume, or {@code null}
     * @return the connected client, with the {@link HandshakeResponse} still to be taken
     * @throws IOException if the connection fails
     */
    private TestClient connect(String name, String token) throws IOException {
        TestClient client = new TestClient();
        client.connect(port);
        clients.add(client);
        client.send(new HandshakeRequest(name, AppVersion.getVersion(), token));
        return client;
    }

    /**
     * Builds a program from a dealt hand.
     *
     * @param hand the hand
     * @return a valid submission using its first cards
     */
    private static SubmitProgram programFrom(HandDealt hand) {
        int unlocked = 5 - hand.lockedCards().size();
        return new SubmitProgram(hand.turn(), hand.hand().subList(0, unlocked).stream().map(Card::priority).toList(), false, null);
    }

    /**
     * Two players go through the whole cycle over the network: handshake, lobby, ready, start, hands, programs, resolution and a
     * second turn. Along the way each player only ever sees their own cards.
     *
     * @throws Exception on any failure
     */
    @Test
    void twoPlayersPlayTurnsOverRealSockets() throws Exception {
        TestClient ann = connect("Ann", null);
        HandshakeResponse annWelcome = ann.take(HandshakeResponse.class);
        assertTrue(annWelcome.isAccepted());
        assertEquals(0, annWelcome.getSeat());
        assertNotNull(annWelcome.getSessionToken());
        TestClient bo = connect("Bo", null);
        assertEquals(1, bo.take(HandshakeResponse.class).getSeat());

        LobbyState lobby = ann.take(LobbyState.class);
        assertFalse(lobby.players().isEmpty());
        bo.send(new SetReady(true));
        ann.send(new StartGameRequest());

        GameStarted annStart = ann.take(GameStarted.class);
        GameStarted boStart = bo.take(GameStarted.class);
        assertEquals(0, annStart.yourRobotId());
        assertEquals(1, boStart.yourRobotId());
        LoadedBoard sentBoard = BoardLoader.parse(annStart.boardJson());
        assertEquals("proving-grounds", sentBoard.definition().id());

        for (int turn = 1; turn <= 2; turn++) {
            assertEquals(turn, ann.take(TurnStarted.class).turn());
            HandDealt annHand = ann.take(HandDealt.class);
            HandDealt boHand = bo.take(HandDealt.class);
            assertEquals(turn, annHand.turn());
            Set<Integer> annCards = new HashSet<>();
            annHand.hand().forEach(card -> annCards.add(card.priority()));
            assertTrue(boHand.hand().stream().noneMatch(card -> annCards.contains(card.priority())),
                "the two players were dealt overlapping cards");

            ann.send(programFrom(annHand));
            bo.send(programFrom(boHand));

            assertEquals(turn, ann.take(TurnResolved.class).turn());
            assertEquals(turn, bo.take(TurnResolved.class).turn());
            assertEquals(2, ann.take(StateSnapshot.class).robots().size());
        }
        // Two turns, two robots: four confirmations were broadcast, naming both robots twice and never carrying any cards.
        Set<Integer> confirmedRobots = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            confirmedRobots.add(ann.take(PlayerConfirmed.class).robotId());
        }
        assertEquals(Set.of(0, 1), confirmedRobots);
    }

    /**
     * A client of a different version is turned away and disconnected before it can do anything.
     *
     * @throws Exception on any failure
     */
    @Test
    void aClientOfAnotherVersionIsRefused() throws Exception {
        TestClient stale = new TestClient();
        stale.connect(port);
        clients.add(stale);

        stale.send(new HandshakeRequest("Old", "0.0.0-ancient"));

        HandshakeResponse response = stale.take(HandshakeResponse.class);
        assertFalse(response.isAccepted());
        assertTrue(response.getMessage().contains("update"));
        assertEquals(AppVersion.getVersion(), response.getServerVersion());
        assertTrue(stale.awaitDisconnect());
    }

    /**
     * Messages sent before the handshake are ignored, so a client cannot act without a seat.
     *
     * @throws Exception on any failure
     */
    @Test
    void messagesBeforeTheHandshakeAreIgnored() throws Exception {
        TestClient sneaky = new TestClient();
        sneaky.connect(port);
        clients.add(sneaky);

        sneaky.send(new StartGameRequest());
        sneaky.send(new SetReady(true));

        assertFalse(sneaky.receivesWithin(RequestRejected.class, 200));
        TestClient honest = connect("Honest", null);
        assertTrue(honest.take(HandshakeResponse.class).isAccepted());
        assertFalse(honest.take(LobbyState.class).players().get(0).ready());
    }

    /**
     * A player whose connection drops is announced to the others and gets their seat back with the session token: the
     * server resyncs them with the game setup, the state and the current turn.
     *
     * @throws Exception on any failure
     */
    @Test
    void aDroppedPlayerCanComeBackWithTheToken() throws Exception {
        TestClient ann = connect("Ann", null);
        ann.take(HandshakeResponse.class);
        TestClient bo = connect("Bo", null);
        String boToken = bo.take(HandshakeResponse.class).getSessionToken();
        bo.send(new SetReady(true));
        ann.take(LobbyState.class);
        ann.send(new StartGameRequest());
        bo.take(GameStarted.class);
        bo.take(TurnStarted.class);
        bo.take(HandDealt.class);

        bo.close();

        PlayerConnection dropped = ann.take(PlayerConnection.class);
        assertEquals(1, dropped.robotId());
        assertFalse(dropped.connected());

        TestClient boAgain = connect("ignored", boToken);
        HandshakeResponse welcome = boAgain.take(HandshakeResponse.class);
        assertTrue(welcome.isAccepted());
        assertEquals(1, welcome.getSeat());
        assertEquals(boToken, welcome.getSessionToken());
        assertEquals(1, boAgain.take(GameStarted.class).yourRobotId());
        assertEquals(2, boAgain.take(StateSnapshot.class).robots().size());
        assertTrue(boAgain.take(TurnStarted.class).turn() >= 1);
        assertTrue(ann.take(PlayerConnection.class).connected());
    }

    /**
     * Presenting a valid token while the old connection is still open takes the seat over: the old connection is closed by the
     * server and the player is not reported as having left.
     *
     * @throws Exception on any failure
     */
    @Test
    void aSecondConnectionWithTheTokenReplacesTheFirst() throws Exception {
        TestClient first = connect("Ann", null);
        String token = first.take(HandshakeResponse.class).getSessionToken();
        TestClient other = connect("Bo", null);
        other.take(HandshakeResponse.class);
        other.send(new SetReady(true));
        first.take(LobbyState.class);
        first.send(new StartGameRequest());
        other.take(GameStarted.class);

        TestClient replacement = connect("Ann", token);

        assertTrue(replacement.take(HandshakeResponse.class).isAccepted());
        assertTrue(first.awaitDisconnect(), "the old connection should have been closed");
        assertFalse(other.receivesWithin(PlayerConnection.class, 200), "the takeover must not look like a disconnect");
    }

    /**
     * A stranger cannot use the token machinery to get into a running game: a made-up token is treated like no token.
     *
     * @throws Exception on any failure
     */
    @Test
    void aMadeUpTokenDoesNotOpenARunningGame() throws Exception {
        TestClient ann = connect("Ann", null);
        ann.take(HandshakeResponse.class);
        TestClient bo = connect("Bo", null);
        bo.take(HandshakeResponse.class);
        bo.send(new SetReady(true));
        ann.take(LobbyState.class);
        ann.send(new StartGameRequest());
        bo.take(GameStarted.class);

        TestClient mallory = connect("Mallory", "made-up-token");

        HandshakeResponse response = mallory.take(HandshakeResponse.class);
        assertFalse(response.isAccepted());
        assertTrue(mallory.awaitDisconnect());
    }

    /**
     * Runs a connection attempt, the way the client's connect screen does, until it has finished.
     *
     * @param link    the link the attempt uses
     * @param version the version the client claims to run
     * @return the finished attempt
     */
    private ConnectionAttempt joinLikeTheClient(NetworkClient link, String version) {
        ConnectionAttempt attempt = new ConnectionAttempt(link, new ServerAddress("localhost", port), "Ann", null, version);
        attempt.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (!attempt.flow().isFinished() && System.currentTimeMillis() < deadline) {
            attempt.update();
        }
        return attempt;
    }

    /**
     * The client's connect flow joins a real server: it is accepted, gets a seat and a token, and the lobby state that follows
     * the acceptance reaches the next screen, either handed over with the connection or still waiting on the link.
     */
    @Test
    void theClientsConnectFlowJoinsARealServer() {
        NetworkClient link = new NetworkClient();

        ConnectionAttempt attempt = joinLikeTheClient(link, AppVersion.getVersion());

        assertEquals(ConnectFlow.Phase.ACCEPTED, attempt.flow().phase());
        ConnectedServer connected = attempt.connected();
        assertEquals(0, connected.welcome().getSeat());
        assertNotNull(connected.welcome().getSessionToken());
        assertEquals(AppVersion.getVersion(), connected.welcome().getServerVersion());
        List<Object> seen = new ArrayList<>(connected.earlyMessages());
        long deadline = System.currentTimeMillis() + 5_000;
        while (seen.stream().noneMatch(LobbyState.class::isInstance) && System.currentTimeMillis() < deadline) {
            link.poll(new NetworkClient.Handler() {
                @Override
                public void onMessage(Object message) {
                    seen.add(message);
                }

                @Override
                public void onDisconnect() {
                }
            });
        }
        assertTrue(seen.stream().anyMatch(LobbyState.class::isInstance));
        link.disconnect();
    }

    /**
     * The client's connect flow tells a version refusal from any other: the server's version is shown, and the connection ends.
     */
    @Test
    void theClientsConnectFlowReportsAVersionMismatch() {
        NetworkClient link = new NetworkClient();

        ConnectionAttempt attempt = joinLikeTheClient(link, "0.0.0-ancient");

        assertEquals(ConnectFlow.Phase.VERSION_MISMATCH, attempt.flow().phase());
        assertEquals(AppVersion.getVersion(), attempt.flow().serverVersion());
        assertNull(attempt.connected());
    }

    /**
     * Nothing listens on the port: the flow reports the server as unreachable instead of hanging.
     *
     * @throws IOException if no free port could be found
     */
    @Test
    void theClientsConnectFlowReportsAnUnreachableServer() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        NetworkClient link = new NetworkClient();
        ConnectionAttempt attempt = new ConnectionAttempt(link, new ServerAddress("localhost", unusedPort), "Ann", null,
            AppVersion.getVersion());
        attempt.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (!attempt.flow().isFinished() && System.currentTimeMillis() < deadline) {
            attempt.update();
        }

        assertEquals(ConnectFlow.Phase.UNREACHABLE, attempt.flow().phase());
        assertFalse(attempt.flow().detail().isEmpty());
    }

    /**
     * The lobby screen enables its start button by the same rule the server applies: with two players it stays off until the
     * other player is ready, and when it turns on the server really starts the game.
     *
     * @throws Exception on any failure
     */
    @Test
    void theStartButtonRuleAgreesWithTheServer() throws Exception {
        TestClient ann = connect("Ann", null);
        int annSeat = ann.take(HandshakeResponse.class).getSeat();
        TestClient bo = connect("Bo", null);
        bo.take(HandshakeResponse.class);

        LobbyState twoPlayers = ann.take(LobbyState.class);
        while (twoPlayers.players().size() < 2) {
            twoPlayers = ann.take(LobbyState.class);
        }
        LobbyView notReady = new LobbyView(twoPlayers, annSeat);
        assertTrue(notReady.iAmHost());
        assertFalse(notReady.canStart(), "the other player is not ready");
        assertEquals(2, twoPlayers.minPlayers());
        assertEquals(12, twoPlayers.boardWidth());
        assertEquals(3, twoPlayers.flagCount());

        bo.send(new SetReady(true));
        LobbyState ready = ann.take(LobbyState.class);
        while (!ready.players().stream().allMatch(player -> player.host() || player.ready())) {
            ready = ann.take(LobbyState.class);
        }
        assertTrue(new LobbyView(ready, annSeat).canStart());

        ann.send(new StartGameRequest());
        assertNotNull(ann.take(GameStarted.class));
    }

    /**
     * Two clients follow a whole turn with the game model of the client: they get their cards, place five each, lock them
     * in, see the turn resolved and are dealt the cards of the next turn. This checks the model against the real server, in
     * particular that a program built by the model is accepted and that the stages come in the order the screen expects.
     *
     * @throws Exception on any failure
     */
    @Test
    void theGameModelFollowsAWholeTurnOnTheRealServer() throws Exception {
        TestClient ann = connect("Ann", null);
        int annSeat = ann.take(HandshakeResponse.class).getSeat();
        TestClient bo = connect("Bo", null);
        int boSeat = bo.take(HandshakeResponse.class).getSeat();
        bo.send(new SetReady(true));
        LobbyState lobby = ann.take(LobbyState.class);
        while (!new LobbyView(lobby, annSeat).canStart()) {
            lobby = ann.take(LobbyState.class);
        }
        ann.send(new StartGameRequest());
        GameModel annModel = new GameModel(ann.take(GameStarted.class));
        GameModel boModel = new GameModel(bo.take(GameStarted.class));
        assertEquals(annSeat, annModel.mySeat());
        assertEquals(boSeat, boModel.mySeat());

        programTurn(ann, annModel, 1);
        programTurn(bo, boModel, 1);

        for (TestClient client : List.of(ann, bo)) {
            GameModel model = client == ann ? annModel : boModel;
            model.apply(client.take(PlayerConfirmed.class));
            model.apply(client.take(PlayerConfirmed.class));
            TurnResolved resolved = client.take(TurnResolved.class);
            model.apply(resolved);
            assertEquals(GameModel.Stage.RESOLVING, model.stage());
            model.apply(client.take(StateSnapshot.class));
            assertTrue(model.isResolutionOpen(), "the end state is held until the replay is over");
            TurnReplay replay = new TurnReplay(model.robotsBeforeResolution(), resolved.events(), model::nameOf);
            replay.skipToEnd();
            model.completeResolution();
            for (var pose : replay.frame().poses()) {
                var robot = model.robots().get(pose.seat());
                assertEquals(robot.position().x(), pose.x(), 0.001f);
                assertEquals(robot.position().y(), pose.y(), 0.001f);
                assertEquals(robot.facing(), TurnReplay.facingOf(pose.rotation()));
            }
            model.apply(client.take(TurnStarted.class));
            model.apply(client.take(HandDealt.class));
            assertEquals(GameModel.Stage.PROGRAMMING, model.stage());
            assertEquals(2, model.turn());
            assertEquals(5, model.draft().freeRegisterCount());
        }
        assertEquals(2, annModel.robots().size());
    }

    /**
     * Plays the programming part of a turn for one client through its model: takes the turn start and the cards, places
     * five cards and sends the program.
     *
     * @param client the client
     * @param model  its model
     * @param turn   the turn number
     */
    private static void programTurn(TestClient client, GameModel model, int turn) {
        model.apply(client.take(TurnStarted.class));
        model.apply(client.take(HandDealt.class));
        assertEquals(GameModel.Stage.PROGRAMMING, model.stage());
        assertEquals(turn, model.turn());
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        assertTrue(model.canConfirm());
        client.send(model.submit());
    }
}
