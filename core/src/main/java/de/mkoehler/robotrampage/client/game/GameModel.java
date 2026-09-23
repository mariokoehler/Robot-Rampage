package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.ProgramRevealed;
import de.mkoehler.robotrampage.net.messages.RespawnFacingChosen;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.SetTimerPaused;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerPaused;
import de.mkoehler.robotrampage.net.messages.TimerUpdate;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.RobotStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The client's picture of a running game, kept up to date from the messages of the server. It holds the board, the players,
 * the public state of every robot, and everything about the turn in progress: whose program is awaited, who has locked in,
 * the time left, and this player's own hand and program. It has no graphics, so every rule about what the programming
 * screen shows can be tested.
 * <p>
 * The model never runs the rules. Robot states come from the server's snapshots, and the events of a turn are only kept
 * for whoever plays them back.
 *
 * @author Mario Koehler
 */
public final class GameModel {

    /**
     * Where this player is in the turn.
     */
    public enum Stage {
        /** The game has started but the first turn has not, or the last turn is over and the next has not begun. */
        WAITING,
        /** This player must program: the cards are being placed. */
        PROGRAMMING,
        /** This player has sent the program, or it was locked in already, and waits for the others. */
        SUBMITTED,
        /** This player takes no part in the programming of this turn: the robot is powered down or out of the game. */
        SITTING_OUT,
        /** The turn has been played out by the server and the client shows what happened. */
        RESOLVING,
        /** The game has ended. */
        OVER
    }

    /**
     * What the players panel says about a player.
     */
    public enum PlayerStatus {
        /** Nothing to say, for example while a turn is being played out. */
        NONE,
        /** The player is still choosing cards. */
        THINKING,
        /** The player has locked in a program. */
        CONFIRMED,
        /** The player's connection is down. */
        AWAY,
        /** The player's robot is powered down this turn. */
        POWERED_DOWN,
        /** The player is out of the game. */
        OUT
    }

    /**
     * One line of the players panel.
     *
     * @param seat   the seat, 0 to 7
     * @param name   the display name
     * @param you    whether this is the player looking at the screen
     * @param lives  the lives the robot has left
     * @param damage the damage the robot has
     * @param status what the panel says about the player
     */
    public record PlayerRow(int seat, String name, boolean you, int lives, int damage, PlayerStatus status) {
    }

    private final Board board;
    private final int mySeat;
    private final Map<Integer, PlayerInfo> players = new TreeMap<>();
    private final Map<Integer, RobotState> robots = new TreeMap<>();
    private final Set<Integer> awaited = new HashSet<>();
    private final Set<Integer> confirmed = new HashSet<>();
    private final Set<Integer> disconnected = new HashSet<>();
    private final Set<Integer> removed = new HashSet<>();
    private Stage stage = Stage.WAITING;
    private int turn;
    private int programmingSeconds;
    private float remainingSeconds;
    private boolean timerPaused;
    private ProgramDraft draft;
    private boolean poweredDownThisTurn;
    private boolean canChooseRespawnFacing;
    private boolean powerDownNext;
    private Direction respawnFacing;
    private boolean submissionPending;
    private boolean submittedByMe;
    private boolean filledAtRandom;
    private boolean programRevealed;
    private List<Card> lockedCardsThisTurn = List.of();
    private TurnResolved lastResolved;
    private List<RobotState> robotsBeforeResolution = List.of();
    private boolean resolutionOpen;
    private StateSnapshot heldSnapshot;
    private GameOver heldGameOver;
    private int winnerRobotId = GameEvent.NO_ROBOT;
    private final Map<Integer, Standings.FlagTouch> lastFlags = new HashMap<>();
    private final Map<Integer, Integer> eliminatedTurns = new HashMap<>();
    private boolean myEliminationSeen;
    private float lobbyCountdown = -1f;
    private int revision;

    /**
     * Creates the model of a game that has just started. Every robot stands on its start square with full lives until the
     * server says otherwise.
     *
     * @param started the message that started the game
     */
    public GameModel(GameStarted started) {
        this.board = BoardLoader.parse(started.boardJson()).board();
        this.mySeat = started.yourRobotId();
        for (PlayerInfo player : started.players()) {
            players.put(player.seat(), player);
            StartSquare start = board.startSquares().get(player.seat());
            robots.put(player.seat(), new RobotState(player.seat(), start.position(), start.facing(), 0,
                Robot.STARTING_LIVES, 0, start.position(), RobotStatus.ACTIVE, false, false));
        }
    }

