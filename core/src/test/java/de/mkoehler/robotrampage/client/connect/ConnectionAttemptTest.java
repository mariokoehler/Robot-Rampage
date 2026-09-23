package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.client.connect.ConnectFlow.Phase;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.NetworkConstants;
import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ConnectionAttempt} against a link that can be made to succeed, fail, wait and answer on command, so the
 * slow and racing cases are deterministic.
 *
 * @author Mario Koehler
 */
class ConnectionAttemptTest {

    private static final String MINE = "1.0";
    private static final long WAIT_MILLIS = 5_000;

    /**
     * A link whose behaviour a test decides: whether opening waits for a gate, what it throws, and what the server
     * "sends".
     */
    private static final class FakeLink implements ServerLink {

        final CountDownLatch gate = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final List<Object> sent = new CopyOnWriteArrayList<>();
        final List<Object> incoming = new CopyOnWriteArrayList<>();
        volatile boolean waitForGate;
        volatile IOException failure;
        volatile boolean connectCalled;
        volatile boolean dropAfterIncoming;

        @Override
        public void connect(String host, int tcpPort) throws IOException {
            connectCalled = true;
            if (waitForGate) {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void poll(NetworkClient.Handler handler) {
            List<Object> batch = new ArrayList<>(incoming);
            incoming.clear();
            batch.forEach(handler::onMessage);
            if (dropAfterIncoming && !batch.isEmpty()) {
                handler.onDisconnect();
            }
        }

        @Override
        public void send(Object message) {
            sent.add(message);
        }

        @Override
        public void disconnect() {
            disconnected.countDown();
        }
    }

    private final FakeLink link = new FakeLink();

    /**
     * Creates and starts an attempt on the fake link.
     *
     * @param token the session token to present
     * @return the started attempt
     */
    private ConnectionAttempt started(String token) {
        ConnectionAttempt attempt = new ConnectionAttempt(link, new ServerAddress("localhost", 4000), "Mario", token, MINE);
        attempt.start();
        return attempt;
    }

    /**
     * Calls {@code update} until a condition holds.
     *
     * @param attempt   the attempt to drive
     * @param condition what to wait for
     */
    private static void updateUntil(ConnectionAttempt attempt, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out in phase " + attempt.flow().phase());
            }
            attempt.update();
            Thread.onSpinWait();
        }
    }

    /**
     * Waits for the link to be closed.
     */
    private void awaitDisconnect() throws InterruptedException {
        assertTrue(link.disconnected.await(WAIT_MILLIS, TimeUnit.MILLISECONDS), "the link was not closed");
    }

    /**
     * An accepted player: the request carries name, version and token; the acceptance ends the attempt, the link stays
     * open, and messages that came with the acceptance are handed on in order.
     */
    @Test
    void anAcceptedAttemptHandsOverTheOpenLink() {
        ConnectionAttempt attempt = started("old-token");
        updateUntil(attempt, () -> !link.sent.isEmpty());
        HandshakeRequest request = (HandshakeRequest) link.sent.get(0);
        assertEquals("Mario", request.getDisplayName());
        assertEquals(MINE, request.getVersion());
        assertEquals("old-token", request.getSessionToken());
        assertEquals(Phase.HANDSHAKING, attempt.flow().phase());

        HandshakeResponse welcome = new HandshakeResponse("Welcome", 1, "new-token", MINE, 600);
        LobbyState lobby = new LobbyState(List.of(), "Proving Grounds", 8, 2, 12, 12, 3, 3, 90, "proving-grounds", "{}", List.of());
        link.incoming.add(welcome);
        link.incoming.add(lobby);
        updateUntil(attempt, () -> attempt.flow().phase() == Phase.ACCEPTED);

        ConnectedServer connected = attempt.connected();
        assertNotNull(connected);
        assertEquals(welcome, connected.welcome());
        assertEquals(List.of(lobby), connected.earlyMessages());
        assertFalse(connected.closedAlready());
        assertEquals(link, connected.link());
        assertEquals(1, link.disconnected.getCount(), "an accepted link stays open");
    }

    /**
     * A connection that drops right behind the acceptance is still accepted, and the drop is reported with it.
     */
    @Test
    void aDropRightAfterTheAcceptanceIsReportedWithIt() {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());
        assertNull(((HandshakeRequest) link.sent.get(0)).getSessionToken());

        link.dropAfterIncoming = true;
        link.incoming.add(new HandshakeResponse("Welcome", 1, "t", MINE, 600));
        updateUntil(attempt, () -> attempt.flow().phase() == Phase.ACCEPTED);

