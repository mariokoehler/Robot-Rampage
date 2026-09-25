package de.mkoehler.robotrampage.session;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.bot.BotBrain;
import de.mkoehler.robotrampage.bot.BotDecision;
import de.mkoehler.robotrampage.bot.BotNames;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.net.NetworkConstants;
import de.mkoehler.robotrampage.net.messages.BoardChoice;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.ProgramRevealed;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RespawnFacingChosen;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerPaused;
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

    private final List<LoadedBoard> boards;
    private final List<String> boardJsons;
    private int selectedBoard;
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
    private long programmingMillis;
    private long programmingDeadline;
    private boolean timerPaused;
    private long pausedAt;
    private boolean squeezeActive;
    private long nextTurnAt;
    private int fillCounter;
    private final Random botRandom;

    /**
     * Creates a session in the lobby phase.
     *
     * @param board  the only board, which will be played
     * @param config the timings and limits
     * @param seed   the seed of the game's randomness: the deck's shuffles and the random fills of programs that
     *               time out
     * @param clock  the source of time, in milliseconds; only differences matter
     * @param outbox where outgoing messages go
     */
    public GameSession(LoadedBoard board, SessionConfig config, long seed, LongSupplier clock, Outbox outbox) {
        this(List.of(board), config, seed, clock, outbox);
    }

    /**
     * Creates a session in the lobby phase that offers several boards for the host to choose from; the first one is
     * chosen until the host picks another.
     *
     * @param boards the boards, in the order the lobby lists them; at least one, each with at least
     *               {@link SessionConfig#minPlayers()} start squares, and no two with the same id
     * @param config the timings and limits
     * @param seed   the seed of the game's randomness: the deck's shuffles and the random fills of programs that
     *               time out
     * @param clock  the source of time, in milliseconds; only differences matter
     * @param outbox where outgoing messages go
     * @throws IllegalArgumentException if there is no board, two share an id, or one has too few start squares to ever
     *                                  be started
     */
    public GameSession(List<LoadedBoard> boards, SessionConfig config, long seed, LongSupplier clock, Outbox outbox) {
        if (boards.isEmpty()) {
            throw new IllegalArgumentException("A session needs at least one board");
        }
        if (boards.stream().map(each -> each.definition().id()).distinct().count() != boards.size()) {
            throw new IllegalArgumentException("Two boards share an id");
        }
        for (LoadedBoard each : boards) {
            if (each.board().startSquares().size() < config.minPlayers()) {
                throw new IllegalArgumentException("The board " + each.definition().id() + " has "
                    + each.board().startSquares().size() + " start squares, but a game needs " + config.minPlayers()
                    + " players");
            }
        }
        this.boards = List.copyOf(boards);
        this.boardJsons = boards.stream().map(each -> BoardLoader.toJson(each.definition())).toList();
        this.config = config;
        this.programmingMillis = config.programmingMillis();
        this.seed = seed;
        this.botRandom = new Random(seed ^ 0x5DEECE66DL);
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
     * Returns how long a disconnected player may take to come back before their robot is removed, for a client to show and
     * to count its own reconnect attempts against. The same for every player, so it only needs to travel once, at the
     * handshake.
     *
     * @return the reconnect grace period in whole seconds, rounded up
     */
    public int reconnectGraceSeconds() {
        return (int) ((config.reconnectGraceMillis() + 999) / 1000);
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
        if (nameTaken(name)) {
            return JoinResult.rejected("That name is already taken.");
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
     * for the reconnect grace period; if they still owe a program it is filled in at random at once. If the player is the
     * host and has the timer paused, it runs again, so nobody is left waiting for a host who is gone.
     *
     * @param seat the player's seat
     */
    public void disconnect(int seat) {
        SessionPlayer player = players.get(seat);
        if (player == null || !player.connected) {
            return;
        }
        player.connected = false;
        player.disconnectedAt = now();
        if (seat == hostSeat()) {
            resumeTimer();
        }
        if (phase == Phase.LOBBY) {
            players.remove(seat);
            forgetBotsIfNoHumanIsLeft();
            broadcastLobby();
            return;
        }
        if (phase == Phase.GAME_OVER && players.values().stream().noneMatch(other -> !other.bot && other.connected)) {
            resetToLobby();
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
     * Sets how long players get to program a turn, on the host's request. Only meaningful in the lobby: it takes effect
     * from the next turn dealt, which for a game not yet started is every turn. Refused for anybody but the host, outside
     * the lobby, or outside {@link NetworkConstants#MIN_PROGRAMMING_SECONDS} to
     * {@link NetworkConstants#MAX_PROGRAMMING_SECONDS} seconds.
     *
     * @param seat    the requesting player's seat
     * @param seconds the new programming time, in seconds
     */
    public void setProgrammingSeconds(int seat, int seconds) {
        if (!players.containsKey(seat)) {
            return;
        }
        if (phase != Phase.LOBBY) {
            reject(seat, "The programming time can only be changed in the lobby.");
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can change the programming time.");
            return;
        }
        if (seconds < NetworkConstants.MIN_PROGRAMMING_SECONDS || seconds > NetworkConstants.MAX_PROGRAMMING_SECONDS) {
            reject(seat, "The programming time must be between " + NetworkConstants.MIN_PROGRAMMING_SECONDS + " and "
                + NetworkConstants.MAX_PROGRAMMING_SECONDS + " seconds.");
            return;
        }
        programmingMillis = seconds * 1000L;
        broadcastLobby();
    }

    /**
     * Chooses the board the game will be played on, on the host's request. Refused for anybody but the host, outside
     * the lobby, for an id this session does not offer, or for a board with too few start squares for a seat already
     * taken (seats are start squares). Choosing another board clears everybody's ready flag, since they agreed to a
     * different one; choosing the board already chosen changes nothing. The id is only ever looked up among the boards
     * this session was given, never turned into a file or resource name.
     *
     * @param seat    the requesting player's seat
     * @param boardId the id of the board
     */
    public void selectBoard(int seat, String boardId) {
        if (!players.containsKey(seat)) {
            return;
        }
        if (phase != Phase.LOBBY) {
            reject(seat, "The board can only be changed in the lobby.");
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can choose the board.");
            return;
        }
        int index = -1;
        for (int i = 0; i < boards.size(); i++) {
            if (boards.get(i).definition().id().equals(boardId)) {
                index = i;
            }
        }
        if (index < 0) {
            reject(seat, "This server has no board called " + boardId + ".");
            return;
        }
        LoadedBoard wanted = boards.get(index);
        int seats = wanted.board().startSquares().size();
        int highestSeat = players.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        if (highestSeat >= seats) {
            reject(seat, wanted.definition().name() + " has only " + seats + " start squares, but seat "
                + (highestSeat + 1) + " is taken.");
            return;
        }
        if (index == selectedBoard) {
            return;
        }
        selectedBoard = index;
        players.values().forEach(player -> player.ready = player.bot);
        broadcastLobby();
    }

    /**
     * Seats a computer-controlled robot on the lowest free seat, on the host's request (design.md 2.14). It gets a name
     * from {@link BotNames} that nobody at the table uses, and is ready at once. Refused for anybody but the host, outside
     * the lobby, or when every seat is taken.
     *
     * @param seat the requesting player's seat
     */
    public void addBot(int seat) {
        if (!players.containsKey(seat)) {
            return;
        }
        if (phase != Phase.LOBBY) {
            reject(seat, "Bots can only be added in the lobby.");
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can add bots.");
            return;
        }
        int free = lowestFreeSeat();
        if (free < 0) {
            reject(seat, "Every seat is taken.");
            return;
        }
        Set<String> names = new HashSet<>();
        players.values().stream().filter(player -> !player.left).forEach(player -> names.add(player.name));
        players.put(free, new SessionPlayer(free, BotNames.pick(names, botRandom), UUID.randomUUID().toString(),
            nextJoinOrder++, true));
        broadcastLobby();
    }

    /**
     * Takes a computer-controlled robot off its seat, on the host's request. Refused for anybody but the host, outside the
     * lobby, or for a seat without a bot, so no human can ever be removed this way.
     *
     * @param seat    the requesting player's seat
     * @param botSeat the seat of the bot to remove
     */
    public void removeBot(int seat, int botSeat) {
        if (!players.containsKey(seat)) {
            return;
        }
        if (phase != Phase.LOBBY) {
            reject(seat, "Bots can only be removed in the lobby.");
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can remove bots.");
            return;
        }
        SessionPlayer bot = players.get(botSeat);
        if (bot == null || !bot.bot) {
            reject(seat, "There is no bot on seat " + (botSeat + 1) + ".");
            return;
        }
        players.remove(botSeat);
        broadcastLobby();
    }

    /**
     * Returns the board chosen for the game.
     *
     * @return the board
     */
    public LoadedBoard board() {
        return boards.get(selectedBoard);
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
            StartSquare start = board().board().startSquares().get(player.seat);
            robots.add(new Robot(player.seat, start.position(), start.facing()));
        }
        state = new GameState(board().board(), robots, Deck.standard(seed));
        turn = 0;
        fillCounter = 0;
        for (SessionPlayer player : players.values()) {
            outbox.send(player.seat, new GameStarted(boardJsons.get(selectedBoard), playerInfos(), player.seat));
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
            outbox.broadcast(new RespawnFacingChosen(robot.id(), program.respawnFacing()));
        }
        confirm(player);
        afterConfirmation();
    }

    /**
     * Turns a just-re-entered robot to the facing its player picked in the respawn dialog, sent the moment they press
     * "Go" rather than only bundled with their full program submission (which may follow much later, or never if they
     * are squeezed or disconnect) — so every client can show the choice right away (design.md 2.13). Equivalent to
     * setting the same facing on {@link SubmitProgram}, since nothing else can happen to the robot in between; a
     * program submitted afterwards may repeat the same facing there harmlessly.
     *
     * @param seat   the player's seat
     * @param facing the facing they picked
     */
    public void chooseRespawnFacing(int seat, Direction facing) {
        SessionPlayer player = players.get(seat);
        if (player == null || phase != Phase.PROGRAMMING || !player.awaiting || player.confirmed) {
            reject(seat, "You cannot pick a facing right now.");
            return;
        }
        if (!respawnedThisTurn.contains(seat)) {
            reject(seat, "Your robot did not re-enter this turn.");
            return;
        }
        Robot robot = state.robot(seat);
        robot.setFacing(facing);
        outbox.broadcast(new RespawnFacingChosen(robot.id(), facing));
    }

    // ------------------------------------------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------------------------------------------

    /**
     * Lets time pass: removes players whose reconnect grace period is over, fills in the programs of players who ran
     * out of time and resolves the turn, starts the next turn after the pause, and returns to the lobby after the
     * game-over pause. Must be called regularly. While the host has the timer paused, nothing happens.
     */
    public void tick() {
        if (timerPaused) {
            return;
        }
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
            case GAME_OVER, LOBBY -> {
            }
        }
    }

    /**
     * Stops or restarts the programming timer on the host's request, for example for a break or a discussion of the
     * rules. Only the host may do it, and only while players are programming. Players can go on programming and
     * confirming while the timer is stopped; the timer also restarts by itself when the turn is resolved, so a pause
     * never carries over into the next turn. Everybody is told.
     *
     * @param seat   the requesting player's seat
     * @param paused {@code true} to stop the timer, {@code false} to restart it
     */
    public void setTimerPaused(int seat, boolean paused) {
        if (!players.containsKey(seat)) {
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can pause the timer.");
            return;
        }
        if (phase != Phase.PROGRAMMING) {
            reject(seat, "The timer can only be paused while players are programming.");
            return;
        }
        if (paused && !timerPaused) {
            timerPaused = true;
            pausedAt = clock.getAsLong();
            outbox.broadcast(new TimerPaused(true, secondsUntil(programmingDeadline)));
        } else if (!paused) {
            resumeTimer();
        }
    }

    /**
     * Lets the programming timer run on after a pause: the time spent paused is added to the programming deadline and
     * to the time every disconnected player has been away, so neither runs out because of the pause. Does nothing when
     * the timer is not paused. Everybody is told.
     */
    private void resumeTimer() {
        if (!timerPaused) {
            return;
        }
        long paused = clock.getAsLong() - pausedAt;
        timerPaused = false;
        programmingDeadline += paused;
        for (SessionPlayer player : players.values()) {
            player.disconnectedAt += paused;
        }
        outbox.broadcast(new TimerPaused(false, secondsUntil(programmingDeadline)));
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
        programmingDeadline = now + programmingMillis;
        squeezeActive = false;
        phase = Phase.PROGRAMMING;

        outbox.broadcast(new TurnStarted(turn, respawnLog.entries(), awaited, (int) (programmingMillis / 1000)));
        for (SessionPlayer player : players.values()) {
            HandDealt hand = handFor(player);
            if (hand != null) {
                outbox.send(player.seat, hand);
            }
        }
        for (SessionPlayer player : players.values()) {
            if (player.awaiting && player.bot) {
                playBot(player);
            } else if (player.awaiting && !player.connected) {
                fillRandomly(player);
            }
        }
        afterConfirmation();
    }

    /**
     * Lets the computer program a bot's robot for this turn (design.md 2.14): it picks its re-entry facing if it may, then
     * locks its program in at once. Should the brain ever fail, the bot's cards are filled in at random instead, so a bot
     * can never hold up a turn or stop the server.
     *
     * @param player the bot, which owes a program
     */
    private void playBot(SessionPlayer player) {
        Robot robot = state.robot(player.seat);
        try {
            BotDecision decision = BotBrain.decide(state, player.seat, player.hand,
                respawnedThisTurn.contains(player.seat), botRandom);
            Programming.submit(state, player.seat, player.hand, decision.program(), decision.powerDown());
            if (decision.facing() != null) {
                robot.setFacing(decision.facing());
                outbox.broadcast(new RespawnFacingChosen(robot.id(), decision.facing()));
            }
            confirm(player);
        } catch (RuntimeException e) {
            fillRandomly(player);
        }
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
                if (!player.bot) {
                    awaited++;
                }
                if (!player.confirmed) {
                    pending++;
                }
            }
        }
        if (pending == 0) {
            resolveTurn();
        } else if (!squeezeActive && awaited >= 2 && pending == 1) {
            squeezeActive = true;
            long now = now();
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
        revealProgram(player);
    }

    /**
     * Tells a player what is actually in their own five registers, once their program is locked in for a reason that
     * left them not knowing: filled in at random, or already locked in when they (re)connected. A player who locked
     * their own program in while connected already knows what they placed and is never sent this.
     *
     * @param player the player whose program to reveal to them
     */
    private void revealProgram(SessionPlayer player) {
        Robot robot = state.robot(player.seat);
        List<Card> cards = new ArrayList<>();
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            cards.add(robot.register(index));
        }
        outbox.send(player.seat, new ProgramRevealed(turn, cards));
    }

    /**
     * Plays out the turn, tells everybody what happened, and either ends the game or schedules the next turn.
     */
    private void resolveTurn() {
        resumeTimer();
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
     * Ends the game and shows the results. The session stays in this phase until the host explicitly asks for the
     * lobby ({@link #returnToLobby(int)}); there is no timer of its own.
     */
    private void finishGame() {
        resumeTimer();
        phase = Phase.GAME_OVER;
        outbox.broadcast(new GameOver(state.winnerId(), robotStates()));
    }

    /**
     * Takes everybody back to the lobby on the host's request, once they are done looking at the results.
     *
     * @param seat the requesting player's seat
     */
    public void returnToLobby(int seat) {
        if (phase != Phase.GAME_OVER) {
            reject(seat, "There are no results to leave yet.");
            return;
        }
        if (seat != hostSeat()) {
            reject(seat, "Only the host can return everyone to the lobby.");
            return;
        }
        resetToLobby();
    }

    /**
     * Goes back to the lobby after a game: players who dropped out are forgotten, the rest keep their seats and
     * must get ready again.
     */
    private void resetToLobby() {
        players.values().removeIf(player -> !player.connected || player.left);
        forgetBotsIfNoHumanIsLeft();
        for (SessionPlayer player : players.values()) {
            player.ready = player.bot;
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
        if (players.values().stream().allMatch(other -> other.bot || other.left)) {
            resetToLobby();
        } else if (state.isOver()) {
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
        outbox.send(player.seat, new GameStarted(boardJsons.get(selectedBoard), playerInfos(), player.seat));
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
                if (timerPaused) {
                    outbox.send(player.seat, new TimerPaused(true, secondsUntil(programmingDeadline)));
                }
                // Excludes the reconnecting player themselves: their own confirmation, if any, is conveyed below by
                // the HandDealt/ProgramRevealed pair instead, which also says WHY it is already locked in. Sending it
                // here too would make the client wrongly infer a random fill (GameModel.apply's PlayerConfirmed
                // handling), even for a program the player had locked in themselves before disconnecting.
                for (SessionPlayer other : players.values()) {
                    if (other.seat != player.seat && other.awaiting && other.confirmed) {
                        outbox.send(player.seat, new PlayerConfirmed(other.seat));
                    }
                }
                // Kept as one pairing: a ProgramRevealed without the HandDealt that came with it would have nothing
                // to split the registers against (GameModel.revealProgram reads the damage tail from the most recent
                // HandDealt), so the two are only ever sent together.
                HandDealt hand = handFor(player);
                if (hand != null) {
                    outbox.send(player.seat, hand);
                    if (player.awaiting && player.confirmed) {
                        revealProgram(player);
                    }
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
        LoadedBoard board = board();
        List<BoardChoice> choices = boards.stream().map(each -> new BoardChoice(each.definition().id(),
            each.definition().name(), each.board().startSquares().size())).toList();
        outbox.broadcast(new LobbyState(playerInfos(), board.definition().name(), board.board().startSquares().size(),
            config.minPlayers(), board.board().width(), board.board().height(), board.board().flags().size(),
            Robot.STARTING_LIVES, (int) (programmingMillis / 1000), board.definition().id(),
            boardJsons.get(selectedBoard), choices));
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
            infos.add(new PlayerInfo(player.seat, player.name, player.ready, player.connected, player.seat == host,
                player.bot));
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
        return players.values().stream().filter(player -> !player.left && !player.bot)
            .min((a, b) -> Integer.compare(a.joinOrder, b.joinOrder)).map(player -> player.seat).orElse(-1);
    }

    /**
     * Takes every bot off the table once no human is seated any more, so bots never sit alone in a lobby nobody can start
     * or play on in a game nobody watches.
     */
    private void forgetBotsIfNoHumanIsLeft() {
        if (players.values().stream().allMatch(player -> player.bot)) {
            players.clear();
        }
    }

    /**
     * Finds the lowest seat number nobody is sitting on.
     *
     * @return the seat, or -1 if the board has no start square left
     */
    private int lowestFreeSeat() {
        for (int seat = 0; seat < board().board().startSquares().size(); seat++) {
            if (!players.containsKey(seat)) {
                return seat;
            }
        }
        return -1;
    }

    /**
     * Checks whether a name is already in use by a seated player, so two players are never shown under the same name.
     * Compared case-insensitively (so "Ann" and "ann" also collide), and only against players who have not left for
     * good, so a departed player's name can be reused.
     *
     * @param name the cleaned name a new player wants to join under
     * @return {@code true} if the name is taken
     */
    private boolean nameTaken(String name) {
        for (SessionPlayer player : players.values()) {
            if (!player.left && player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
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
     * Returns the time the session goes by: the clock, or while the timer is paused the instant it was paused at.
     *
     * @return the time in clock milliseconds
     */
    private long now() {
        return timerPaused ? pausedAt : clock.getAsLong();
    }

    /**
     * Converts the time left until a deadline to whole seconds, rounding up. While the timer is paused the time is the
     * instant it was paused at, so the answer does not change.
     *
     * @param deadline the deadline, in clock milliseconds
     * @return the seconds left, at least 0
     */
    private int secondsUntil(long deadline) {
        long remaining = Math.max(0, deadline - now());
        return (int) ((remaining + 999) / 1000);
    }
}