    /**
     * Takes in a message of the server that belongs to the running game. Other messages are ignored.
     *
     * @param message a message from the server
     */
    public void apply(Object message) {
        if (message instanceof TurnStarted started) {
            startTurn(started);
        } else if (message instanceof HandDealt hand) {
            takeHand(hand);
        } else if (message instanceof ProgramRevealed revealed) {
            revealProgram(revealed);
        } else if (message instanceof PlayerConfirmed done) {
            confirmed.add(done.robotId());
            if (done.robotId() == mySeat) {
                if (!submissionPending && !submittedByMe && stage == Stage.PROGRAMMING) {
                    filledAtRandom = true;
                }
                submissionPending = false;
                stage = Stage.SUBMITTED;
            }
        } else if (message instanceof TimerUpdate update) {
            remainingSeconds = update.secondsRemaining();
        } else if (message instanceof TimerPaused pause) {
            timerPaused = pause.paused();
            remainingSeconds = pause.secondsRemaining();
        } else if (message instanceof TurnResolved resolved) {
            completeResolution();
            lastResolved = resolved;
            noteFlags(resolved);
            robotsBeforeResolution = robots();
            resolutionOpen = true;
            stage = Stage.RESOLVING;
        } else if (message instanceof StateSnapshot snapshot) {
            if (resolutionOpen) {
                heldSnapshot = snapshot;
            } else {
                replaceRobots(snapshot.robots());
            }
        } else if (message instanceof RespawnFacingChosen chosen) {
            faceRobot(chosen);
        } else if (message instanceof PlayerConnection connection) {
            if (connection.connected()) {
                disconnected.remove(connection.robotId());
            } else {
                disconnected.add(connection.robotId());
            }
        } else if (message instanceof PlayerLeft left) {
            removed.add(left.robotId());
        } else if (message instanceof GameOver over) {
            lobbyCountdown = over.lobbyInSeconds();
            if (resolutionOpen) {
                heldGameOver = over;
            } else {
                finishGame(over);
            }
        } else {
            return;
        }
        revision++;
    }

    /**
     * Lets time pass. Only the local countdown of the programming time moves, and not while the host has stopped the timer;
     * the server's own count is taken over whenever it sends one.
     *
     * @param seconds the seconds since the last call
     */
    public void tick(float seconds) {
        if (timerRuns() && !timerPaused) {
            remainingSeconds = Math.max(0f, remainingSeconds - seconds);
        }
        if (lobbyCountdown > 0f) {
            lobbyCountdown = Math.max(0f, lobbyCountdown - seconds);
        }
    }

    /**
     * Starts a turn: who must program, the time, and the robots that re-entered.
     *
     * @param started the message
     */
    private void startTurn(TurnStarted started) {
        completeResolution();
        turn = started.turn();
        awaited.clear();
        awaited.addAll(started.awaitedRobotIds());
        confirmed.clear();
        programmingSeconds = started.programmingSeconds();
        remainingSeconds = programmingSeconds;
        timerPaused = false;
        draft = null;
        poweredDownThisTurn = false;
        canChooseRespawnFacing = false;
        powerDownNext = false;
        respawnFacing = null;
        submissionPending = false;
        submittedByMe = false;
        filledAtRandom = false;
        programRevealed = false;
        lockedCardsThisTurn = List.of();
        for (LoggedEvent logged : started.respawnEvents()) {
            if (logged.event() instanceof GameEvent.RobotRespawned respawned) {
                RobotState old = robots.get(respawned.robotId());
                if (old != null) {
                    robots.put(old.robotId(), new RobotState(old.robotId(), respawned.position(), respawned.facing(), 0,
                        old.lives(), old.flagsTouched(), old.archiveMarker(), RobotStatus.ACTIVE, false, false));
                }
            }
        }
        stage = awaited.contains(mySeat) ? Stage.PROGRAMMING : Stage.SITTING_OUT;
    }

