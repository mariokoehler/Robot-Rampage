package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LaserSource;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.SubPhase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the guarantees {@link MessageRegistry} relies on: registration order
 * is deterministic across independent {@link Kryo} instances (the client and
 * the server each own one), and every registered message class survives a
 * serialize/deserialize round trip.
 * <p>
 * When a new message class is added to {@link MessageRegistry}, add it to
 * {@link #registrationIdsAreIdenticalAcrossIndependentKryoInstances()} and
 * give it a round-trip test.
 *
 * @author Mario Koehler
 */
class MessageRegistryTest {

    /**
     * Two independent Kryo instances, registered by the same method, must hand
     * out identical ids for every message class &mdash; otherwise a client and
     * a server would misread each other's messages.
     */
    @Test
    void registrationIdsAreIdenticalAcrossIndependentKryoInstances() {
        Kryo first = new Kryo();
        Kryo second = new Kryo();

        MessageRegistry.register(first);
        MessageRegistry.register(second);

        Class<?>[] messageClasses = {
            HandshakeRequest.class, HandshakeResponse.class,
            Position.class, Direction.class, CardType.class, Card.class,
            MoveCause.class, RotationCause.class, DestructionCause.class, SubPhase.class,
            GameEvent.RobotMoved.class, GameEvent.RobotRotated.class, GameEvent.RobotDestroyed.class,
            LoggedEvent.class, LaserSource.class, GameEvent.LaserFired.class, GameEvent.RobotDamaged.class,
            GameEvent.RegisterRevealed.class, GameEvent.FlagTouched.class, GameEvent.ArchiveMarkerMoved.class,
            GameEvent.RobotRepaired.class, GameEvent.RobotPoweredDown.class, GameEvent.RobotPoweredUp.class,
            GameEvent.RobotRespawned.class, GameEvent.GameEnded.class};
        for (Class<?> messageClass : messageClasses) {
            assertEquals(first.getRegistration(messageClass).getId(), second.getRegistration(messageClass).getId(),
                "id mismatch for " + messageClass.getSimpleName());
        }
    }

    /**
     * A {@link HandshakeRequest} must keep both of its fields across the wire.
     */
    @Test
    void handshakeRequestSurvivesRoundTrip() {
        HandshakeRequest original = new HandshakeRequest("Mario", "0.1.0-SNAPSHOT");

        HandshakeRequest copy = roundTrip(original, HandshakeRequest.class);

        assertEquals("Mario", copy.getDisplayName());
        assertEquals("0.1.0-SNAPSHOT", copy.getVersion());
    }

    /**
     * A {@link HandshakeResponse} must keep both of its fields across the wire,
     * for an accepted as well as a rejected handshake.
     */
    @Test
    void handshakeResponseSurvivesRoundTrip() {
        HandshakeResponse accepted = roundTrip(new HandshakeResponse(true, "Welcome.", "1.2"), HandshakeResponse.class);
        assertTrue(accepted.isAccepted());
        assertEquals("Welcome.", accepted.getMessage());
        assertEquals("1.2", accepted.getServerVersion());

        HandshakeResponse rejected = roundTrip(new HandshakeResponse(false, "Version mismatch.", "2.0"),
            HandshakeResponse.class);
        assertFalse(rejected.isAccepted());
        assertEquals("Version mismatch.", rejected.getMessage());
        assertEquals("2.0", rejected.getServerVersion());
    }

    /**
     * Every kind of {@link GameEvent}, wrapped in a {@link LoggedEvent} the way it travels
     * in a turn's event log, must survive the wire. This is the proof that Kryo copes with
     * records and with a field typed as a sealed interface.
     */
    @Test
    void loggedEventsOfEveryKindSurviveRoundTrip() {
        GameEvent[] events = {
            new GameEvent.RobotMoved(3, new Position(1, 2), new Position(1, 3), MoveCause.PUSHED),
            new GameEvent.RobotRotated(0, Direction.NORTH, Direction.WEST, RotationCause.BELT),
            new GameEvent.RobotDestroyed(7, DestructionCause.PIT),
            new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 2), Direction.EAST,
                new Position(4, 2), 1, 2),
            new GameEvent.RobotDamaged(1, 2, 5, LaserSource.ROBOT),
            new GameEvent.RegisterRevealed(2, new Card(CardType.MOVE_3, 800)),
            new GameEvent.FlagTouched(0, 2, new Position(5, 5)),
            new GameEvent.ArchiveMarkerMoved(0, new Position(5, 5)),
            new GameEvent.RobotRepaired(4, 1, 3),
            new GameEvent.RobotPoweredDown(4),
            new GameEvent.RobotPoweredUp(4),
            new GameEvent.RobotRespawned(6, new Position(1, 1), Direction.SOUTH),
            new GameEvent.GameEnded(GameEvent.NO_ROBOT)};
        for (GameEvent event : events) {
            LoggedEvent original = new LoggedEvent(4, SubPhase.ALL_BELTS, event);

            LoggedEvent copy = roundTrip(original, LoggedEvent.class);

            assertEquals(original, copy);
        }
    }

    /**
     * Every kind of {@link GameEvent} the sealed interface permits must be registered, so
     * adding an event record without registering it fails here instead of at runtime on
     * the wire.
     */
    @Test
    void everyGameEventTypeIsRegistered() {
        Kryo kryo = new Kryo();
        MessageRegistry.register(kryo);

        Class<?>[] permitted = GameEvent.class.getPermittedSubclasses();
        assertTrue(permitted.length > 0);
        for (Class<?> eventType : permitted) {
            assertNotNull(kryo.getClassResolver().getRegistration(eventType),
                eventType.getSimpleName() + " is not registered");
        }
    }

    /**
     * A {@link Card} must keep its type and priority across the wire.
     */
    @Test
    void cardSurvivesRoundTrip() {
        Card original = new Card(CardType.U_TURN, 30);

        assertEquals(original, roundTrip(original, Card.class));
    }

    /**
     * Serializes {@code original} with a freshly registered {@link Kryo} and
     * reads it back with a second, independently registered one, mimicking one
     * message travelling from one endpoint to the other.
     *
     * @param original the message to send
     * @param type     the message's class
     * @param <T>      the message type
     * @return the deserialized copy
     */
    private static <T> T roundTrip(T original, Class<T> type) {
        Kryo sender = new Kryo();
        MessageRegistry.register(sender);
        Kryo receiver = new Kryo();
        MessageRegistry.register(receiver);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Output output = new Output(bytes)) {
            sender.writeObject(output, original);
        }
        try (Input input = new Input(bytes.toByteArray())) {
            return receiver.readObject(input, type);
        }
    }
}
