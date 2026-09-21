package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.NetworkConstants;
import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Joins a server: opens the connection on a background thread, sends the request, and reports what happens through a
 * {@link ConnectFlow}.
 * <p>
 * <b>Threading.</b> Opening the connection blocks, so it runs on a worker thread of its own. That thread does nothing but
 * open (and later close) the link and put the outcome in a queue. Everything else, including the {@link ConnectFlow}
 * and the screens that read it, is touched only by the thread that calls {@link #update()} every frame. When the attempt
 * is cancelled the link is closed on the worker thread, after the connection try has ended, and an outcome that arrives
 * later is dropped.
 *
 * @author Mario Koehler
 */
public final class ConnectionAttempt {

    /**
     * What the worker thread reports about opening the connection.
     *
     * @param failure why the connection could not be opened, or {@code null} if it is open
     */
    private record Opened(String failure) {
    }

    private final ServerLink link;
    private final ServerAddress address;
    private final String displayName;
    private final String sessionToken;
    private final ConnectFlow flow;
    private final String clientVersion;
    private final ConcurrentLinkedQueue<Opened> outcomes = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "connect-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Object> earlyMessages = new ArrayList<>();
    private boolean closedAfterAnswer;
    private boolean released;
    private ConnectedServer connected;

    /**
     * Creates an attempt; nothing happens until {@link #start()}.
     *
     * @param link          the connection to use
     * @param address       the server to join
     * @param displayName   the player's name, already checked
     * @param sessionToken  the token of an earlier session to take its seat back, or {@code null} to join as a new player
     * @param clientVersion the version of this game
     */
    public ConnectionAttempt(ServerLink link, ServerAddress address, String displayName, String sessionToken,
                             String clientVersion) {
        this.link = link;
        this.address = address;
        this.displayName = displayName;
        this.sessionToken = sessionToken;
        this.clientVersion = clientVersion;
        this.flow = new ConnectFlow(clientVersion);
    }

    /**
     * Returns the flow that says how the attempt stands. Read it from the thread that calls {@link #update()}.
     *
     * @return the flow
     */
    public ConnectFlow flow() {
        return flow;
    }

    /**
     * Returns the address this attempt is joining.
     *
     * @return the address
     */
    public ServerAddress address() {
        return address;
    }

    /**
     * Returns the accepted connection.
     *
     * @return the connection once the flow is {@link ConnectFlow.Phase#ACCEPTED}, otherwise {@code null}
     */
    public ConnectedServer connected() {
        return connected;
    }

    /**
     * Starts opening the connection in the background.
     */
    public void start() {
        flow.begin();
        worker.execute(this::open);
    }

    /**
     * Gives up. The connection try that may still be running is left to end on its own thread, and the link is closed
     * behind it, whatever its outcome. Returns at once.
     */
    public void cancel() {
        if (flow.isFinished()) {
            return;
        }
        flow.cancel();
        release();
    }

    /**
     * Takes in what happened since the last call and moves the flow along. Call it every frame from the loop thread.
     */
    public void update() {
        Opened opened;
        while ((opened = outcomes.poll()) != null) {
            if (flow.phase() != ConnectFlow.Phase.CONNECTING) {
                continue;
            }
            if (opened.failure() != null) {
                flow.onConnectFailed(opened.failure());
                release();
            } else if (flow.onConnected()) {
                link.send(new HandshakeRequest(displayName, clientVersion, sessionToken));
            }
        }
        if (flow.phase() == ConnectFlow.Phase.HANDSHAKING) {
            link.poll(new NetworkClient.Handler() {
                @Override
                public void onMessage(Object message) {
                    take(message);
                }

                @Override
                public void onDisconnect() {
                    if (flow.phase() == ConnectFlow.Phase.ACCEPTED) {
                        closedAfterAnswer = true;
                    } else {
                        flow.onDisconnected();
                    }
                }
            });
            finishIfAnswered();
        }
    }

    /**
     * Handles one message that arrived after the request was sent: the answer, or, if the answer already came, a message
     * that belongs to the next screen.
     *
     * @param message the message
     */
    private void take(Object message) {
        if (flow.phase() == ConnectFlow.Phase.HANDSHAKING && message instanceof HandshakeResponse response) {
            flow.onResponse(response);
        } else if (flow.phase() == ConnectFlow.Phase.ACCEPTED) {
            earlyMessages.add(message);
        }
    }

    /**
     * Wraps up once the flow has reached a final phase: hands over an accepted connection, or closes a failed one.
     */
    private void finishIfAnswered() {
        switch (flow.phase()) {
            case ACCEPTED -> {
                connected = new ConnectedServer(link, flow.response(), earlyMessages, closedAfterAnswer);
                worker.shutdown();
            }
            case UNREACHABLE, VERSION_MISMATCH, REFUSED -> release();
            default -> {
            }
        }
    }

    /**
     * Closes the link on the worker thread, behind a connection try that may still be running, and lets the thread end.
     */
    private void release() {
        if (released) {
            return;
        }
        released = true;
        worker.execute(link::disconnect);
        worker.shutdown();
    }

    /**
     * Opens the connection; runs on the worker thread and reports through the outcome queue.
     */
    private void open() {
        try {
            link.connect(address.host(), address.port());
            outcomes.add(new Opened(null));
        } catch (IOException | RuntimeException e) {
            outcomes.add(new Opened(describe(e)));
        }
    }

    /**
     * Words a failed connection try for the player.
     *
     * @param failure what was thrown
     * @return a sentence for the dialog, such as {@code Connection timed out after 10 seconds}
     */
    static String describe(Exception failure) {
        if (failure instanceof SocketTimeoutException) {
            return "Connection timed out after " + NetworkConstants.CONNECTION_TIMEOUT_MILLIS / 1000 + " seconds";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }
}
