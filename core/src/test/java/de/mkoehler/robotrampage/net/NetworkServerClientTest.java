package de.mkoehler.robotrampage.net;

import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.SetReady;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link NetworkServer} and {@link NetworkClient} against each other over real loopback sockets, including
 * the threading contract: KryoNet's threads only enqueue, and events reach the handler on the polling thread.
 *
 * @author Mario Koehler
 */
class NetworkServerClientTest {

    private static final long TIMEOUT_MILLIS = 5_000;

    private NetworkServer server;
    private NetworkClient client;

    /**
     * Shuts down whatever a test started.
     */
    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Finds a TCP port nobody is listening on.
     *
     * @return a free port
     * @throws IOException if no socket could be opened
     */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Polls until a condition holds or a timeout passes.
     *
     * @param condition what to wait for; may poll the network as a side effect
     * @return whether the condition became true in time
     * @throws InterruptedException if the wait is interrupted
     */
    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    /**
     * Records what a server handler sees, including which thread it was called on.
     */
    private static final class ServerLog implements NetworkServer.Handler {

        final List<Object> messages = new ArrayList<>();
        final List<Integer> connections = new ArrayList<>();
        final List<Integer> disconnects = new ArrayList<>();
        final List<String> threads = new ArrayList<>();

        /**
         * Records a received message.
         *
         * @param connectionId the connection
         * @param message      the message
         */
        @Override
        public void onMessage(int connectionId, Object message) {
            messages.add(message);
            connections.add(connectionId);
            threads.add(Thread.currentThread().getName());
        }

        /**
         * Records a disconnect.
         *
         * @param connectionId the connection
         */
        @Override
        public void onDisconnect(int connectionId) {
            disconnects.add(connectionId);
            threads.add(Thread.currentThread().getName());
        }
    }

    /**
     * Records what a client handler sees.
     */
    private static final class ClientLog implements NetworkClient.Handler {

        final List<Object> messages = new ArrayList<>();
        int disconnects;

        /**
         * Records a received message.
         *
         * @param message the message
         */
        @Override
        public void onMessage(Object message) {
            messages.add(message);
        }

        /**
         * Counts a disconnect.
         */
        @Override
        public void onDisconnect() {
            disconnects++;
        }
    }

    /**
     * A message goes from client to server and a reply comes back, and both are delivered to the handler on the thread that
     * polls, never on a KryoNet thread.
     *
     * @throws Exception on any failure
     */
    @Test
    void messagesTravelBothWaysAndAreDeliveredOnThePollingThread() throws Exception {
        int port = freePort();
        server = new NetworkServer();
        server.start(port);
        client = new NetworkClient();
        client.connect("localhost", port);
        ServerLog serverLog = new ServerLog();
        ClientLog clientLog = new ClientLog();

        client.send(new HandshakeRequest("Mario", "1.0", "tok"));
        assertTrue(waitFor(() -> {
            server.poll(serverLog);
            return !serverLog.messages.isEmpty();
        }));
        HandshakeRequest received = (HandshakeRequest) serverLog.messages.get(0);
        assertEquals("tok", received.getSessionToken());

        server.send(serverLog.connections.get(0), new HandshakeResponse("Hi", 2, "abc", "1.0", 600));
        assertTrue(waitFor(() -> {
            client.poll(clientLog);
            return !clientLog.messages.isEmpty();
        }));
        assertEquals(2, ((HandshakeResponse) clientLog.messages.get(0)).getSeat());

        String testThread = Thread.currentThread().getName();
        assertTrue(serverLog.threads.stream().allMatch(testThread::equals), "server events on " + serverLog.threads);
    }

    /**
     * Messages from one client arrive in the order they were sent.
     *
     * @throws Exception on any failure
     */
    @Test
    void messagesArriveInOrder() throws Exception {
        int port = freePort();
        server = new NetworkServer();
        server.start(port);
        client = new NetworkClient();
        client.connect("localhost", port);
        ServerLog serverLog = new ServerLog();

        for (int i = 0; i < 50; i++) {
            client.send(new SetReady(i % 2 == 0));
        }

        assertTrue(waitFor(() -> {
            server.poll(serverLog);
            return serverLog.messages.size() == 50;
        }));
        for (int i = 0; i < 50; i++) {
            assertEquals(i % 2 == 0, ((SetReady) serverLog.messages.get(i)).ready());
        }
    }

    /**
     * Both sides learn when the connection ends: the server when a client goes away, and the client when the server closes
     * its connection.
     *
     * @throws Exception on any failure
     */
    @Test
    void disconnectsAreReportedOnBothSides() throws Exception {
        int port = freePort();
        server = new NetworkServer();
        server.start(port);
        client = new NetworkClient();
        client.connect("localhost", port);
        ServerLog serverLog = new ServerLog();
        ClientLog clientLog = new ClientLog();
        client.send(new SetReady(true));
        assertTrue(waitFor(() -> {
            server.poll(serverLog);
            return !serverLog.messages.isEmpty();
        }));

        server.close(serverLog.connections.get(0));

        assertTrue(waitFor(() -> {
            client.poll(clientLog);
            server.poll(serverLog);
            return clientLog.disconnects == 1 && serverLog.disconnects.size() == 1;
        }));
        assertFalse(client.isConnected());
        assertEquals(serverLog.connections.get(0), serverLog.disconnects.get(0));
    }

    /**
     * Connecting to a port nobody listens on fails with an exception instead of hanging.
     *
     * @throws Exception on any failure
     */
    @Test
    void connectingToNothingFails() throws Exception {
        int port = freePort();
        client = new NetworkClient();

        assertThrows(IOException.class, () -> client.connect("localhost", port));
    }
}
