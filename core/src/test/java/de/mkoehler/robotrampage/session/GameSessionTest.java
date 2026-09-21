package de.mkoehler.robotrampage.session;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.net.NetworkConstants;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerPaused;
import de.mkoehler.robotrampage.net.messages.TimerUpdate;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the session state machine (design.md 2.13, 3.5) without any network: lobby rules, the programming phase
 * with its timers, disconnects and reconnects, and how the game ends. Time is a fake clock advanced by hand.
 *
 * @author Mario Koehler
 */
class GameSessionTest {

    private static final long CAP = 90_000;
    private static final long LAST_PLAYER = 30_000;
    private static final long GRACE = 600_000;
    private static final long PAUSE = 2_000;
    private static final long GAME_OVER_PAUSE = 15_000;

    /**
     * A 5x30 board with four start squares on the bottom row and one flag at the far end. It is too long for any robot
     * to reach the flag within a single turn (at most 15 squares in five registers), so no test game is won by accident.
     */
    private static final String BOARD_JSON = """
        {"formatVersion": 1, "id": "t", "name": "Test Board", "width": 5, "height": 30,
         "flags": [{"x": 4, "y": 29}],
         "startSquares": [{"x": 0, "y": 0, "facing": "NORTH"}, {"x": 1, "y": 0, "facing": "NORTH"},
                          {"x": 2, "y": 0, "facing": "NORTH"}, {"x": 3, "y": 0, "facing": "NORTH"}]}
        """;

    /**
     * One message that went into the outbox, and who it was for.
     *
     * @param seat    the receiving seat, or -1 for a broadcast
     * @param message the message
     */
    private record Sent(int seat, Object message) {
    }

    /**
     * An outbox that just remembers everything.
     */
    private static final class RecordingOutbox implements Outbox {

        final List<Sent> log = new ArrayList<>();

        /**
         * Records a private message.
         *
         * @param seat    the receiving seat
         * @param message the message
         */
        @Override
        public void send(int seat, Object message) {
            log.add(new Sent(seat, message));
        }

        /**
         * Records a broadcast.
         *
         * @param message the message
         */
        @Override
        public void broadcast(Object message) {
            log.add(new Sent(-1, message));
        }

        /**
         * Returns the messages of one type a player would have received, in order: those sent to them and broadcasts.
         *
         * @param seat the player
         * @param type the message class
         * @param <T>  the message type
         * @return the messages
         */
        <T> List<T> receivedBy(int seat, Class<T> type) {
            return log.stream().filter(sent -> (sent.seat() == seat || sent.seat() == -1) && type.isInstance(sent.message()))
                .map(sent -> type.cast(sent.message())).toList();
        }

        /**
         * Returns the last message of a type a player received.
         *
         * @param seat the player
         * @param type the message class
         * @param <T>  the message type
         * @return the last such message
         */
        <T> T lastReceivedBy(int seat, Class<T> type) {
            List<T> all = receivedBy(seat, type);
            assertFalse(all.isEmpty(), "seat " + seat + " never received a " + type.getSimpleName());
            return all.get(all.size() - 1);
        }

        /**
         * Counts the broadcasts of a type.
         *
         * @param type the message class
         * @return how many were broadcast
         */
        long broadcasts(Class<?> type) {
            return log.stream().filter(sent -> sent.seat() == -1 && type.isInstance(sent.message())).count();
        }
    }

    private AtomicLong now;
    private RecordingOutbox outbox;
    private GameSession session;
    private final List<String> tokens = new ArrayList<>();

