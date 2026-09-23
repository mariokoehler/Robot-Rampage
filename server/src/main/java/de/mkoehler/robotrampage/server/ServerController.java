package de.mkoehler.robotrampage.server;

import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.net.AppVersion;
import de.mkoehler.robotrampage.net.NetworkServer;
import de.mkoehler.robotrampage.net.messages.ChooseRespawnFacing;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.ReturnToLobby;
import de.mkoehler.robotrampage.net.messages.SetReady;
import de.mkoehler.robotrampage.net.messages.SetTimerPaused;
import de.mkoehler.robotrampage.net.messages.StartGameRequest;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.session.GameSession;
import de.mkoehler.robotrampage.session.JoinResult;
import de.mkoehler.robotrampage.session.Outbox;
import de.mkoehler.robotrampage.session.SessionConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Connects the network to the {@link GameSession}: it performs the handshake, keeps track of which connection belongs
 * to which seat, translates incoming messages into session commands, and delivers the session's outgoing messages.
 * <p>
 * <b>Single-threaded by contract</b> (design.md 3.5): {@link #update()} is the only entry point and must always be called
 * from the same thread, normally the headless application's render loop. It drains the events the network threads
 * queued, applies them to the session, and lets time pass. Nothing else touches the session.
 *
 * @author Mario Koehler
 */
public final class ServerController implements NetworkServer.Handler, Outbox {

    private final NetworkServer network;
    private final GameSession session;
    private final Map<Integer, Integer> seatByConnection = new HashMap<>();
    private final Map<Integer, Integer> connectionBySeat = new HashMap<>();

    /**
     * Creates a controller with a fresh session in the lobby.
     *
     * @param network the network endpoint to serve on; already started or started later, the controller does not care
     * @param board   the board to play
     * @param config  the session's timings
     * @param seed    the game's random seed
     * @param clock   the source of time in milliseconds
     */
    public ServerController(NetworkServer network, LoadedBoard board, SessionConfig config, long seed, LongSupplier clock) {
        this.network = network;
        this.session = new GameSession(board, config, seed, clock, this);
    }

    /**
     * Handles everything that happened since the last call, then lets time pass in the session. Call regularly from the
     * loop thread.
     */
    public void update() {
        network.poll(this);
        session.tick();
    }

    /**
     * Dispatches a message from a client. The handshake comes first; every other message is only honoured on a connection
     * that has completed it, and is attributed to that connection's seat, never to anything the message itself claims.
     *
     * @param connectionId the connection it arrived on
     * @param message      the message
     */
    @Override
    public void onMessage(int connectionId, Object message) {
        if (message instanceof HandshakeRequest request) {
            handshake(connectionId, request);
            return;
        }
        Integer seat = seatByConnection.get(connectionId);
        if (seat == null) {
            return;
        }
        if (message instanceof SetReady ready) {
            session.setReady(seat, ready.ready());
        } else if (message instanceof StartGameRequest) {
            session.startGame(seat);
        } else if (message instanceof SubmitProgram program) {
            session.submitProgram(seat, program);
        } else if (message instanceof SetTimerPaused pause) {
            session.setTimerPaused(seat, pause.paused());
        } else if (message instanceof ChooseRespawnFacing chosen) {
            session.chooseRespawnFacing(seat, chosen.facing());
        } else if (message instanceof ReturnToLobby) {
            session.returnToLobby(seat);
        }
    }

    /**
     * Handles a connection ending. If it was the connection currently serving a seat, the session is told the player has
     * gone; a connection that was already replaced by a reconnect changes nothing.
     *
     * @param connectionId the connection that ended
     */
    @Override
    public void onDisconnect(int connectionId) {
        Integer seat = seatByConnection.remove(connectionId);
        if (seat != null && connectionBySeat.get(seat) != null && connectionBySeat.get(seat) == connectionId) {
            connectionBySeat.remove(seat);
            session.disconnect(seat);
        }
    }

    /**
     * Sends a session message to one seat, if a connection serves it.
     *
     * @param seat    the receiving seat
     * @param message the message
     */
    @Override
    public void send(int seat, Object message) {
        Integer connectionId = connectionBySeat.get(seat);
        if (connectionId != null) {
            network.send(connectionId, message);
        }
    }

    /**
     * Sends a session message to every connected seat.
     *
     * @param message the message
     */
    @Override
    public void broadcast(Object message) {
        for (int connectionId : connectionBySeat.values()) {
            network.send(connectionId, message);
        }
    }

    /**
     * Answers a client's opening request: refuses clients of a different version, asks the session to seat the player (or
     * re-seat them, if they present a valid token), replaces an older connection of the same seat, and only then, after the
     * answer has been sent, lets the session bring the player up to date.
     *
     * @param connectionId the connection the request came on
     * @param request      the request
     */
    private void handshake(int connectionId, HandshakeRequest request) {
        if (seatByConnection.containsKey(connectionId)) {
            return;
        }
        String serverVersion = AppVersion.getVersion();
        if (!serverVersion.equals(request.getVersion())) {
            refuse(connectionId, "Client version " + request.getVersion() + " does not match server version "
                + serverVersion + ". Please update your client.");
            return;
        }
        JoinResult result = session.join(request.getDisplayName(), request.getSessionToken());
        if (!result.accepted()) {
            refuse(connectionId, result.message());
            return;
        }
        Integer previous = connectionBySeat.put(result.seat(), connectionId);
        if (previous != null && previous != connectionId) {
            seatByConnection.remove(previous);
            network.close(previous);
        }
        seatByConnection.put(connectionId, result.seat());
        network.send(connectionId, new HandshakeResponse(result.message(), result.seat(), result.sessionToken(),
            serverVersion, session.reconnectGraceSeconds()));
        session.attach(result.seat());
    }

    /**
     * Rejects a client and closes its connection.
     *
     * @param connectionId the connection
     * @param reason       what to tell the player
     */
    private void refuse(int connectionId, String reason) {
        network.send(connectionId, new HandshakeResponse(false, reason, AppVersion.getVersion()));
        network.close(connectionId);
    }
}
