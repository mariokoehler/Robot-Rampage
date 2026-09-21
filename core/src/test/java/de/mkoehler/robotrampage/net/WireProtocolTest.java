package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.SetReady;
import de.mkoehler.robotrampage.net.messages.StartGameRequest;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerUpdate;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.SubPhase;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the session protocol on the wire (design.md 3.5): every message survives serialisation with all its
 * data, every message class is registered, and the biggest messages a real game produces fit the network buffers.
 *
 * @author Mario Koehler
 */
class WireProtocolTest {

    /**
     * Serialises a message with one Kryo and reads it back with an independent one, like a message crossing
     * from one endpoint to the other.
     *
     * @param original the message
     * @param <T>      the message type
     * @return the received copy
     */
    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T original) {
        Kryo sender = new Kryo();
        MessageRegistry.register(sender);
        Kryo receiver = new Kryo();
        MessageRegistry.register(receiver);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Output output = new Output(bytes)) {
            sender.writeClassAndObject(output, original);
        }
        try (Input input = new Input(bytes.toByteArray())) {
            return (T) receiver.readClassAndObject(input);
        }
    }

    /**
     * The extended handshake keeps the session token and seat.
     */
    @Test
    void handshakeCarriesTheSessionToken() {
        HandshakeRequest resume = roundTrip(new HandshakeRequest("Mario", "1.0", "token-123"));
        assertEquals("token-123", resume.getSessionToken());
        assertNull(roundTrip(new HandshakeRequest("Mario", "1.0")).getSessionToken());

        HandshakeResponse welcome = roundTrip(new HandshakeResponse("Welcome.", 3, "abc", "1.0"));
        assertTrue(welcome.isAccepted());
        assertEquals(3, welcome.getSeat());
        assertEquals("abc", welcome.getSessionToken());
        assertEquals("1.0", welcome.getServerVersion());
        assertEquals(-1, roundTrip(new HandshakeResponse(false, "no", "1.0")).getSeat());
    }

    /**
     * Every lobby and turn message survives the wire with all of its data, including lists, nulls and a record
     * without any fields.
     */
    @Test
    void everyProtocolMessageSurvivesTheWire() {
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", true, true, true), new PlayerInfo(3, "Bo", false, false, false));
        RobotState robot = new RobotState(3, new Position(4, 5), Direction.WEST, 2, 3, 1, new Position(1, 1),
            de.mkoehler.robotrampage.rules.RobotStatus.ACTIVE, false, true);
        RobotState gone = new RobotState(0, null, Direction.NORTH, 9, 0, 0, new Position(0, 0),
            de.mkoehler.robotrampage.rules.RobotStatus.ELIMINATED, false, false);
        LoggedEvent event = new LoggedEvent(2, SubPhase.LASERS, new GameEvent.RobotDestroyed(3, DestructionCause.DAMAGE));
        List<Object> messages = List.of(
            new LobbyState(players, "Proving Grounds", 8),
            new SetReady(true),
            new StartGameRequest(),
            new GameStarted("{\"json\": true}", players, 3),
            new TurnStarted(4, List.of(event), List.of(0, 3), 90),
            new HandDealt(4, List.of(new Card(CardType.MOVE_1, 500), new Card(CardType.U_TURN, 20)),
                List.of(new Card(CardType.BACK_UP, 440)), true, false),
            new SubmitProgram(4, List.of(500, 20, 660, 70, 90), true, Direction.EAST),
            new SubmitProgram(4, List.of(), false, null),
            new RequestRejected("Too few cards"),
            new PlayerConfirmed(3),
            new TimerUpdate(30),
            new TurnResolved(4, List.of(event)),
            new StateSnapshot(4, List.of(gone, robot), true, 3),
            new PlayerConnection(3, false),
            new PlayerLeft(3, List.of(event)),
            new GameOver(-1, List.of(gone, robot)));

        for (Object message : messages) {
            assertEquals(message, roundTrip(message), message.getClass().getSimpleName());
        }
    }

    /**
     * Only this game's own classes count as protocol messages; KryoNet's housekeeping objects and nulls do not.
     */
    @Test
    void onlyOwnClassesAreProtocolMessages() {
        assertTrue(MessageRegistry.isProtocolMessage(new SetReady(true)));
        assertTrue(MessageRegistry.isProtocolMessage(new HandshakeRequest("a", "b")));
        assertFalse(MessageRegistry.isProtocolMessage("a string"));
        assertFalse(MessageRegistry.isProtocolMessage(new com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive()));
        assertFalse(MessageRegistry.isProtocolMessage(null));
    }

    /**
     * Every class in the messages package is registered, so a new message that is forgotten in
     * {@link MessageRegistry} fails here and not on the wire.
     */
    @Test
    void everyMessageClassIsRegistered() {
        Kryo kryo = new Kryo();
        MessageRegistry.register(kryo);

        int checked = 0;
        for (JavaClass messageClass : new ClassFileImporter().importPackages("de.mkoehler.robotrampage.net.messages")) {
            assertNotNull(kryo.getClassResolver().getRegistration(messageClass.reflect()),
                messageClass.getSimpleName() + " is not registered");
            checked++;
        }
        assertTrue(checked >= 19, "expected to find all message classes, found " + checked);
    }

    /**
     * Real turns of randomised games, wrapped in a {@code TurnResolved} and a {@code StateSnapshot}, survive
     * the wire unchanged — every event type that actually occurs, with its real values — and even the biggest of
     * them is far smaller than the object buffer.
     */
    @Test
    void realTurnsSurviveTheWireAndFitTheBuffers() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        int largestTurn = 0;
        int turnsChecked = 0;

        for (long seed = 1; seed <= 12; seed++) {
            List<Robot> robots = new ArrayList<>();
            for (int id = 0; id < board.startSquares().size(); id++) {
                StartSquare start = board.startSquares().get(id);
                robots.add(new Robot(id, start.position(), start.facing()));
            }
            GameState state = new GameState(board, robots, Deck.standard(seed));
            Random random = new Random(seed);
            for (int turn = 1; turn <= 40 && !state.isOver(); turn++) {
                Respawner.respawn(state, Map.of(), new EventLog());
                for (Map.Entry<Integer, List<Card>> entry : Programming.deal(state).entrySet()) {
                    Robot robot = state.robot(entry.getKey());
                    List<Card> shuffled = new ArrayList<>(entry.getValue());
                    Collections.shuffle(shuffled, random);
                    int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
                    Programming.submit(state, robot.id(), entry.getValue(), shuffled.subList(0, unlocked), random.nextInt(10) == 0);
                }
                TurnResult result = TurnResolver.resolve(state);
                state = result.state();

                TurnResolved resolved = new TurnResolved(turn, result.events());
                assertEquals(resolved, roundTrip(resolved), "seed " + seed + " turn " + turn);
                List<RobotState> snapshot = new ArrayList<>();
                state.robots().forEach(robot -> snapshot.add(RobotState.of(robot)));
                StateSnapshot snapshotMessage = new StateSnapshot(turn, snapshot, state.isOver(), state.winnerId());
                assertEquals(snapshotMessage, roundTrip(snapshotMessage));

                largestTurn = Math.max(largestTurn, serializedSize(resolved));
                turnsChecked++;
            }
        }

        assertTrue(turnsChecked > 100, "checked only " + turnsChecked + " turns");
        assertTrue(largestTurn * 4 < NetworkConstants.OBJECT_BUFFER_SIZE,
            "the biggest turn (" + largestTurn + " bytes) leaves less than 4x headroom in the "
                + NetworkConstants.OBJECT_BUFFER_SIZE + " byte object buffer");
    }

    /**
     * Measures how many bytes a message takes on the wire, failing if it does not fit the object buffer at all.
     *
     * @param message the message
     * @return its serialised size in bytes
     */
    private static int serializedSize(Object message) {
        Kryo kryo = new Kryo();
        MessageRegistry.register(kryo);
        Output output = new Output(NetworkConstants.OBJECT_BUFFER_SIZE);
        kryo.writeClassAndObject(output, message);
        return output.position();
    }
}