    /**
     * Creates a fresh session in the lobby, with a clock at 1,000 ms.
     */
    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000);
        outbox = new RecordingOutbox();
        session = newSession(42L);
    }

    /**
     * Builds a session on the test board.
     *
     * @param seed the game seed
     * @return the session
     */
    private GameSession newSession(long seed) {
        LoadedBoard board = BoardLoader.parse(BOARD_JSON);
        SessionConfig config = new SessionConfig(CAP, LAST_PLAYER, GRACE, PAUSE, 0, PAUSE, GAME_OVER_PAUSE, 2);
        return new GameSession(board, config, seed, now::get, outbox);
    }

    /**
     * Joins a new player and attaches them, as the server does after answering the handshake.
     *
     * @param name the display name
     * @return the seat
     */
    private int join(String name) {
        JoinResult result = session.join(name, null);
        assertTrue(result.accepted(), result.message());
        tokens.add(result.sessionToken());
        session.attach(result.seat());
        return result.seat();
    }

    /**
     * Joins the given number of players, everyone but the host ready, and starts the game.
     *
     * @param players how many players
     */
    private void startWith(int players) {
        for (int i = 0; i < players; i++) {
            join("Player" + i);
        }
        for (int seat = 1; seat < players; seat++) {
            session.setReady(seat, true);
        }
        session.startGame(0);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
    }

    /**
     * Builds a valid submission from a player's current hand.
     *
     * @param seat the player
     * @return a program using the first cards of their hand
     */
    private SubmitProgram programFor(int seat) {
        HandDealt hand = outbox.lastReceivedBy(seat, HandDealt.class);
        int unlocked = 5 - hand.lockedCards().size();
        List<Integer> priorities = new ArrayList<>();
        for (int i = 0; i < unlocked; i++) {
            priorities.add(hand.hand().get(i).priority());
        }
        return new SubmitProgram(hand.turn(), priorities, false, null);
    }

    /**
     * Makes a player submit a valid program.
     *
     * @param seat the player
     */
    private void submitFor(int seat) {
        session.submitProgram(seat, programFor(seat));
    }

    /**
     * Lets time pass and ticks the session.
     *
     * @param millis how many milliseconds to advance
     */
    private void advance(long millis) {
        now.addAndGet(millis);
        session.tick();
    }

    // ---------------------------------------------------------------------------------------------- lobby

    /**
     * Players get the lowest free seats and distinct tokens, the first to join is the host, and everybody receives
     * the lobby state when someone is attached.
     */
    @Test
    void joiningAssignsSeatsTokensAndAHost() {
        int first = join("Ann");
        int second = join("Bo");

        assertEquals(0, first);
        assertEquals(1, second);
        assertNotEquals(tokens.get(0), tokens.get(1));
        LobbyState lobby = outbox.lastReceivedBy(0, LobbyState.class);
        assertEquals(2, lobby.players().size());
        assertTrue(lobby.players().get(0).host());
        assertFalse(lobby.players().get(1).host());
        assertEquals("Test Board", lobby.boardName());
        assertEquals(4, lobby.maxPlayers());
    }

    /**
     * The lobby tells clients everything they show about the game and everything they need to know whether the host may
     * start: the board's size and flags, the lives, the programming time and the number of players required.
     */
    @Test
    void theLobbyStateCarriesTheFactsOfTheGame() {
        join("Ann");

        LobbyState lobby = outbox.lastReceivedBy(0, LobbyState.class);

        assertEquals(2, lobby.minPlayers());
        assertEquals(5, lobby.boardWidth());
        assertEquals(30, lobby.boardHeight());
        assertEquals(1, lobby.flagCount());
        assertEquals(Robot.STARTING_LIVES, lobby.lives());
        assertEquals(CAP / 1000, lobby.programmingSeconds());
    }

    /**
     * A leaving player frees their seat for the next one and the host passes on.
     */
    @Test
    void leavingFreesTheSeatAndPassesOnTheHost() {
        join("Ann");
        join("Bo");

        session.disconnect(0);

        LobbyState lobby = outbox.lastReceivedBy(1, LobbyState.class);
        assertEquals(1, lobby.players().size());
        assertTrue(lobby.players().get(0).host());
        assertEquals(0, join("Cy"));
    }

    /**
     * Names are cleaned up and an empty name is refused.
     */
    @Test
    void namesAreCleanedAndMustNotBeEmpty() {
        assertFalse(session.join("  \n\t ", null).accepted());
        assertFalse(session.join(null, null).accepted());
        JoinResult result = session.join("  Bo\u0007b " + "x".repeat(40), null);
        assertTrue(result.accepted());
        session.attach(result.seat());

        String name = outbox.lastReceivedBy(result.seat(), LobbyState.class).players().get(0).name();
        assertTrue(name.startsWith("Bob"), name);
        assertTrue(name.length() <= NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
    }

    /**
     * The lobby is full when every start square has a player, and nobody can join a running game.
     */
    @Test
    void fullLobbyAndRunningGameTurnNewPlayersAway() {
        for (int i = 0; i < 4; i++) {
            join("P" + i);
        }
        assertFalse(session.join("Extra", null).accepted());

        session.setReady(1, true);
        session.setReady(2, true);
        session.setReady(3, true);
        session.startGame(0);

        JoinResult late = session.join("Late", null);
        assertFalse(late.accepted());
        assertTrue(late.message().contains("in progress"));
    }

    /**
     * Only the host can start, with enough players, everybody else ready; otherwise the request is refused with a
     * reason.
     */
    @Test
    void startingNeedsTheHostEnoughPlayersAndReadiness() {
        join("Ann");
        session.startGame(0);
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("At least 2"));

        join("Bo");
        session.startGame(1);
        assertTrue(outbox.lastReceivedBy(1, RequestRejected.class).reason().contains("host"));

        session.startGame(0);
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("ready"));
        assertEquals(GameSession.Phase.LOBBY, session.phase());

        session.setReady(1, true);
        session.startGame(0);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
    }

    // ------------------------------------------------------------------------------------- the first turn

    /**
     * Starting the game sends every player their own setup and their own hand, and nothing hidden is ever broadcast.
     */
    @Test
    void startingSendsEachPlayerTheirOwnHandAndBroadcastsNoHiddenInformation() {
        startWith(3);

        for (int seat = 0; seat < 3; seat++) {
            assertEquals(seat, outbox.lastReceivedBy(seat, GameStarted.class).yourRobotId());
            HandDealt hand = outbox.lastReceivedBy(seat, HandDealt.class);
            assertEquals(1, hand.turn());
            assertEquals(9, hand.hand().size());
            assertTrue(hand.lockedCards().isEmpty());
            assertFalse(hand.canChooseRespawnFacing());
        }
        assertNotEquals(outbox.lastReceivedBy(0, HandDealt.class).hand(), outbox.lastReceivedBy(1, HandDealt.class).hand());
        assertEquals(0, outbox.broadcasts(HandDealt.class));
        assertEquals(0, outbox.broadcasts(SubmitProgram.class));
        TurnStarted turnStarted = outbox.lastReceivedBy(0, TurnStarted.class);
        assertEquals(List.of(0, 1, 2), turnStarted.awaitedRobotIds());
        assertEquals(90, turnStarted.programmingSeconds());
    }

    /**
     * A valid program is confirmed to everybody without revealing it, and once everybody has confirmed the turn is
     * resolved and its results are broadcast.
     */
    @Test
    void confirmingAndResolvingATurn() {
        startWith(2);

        submitFor(0);
        assertEquals(1, outbox.broadcasts(PlayerConfirmed.class));
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        submitFor(1);

        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(2, outbox.broadcasts(PlayerConfirmed.class));
        assertEquals(1, outbox.broadcasts(TurnResolved.class));
        TurnResolved resolved = outbox.lastReceivedBy(0, TurnResolved.class);
        assertEquals(1, resolved.turn());
        assertFalse(resolved.events().isEmpty());
        StateSnapshot snapshot = outbox.lastReceivedBy(0, StateSnapshot.class);
        assertEquals(2, snapshot.robots().size());
        assertFalse(snapshot.over());
        assertEquals(84, Programs.cardsInPlay(session.gameState()));
    }

    /**
     * After the pause between turns the next turn is dealt with a fresh hand.
     */
    @Test
    void theNextTurnStartsAfterThePause() {
        startWith(2);
        submitFor(0);
        submitFor(1);

        advance(PAUSE - 1);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        advance(1);

        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        assertEquals(2, session.turn());
        assertEquals(2, outbox.lastReceivedBy(0, HandDealt.class).turn());
    }

    /**
     * Invalid programs are refused with a reason and change nothing: wrong number of cards, a card not in the hand
     * (including a card from another player's hand), a card used twice, a submission for another turn.
     */
    @Test
    void invalidProgramsAreRefusedAndChangeNothing() {
        startWith(2);
        HandDealt mine = outbox.lastReceivedBy(0, HandDealt.class);
        HandDealt theirs = outbox.lastReceivedBy(1, HandDealt.class);
        List<Integer> five = mine.hand().subList(0, 5).stream().map(Card::priority).toList();

        session.submitProgram(0, new SubmitProgram(1, five.subList(0, 4), false, null));
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().length() > 0);

        List<Integer> stolen = new ArrayList<>(five);
        stolen.set(0, theirs.hand().get(0).priority());
        session.submitProgram(0, new SubmitProgram(1, stolen, false, null));
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("not in your hand"));

        List<Integer> twice = new ArrayList<>(five);
        twice.set(1, five.get(0));
        session.submitProgram(0, new SubmitProgram(1, twice, false, null));

        session.submitProgram(0, new SubmitProgram(7, five, false, null));

        assertEquals(4, outbox.receivedBy(0, RequestRejected.class).size());
        assertEquals(0, outbox.broadcasts(PlayerConfirmed.class));
        session.submitProgram(0, new SubmitProgram(1, five, false, null));
        assertEquals(1, outbox.broadcasts(PlayerConfirmed.class));
    }

    /**
     * A confirmed program is final: submitting again is refused.
     */
    @Test
    void aConfirmedProgramCannotBeChanged() {
        startWith(2);
        submitFor(0);

        session.submitProgram(0, programFor(0));

        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("locked in"));
        assertEquals(1, outbox.broadcasts(PlayerConfirmed.class));
    }

    // -------------------------------------------------------------------------------------------- timers

    /**
     * When only one player is left to confirm, everybody is told the squeeze has begun, and the last player is
     * filled in at random once their 30 seconds are up.
     */
    @Test
    void theLastPlayerIsSqueezedAndThenFilledInAtRandom() {
        startWith(3);
        submitFor(0);
        assertEquals(0, outbox.broadcasts(TimerUpdate.class));
        submitFor(1);

        assertEquals(1, outbox.broadcasts(TimerUpdate.class));
        assertEquals(30, outbox.lastReceivedBy(2, TimerUpdate.class).secondsRemaining());

        advance(LAST_PLAYER - 1);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        advance(1);

        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(3, outbox.broadcasts(PlayerConfirmed.class));
        assertEquals(84, Programs.cardsInPlay(session.gameState()));
    }

    /**
     * If nobody submits, everybody is filled in at random when the hard cap is reached, and the turn is resolved.
     */
    @Test
    void theHardCapFillsInEveryoneWhoIsStillThinking() {
        startWith(3);

        advance(CAP - 1);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        advance(1);

        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(3, outbox.broadcasts(PlayerConfirmed.class));
        assertEquals(1, outbox.broadcasts(TurnResolved.class));
    }

    /**
     * The squeeze never lets the last player have more than the hard cap allows: if less than 30 seconds of the cap
     * remain, the cap still wins.
     */
    @Test
    void theSqueezeNeverExtendsTheHardCap() {
        startWith(2);
        advance(CAP - 10_000);

        submitFor(0);

        advance(10_000);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * Random fills come from the game seed, so two identical games with identical inputs resolve identically.
     */
    @Test
    void randomFillsAreReproducible() {
        startWith(3);
        advance(CAP);
        List<Object> first = outbox.receivedBy(0, TurnResolved.class).stream().map(m -> (Object) m).toList();

        RecordingOutbox otherOutbox = new RecordingOutbox();
        now.set(1_000);
        GameSession other = new GameSession(BoardLoader.parse(BOARD_JSON),
            new SessionConfig(CAP, LAST_PLAYER, GRACE, PAUSE, 0, PAUSE, GAME_OVER_PAUSE, 2), 42L, now::get, otherOutbox);
        for (int i = 0; i < 3; i++) {
            other.attach(other.join("Player" + i, null).seat());
        }
        other.setReady(1, true);
        other.setReady(2, true);
        other.startGame(0);
        now.addAndGet(CAP);
        other.tick();

        assertEquals(first, otherOutbox.receivedBy(0, TurnResolved.class).stream().map(m -> (Object) m).toList());
    }

    /**
     * A stopped timer neither expires nor runs down: however long the host waits, nobody is filled in, and after the
     * restart exactly the time that was left remains.
     */
    @Test
    void aPausedTimerStandsStillAndResumesWithTheTimeLeft() {
        startWith(3);
        advance(CAP - 10_000);

        session.setTimerPaused(0, true);

        assertEquals(new TimerPaused(true, 10), outbox.lastReceivedBy(2, TimerPaused.class));
        advance(5 * CAP);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        assertEquals(0, outbox.broadcasts(PlayerConfirmed.class));

        session.setTimerPaused(0, false);

        assertEquals(new TimerPaused(false, 10), outbox.lastReceivedBy(2, TimerPaused.class));
        advance(9_999);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        advance(1);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(3, outbox.broadcasts(PlayerConfirmed.class));
    }

    /**
     * Only the host can stop the timer, and only while players are programming; everybody else is told why not and the
     * timer keeps running.
     */
    @Test
    void onlyTheHostCanPauseAndOnlyWhileProgramming() {
        join("Ann");
        join("Bo");
        session.setTimerPaused(0, true);
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("programming"));
        session.setReady(1, true);
        session.startGame(0);

        session.setTimerPaused(1, true);

        assertTrue(outbox.lastReceivedBy(1, RequestRejected.class).reason().contains("host"));
        assertEquals(0, outbox.broadcasts(TimerPaused.class));
        advance(CAP);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        session.setTimerPaused(0, true);
        assertTrue(outbox.lastReceivedBy(0, RequestRejected.class).reason().contains("programming"));
        assertEquals(0, outbox.broadcasts(TimerPaused.class));
    }

    /**
     * Players can go on confirming while the timer is stopped. The squeeze on the last player starts from the moment of
     * the pause, so the last player has their full 30 seconds after the restart.
     */
    @Test
    void theSqueezeStartedWhilePausedRunsFromTheRestart() {
        startWith(3);
        advance(1_000);
        session.setTimerPaused(0, true);
        advance(CAP);

        submitFor(0);
        submitFor(1);

        assertEquals(new TimerUpdate(30), outbox.lastReceivedBy(2, TimerUpdate.class));
        advance(CAP);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        session.setTimerPaused(0, false);
        advance(LAST_PLAYER - 1);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        advance(1);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * A pause does not outlive its turn: when the last player confirms during it, the turn is resolved, everybody is told
     * the timer runs again, and the next turn begins with a running timer.
     */
    @Test
    void aPauseEndsWhenTheTurnIsResolved() {
        startWith(2);
        session.setTimerPaused(0, true);

        submitFor(0);
        submitFor(1);

        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertFalse(outbox.lastReceivedBy(0, TimerPaused.class).paused());
        advance(PAUSE);
        assertEquals(2, session.turn());
        advance(CAP);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(2, outbox.receivedBy(0, TurnResolved.class).size());
    }

    /**
     * The reconnect grace of a disconnected player does not run down while the timer is stopped.
     */
    @Test
    void thePauseDoesNotUseUpTheReconnectGrace() {
        startWith(3);
        session.disconnect(2);
        session.setTimerPaused(0, true);
        advance(2 * GRACE);
        assertEquals(0, outbox.broadcasts(PlayerLeft.class));

        session.setTimerPaused(0, false);

        advance(GRACE - 1);
        assertEquals(0, outbox.broadcasts(PlayerLeft.class));
        advance(1);
        assertEquals(1, outbox.broadcasts(PlayerLeft.class));
    }

    /**
     * When the host drops while the timer is stopped, the timer runs again, so the game cannot be stuck waiting for a host
     * who is gone.
     */
    @Test
    void aPauseEndsWhenTheHostDisconnects() {
        startWith(3);
        session.setTimerPaused(0, true);
        advance(30_000);

        session.disconnect(0);

        assertEquals(new TimerPaused(false, 90), outbox.lastReceivedBy(1, TimerPaused.class));
        advance(CAP - 1);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        advance(1);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * A player who comes back while the timer is stopped is told that it is.
     */
    @Test
    void aReturningPlayerIsToldThatTheTimerIsPaused() {
        startWith(2);
        advance(20_000);
        session.disconnect(1); // leaves the host as the last player, so the squeeze leaves 30 s
        session.setTimerPaused(0, true);
        advance(60_000);
        int before = outbox.log.size();

        session.join("ignored", tokens.get(1));
        session.attach(1);

        List<Object> resent = outbox.log.subList(before, outbox.log.size()).stream()
            .filter(sent -> sent.seat() == 1).map(Sent::message).toList();
        assertTrue(resent.contains(new TimerPaused(true, 30)));
        assertTrue(resent.stream().anyMatch(message -> message instanceof TurnStarted started && started.programmingSeconds() == 30));
    }

    // ------------------------------------------------------------------------------ disconnect / reconnect

    /**
     * A player who disconnects while owing a program is filled in at once; everybody is told, and the turn does not wait
     * for them.
     */
    @Test
    void disconnectingWhileProgrammingFillsInAtOnce() {
        startWith(2);

        session.disconnect(1);

        assertEquals(1, outbox.broadcasts(PlayerConnection.class));
        assertFalse(outbox.receivedBy(0, PlayerConnection.class).get(0).connected());
        assertEquals(1, outbox.broadcasts(PlayerConfirmed.class));
        submitFor(0);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * A disconnected player's robot keeps playing random programs in the following turns.
     */
    @Test
    void aDisconnectedPlayerIsFilledInEveryTurn() {
        startWith(2);
        session.disconnect(1);
        submitFor(0);

        advance(PAUSE);

        assertEquals(2, session.turn());
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        assertEquals(3, outbox.broadcasts(PlayerConfirmed.class));
        submitFor(0);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * A player who comes back with their token in time gets their seat and is brought fully up to date, including
     * their own unspent hand; the others are told they are back.
     */
    @Test
    void reconnectingWithTheTokenResyncsThePlayer() {
        startWith(3);
        submitFor(1);
        session.disconnect(0);
        assertEquals(2, outbox.broadcasts(PlayerConfirmed.class));
        int before = outbox.log.size();

        JoinResult back = session.join("ignored", tokens.get(0));
        assertTrue(back.accepted());
        assertEquals(0, back.seat());
        assertEquals(tokens.get(0), back.sessionToken());
        assertEquals(before, outbox.log.size());
        session.attach(0);

        List<Object> resent = outbox.log.subList(before, outbox.log.size()).stream()
            .filter(sent -> sent.seat() == 0).map(Sent::message).toList();
        assertTrue(resent.stream().anyMatch(GameStarted.class::isInstance));
        assertTrue(resent.stream().anyMatch(StateSnapshot.class::isInstance));
        assertTrue(resent.stream().anyMatch(TurnStarted.class::isInstance));
        assertTrue(resent.stream().anyMatch(PlayerConfirmed.class::isInstance));
        assertEquals(1, outbox.log.subList(before, outbox.log.size()).stream()
            .filter(sent -> sent.seat() == -1 && sent.message() instanceof PlayerConnection connection && connection.connected())
            .count());
    }

    /**
     * A player who disconnected before confirming was filled in, so on returning they see that their program is
     * already locked in and are not asked again.
     */
    @Test
    void aReturningPlayerWhoWasFilledInIsNotAskedAgain() {
        startWith(3);
        session.disconnect(2);
        session.join("x", tokens.get(2));
        session.attach(2);

        HandDealt hand = outbox.lastReceivedBy(2, HandDealt.class);
        assertTrue(hand.hand().isEmpty());
        session.submitProgram(2, new SubmitProgram(1, List.of(), false, null));
        assertTrue(outbox.lastReceivedBy(2, RequestRejected.class).reason().contains("locked in"));
    }

    /**
     * A token opens nothing for a stranger: an unknown token can only join like anybody else, and a running game turns a
     * newcomer away.
     */
    @Test
    void unknownTokensGetNoSeat() {
        startWith(2);

        assertFalse(session.join("Mallory", "not-a-real-token").accepted());
    }

    // -------------------------------------------------------------------------------------- grace, game over

    /**
     * After the grace period a disconnected player is removed: their robot is eliminated, everybody is told, and their
     * token no longer works. With two players, the other one wins.
     */
    @Test
    void graceExpiryRemovesThePlayerAndEndsATwoPlayerGame() {
        startWith(2);
        session.disconnect(1);

        advance(GRACE - 1);
        assertEquals(0, outbox.broadcasts(PlayerLeft.class));
        advance(1);

        assertEquals(1, outbox.broadcasts(PlayerLeft.class));
        assertEquals(RobotStatus.ELIMINATED, session.gameState().robot(1).status());
        assertEquals(GameSession.Phase.GAME_OVER, session.phase());
        GameOver over = outbox.lastReceivedBy(0, GameOver.class);
        assertEquals(0, over.winnerRobotId());
        assertFalse(session.join("x", tokens.get(1)).accepted());
    }

    /**
     * With three players, one leaving does not end the game and the turn carries on with the rest. Every one of the 84 cards
     * stays accounted for: those in the deck piles and registers plus the hands just dealt for the next turn.
     */
    @Test
    void oneLeavingOfThreeDoesNotEndTheGameAndKeepsEveryCard() {
        startWith(3);
        submitFor(0);
        submitFor(1);
        session.disconnect(2);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());

        advance(GRACE);

        assertEquals(1, outbox.broadcasts(PlayerLeft.class));
        assertNotEquals(GameSession.Phase.GAME_OVER, session.phase());
        assertEquals(2, session.turn());
        int inHands = outbox.lastReceivedBy(0, HandDealt.class).hand().size() + outbox.lastReceivedBy(1, HandDealt.class).hand().size();
        assertEquals(84, Programs.cardsInPlay(session.gameState()) + inHands);
    }

    /**
     * A player who is removed in the middle of a programming phase takes nothing with them: their locked-in cards go back to
     * the discard pile, the others' turn goes on and every card is accounted for when it is resolved.
     */
    @Test
    void aPlayerRemovedWhileProgrammingKeepsEveryCard() {
        outbox = new RecordingOutbox();
        session = new GameSession(BoardLoader.parse(BOARD_JSON),
            new SessionConfig(CAP, LAST_PLAYER, 60_000, PAUSE, 0, PAUSE, GAME_OVER_PAUSE, 2), 42L, now::get, outbox);
        startWith(3);
        session.disconnect(2);
        submitFor(0);
        submitFor(1);
        advance(PAUSE);
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());

        advance(60_000);

        assertEquals(1, outbox.broadcasts(PlayerLeft.class));
        assertEquals(GameSession.Phase.PROGRAMMING, session.phase());
        submitFor(0);
        submitFor(1);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
        assertEquals(84, Programs.cardsInPlay(session.gameState()));
    }

    /**
     * After the game-over pause the session returns to the lobby: players who dropped are forgotten, the rest keep their
     * seats but must get ready again, and a new game can start.
     */
    @Test
    void afterGameOverTheSessionReturnsToTheLobby() {
        startWith(3);
        session.disconnect(2);
        advance(GRACE);
        session.disconnect(1);
        advance(GRACE);
        assertEquals(GameSession.Phase.GAME_OVER, session.phase());

        advance(GAME_OVER_PAUSE);

        assertEquals(GameSession.Phase.LOBBY, session.phase());
        LobbyState lobby = outbox.lastReceivedBy(0, LobbyState.class);
        assertEquals(1, lobby.players().size());
        assertFalse(lobby.players().get(0).ready());
        assertNotNull(session.join("Newcomer", null).sessionToken());
    }

    // ------------------------------------------------------------------------------------ rules interplay

    /**
     * A robot that re-enters this turn lets its player pick the facing with their program, and only then: a player whose
     * robot did not re-enter cannot use the option.
     */
    @Test
    void aRespawnedRobotsPlayerMayChooseTheFacing() {
        startWith(2);
        submitFor(0);
        submitFor(1);
        Robot victim = session.gameState().robot(1);
        victim.setStatus(RobotStatus.DESTROYED);
        victim.setPosition(null);
        victim.setLives(2);

        advance(PAUSE);

        assertTrue(outbox.lastReceivedBy(1, HandDealt.class).canChooseRespawnFacing());
        SubmitProgram program = programFor(1);
        session.submitProgram(1, new SubmitProgram(program.turn(), program.cardPriorities(), false, Direction.SOUTH));
        assertEquals(Direction.SOUTH, session.gameState().robot(1).facing());

        // Robot 0 may itself have fallen off the open board and re-entered by chance; only if it did not is the option refused.
        boolean respawned = outbox.lastReceivedBy(0, TurnStarted.class).respawnEvents().stream()
            .anyMatch(entry -> entry.event() instanceof de.mkoehler.robotrampage.rules.GameEvent.RobotRespawned respawn
                && respawn.robotId() == 0);
        assertEquals(respawned, outbox.lastReceivedBy(0, HandDealt.class).canChooseRespawnFacing());
        if (!respawned) {
            Direction before = session.gameState().robot(0).facing();
            SubmitProgram other = programFor(0);
            session.submitProgram(0, new SubmitProgram(other.turn(), other.cardPriorities(), false, Direction.WEST));
            assertEquals(before, session.gameState().robot(0).facing());
        }
    }

    /**
     * A powered-down robot is dealt nothing and is not waited for, but its player may announce staying down.
     */
    @Test
    void aPoweredDownPlayerIsNotWaitedForButMayStayDown() {
        startWith(2);
        submitFor(0);
        submitFor(1);
        session.gameState().robot(1).setPoweredDown(true);
        advance(PAUSE);

        HandDealt hand = outbox.lastReceivedBy(1, HandDealt.class);
        assertTrue(hand.poweredDown());
        assertTrue(hand.hand().isEmpty());
        assertEquals(List.of(0), outbox.lastReceivedBy(0, TurnStarted.class).awaitedRobotIds());

        session.submitProgram(1, new SubmitProgram(hand.turn(), List.of(), true, null));
        assertTrue(session.gameState().robot(1).isPowerDownAnnounced());
        submitFor(0);
        assertEquals(GameSession.Phase.RESOLVING, session.phase());
    }

    /**
     * Many turns with timeouts in between never lose a card and keep the phases consistent.
     */
    @Test
    void manyTurnsKeepEveryCard() {
        startWith(4);
        for (int turn = 1; turn <= 12 && session.phase() != GameSession.Phase.GAME_OVER; turn++) {
            if (turn % 3 == 0) {
                advance(CAP);
            } else {
                for (int seat = 0; seat < 4; seat++) {
                    if (session.gameState().robot(seat).isActive() && !session.gameState().robot(seat).isPoweredDown()
                        && session.phase() == GameSession.Phase.PROGRAMMING) {
                        submitFor(seat);
                    }
                }
            }
            assertEquals(84, Programs.cardsInPlay(session.gameState()), "after turn " + turn);
            advance(PAUSE);
        }
    }
}
