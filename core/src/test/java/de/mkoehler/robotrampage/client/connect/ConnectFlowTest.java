package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.client.connect.ConnectFlow.Phase;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies every path through {@link ConnectFlow}, including events that arrive when they no longer matter.
 *
 * @author Mario Koehler
 */
class ConnectFlowTest {

    private static final String MINE = "1.0";

    private final ConnectFlow flow = new ConnectFlow(MINE);

    /**
     * Moves a new flow to the point where the request has been sent.
     */
    private void handshaking() {
        flow.begin();
        assertTrue(flow.onConnected());
    }

    /**
     * A flow starts idle, and begins connecting.
     */
    @Test
    void startsIdleAndBeginsConnecting() {
        assertEquals(Phase.IDLE, flow.phase());
        assertFalse(flow.isFinished());

        flow.begin();

        assertEquals(Phase.CONNECTING, flow.phase());
        assertThrows(IllegalStateException.class, flow::begin);
    }

    /**
     * An accepting answer ends in {@link Phase#ACCEPTED} and keeps the response.
     */
    @Test
    void anAcceptedPlayerIsAccepted() {
        handshaking();
        HandshakeResponse welcome = new HandshakeResponse("Welcome", 2, "token", MINE, 600);

        flow.onResponse(welcome);

        assertEquals(Phase.ACCEPTED, flow.phase());
        assertTrue(flow.isFinished());
        assertSame(welcome, flow.response());
    }

    /**
     * A failed connection try is reported as unreachable with its reason.
     */
    @Test
    void aFailedConnectionTryIsUnreachable() {
        flow.begin();

        flow.onConnectFailed("Connection refused");

        assertEquals(Phase.UNREACHABLE, flow.phase());
        assertEquals("Connection refused", flow.detail());
        assertNull(flow.response());
        assertEquals("", flow.serverVersion());
    }

    /**
     * A refusal that names another server version is a version mismatch and carries both the message and the version.
     */
    @Test
    void aRefusalForTheVersionIsAVersionMismatch() {
        handshaking();

        flow.onResponse(new HandshakeResponse(false, "Please update.", "2.0"));

        assertEquals(Phase.VERSION_MISMATCH, flow.phase());
        assertEquals("2.0", flow.serverVersion());
        assertEquals("Please update.", flow.detail());
    }

    /**
     * A refusal from a server that runs the same version is another kind of refusal, such as a full game.
     */
    @Test
    void aRefusalFromTheSameVersionIsAPlainRefusal() {
        handshaking();

        flow.onResponse(new HandshakeResponse(false, "The game is full.", MINE));

        assertEquals(Phase.REFUSED, flow.phase());
        assertEquals("The game is full.", flow.detail());
    }

    /**
     * A refusal that does not say which version the server runs cannot be a version mismatch.
     */
    @Test
    void aRefusalWithoutAServerVersionIsAPlainRefusal() {
        handshaking();

        flow.onResponse(new HandshakeResponse(false, null, null));

        assertEquals(Phase.REFUSED, flow.phase());
        assertEquals("", flow.detail());
        assertEquals("", flow.serverVersion());
    }

    /**
     * A connection that ends before the server answered is unreachable.
     */
    @Test
    void aDisconnectBeforeTheAnswerIsUnreachable() {
        handshaking();

        flow.onDisconnected();

        assertEquals(Phase.UNREACHABLE, flow.phase());
        assertFalse(flow.detail().isEmpty());
    }

    /**
     * The disconnect that follows an answer changes nothing.
     */
    @Test
    void aDisconnectAfterTheAnswerIsIgnored() {
        handshaking();
        flow.onResponse(new HandshakeResponse(false, "No.", MINE));

        flow.onDisconnected();

        assertEquals(Phase.REFUSED, flow.phase());
    }

    /**
     * Once cancelled, a connection that opens late, fails late or is answered late changes nothing.
     */
    @Test
    void aCancelledAttemptStaysCancelled() {
        flow.begin();
        flow.cancel();

        assertFalse(flow.onConnected());
        flow.onConnectFailed("late failure");
        flow.onResponse(new HandshakeResponse("Welcome", 0, "t", MINE, 600));
        flow.onDisconnected();

        assertEquals(Phase.CANCELLED, flow.phase());
        assertEquals("", flow.detail());
        assertNull(flow.response());
    }

    /**
     * Cancelling a finished attempt does not undo the result.
     */
    @Test
    void cancellingAFinishedAttemptChangesNothing() {
        handshaking();
        flow.onResponse(new HandshakeResponse("Welcome", 0, "t", MINE, 600));

        flow.cancel();

        assertEquals(Phase.ACCEPTED, flow.phase());
    }

    /**
     * An answer that comes before the connection is up is ignored.
     */
    @Test
    void anAnswerBeforeTheConnectionIsIgnored() {
        flow.begin();

        flow.onResponse(new HandshakeResponse("Welcome", 0, "t", MINE, 600));

        assertEquals(Phase.CONNECTING, flow.phase());
    }
}
