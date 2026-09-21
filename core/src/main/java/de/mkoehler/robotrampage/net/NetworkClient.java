package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The client end of the connection: a thin wrapper around a KryoNet {@link Client} (TCP only, design.md 3.5).
 * <p>
 * <b>Threading contract.</b> KryoNet calls its listener on its own network thread. This class only <em>enqueues</em>
 * what happens there; the render loop calls {@link #poll(Handler)} from its own thread to receive the events, so the
 * rest of the client never sees a second thread. {@link #connect(String, int)} blocks until connected or failed, and
 * must therefore never be called from the render thread; {@link #disconnect()} may block briefly for the same reason.
 *
 * @author Mario Koehler
 */
public final class NetworkClient implements ServerLink {

    /**
     * Receives the events {@link NetworkClient#poll(Handler)} drains from the network thread.
     */
    public interface Handler {

        /**
         * A message arrived from the server.
         *
         * @param message the message
         */
        void onMessage(Object message);

        /**
         * The connection ended, whether closed by this side, closed by the server, or lost. Called once.
         */
        void onDisconnect();
    }

    /**
     * Something that happened on the network thread and is waiting to be handled on the loop thread.
     *
     * @param message the received message, or {@code null} for a disconnect
     */
    private record Event(Object message) {
    }

    private final Client client;
    private final ConcurrentLinkedQueue<Event> inbox = new ConcurrentLinkedQueue<>();

    /**
     * Creates a client that is not connected yet; call {@link #connect(String, int)}.
     */
    public NetworkClient() {
        this.client = new Client(NetworkConstants.WRITE_BUFFER_SIZE, NetworkConstants.OBJECT_BUFFER_SIZE);
        MessageRegistry.register(client.getKryo());
        client.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (MessageRegistry.isProtocolMessage(object)) {
                    inbox.add(new Event(object));
                }
            }

            @Override
            public void disconnected(Connection connection) {
                inbox.add(new Event(null));
            }
        });
    }

    /**
     * Connects to a server. Blocks until the connection is established or fails, so call it from a background thread,
     * never from the render thread.
     *
     * @param host    the server's host name or address
     * @param tcpPort the server's TCP port
     * @throws IOException if the connection could not be established within
     *                     {@link NetworkConstants#CONNECTION_TIMEOUT_MILLIS}
     */
    @Override
    public void connect(String host, int tcpPort) throws IOException {
        client.start();
        try {
            client.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, host, tcpPort);
        } catch (IOException | RuntimeException e) {
            client.stop();
            throw e;
        }
    }

    /**
     * Returns whether the connection is currently up.
     *
     * @return {@code true} while connected
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Hands every event that has arrived since the last call to a handler, in arrival order. Call this every frame from
     * the render thread.
     *
     * @param handler where the events go
     */
    @Override
    public void poll(Handler handler) {
        Event event;
        while ((event = inbox.poll()) != null) {
            if (event.message() == null) {
                handler.onDisconnect();
            } else {
                handler.onMessage(event.message());
            }
        }
    }

    /**
     * Sends a message to the server.
     *
     * @param message the message, one of the classes registered in {@link MessageRegistry}
     */
    @Override
    public void send(Object message) {
        client.sendTCP(message);
    }

    /**
     * Closes the connection and stops the network thread. Safe to call repeatedly.
     */
    @Override
    public void disconnect() {
        client.stop();
    }
}
