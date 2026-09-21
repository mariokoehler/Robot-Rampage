package de.mkoehler.robotrampage.server;

import de.mkoehler.robotrampage.net.NetworkClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A client for tests: it connects with the real {@link NetworkClient}, collects everything the server sends and lets a test
 * wait for a particular message.
 *
 * @author Mario Koehler
 */
final class TestClient implements NetworkClient.Handler {

    private static final long TIMEOUT_MILLIS = 5_000;

    private final NetworkClient client = new NetworkClient();
    private final List<Object> received = new ArrayList<>();
    private int disconnects;

    /**
     * Connects to a server on this machine.
     *
     * @param port the server's TCP port
     * @throws IOException if the connection fails
     */
    void connect(int port) throws IOException {
        client.connect("localhost", port);
    }

    /**
     * Sends a message to the server.
     *
     * @param message the message
     */
    void send(Object message) {
        client.send(message);
    }

    /**
     * Closes the connection from this side.
     */
    void close() {
        client.disconnect();
    }

    /**
     * Records a message; called by the poll loop on the test thread.
     *
     * @param message the message
     */
    @Override
    public void onMessage(Object message) {
        received.add(message);
    }

    /**
     * Counts the disconnect; called by the poll loop on the test thread.
     */
    @Override
    public void onDisconnect() {
        disconnects++;
    }

    /**
     * Waits for the next message of a type and removes it from the collected messages, so later calls find the ones after it.
     *
     * @param type the message class to wait for
     * @param <T>  the message type
     * @return the message
     * @throws AssertionError if none arrives in time
     */
    <T> T take(Class<T> type) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            client.poll(this);
            for (int i = 0; i < received.size(); i++) {
                if (type.isInstance(received.get(i))) {
                    return type.cast(received.remove(i));
                }
            }
            sleep();
        }
        throw new AssertionError("Timed out waiting for " + type.getSimpleName() + "; received so far: " + summary());
    }

    /**
     * Waits until the server has closed the connection.
     *
     * @return {@code true} if the disconnect arrived in time
     */
    boolean awaitDisconnect() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            client.poll(this);
            if (disconnects > 0) {
                return true;
            }
            sleep();
        }
        return false;
    }

    /**
     * Polls for a short while and reports whether a message of a type showed up. Used to check that something does
     * <em>not</em> arrive.
     *
     * @param type   the message class
     * @param millis how long to watch
     * @return whether one arrived
     */
    boolean receivesWithin(Class<?> type, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            client.poll(this);
            if (received.stream().anyMatch(type::isInstance)) {
                return true;
            }
            sleep();
        }
        return false;
    }

    /**
     * Describes what has been received and not yet taken, for failure messages.
     *
     * @return the simple class names of the pending messages
     */
    private String summary() {
        return received.stream().map(m -> m.getClass().getSimpleName()).toList().toString();
    }

    /**
     * Sleeps a few milliseconds between polls.
     */
    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", e);
        }
    }
}