    /**
     * Turns a re-entered robot to the facing its player just picked, so every client shows it right away instead of
     * only once the turn resolves (design.md 2.13).
     *
     * @param chosen the message
     */
    private void faceRobot(RespawnFacingChosen chosen) {
        RobotState robot = robots.get(chosen.robotId());
        if (robot != null) {
            robots.put(robot.robotId(), new RobotState(robot.robotId(), robot.position(), chosen.facing(), robot.damage(),
                robot.lives(), robot.flagsTouched(), robot.archiveMarker(), robot.status(), robot.poweredDown(),
                robot.powerDownAnnounced()));
        }
    }

    /**
     * Ends the game with the final states and the winner.
     *
     * @param over the message
     */
    private void finishGame(GameOver over) {
        replaceRobots(over.robots());
        winnerRobotId = over.winnerRobotId();
        stage = Stage.OVER;
    }

    /**
     * Takes in this player's cards for the turn. An empty hand together with a robot that is not powered down and still has
     * free registers means the program was locked in before, which happens when the player comes back to a running game.
     *
     * @param hand the message
     */
    private void takeHand(HandDealt hand) {
        if (hand.turn() != turn) {
            return;
        }
        poweredDownThisTurn = hand.poweredDown();
        canChooseRespawnFacing = hand.canChooseRespawnFacing();
        if (!awaited.contains(mySeat)) {
            stage = Stage.SITTING_OUT;
            return;
        }
        lockedCardsThisTurn = hand.lockedCards();
        boolean lockedInAlready = hand.hand().isEmpty() && hand.lockedCards().size() < ProgramDraft.REGISTERS;
        if (lockedInAlready) {
            confirmed.add(mySeat);
            stage = Stage.SUBMITTED;
        } else {
            draft = new ProgramDraft(hand.hand(), hand.lockedCards());
            stage = Stage.PROGRAMMING;
        }
    }

    /**
     * Takes in this player's own registers, once the server has told them what is actually in them: filled in at
     * random, or already locked in before they (re)connected. Never sent for a program the player locked in
     * themselves while connected, since they already know what they placed. Only the free registers come from this
     * message; the damage-locked tail is the one the most recent {@link HandDealt} of this turn already gave.
     *
     * @param revealed the message
     */
    private void revealProgram(ProgramRevealed revealed) {
        if (revealed.turn() != turn) {
            return;
        }
        List<Card> free = revealed.cards().subList(0, revealed.cards().size() - lockedCardsThisTurn.size());
        draft = ProgramDraft.revealed(lockedCardsThisTurn, free);
        confirmed.add(mySeat);
        programRevealed = true;
        stage = Stage.SUBMITTED;
    }

    /**
     * Remembers, for every flag a robot touched in a turn that is about to be played back, which flag it was and when, so the
     * results can say where the game was won.
     *
     * @param resolved the resolved turn
     */
    private void noteFlags(TurnResolved resolved) {
        for (LoggedEvent logged : resolved.events()) {
            if (logged.event() instanceof GameEvent.FlagTouched touched) {
                lastFlags.put(touched.robotId(), new Standings.FlagTouch(touched.flagNumber(), resolved.turn(), logged.register()));
            }
        }
    }

    /**
     * Remembers the turn in which a robot was eliminated, for the robots that the given states show as eliminated for the first
     * time. Players who left the game are not counted: they did not lose their lives. If this player's own robot is one of
     * them, {@link #myEliminationJustSeen()} starts returning {@code true}.
     *
     * @param after the states the turn ended with
     * @param turn  the turn
     */
    private void noteEliminations(List<RobotState> after, int turn) {
        for (RobotState state : after) {
            RobotState before = robots.get(state.robotId());
            if (state.status() == RobotStatus.ELIMINATED && before != null && before.status() != RobotStatus.ELIMINATED
                && !removed.contains(state.robotId())) {
                eliminatedTurns.putIfAbsent(state.robotId(), turn);
                if (state.robotId() == mySeat) {
                    myEliminationSeen = true;
                }
            }
        }
    }

    /**
     * Returns whether this player's own robot was just seen to lose its last life, so a screen can tell the player once.
     * Only an elimination this client watched happen counts: a robot that is already eliminated when the game is joined or
     * resynced, before any turn of it was replayed, does not set this off, since the client cannot tell that apart from one
     * that was always out.
     *
     * @return {@code true} if the robot's status turned to {@link RobotStatus#ELIMINATED} in a turn this client replayed
     */
    public boolean myEliminationJustSeen() {
        return myEliminationSeen;
    }

