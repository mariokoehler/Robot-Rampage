package de.mkoehler.robotrampage.session;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.net.NetworkConstants;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerUpdate;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.Forfeit;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.GameOutcome;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The state machine of one game on the server: lobby, programming, resolving, game over (design.md 2.13,
 * 3.5).
 * <p>
 * The session is deliberately <em>protocol-agnostic and single-threaded</em>. It is driven by plain method calls
 * ({@link #join}, {@link #attach}, {@link #disconnect}, {@link #setReady}, {@link #startGame},
 * {@link #submitProgram} and {@link #tick}), all made from one thread, and it answers by putting messages into an
 * {@link Outbox}. It never touches a socket or a thread, and it takes its time from an injected clock, never from
 * the system clock, so every timer can be tested by advancing a fake clock.
 * <p>
 * A player is identified by their <em>seat</em>, which doubles as their robot's id and selects their start square;
 * seats never change during a game. Everything hidden from other players &mdash; hands and programs &mdash; is only
 * ever sent to the player it belongs to.
 *
 * @author Mario Koehler
 */
public final class GameSession {

    /**
     * The phases a session goes through.
     */
    public enum Phase {

        /**
         * Players gather and get ready; the host starts the game.
         */
        LOBBY,

        /**
         * A turn has been dealt and players are choosing their programs.
         */
        PROGRAMMING,

        /**
         * A turn has been played out; the session waits a moment so clients can animate it.
         */
        RESOLVING,

        /**
         * The game has ended and its results are on display.
         */
        GAME_OVER
    }

    private static final int MAX_NAME_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH;
    private static final long FILL_STRIDE = 0x9E3779B97F4A7C15L;

    private final LoadedBoard board;
    private final String boardJson;
    private final SessionConfig config;
    private final long seed;
    private final LongSupplier clock;
    private final Outbox outbox;

    private final Map<Integer, SessionPlayer> players = new TreeMap<>();
    private int nextJoinOrder;
    private Phase phase = Phase.LOBBY;

    private GameState state;
    private int turn;
    private Set<Integer> respawnedThisTurn = Set.of();
    private long programmingDeadline;
    private boolean squeezeActive;
    private long nextTurnAt;
    private long backToLobbyAt;
    private int fillCounter;

    /**
     * Creates a session in the lobby phase.
     *
     * @param board  the board that will be played
     * @param config the timings and limits
     * @param seed   the seed of the game's randomness: the deck's shuffles and the random fills of programs that
     *               time out
     * @param clock  the source of time, in milliseconds; only differences matter
     * @param outbox where outgoing messages go
     */
    public GameSession(LoadedBoard board, SessionConfig config, long seed, LongSupplier clock, Outbox outbox) {
        this.board = board;
        this.boardJson = BoardLoader.toJson(board.definition());
        this.config = config;
        this.seed = seed;
        this.clock = clock;
        this.outbox = outbox;
    }

    /**
     * Returns the current phase.
     *
     * @return the phase
     */
    public Phase phase() {
        return phase;
    }

    /**
     * Returns the number of the current turn.
     *
     * @return the turn, or 0 before the game has started
     */
    public int turn() {
        return turn;
    }

    /**
     * Returns the rules state of the running game, for inspection in tests and by the server's own bookkeeping.
     * The state must not be modified.
     *
     * @return the game state, or {@code null} in the lobby
     */
    GameState gameState() {
        return state;
    }

    // ------------------------------------------------------------------------------------------------------
    // Joining, leaving, the lobby
    // ------------------------------------------------------------------------------------------------------

    /**
     * Lets a player in, or takes a returning player back to their seat.
     * <p>
     * A valid session token always re-seats its owner, in any phase, unless they have already been removed from the
     * game. Otherwise a new player can only join in the lobby, while seats are free. The player is only
     * <em>registered</em> here; call {@link #attach(int)} once the answer has been sent to them, so the state
     * messages that follow arrive after it.
     *
     * @param displayName  the name to show for a new player
     * @param sessionToken the token from an earlier session to resume, or {@code null}
     * @return whether the player was let in, and if so their seat and token
     */
    public JoinResult join(String displayName, String sessionToken) {
        if (sessionToken != null) {
            for (SessionPlayer known : players.values()) {
                if (known.token.equals(sessionToken)) {
                    if (known.left) {
                        return JoinResult.rejected("You have left this game.");
                    }
                    known.reconnected = !known.connected;
                    known.connected = true;
                    return new JoinResult(true, "Welcome back, " + known.name + ".", known.seat, known.token);
                }
            }
        }
        String name = cleanName(displayName);
        if (name == null) {
            return JoinResult.rejected("Please enter a name.");
        }
        if (phase != Phase.LOBBY) {
            return JoinResult.rejected("A game is already in progress.");
        }
        int seat = lowestFreeSeat();
        if (seat < 0) {
            return JoinResult.rejected("The game is full.");
        }
        SessionPlayer player = new SessionPlayer(seat, name, UUID.randomUUID().toString(), nextJoinOrder++);
        players.put(seat, player);
        return new JoinResult(true, "Welcome, " + name + ".", seat, player.token);
    }

    /**
     * Tells the session that a joined player's connection is ready to receive messages. In the lobby everybody gets
     * the new lobby state; in a running game the returning player is brought up to date (see the resync described
     * in design.md 3.5) and the others are told they are back.
     *
     * @param seat the player's seat
     */
    public void attach(int seat) {
        SessionPlayer player = players.get(seat);
        if (player == null) {
            return;
        }
        if (phase == Phase.LOBBY) {
            broadcastLobby();
            return;
        }
        resync(player);
        if (player.reconnected) {
            player.reconnected = false;
            outbox.broadcast(new PlayerConnection(seat, true));
        }
    }

    /**
     * Handles a player's connection dropping. In the lobby the player simply leaves. In a game their seat is kept
     * for the reconnect grace period; if they still owe a program it is filled in at random at once.
     *
     * @param seat the player's seat
     */
    public void disconnect(int seat) {
        SessionPlayer player = players.get(seat);
        if (player == null || !player.connected) {
            return;
        }
        player.connected = false;
        player.disconnectedAt = clock.getAsLong();
        if (phase == Phase.LOBBY) {
            players.remove(seat);
            broadcastLobby();
            return;
        }
        outbox.broadcast(new PlayerConnection(seat, false));
        if (phase == Phase.PROGRAMMING && player.awaiting && !player.confirmed) {
            fillRandomly(player);
            afterConfirmation();
        }
    }

    /**
     * Sets whether a player is ready to start. Only meaningful in the lobby.
     *
     * @param seat  the player's seat
     * @param ready whether they are ready
     */
    public void setReady(int seat, boolean ready) {
        SessionPlayer player = players.get(seat);
        if (player == null || phase != Phase.LOBBY) {
            return;
        }
        player.ready = ready;
        broadcastLobby();
    }

    /**
     * Starts the game on the host's request. It only happens in the lobby, if the request comes from the host,
     * there are enough players and every other player is ready; otherwise the requester is told why not.
     *
     * @param seat the requesting player's seat
     */
    public void startGame(int seat) {
        if (phase != Phase.LOBBY || !players.containsKey(seat)) {
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can start the game.");
            return;
        }
        if (players.size() < config.minPlayers()) {
            reject(seat, "At least " + config.minPlayers() + " players are needed to start.");
            return;
        }
        for (SessionPlayer player : players.values()) {
            if (player.seat != seat && !player.ready) {
                reject(seat, "Not everybody is ready yet.");
                return;
            }
        }

        List<Robot> robots = new ArrayList<>();
        for (SessionPlayer player : players.values()) {
            StartSquare start = board.board().startSquares().get(player.seat);
            robots.add(new Robot(player.seat, start.position(), start.facing()));
        }
        state = new GameState(board.board(), robots, Deck.standard(seed));
        turn = 0;
        fillCounter = 0;
        for (SessionPlayer player : players.values()) {
            outbox.send(player.seat, new GameStarted(boardJson, playerInfos(), player.seat));
        }
        beginTurn();
    }

    // ------------------------------------------------------------------------------------------------------
    // Programming
    // ------------------------------------------------------------------------------------------------------

    /**
     * Accepts a player's program for the current turn. The cards are named by priority and must all come from the
     * player's own dealt hand, one per unlocked register. An invalid submission is refused with a message and the
     * player may try again; a valid one is final.
     * <p>
     * A player whose robot is powered down needs no program, but may use this to announce staying powered down.
     *
     * @param seat    the submitting player's seat
     * @param program the submission
     */
    public void submitProgram(int seat, SubmitProgram program) {
        SessionPlayer player = players.get(seat);
        if (player == null) {
            return;
        }
        if (phase != Phase.PROGRAMMING || program.turn() != turn) {
            reject(seat, "There is no program to submit right now.");
            return;
        }
        Robot robot = state.robot(seat);
        if (!player.awaiting) {
            if (robot.isPoweredDown()) {
                robot.setPowerDownAnnounced(program.powerDown());
            } else {
                reject(seat, "You do not need to program this turn.");
            }
            return;
        }
        if (player.confirmed) {
            reject(seat, "Your program is already locked in.");
            return;
        }
        List<Card> chosen = new ArrayList<>();
        for (Integer priority : program.cardPriorities()) {
            Optional<Card> card = player.hand.stream().filter(c -> priority != null && c.priority() == priority).findFirst();
            if (card.isEmpty()) {
                reject(seat, "Card " + priority + " is not in your hand.");
                return;
            }
            chosen.add(card.get());
        }
        try {
            Programming.submit(state, seat, player.hand, chosen, program.powerDown());
        } catch (IllegalArgumentException e) {
            reject(seat, e.getMessage());
            return;
        }
        if (program.respawnFacing() != null && respawnedThisTurn.contains(seat)) {
            robot.setFacing(program.respawnFacing());
        }
        confirm(player);
        afterConfirmation();
    }

    // ------------------------------------------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------------------------------------------

    /**
     * Lets time pass: removes players whose reconnect grace period is over, fills in the programs of players who ran
     * out of time and resolves the turn, starts the next turn after the pause, and returns to the lobby after the
     * game-over pause. Must be called regularly.
     */
    public void tick() {
        long now = clock.getAsLong();
        if (phase == Phase.PROGRAMMING || phase == Phase.RESOLVING) {
            removePlayersAwayTooLong(now);
        }
        switch (phase) {
            case PROGRAMMING -> {
                if (now >= programmingDeadline) {
                    for (SessionPlayer player : players.values()) {
                        if (player.awaiting && !player.confirmed) {
                            fillRandomly(player);
                        }
                    }
                    afterConfirmation();
                }
            }
            case RESOLVING -> {
                if (now >= nextTurnAt) {
                    beginTurn();
                }
            }
            case GAME_OVER -> {
                if (now >= backToLobbyAt) {
                    returnToLobby();
                }
            }
            case LOBBY -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Turn flow
    // ------------------------------------------------------------------------------------------------------

    /**
     * Starts the next turn: destroyed robots re-enter, hands are dealt, everybody is told, and each player who must
     * program gets their cards.
     */
    private void beginTurn() {
        turn++;
        EventLog respawnLog = new EventLog();
        Respawner.respawn(state, Map.of(), respawnLog);
        Set<Integer> respawned = new HashSet<>();
        for (LoggedEvent entry : respawnLog.entries()) {
            if (entry.event() instanceof GameEvent.RobotRespawned respawn) {
                respawned.add(respawn.robotId());
            }
        }
        respawnedThisTurn = respawned;

        Map<Integer, List<Card>> hands = Programming.deal(state);
        List<Integer> awaited = new ArrayList<>();
        for (SessionPlayer player : players.values()) {
            player.awaiting = hands.containsKey(player.seat) && !player.left;
            player.confirmed = !player.awaiting;
            player.hand = player.awaiting ? hands.get(player.seat) : List.of();
            if (player.awaiting) {
                awaited.add(player.seat);
            }
        }
        long now = clock.getAsLong();
        programmingDeadline = now + config.programmingMillis();
        squeezeActive = false;
        phase = Phase.PROGRAMMING;

        outbox.broadcast(new TurnStarted(turn, respawnLog.entries(), awaited, (int) (config.programmingMillis() / 1000)));
        for (SessionPlayer player : players.values()) {
            HandDealt hand = handFor(player);
            if (hand != null) {
                outbox.send(player.seat, hand);
            }
        }
        for (SessionPlayer player : players.values()) {
            if (player.awaiting && !player.connected) {
                fillRandomly(player);
            }
        }
        afterConfirmation();
    }

    /**
     * Builds the hand message for a player: their cards, their locked cards and their status this turn.
     *
     * @param player the player
     * @return the message, or {@code null} for a player who takes no part in this turn (eliminated or removed)
     */
    private HandDealt handFor(SessionPlayer player) {
        Robot robot = state.robot(player.seat);
        if (player.left || !(robot.isActive())) {
            return null;
        }
        List<Card> locked = new ArrayList<>();
        for (int index = Robot.REGISTER_COUNT - robot.lockedRegisterCount(); index < Robot.REGISTER_COUNT; index++) {
            locked.add(robot.register(index));
        }
        List<Card> hand = player.confirmed && player.awaiting ? List.of() : player.hand;
        return new HandDealt(turn, hand, locked, respawnedThisTurn.contains(player.seat), robot.isPoweredDown());
    }

    /**
     * Checks what a confirmation (or a disconnect, or a removal) means for the turn: resolves it if nobody is
     * still deciding, and starts the squeeze on the last player if only one is left.
     */
    private void afterConfirmation() {
        if (phase != Phase.PROGRAMMING) {
            return;
        }
        int awaited = 0;
        int pending = 0;
        for (SessionPlayer player : players.values()) {
            if (player.awaiting) {
                awaited++;
                if (!player.confirmed) {
                    pending++;
                }
            }
        }
        if (pending == 0) {
            resolveTurn();
        } else if (!squeezeActive && awaited >= 2 && pending == 1) {
            squeezeActive = true;
            long now = clock.getAsLong();
            programmingDeadline = Math.min(programmingDeadline, now + config.lastPlayerMillis());
            outbox.broadcast(new TimerUpdate(secondsUntil(programmingDeadline)));
        }
    }

    /**
     * Locks a player's program in and tells everybody that they are done.
     *
     * @param player the player
     */
    private void confirm(SessionPlayer player) {
        player.confirmed = true;
        player.hand = List.of();
        outbox.broadcast(new PlayerConfirmed(player.seat));
    }

    /**
     * Programs a player's robot with a random selection of their dealt cards, as when their time has run out or
     * they have disconnected. The choice comes from a stream derived from the game seed and a counter, so it is
     * reproducible.
     *
     * @param player the player who still owes a program
     */
    private void fillRandomly(SessionPlayer player) {
        Robot robot = state.robot(player.seat);
        List<Card> shuffled = new ArrayList<>(player.hand);
        Collections.shuffle(shuffled, new Random(seed + FILL_STRIDE * ++fillCounter));
        int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
        Programming.submit(state, player.seat, player.hand, new ArrayList<>(shuffled.subList(0, unlocked)), false);
        confirm(player);
    }

    /**
     * Plays out the turn, tells everybody what happened, and either ends the game or schedules the next turn.
     */
    private void resolveTurn() {
        TurnResult result = TurnResolver.resolve(state);
        state = result.state();
        outbox.broadcast(new TurnResolved(turn, result.events()));
        outbox.broadcast(snapshot());
        if (state.isOver()) {
            finishGame();
        } else {
            phase = Phase.RESOLVING;
            nextTurnAt = clock.getAsLong() + config.pauseAfterTurn(result.events().size());
        }
    }

    /**
     * Ends the game and shows the results.
     */
    private void finishGame() {
        phase = Phase.GAME_OVER;
        backToLobbyAt = clock.getAsLong() + config.gameOverMillis();
        outbox.broadcast(new GameOver(state.winnerId(), robotStates()));
    }

    /**
     * Goes back to the lobby after a game: players who dropped out are forgotten, the rest keep their seats and
     * must get ready again.
     */
    private void returnToLobby() {
        players.values().removeIf(player -> !player.connected || player.left);
        for (SessionPlayer player : players.values()) {
            player.ready = false;
            player.awaiting = false;
            player.confirmed = false;
            player.hand = List.of();
        }
        state = null;
        turn = 0;
        phase = Phase.LOBBY;
        broadcastLobby();
    }

    /**
     * Removes the players who have stayed away past the reconnect grace period: their robots leave the game for
     * good (design.md 2.13).
     *
     * @param now the current time
     */
    private void removePlayersAwayTooLong(long now) {
        for (SessionPlayer player : new ArrayList<>(players.values())) {
            if (!player.connected && !player.left && now - player.disconnectedAt >= config.reconnectGraceMillis()) {
                removeFromGame(player);
            }
        }
    }

    /**
     * Takes a player who is gone for good out of the game and ends the game if too few players are left.
     *
     * @param player the player
     */
    private void removeFromGame(SessionPlayer player) {
        // A disconnected player never owes a program: it is filled in the moment they drop or a turn is dealt to
        // them, so there is no unspent hand to return here. Forfeit returns the cards already in their registers.
        player.left = true;
        player.confirmed = true;
        EventLog log = new EventLog();
        Forfeit.forfeit(state, player.seat, log);
        GameOutcome.endIfOneRobotIsLeft(state, log);
        outbox.broadcast(new PlayerLeft(player.seat, log.entries()));
        if (state.isOver()) {
            if (phase != Phase.GAME_OVER) {
                finishGame();
            }
        } else {
            afterConfirmation();
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------------------------------------------

    /**
     * Brings a player who has just (re)connected up to date: the game setup, the state of every robot, and where the
     * current turn stands, including their own hand if they still owe a program.
     *
     * @param player the player to update
     */
    private void resync(SessionPlayer player) {
        outbox.send(player.seat, new GameStarted(boardJson, playerInfos(), player.seat));
        outbox.send(player.seat, snapshot());
        switch (phase) {
            case PROGRAMMING -> {
                List<Integer> awaited = new ArrayList<>();
                for (SessionPlayer other : players.values()) {
                    if (other.awaiting) {
                        awaited.add(other.seat);
                    }
                }
                outbox.send(player.seat, new TurnStarted(turn, List.of(), awaited, secondsUntil(programmingDeadline)));
                for (SessionPlayer other : players.values()) {
                    if (other.awaiting && other.confirmed) {
                        outbox.send(player.seat, new PlayerConfirmed(other.seat));
                    }
                }
                HandDealt hand = handFor(player);
                if (hand != null) {
                    outbox.send(player.seat, hand);
                }
            }
            case GAME_OVER -> outbox.send(player.seat, new GameOver(state.winnerId(), robotStates()));
            default -> {
            }
        }
    }

    /**
     * Sends a player a refusal.
     *
     * @param seat   the player
     * @param reason why the request was refused
     */
    private void reject(int seat, String reason) {
        outbox.send(seat, new RequestRejected(reason));
    }

    /**
     * Tells everybody the state of the lobby.
     */
    private void broadcastLobby() {
        outbox.broadcast(new LobbyState(playerInfos(), board.definition().name(), board.board().startSquares().size(),
            config.minPlayers(), board.board().width(), board.board().height(), board.board().flags().size(),
            Robot.STARTING_LIVES, (int) (config.programmingMillis() / 1000)));
    }

    /**
     * Describes all seated players.
     *
     * @return one entry per player, ordered by seat
     */
    private List<PlayerInfo> playerInfos() {
        int host = hostSeat();
        List<PlayerInfo> infos = new ArrayList<>();
        for (SessionPlayer player : players.values()) {
            infos.add(new PlayerInfo(player.seat, player.name, player.ready, player.connected, player.seat == host));
        }
        return infos;
    }

    /**
     * Builds the public state of the whole game.
     *
     * @return the snapshot
     */
    private StateSnapshot snapshot() {
        return new StateSnapshot(turn, robotStates(), state.isOver(), state.winnerId());
    }

    /**
     * Describes every robot of the game.
     *
     * @return the public state of each robot, ordered by id
     */
    private List<RobotState> robotStates() {
        List<RobotState> states = new ArrayList<>();
        for (Robot robot : state.robots()) {
            states.add(RobotState.of(robot));
        }
        return states;
    }

    // ------------------------------------------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------------------------------------------

    /**
     * Returns the seat of the host: the player who joined first and is still here.
     *
     * @return the host's seat, or -1 if nobody is seated
     */
    private int hostSeat() {
        return players.values().stream().filter(player -> !player.left)
            .min((a, b) -> Integer.compare(a.joinOrder, b.joinOrder)).map(player -> player.seat).orElse(-1);
    }

    /**
     * Finds the lowest seat number nobody is sitting on.
     *
     * @return the seat, or -1 if the board has no start square left
     */
    private int lowestFreeSeat() {
        for (int seat = 0; seat < board.board().startSquares().size(); seat++) {
            if (!players.containsKey(seat)) {
                return seat;
            }
        }
        return -1;
    }

    /**
     * Cleans up a display name: control characters are removed, whitespace is trimmed and the length is limited.
     *
     * @param displayName the name as sent by the client
     * @return the cleaned name, or {@code null} if nothing is left of it
     */
    private static String cleanName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String cleaned = displayName.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Converts the time left until a deadline to whole seconds, rounding up.
     *
     * @param deadline the deadline, in clock milliseconds
     * @return the seconds left, at least 0
     */
    private int secondsUntil(long deadline) {
        long remaining = Math.max(0, deadline - clock.getAsLong());
        return (int) ((remaining + 999) / 1000);
    }
}