        assertTrue(attempt.connected().closedAlready());
    }

    /**
     * A server of another version refuses; the attempt ends as a version mismatch and the link is closed.
     */
    @Test
    void aVersionRefusalClosesTheLink() throws InterruptedException {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());

        link.incoming.add(new HandshakeResponse(false, "Please update.", "2.0"));
        updateUntil(attempt, () -> attempt.flow().isFinished());

        assertEquals(Phase.VERSION_MISMATCH, attempt.flow().phase());
        assertEquals("2.0", attempt.flow().serverVersion());
        assertNull(attempt.connected());
        awaitDisconnect();
    }

    /**
     * Any other refusal ends the attempt as a plain refusal with the server's words.
     */
    @Test
    void anotherRefusalKeepsTheServersMessage() throws InterruptedException {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());

        link.incoming.add(new HandshakeResponse(false, "The game is full.", MINE));
        updateUntil(attempt, () -> attempt.flow().isFinished());

        assertEquals(Phase.REFUSED, attempt.flow().phase());
        assertEquals("The game is full.", attempt.flow().detail());
        awaitDisconnect();
    }

    /**
     * A refused connection is reported as unreachable with the reason, and no request is sent.
     */
    @Test
    void aFailedConnectionIsUnreachable() throws InterruptedException {
        link.failure = new ConnectException("Connection refused");
        ConnectionAttempt attempt = started(null);

        updateUntil(attempt, () -> attempt.flow().isFinished());

        assertEquals(Phase.UNREACHABLE, attempt.flow().phase());
        assertEquals("Connection refused", attempt.flow().detail());
        assertTrue(link.sent.isEmpty());
        awaitDisconnect();
    }

    /**
     * A timeout is worded with the configured number of seconds.
     */
    @Test
    void aTimeoutNamesTheWaitedSeconds() {
        assertEquals("Connection timed out after " + NetworkConstants.CONNECTION_TIMEOUT_MILLIS / 1000 + " seconds",
            ConnectionAttempt.describe(new SocketTimeoutException("Connected, but timed out during TCP registration.")));
        assertEquals("IOException", ConnectionAttempt.describe(new IOException()));
        assertEquals("boom", ConnectionAttempt.describe(new IllegalStateException("boom")));
    }

    /**
     * A connection that ends before the server answers is unreachable.
     */
    @Test
    void aDropBeforeTheAnswerIsUnreachable() throws InterruptedException {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());

        link.dropAfterIncoming = true;
        link.incoming.add("something that is not an answer");
        updateUntil(attempt, () -> attempt.flow().isFinished());

        assertEquals(Phase.UNREACHABLE, attempt.flow().phase());
        awaitDisconnect();
    }

    /**
     * Cancelling while the connection is still being opened returns at once and closes the link once the try ends. When the
     * connection then succeeds late, nothing is sent and the attempt stays cancelled.
     */
    @Test
    void aLateSuccessAfterCancelChangesNothing() throws InterruptedException {
        link.waitForGate = true;
        ConnectionAttempt attempt = started(null);
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!link.connectCalled && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(link.connectCalled);

        attempt.cancel();
        assertEquals(Phase.CANCELLED, attempt.flow().phase());
        assertEquals(1, link.disconnected.getCount(), "the link is closed only after the connection try ended");

        link.gate.countDown();
        awaitDisconnect();
        attempt.update();

        assertEquals(Phase.CANCELLED, attempt.flow().phase());
        assertTrue(link.sent.isEmpty(), "a cancelled attempt sends nothing");
    }

    /**
     * Cancelling after the connection was opened, while waiting for the answer, closes the link, and an answer that arrives
     * afterwards is not taken in.
     */
    @Test
    void cancellingWhileWaitingForTheAnswerClosesTheLink() throws InterruptedException {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());

        attempt.cancel();
        awaitDisconnect();
        link.incoming.add(new HandshakeResponse("Welcome", 1, "t", MINE, 600));
        attempt.update();

        assertEquals(Phase.CANCELLED, attempt.flow().phase());
        assertNull(attempt.connected());
    }

    /**
     * Cancelling twice, or after the attempt finished, is harmless and does not close an accepted link.
     */
    @Test
    void cancellingAgainIsHarmless() {
        ConnectionAttempt attempt = started(null);
        updateUntil(attempt, () -> !link.sent.isEmpty());
        link.incoming.add(new HandshakeResponse("Welcome", 1, "t", MINE, 600));
        updateUntil(attempt, () -> attempt.flow().phase() == Phase.ACCEPTED);

        attempt.cancel();
        attempt.cancel();

        assertEquals(Phase.ACCEPTED, attempt.flow().phase());
        assertEquals(1, link.disconnected.getCount());
    }
}
