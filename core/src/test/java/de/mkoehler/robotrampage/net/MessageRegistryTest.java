package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        Class<?>[] messageClasses = {HandshakeRequest.class, HandshakeResponse.class};
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
        HandshakeResponse accepted = roundTrip(new HandshakeResponse(true, "Welcome."), HandshakeResponse.class);
        assertTrue(accepted.isAccepted());
        assertEquals("Welcome.", accepted.getMessage());

        HandshakeResponse rejected = roundTrip(new HandshakeResponse(false, "Version mismatch."),
            HandshakeResponse.class);
        assertFalse(rejected.isAccepted());
        assertEquals("Version mismatch.", rejected.getMessage());
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
