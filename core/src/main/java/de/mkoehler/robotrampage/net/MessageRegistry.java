package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;

/**
 * Registers every class sent over the wire with a {@link Kryo} instance, in a
 * fixed order.
 * <p>
 * Kryo assigns each registered class a numeric id based on registration order
 * and relies on that id, rather than the class name, to identify types on the
 * wire. Both ends of a connection must therefore register the exact same
 * classes in the exact same order, or messages will be misread on the
 * receiving end. This class is the single shared place that order is defined,
 * to be used by both the server and the client.
 * <p>
 * The order is append-only: new classes are added at the end of
 * {@link #register(Kryo)}, existing lines are never reordered or removed.
 *
 * @author Mario Koehler
 */
public final class MessageRegistry {

    /**
     * Not instantiable; this class only exposes a static registration method.
     */
    private MessageRegistry() {
    }

    /**
     * Registers all wire message classes with the given {@link Kryo} instance.
     *
     * @param kryo the Kryo instance to register classes with, typically obtained
     *             from an {@code EndPoint}'s {@code getKryo()} method
     */
    public static void register(Kryo kryo) {
        kryo.register(HandshakeRequest.class);
        kryo.register(HandshakeResponse.class);
    }
}
