package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.client.connect.Reconnector.Phase;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Reconnector} against a sequence of fake connections, one per try, so failures, retries and the grace
 * period are deterministic and need no real network or real waiting.
 * <p>
 * The connection tries themselves are real background threads (the same ones {@link ConnectionAttempt} always uses), so
 * waiting for one to resolve is a real, if short, wait; the seconds simulated by {@link Reconnector#update} are a separate
 * thing and are advanced explicitly, in one step, only when a test means to cross the retry delay or the grace period —
 * never inside the polling loop, which would otherwise blow through both just by looping quickly.
 *
 * @author Mario Koehler
 */
class ReconnectorTest {

    private static final String MINE = "1.0";
    private static final String NAME = "Mario";
    private static final long WAIT_MILLIS = 5_000;
    private static final float PAST_RETRY_DELAY = 4f;
    private static final ServerAddress ADDRESS = new ServerAddress("localhost", 4000);

    /**
     * A link that either fails to connect at once with a given cause, or connects and answers with a given handshake
     * response, accepting or refusing.
     */
    private static final class FakeLink implements ServerLink {
        private final IOException failure;
        private final HandshakeResponse answer;
        private final List<Object> sent = new CopyOnWriteArrayList<>();
        private volatile boolean disconnected;

        private FakeLink(IOException failure, HandshakeResponse answer) {
            this.failure = failure;
            this.answer = answer;
        }

        static FakeLink failing() {
            return new FakeLink(new ConnectException("Connection refused"), null);
        }

        static FakeLink answering(HandshakeResponse answer) {
            return new FakeLink(null, answer);
        }

        @Override
        public void connect(String host, int tcpPort) throws IOException {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void poll(NetworkClient.Handler handler) {
            if (answer != null) {
                handler.onMessage(answer);
            }
        }

        @Override
        public void send(Object message) {
            sent.add(message);
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }
    }

    /**
     * Hands out the fake links of a test, one per call, in order.
     */
    private static final class Script {
        private final Deque<FakeLink> links;

        Script(FakeLink... links) {
            this.links = new ArrayDeque<>(List.of(links));
        }

        ServerLink next() {
            return links.pollFirst();
        }
    }

    /**
     * Waits for the connection try under way to resolve, without simulating any time passing: each spin only drains the
     * background thread's outcome, so grace and retry countdowns are untouched by how many spins it takes.
     *
     * @param reconnector the reconnector to drive
     * @param condition   what to wait for
     */
    private static void awaitAttempt(Reconnector reconnector, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out in phase " + reconnector.phase());
            }
            reconnector.update(0f);
            Thread.onSpinWait();
        }
    }

    /**
     * A reconnect that succeeds on the first try takes the connection over and reports the seat.
     */
    @Test
    void aSuccessfulFirstTryHandsOverTheConnection() {
        HandshakeResponse welcome = new HandshakeResponse("Welcome back", 2, "abc", MINE, 60);
        Script script = new Script(FakeLink.answering(welcome));

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);
        assertEquals(1, reconnector.attemptNumber());
        awaitAttempt(reconnector, () -> reconnector.phase() != Phase.TRYING);

        assertEquals(Phase.SUCCEEDED, reconnector.phase());
        assertEquals(welcome, reconnector.connected().welcome());
        assertNull(reconnector.giveUpReason());
    }

    /**
     * The session token and the real display name are both presented again on the fresh connection: the name is ignored
     * whenever the token still names a seat, but a token the server no longer recognises falls back to an ordinary join,
     * which needs a real name to be worth anything.
     */
    @Test
    void theTokenAndNameArePresentedAgain() {
        HandshakeResponse welcome = new HandshakeResponse("Welcome back", 0, "abc", MINE, 60);
        FakeLink link = FakeLink.answering(welcome);
        Script script = new Script(link);

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);
        awaitAttempt(reconnector, () -> reconnector.phase() != Phase.TRYING);

        assertEquals(1, link.sent.size());
        HandshakeRequest request = (HandshakeRequest) link.sent.get(0);
        assertEquals(NAME, request.getDisplayName());
        assertEquals("token-123", request.getSessionToken());
        assertEquals(MINE, request.getVersion());
    }

    /**
     * A failed try is followed by another, on a fresh connection each time, once the retry delay has passed, until one
     * succeeds.
     */
    @Test
    void aFailedTryIsFollowedByAnother() {
        HandshakeResponse welcome = new HandshakeResponse("Welcome back", 1, "abc", MINE, 60);
        Script script = new Script(FakeLink.failing(), FakeLink.failing(), FakeLink.answering(welcome));

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);
        awaitAttempt(reconnector, () -> reconnector.isWaitingToRetry() || reconnector.phase() != Phase.TRYING);
        assertEquals(1, reconnector.attemptNumber(), "the second try must wait for the retry delay");
        reconnector.update(PAST_RETRY_DELAY);
        assertEquals(2, reconnector.attemptNumber());

        awaitAttempt(reconnector, () -> reconnector.isWaitingToRetry() || reconnector.phase() != Phase.TRYING);
        reconnector.update(PAST_RETRY_DELAY);
        assertEquals(3, reconnector.attemptNumber());
        awaitAttempt(reconnector, () -> reconnector.phase() != Phase.TRYING);

        assertEquals(Phase.SUCCEEDED, reconnector.phase());
        assertEquals(3, reconnector.attemptNumber());
    }

    /**
     * The grace period is counted down while trying, and running out gives up as soon as the try in progress has failed,
     * however many tries that took.
     */
    @Test
    void runningOutOfGraceGivesUp() {
        Script script = new Script(FakeLink.failing(), FakeLink.failing());

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 5f, script::next);
        awaitAttempt(reconnector, () -> reconnector.isWaitingToRetry() || reconnector.phase() != Phase.TRYING);
        reconnector.update(10f);

        assertEquals(Phase.GAVE_UP, reconnector.phase());
        assertEquals(0, reconnector.secondsLeft());
        assertTrue(reconnector.giveUpReason().contains("grace period"));
        assertNull(reconnector.connected());
    }

    /**
     * The seconds left, and the fraction of the grace period left, count down as time passes.
     */
    @Test
    void theCountdownReflectsTheTimeLeft() {
        Script script = new Script(FakeLink.failing());
        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 100f, script::next);

        assertEquals(100, reconnector.secondsLeft());
        assertEquals(1f, reconnector.fractionLeft(), 0.001f);
        reconnector.update(40f);

        assertEquals(60, reconnector.secondsLeft());
        assertEquals(0.6f, reconnector.fractionLeft(), 0.001f);
    }

    /**
     * A version mismatch or another outright refusal gives up at once: retrying would not help.
     */
    @Test
    void aRefusalGivesUpWithoutRetrying() {
        HandshakeResponse mismatch = new HandshakeResponse(false, "Please update.", "9.9");
        Script script = new Script(FakeLink.answering(mismatch));

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);
        awaitAttempt(reconnector, () -> reconnector.phase() != Phase.TRYING);

        assertEquals(Phase.GAVE_UP, reconnector.phase());
        assertEquals(1, reconnector.attemptNumber());
        assertEquals("Please update.", reconnector.giveUpReason());
    }

    /**
     * A made-up or expired token that the server refuses for a reason other than the version also gives up at once.
     */
    @Test
    void aPlainRefusalAlsoGivesUpWithoutRetrying() {
        HandshakeResponse refusal = new HandshakeResponse(false, "A game is already in progress.", MINE);
        Script script = new Script(FakeLink.answering(refusal));

        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);
        awaitAttempt(reconnector, () -> reconnector.phase() != Phase.TRYING);

        assertEquals(Phase.GAVE_UP, reconnector.phase());
        assertEquals("A game is already in progress.", reconnector.giveUpReason());
    }

    /**
     * Cancelling stops trying at once, whether or not a try is under way, and does not report success even if one was
     * about to succeed.
     */
    @Test
    void cancellingStopsTrying() {
        HandshakeResponse welcome = new HandshakeResponse("Welcome back", 0, "abc", MINE, 60);
        Script script = new Script(FakeLink.answering(welcome));
        Reconnector reconnector = new Reconnector(ADDRESS, NAME, "token-123", MINE, 60f, script::next);

        reconnector.cancel();

        assertEquals(Phase.GAVE_UP, reconnector.phase());
        assertNull(reconnector.connected());
        reconnector.update(1f);
        assertEquals(Phase.GAVE_UP, reconnector.phase());
        reconnector.cancel();
        assertEquals(Phase.GAVE_UP, reconnector.phase(), "cancelling twice is harmless");
    }
}