    /**
     * Replaces the state of the robots by the server's.
     *
     * @param states the states, one per robot
     */
    private void replaceRobots(List<RobotState> states) {
        for (RobotState state : states) {
            robots.put(state.robotId(), state);
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // What the screens ask
    // ------------------------------------------------------------------------------------------------------

    /**
     * Returns the board.
     *
     * @return the board
     */
    public Board board() {
        return board;
    }

    /**
     * Returns the seat of this client's player, which is also the id of their robot.
     *
     * @return the seat
     */
    public int mySeat() {
        return mySeat;
    }

    /**
     * Returns how often the model changed. A screen redraws its panels when this number is not the one it saw last.
     *
     * @return the count of changes
     */
    public int revision() {
        return revision;
    }

    /**
     * Returns where this player is in the turn.
     *
     * @return the stage
     */
    public Stage stage() {
        return stage;
    }

    /**
     * Returns the number of the turn in progress.
     *
     * @return the turn, 0 before the first turn
     */
    public int turn() {
        return turn;
    }

    /**
     * Returns the public state of every robot, ordered by id.
     *
     * @return the robots
     */
    public List<RobotState> robots() {
        return new ArrayList<>(robots.values());
    }

    /**
     * Returns this player's robot.
     *
     * @return its public state
     */
    public RobotState myRobot() {
        return robots.get(mySeat);
    }

    /**
     * Returns the display name of a player.
     *
     * @param seat the seat
     * @return the name
     */
    public String nameOf(int seat) {
        return players.get(seat).name();
    }

    /**
     * Returns the robot that won, once the game is over.
     *
     * @return the winner's id, or {@link GameEvent#NO_ROBOT} if nobody has won
     */
    public int winnerRobotId() {
        return winnerRobotId;
    }

    /**
     * Returns the results of the game, for the Game Over screen.
     *
     * @return the standings, from the state the game ended with
     */
    public Standings standings() {
        return Standings.of(new ArrayList<>(players.values()), robots(), winnerRobotId, mySeat, board.flags().size(),
            Robot.STARTING_LIVES, lastFlags, eliminatedTurns, removed);
    }

    /**
     * Returns the seconds until the server takes everybody back to the lobby, counted from the moment the end of the game
     * arrived.
     *
     * @return the seconds, rounded up; 0 when they are up, and 0 as well as long as the game has not ended
     */
    public int lobbySecondsLeft() {
        return (int) Math.ceil(Math.max(0f, lobbyCountdown));
    }

    /**
     * Returns what happened in the last turn, for whoever plays it back.
     *
     * @return the message, or {@code null} before the first turn ended
     */
    public TurnResolved lastResolved() {
        return lastResolved;
    }

    /**
     * Returns the state of every robot as it was before the turn that has just been resolved, which is where a replay of the
     * turn starts.
     *
     * @return the robots before the turn
     */
    public List<RobotState> robotsBeforeResolution() {
        return robotsBeforeResolution;
    }

    /**
     * Returns whether a resolved turn is waiting to be played back. While it is, the state the server sent for the end of
     * the turn, and the end of the game if the turn ended it, are held back so they do not show before the replay.
     *
     * @return {@code true} from the moment a turn is resolved until {@link #completeResolution()}
     */
    public boolean isResolutionOpen() {
        return resolutionOpen;
    }

    /**
     * Takes in the state the server reached at the end of the resolved turn and, if that turn ended the game, the end of the
     * game. Call it when the replay is over or skipped. A new turn or a new resolution calls it by itself, so a replay that
     * is still running is cut short and the state is never stale.
     */
    public void completeResolution() {
        if (!resolutionOpen) {
            return;
        }
        resolutionOpen = false;
        // A resolution is only ever open after a TurnResolved has been taken in, which is what sets lastResolved.
        int resolvedTurn = lastResolved == null ? 0 : lastResolved.turn();
        if (heldSnapshot != null) {
            noteEliminations(heldSnapshot.robots(), resolvedTurn);
            replaceRobots(heldSnapshot.robots());
            heldSnapshot = null;
        }
        if (heldGameOver != null) {
            noteEliminations(heldGameOver.robots(), resolvedTurn);
            finishGame(heldGameOver);
            heldGameOver = null;
        }
        revision++;
    }

    /**
     * Returns the program being put together.
     *
     * @return the draft, or {@code null} when this player has no cards to place
     */
    public ProgramDraft draft() {
        return draft;
    }

    /**
     * Returns a rough local preview of where this player's own robot would go if its program ran alone, register by
     * register, from whatever cards are placed so far (design.md 3.5, 4.3: a "ghost path", a convenience only, never
     * authoritative — see {@link MovementPreview} for exactly what it does and does not simulate).
     * <p>
     * Stops at the first free register that is still empty, even if a damage-locked register further along already
     * shows a known card: a path that skipped over an unknown gap would misrepresent what actually happens there.
     * Starts from the facing chosen for a just-re-entered robot ({@link #respawnFacing()}) when one was chosen, since
     * that is what will actually be submitted, not the server's last-known facing.
     *
     * @return one step per card that would run to completion, in register order; empty while there is nothing placed
     *         yet, this player is not programming, or their own robot is not on the board
     */
    public List<MovementPreview.Step> ghostPath() {
        RobotState me = myRobot();
        if (stage != Stage.PROGRAMMING || draft == null || me == null || me.position() == null) {
            return List.of();
        }
        List<Card> cards = new ArrayList<>();
        for (ProgramDraft.RegisterView view : draft.registers()) {
            if (view.card() == null) {
                break;
            }
            cards.add(view.card());
        }
        if (cards.isEmpty()) {
            return List.of();
        }
        Set<Position> obstacles = new HashSet<>();
        for (RobotState robot : robots.values()) {
            if (robot.robotId() != mySeat && robot.status() == RobotStatus.ACTIVE && robot.position() != null) {
                obstacles.add(robot.position());
            }
        }
        Direction facing = respawnFacing != null ? respawnFacing : me.facing();
        return MovementPreview.path(board, me.position(), facing, cards, obstacles);
    }

    /**
     * Returns whether this player's robot is powered down for the turn.
     *
     * @return {@code true} if it is
     */
    public boolean isPoweredDownThisTurn() {
        return poweredDownThisTurn;
    }

    /**
     * Returns whether this player may choose the facing of the robot that just re-entered.
     *
     * @return {@code true} in the turn of the re-entry
     */
    public boolean canChooseRespawnFacing() {
        return canChooseRespawnFacing;
    }

    /**
     * Returns the facing chosen for the re-entered robot.
     *
     * @return the facing, or {@code null} to keep the one the robot has
     */
    public Direction respawnFacing() {
        return respawnFacing;
    }

    /**
     * Chooses the facing of the re-entered robot; it goes out with the program.
     *
     * @param facing the facing, or {@code null} to keep the robot's own
     */
    public void chooseRespawnFacing(Direction facing) {
        this.respawnFacing = facing;
        revision++;
    }

    /**
     * Returns whether a power-down is announced for after this turn.
     *
     * @return {@code true} if it is
     */
    public boolean powerDownNext() {
        return powerDownNext;
    }

    /**
     * Chooses whether to announce a power-down for after this turn.
     *
     * @param announce {@code true} to announce it
     */
    public void setPowerDownNext(boolean announce) {
        this.powerDownNext = announce;
        revision++;
    }

    /**
     * Returns whether the program can be locked in now.
     *
     * @return {@code true} when the cards are placed and nothing has been sent yet
     */
    public boolean canConfirm() {
        return stage == Stage.PROGRAMMING && draft != null && draft.isComplete() && !submissionPending;
    }

    /**
     * Returns the sentence under the confirm button.
     *
     * @return what is missing, or that the program is ready
     */
    public String confirmHint() {
        if (draft == null) {
            return "Waiting for your cards.";
        }
        int missing = draft.missing();
        if (missing == 0) {
            return draft.freeRegisterCount() == 0 ? "All registers are locked. Confirm to continue." : "Your program is ready.";
        }
        return "Fill " + missing + " more register" + (missing == 1 ? "" : "s") + " to confirm.";
    }

    /**
     * Builds the message that locks the program in, and remembers that it was sent, so it is not sent twice. If the server
     * refuses it, call {@link #submissionRefused()}.
     *
     * @return the message
     * @throws IllegalStateException if the program cannot be confirmed now
     */
    public SubmitProgram submit() {
        if (!canConfirm()) {
            throw new IllegalStateException("The program cannot be confirmed now");
        }
        submissionPending = true;
        submittedByMe = true;
        revision++;
        return draft.toSubmit(turn, powerDownNext, canChooseRespawnFacing ? respawnFacing : null);
    }

    /**
     * Builds the message with which a powered-down player says whether the robot stays down for another turn. It carries no
     * cards.
     *
     * @return the message
     */
    public SubmitProgram announceStayingDown() {
        return new SubmitProgram(turn, List.of(), powerDownNext, null);
    }

    /**
     * Notes that the server refused the program, so it can be changed and sent again.
     */
    public void submissionRefused() {
        if (submissionPending) {
            submissionPending = false;
            submittedByMe = false;
            revision++;
        }
    }

    /**
     * Returns whether the programming timer is part of what the screen shows right now.
     *
     * @return {@code true} while players are programming, whether or not this player is one of them
     */
    private boolean timerRuns() {
        return stage == Stage.PROGRAMMING || stage == Stage.SUBMITTED || stage == Stage.SITTING_OUT;
    }

    /**
     * Returns whether the host has stopped the programming timer.
     *
     * @return {@code true} while it is stopped
     */
    public boolean isTimerPaused() {
        return timerPaused;
    }

    /**
     * Returns whether this player is the host of the game, the one who may stop the timer. The server has the last word: it
     * refuses the request of anybody else.
     *
     * @return {@code true} for the host
     */
    public boolean amHost() {
        PlayerInfo me = players.get(mySeat);
        return me != null && me.host();
    }

    /**
     * Returns whether the timer button is shown: to the host, while players are programming.
     *
     * @return {@code true} if the host can stop or restart the timer now
     */
    public boolean canPauseTimer() {
        return amHost() && timerRuns();
    }

    /**
     * Builds the message with which the host stops the timer, or restarts it if it is stopped.
     *
     * @return the message
     */
    public SetTimerPaused toggleTimerPaused() {
        return new SetTimerPaused(!timerPaused);
    }

    /**
     * Returns the seconds left to program, rounded up.
     *
     * @return the seconds
     */
    public int secondsLeft() {
        return (int) Math.ceil(remainingSeconds);
    }

    /**
     * Returns the time left as minutes and seconds.
     *
     * @return for example {@code 1:07}
     */
    public String timeText() {
        int seconds = secondsLeft();
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    /**
     * Returns how much of the programming time is left.
     *
     * @return from 0 to 1
     */
    public float timeFraction() {
        return programmingSeconds <= 0 ? 0f : Math.min(1f, remainingSeconds / programmingSeconds);
    }

    /**
     * Returns the lines of the players panel, in seat order.
     *
     * @return one row per player
     */
    public List<PlayerRow> playerRows() {
        List<PlayerRow> rows = new ArrayList<>();
        for (PlayerInfo player : players.values()) {
            RobotState robot = robots.get(player.seat());
            rows.add(new PlayerRow(player.seat(), player.name(), player.seat() == mySeat, robot.lives(), robot.damage(),
                statusOf(player.seat(), robot)));
        }
        return rows;
    }

    /**
     * Works out what the players panel says about a player.
     *
     * @param seat  the seat
     * @param robot the player's robot
     * @return the status
     */
    private PlayerStatus statusOf(int seat, RobotState robot) {
        if (robot.status() == RobotStatus.ELIMINATED || removed.contains(seat)) {
            return PlayerStatus.OUT;
        }
        if (stage == Stage.RESOLVING || stage == Stage.OVER || stage == Stage.WAITING) {
            return PlayerStatus.NONE;
        }
        if (disconnected.contains(seat)) {
            return PlayerStatus.AWAY;
        }
        if (awaited.contains(seat)) {
            return confirmed.contains(seat) ? PlayerStatus.CONFIRMED : PlayerStatus.THINKING;
        }
        return robot.poweredDown() ? PlayerStatus.POWERED_DOWN : PlayerStatus.NONE;
    }

    /**
     * Returns whether the cards in this player's registers are known to the client: because the player locked in the
     * program themselves, or because the server has since told them what is in it ({@link ProgramRevealed}, sent when
     * the server filled the registers because time ran out, or when the program was locked in before the player came
     * back).
     *
     * @return {@code true} if the registers can be shown
     */
    public boolean programVisible() {
        return submittedByMe || programRevealed;
    }

    /**
     * Returns the sentence that explains a locked-in program.
     *
     * @return what happened to the program and what happens next
     */
    public String lockedInNote() {
        if (submittedByMe) {
            return "A confirmed program is final. Nobody sees your cards until the turn plays.";
        }
        if (filledAtRandom) {
            return "Time ran out, so your registers were filled at random. Nobody else sees your cards until the turn plays.";
        }
        return "Your program was locked in before you came back. Nobody else sees your cards until the turn plays.";
    }

    /**
     * Returns the title of the screen for the turn's stage.
     *
     * @return for example {@code Program your robot} or {@code Program locked in}
     */
    public String headline() {
        return switch (stage) {
            case WAITING -> "Get ready";
            case PROGRAMMING -> "Program your robot";
            case SUBMITTED -> filledAtRandom ? "Time's up" : "Program locked in";
            case SITTING_OUT -> myRobot().status() == RobotStatus.ELIMINATED ? "You are out" : "Your robot rests";
            case RESOLVING -> "Turn " + turn + " plays";
            case OVER -> "Game over";
        };
    }

    /**
     * Returns who the turn is still waiting for, worded as a sentence for a player who has locked in.
     *
     * @return for example {@code Waiting for Kenji and Łukasz}
     */
    public String waitingForText() {
        List<String> names = new ArrayList<>();
        for (int seat : new TreeMap<>(players).keySet()) {
            if (seat != mySeat && awaited.contains(seat) && !confirmed.contains(seat) && !removed.contains(seat)) {
                names.add(nameOf(seat));
            }
        }
        return names.isEmpty() ? "Waiting for the turn to start" : "Waiting for " + joined(names);
    }

    /**
     * Returns the numbers of this turn's locked registers.
     *
     * @return {@code None}, or for example {@code 4, 5}
     */
    public String lockedRegistersText() {
        if (draft == null || draft.lockedRegisterCount() == 0) {
            return "None";
        }
        List<String> numbers = new ArrayList<>();
        for (int register = draft.freeRegisterCount() + 1; register <= ProgramDraft.REGISTERS; register++) {
            numbers.add(String.valueOf(register));
        }
        return String.join(", ", numbers);
    }

    /**
     * Returns the explanation of locked registers, or an empty text when none are locked.
     *
     * @return for example {@code Registers 4 and 5 are locked by damage. They repeat last turn's cards, so you fill 3
     * registers with 3 cards.}
     */
    public String lockedHint() {
        if (draft == null || draft.lockedRegisterCount() == 0) {
            return "";
        }
        List<String> numbers = new ArrayList<>();
        for (int register = draft.freeRegisterCount() + 1; register <= ProgramDraft.REGISTERS; register++) {
            numbers.add(String.valueOf(register));
        }
        int free = draft.freeRegisterCount();
        boolean single = numbers.size() == 1;
        String locked = (single ? "Register " : "Registers ") + joined(numbers) + (single ? " is" : " are")
            + " locked by damage. " + (single ? "It repeats last turn's card" : "They repeat last turn's cards");
        return free == 0 ? locked + ", so there is nothing to fill: just confirm."
            : locked + ", so you fill " + free + (free == 1 ? " register with 1 card." : " registers with " + free + " cards.");
    }

    /**
     * Returns how many cards this player's robot is dealt, worded for the robot panel.
     *
     * @return for example {@code 7 cards (9 − 2)}
     */
    public String handText() {
        int damage = myRobot().damage();
        int cards = Math.max(0, Robot.FULL_HAND_SIZE - damage);
        return cards + (cards == 1 ? " card" : " cards") + " (" + Robot.FULL_HAND_SIZE + " − " + damage + ")";
    }

    /**
     * Joins words for a sentence: {@code A}, {@code A and B}, {@code A, B and C}.
     *
     * @param words the words
     * @return the joined text
     */
    private static String joined(List<String> words) {
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }

    /**
     * Returns how far this player's robot has come on the flags.
     *
     * @return for example {@code 2 of 3}, meaning the robot is on its way to flag 2 of 3, or {@code All 3 touched}
     */
    public String nextFlagText() {
        int total = board.flags().size();
        int touched = myRobot().flagsTouched();
        return touched >= total ? "All " + total + " touched" : (touched + 1) + " of " + total;
    }
}
