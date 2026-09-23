package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.LoadedBoard;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.game.GameModel.PlayerRow;
import de.mkoehler.robotrampage.client.game.GameModel.PlayerStatus;
import de.mkoehler.robotrampage.client.game.GameModel.Stage;
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
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.rules.SubPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link GameModel} follows a game the way the server tells it, and what it says about the turn on the way.
 *
 * @author Mario Koehler
 */
class GameModelTest {

    private static final int ME = 1;

    private static List<Card> cards(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(CardType.MOVE_1, 100 + i));
        }
        return cards;
    }

    private static GameModel newGame() {
        LoadedBoard board = BoardLoader.loadResource("boards/proving-grounds.json");
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", false, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        return new GameModel(new GameStarted(BoardLoader.toJson(board.definition()), players, ME));
    }

    private static TurnStarted turn(int number, List<Integer> awaited) {
        return new TurnStarted(number, List.of(), awaited, 90);
    }

    private static GameModel programming(int locked) {
        GameModel model = newGame();
        model.apply(turn(1, List.of(0, 1, 2)));
        int handSize = 9 - (locked == 0 ? 0 : locked + 4);
        model.apply(new HandDealt(1, cards(handSize), cards(locked), false, false));
        return model;
    }

    /**
     * A new game has every robot on its start square with full lives, so the panels have something to show before the
     * server has sent any state.
     */
    @Test
    void aNewGameStartsWithFullLivesOnTheStartSquares() {
        GameModel model = newGame();

        assertEquals(Stage.WAITING, model.stage());
        assertEquals(3, model.robots().size());
        RobotState robot = model.myRobot();
        assertEquals(Robot.STARTING_LIVES, robot.lives());
        assertEquals(0, robot.damage());
        assertEquals(model.board().startSquares().get(ME).position(), robot.position());
        assertEquals("Bo", model.nameOf(ME));
        assertEquals("1 of 3", model.nextFlagText());
    }

    /**
     * A turn that awaits this player starts the programming stage once the cards arrive, with a clock that counts down from
     * the time the server gave.
     */
    @Test
    void aTurnAndACardsStartTheProgramming() {
        GameModel model = newGame();

        model.apply(turn(4, List.of(0, 1, 2)));
        assertEquals(Stage.PROGRAMMING, model.stage());
        assertNull(model.draft(), "no cards yet");
        assertEquals("Waiting for your cards.", model.confirmHint());
        model.apply(new HandDealt(4, cards(9), List.of(), false, false));

        assertNotNull(model.draft());
        assertEquals(4, model.turn());
        assertEquals("1:30", model.timeText());
        assertEquals("Fill 5 more registers to confirm.", model.confirmHint());
        assertFalse(model.canConfirm());
    }

    /**
     * The clock runs down with the frames and is corrected by the server's timer message.
     */
    @Test
    void theClockCountsDownAndTakesTheServersTime() {
        GameModel model = programming(0);

        model.tick(23.4f);
        assertEquals("1:07", model.timeText());
        assertEquals(66.6f / 90f, model.timeFraction(), 0.001f);
        model.apply(new TimerUpdate(30));
        assertEquals("0:30", model.timeText());
        model.tick(100f);
        assertEquals("0:00", model.timeText());
    }

    /**
     * While the host has stopped the timer the clock stands still, whatever the frames do, and it starts again from the
     * time the server names. A new turn always starts with a running timer.
     */
    @Test
    void aPausedClockStandsStill() {
        GameModel model = programming(0);
        model.tick(10f);

        model.apply(new TimerPaused(true, 80));
        model.tick(50f);

        assertTrue(model.isTimerPaused());
        assertEquals("1:20", model.timeText());
        model.apply(new TimerPaused(false, 80));
        model.tick(20f);
        assertFalse(model.isTimerPaused());
        assertEquals("1:00", model.timeText());
        model.apply(new TimerPaused(true, 60));
        model.apply(turn(2, List.of(0, 1, 2)));
        assertFalse(model.isTimerPaused());
    }

    /**
     * Only the host is offered the timer button, and only while players are programming; it asks for the opposite of the
     * timer's state.
     */
    @Test
    void onlyTheHostCanPauseAndOnlyWhileProgramming() {
        LoadedBoard board = BoardLoader.loadResource("boards/proving-grounds.json");
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", false, true, true), new PlayerInfo(1, "Bo", true, true, false));
        GameModel host = new GameModel(new GameStarted(BoardLoader.toJson(board.definition()), players, 0));
        GameModel guest = programming(0);
        assertFalse(host.canPauseTimer(), "no turn yet");
        host.apply(turn(1, List.of(0, 1)));

        assertTrue(host.amHost());
        assertTrue(host.canPauseTimer());
        assertFalse(guest.amHost());
        assertFalse(guest.canPauseTimer());
        assertEquals(new SetTimerPaused(true), host.toggleTimerPaused());
        host.apply(new TimerPaused(true, 90));
        assertEquals(new SetTimerPaused(false), host.toggleTimerPaused());
    }

    /**
     * Once five cards are placed the program can be confirmed; confirming builds the message once and then waits for the
     * server, and a refusal lets the player change the program and send it again.
     */
    @Test
    void confirmingSendsTheProgramOnce() {
        GameModel model = programming(0);
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        assertTrue(model.canConfirm());
        assertEquals("Your program is ready.", model.confirmHint());
        model.setPowerDownNext(true);

        SubmitProgram submit = model.submit();

        assertEquals(List.of(100, 101, 102, 103, 104), submit.cardPriorities());
        assertTrue(submit.powerDown());
        assertEquals(1, submit.turn());
        assertFalse(model.canConfirm(), "already sent");
        assertThrows(IllegalStateException.class, model::submit);
        model.submissionRefused();
        assertTrue(model.canConfirm());
        model.submit();
        model.apply(new PlayerConfirmed(ME));
        assertEquals(Stage.SUBMITTED, model.stage());
    }

    /**
     * With two locked registers only three cards are needed, and the hint counts them.
     */
    @Test
    void lockedRegistersReduceTheProgram() {
        GameModel model = programming(2);

        assertEquals(3, model.draft().freeRegisterCount());
        assertEquals("Fill 3 more registers to confirm.", model.confirmHint());
        model.draft().place(model.draft().hand().get(0));
        model.draft().place(model.draft().hand().get(1));
        assertEquals("Fill 1 more register to confirm.", model.confirmHint());
    }

    /**
     * A robot with nine damage has all registers locked and an empty hand, and can confirm at once.
     */
    @Test
    void aFullyLockedRobotCanConfirmAtOnce() {
        GameModel model = newGame();
        model.apply(turn(2, List.of(0, 1, 2)));

        model.apply(new HandDealt(2, List.of(), cards(5), false, false));

        assertEquals(Stage.PROGRAMMING, model.stage());
        assertTrue(model.canConfirm());
        assertEquals(List.of(), model.submit().cardPriorities());
    }

    /**
     * An empty hand with free registers means the program was locked in before, as when a player returns to a running game.
     */
    @Test
    void anEmptyHandMeansTheProgramIsAlreadyLockedIn() {
        GameModel model = newGame();
        model.apply(turn(3, List.of(0, 1, 2)));

        model.apply(new HandDealt(3, List.of(), List.of(), false, false));

        assertEquals(Stage.SUBMITTED, model.stage());
        assertEquals(PlayerStatus.CONFIRMED, row(model, ME).status());
    }

    /**
     * A powered-down robot is not awaited: the player sits out and can only say whether the robot stays down.
     */
    @Test
    void aPoweredDownRobotSitsOut() {
        GameModel model = newGame();
        model.apply(turn(5, List.of(0, 2)));

        model.apply(new HandDealt(5, List.of(), List.of(), false, true));

        assertEquals(Stage.SITTING_OUT, model.stage());
        assertTrue(model.isPoweredDownThisTurn());
        model.setPowerDownNext(true);
        assertEquals(new SubmitProgram(5, List.of(), true, null), model.announceStayingDown());
    }

    /**
     * The players panel says who is thinking, who has confirmed and who is away, and takes lives and damage from the
     * server's snapshot.
     */
    @Test
    void thePlayerRowsFollowTheTurn() {
        GameModel model = programming(0);
        model.apply(new PlayerConfirmed(0));
        model.apply(new PlayerConnection(2, false));
        RobotState damaged = model.robots().get(0);
        model.apply(new StateSnapshot(0, List.of(new RobotState(0, damaged.position(), damaged.facing(), 3, 2, 1,
            damaged.archiveMarker(), RobotStatus.ACTIVE, false, false)), false, -1));

        assertEquals(new PlayerRow(0, "Ann", false, 2, 3, PlayerStatus.CONFIRMED), row(model, 0));
        assertEquals(new PlayerRow(1, "Bo", true, 3, 0, PlayerStatus.THINKING), row(model, 1));
        assertEquals(PlayerStatus.AWAY, row(model, 2).status());
        model.apply(new PlayerConnection(2, true));
        assertEquals(PlayerStatus.THINKING, row(model, 2).status());
    }

    /**
     * A robot that re-enters at the start of a turn stands on its new square with no damage and is active again.
     */
    @Test
    void aRespawnMovesTheRobot() {
        GameModel model = newGame();
        RobotState old = model.myRobot();
        model.apply(new StateSnapshot(1, List.of(new RobotState(ME, null, old.facing(), 10, 2, 1, old.archiveMarker(),
            RobotStatus.DESTROYED, false, false)), false, -1));
        LoggedEvent respawn = new LoggedEvent(0, SubPhase.RESPAWN,
            new GameEvent.RobotRespawned(ME, new Position(4, 4), Direction.WEST));

        model.apply(new TurnStarted(2, List.of(respawn), List.of(0, 1, 2), 90));

        RobotState robot = model.myRobot();
        assertEquals(new Position(4, 4), robot.position());
        assertEquals(Direction.WEST, robot.facing());
        assertEquals(0, robot.damage());
        assertEquals(RobotStatus.ACTIVE, robot.status());
        assertEquals(2, robot.lives());
    }

    /**
     * The respawn facing goes out with the program only in the turn the robot re-entered.
     */
    @Test
    void theRespawnFacingIsSentOnlyInTheTurnOfTheReentry() {
        GameModel model = newGame();
        model.apply(turn(2, List.of(0, 1, 2)));
        model.apply(new HandDealt(2, cards(9), List.of(), true, false));
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        model.chooseRespawnFacing(Direction.SOUTH);

        assertEquals(Direction.SOUTH, model.submit().respawnFacing());

        GameModel other = programming(0);
        other.draft().hand().subList(0, 5).forEach(other.draft()::place);
        other.chooseRespawnFacing(Direction.SOUTH);
        assertNull(other.submit().respawnFacing());
    }

    /**
     * A resolved turn moves the model to the resolving stage and keeps the events for whoever plays them back; the snapshot
     * after it brings the new robot states.
     */
    @Test
    void aResolvedTurnIsKept() {
        GameModel model = programming(0);
        TurnResolved resolved = new TurnResolved(1, List.of());

        model.apply(resolved);

        assertEquals(Stage.RESOLVING, model.stage());
        assertEquals(resolved, model.lastResolved());
        assertEquals(PlayerStatus.NONE, row(model, 0).status());
    }

    /**
     * While a resolved turn waits to be played back, the state the server sent for its end is held back, so the robots stay
     * where they were before the turn; completing the resolution shows the end state.
     */
    @Test
    void theEndStateIsHeldUntilTheResolutionIsComplete() {
        GameModel model = programming(0);
        List<RobotState> before = model.robots();
        RobotState moved = new RobotState(0, new Position(9, 9), Direction.SOUTH, 2, 3, 0, new Position(2, 0),
            RobotStatus.ACTIVE, false, false);

        model.apply(new TurnResolved(1, List.of()));
        model.apply(new StateSnapshot(1, List.of(moved), false, -1));

        assertTrue(model.isResolutionOpen());
        assertEquals(before, model.robotsBeforeResolution());
        assertEquals(before.get(0), model.robots().get(0), "the robots have not jumped yet");
        model.completeResolution();
        assertFalse(model.isResolutionOpen());
        assertEquals(moved, model.robots().get(0));
        assertEquals(Stage.RESOLVING, model.stage(), "the next turn has not begun");
    }

    /**
     * A new turn that arrives while a replay is still running takes in the held state first, so the client never plays on
     * with a stale board.
     */
    @Test
    void aNewTurnCutsTheReplayShort() {
        GameModel model = programming(0);
        RobotState moved = new RobotState(0, new Position(9, 9), Direction.SOUTH, 2, 3, 0, new Position(2, 0),
            RobotStatus.ACTIVE, false, false);
        model.apply(new TurnResolved(1, List.of()));
        model.apply(new StateSnapshot(1, List.of(moved), false, -1));

        model.apply(turn(2, List.of(0, 1, 2)));

        assertFalse(model.isResolutionOpen());
        assertEquals(moved, model.robots().get(0));
        assertEquals(Stage.PROGRAMMING, model.stage());
    }

    /**
     * The end of the game waits for the replay of the turn that ended it, so nobody misses the winning move.
     */
    @Test
    void theEndOfTheGameWaitsForTheReplay() {
        GameModel model = programming(0);
        model.apply(new TurnResolved(1, List.of()));
        model.apply(new GameOver(ME, model.robots(), 15));

        assertEquals(Stage.RESOLVING, model.stage());
        assertEquals(GameEvent.NO_ROBOT, model.winnerRobotId());
        model.completeResolution();
        assertEquals(Stage.OVER, model.stage());
        assertEquals(ME, model.winnerRobotId());
    }

    /**
     * The results remember what the client replayed: the turn and register of the flag that won the game and the turn an
     * eliminated robot was lost in, but not the elimination of a robot whose player left. The countdown to the lobby starts
     * with the end of the game.
     */
    @Test
    void theResultsRememberWhatWasReplayed() {
        GameModel model = programming(0);
        Position flag = model.board().flags().get(model.board().flags().size() - 1);
        RobotState me = model.myRobot();
        RobotState winner = new RobotState(ME, flag, me.facing(), 0, 3, 3, me.archiveMarker(), RobotStatus.ACTIVE, false, false);
        RobotState out = new RobotState(2, null, Direction.NORTH, 9, 0, 1, new Position(0, 0), RobotStatus.ELIMINATED, false,
            false);
        RobotState gone = new RobotState(0, null, Direction.NORTH, 0, 2, 0, new Position(0, 0), RobotStatus.ELIMINATED, false,
            false);
        model.apply(new PlayerLeft(0, List.of()));

        model.apply(new TurnResolved(11, List.of(new LoggedEvent(4, SubPhase.CHECKPOINTS,
            new GameEvent.FlagTouched(ME, model.board().flags().size(), flag)))));
        model.apply(new StateSnapshot(11, List.of(gone, winner, out), true, ME));
        model.apply(new GameOver(ME, List.of(gone, winner, out), 15));

        assertEquals(15, model.lobbySecondsLeft());
        model.completeResolution();
        model.tick(4.5f);
        List<Standings.Row> rows = model.standings().rows();
        assertEquals(11, model.lobbySecondsLeft());
        assertEquals(List.of(ME, 2, 0), rows.stream().map(Standings.Row::seat).toList());
        assertEquals("Touched flag 3 in turn 11, register 4", rows.get(0).detail());
        assertEquals("Eliminated in turn 11", rows.get(1).detail());
        assertEquals("Left the game", rows.get(2).detail());
        assertEquals("Bo wins!", model.standings().headline());
    }

    /**
     * When this player's own robot is seen to lose its last life in a replayed turn, {@link GameModel#myEliminationJustSeen()}
     * turns true once the resolution completes, not before.
     */
    @Test
    void myEliminationIsSeenWhenReplayed() {
        GameModel model = programming(0);
        RobotState me = model.myRobot();
        RobotState eliminated = new RobotState(ME, null, me.facing(), 9, 0, 0, me.archiveMarker(), RobotStatus.ELIMINATED,
            false, false);
        assertFalse(model.myEliminationJustSeen());

        model.apply(new TurnResolved(1, List.of()));
        model.apply(new StateSnapshot(1, List.of(eliminated), false, -1));

        assertFalse(model.myEliminationJustSeen(), "not before the replay completes");
        model.completeResolution();
        assertTrue(model.myEliminationJustSeen());
    }

    /**
     * Another player's elimination does not set this player's own flag.
     */
    @Test
    void anotherPlayersEliminationIsNotMine() {
        GameModel model = programming(0);
        RobotState other = model.robots().get(0);
        RobotState eliminated = new RobotState(0, null, other.facing(), 9, 0, 0, other.archiveMarker(),
            RobotStatus.ELIMINATED, false, false);

        model.apply(new TurnResolved(1, List.of()));
        model.apply(new StateSnapshot(1, List.of(eliminated), false, -1));
        model.completeResolution();

        assertFalse(model.myEliminationJustSeen());
    }

    /**
     * A snapshot that resyncs this client, before any turn of its own was replayed, never sets the flag even if it already
     * shows this player's robot as eliminated: the client cannot tell that apart from a robot that was always out. The state
     * itself is still taken over.
     */
    @Test
    void anEliminationOnlyEverSeenOnResyncIsNotFlagged() {
        GameModel model = newGame();
        RobotState me = model.myRobot();
        RobotState eliminated = new RobotState(ME, null, me.facing(), 9, 0, 0, me.archiveMarker(), RobotStatus.ELIMINATED,
            false, false);

        model.apply(new StateSnapshot(3, List.of(eliminated), false, -1));

        assertFalse(model.myEliminationJustSeen());
        assertEquals(RobotStatus.ELIMINATED, model.myRobot().status());
    }

    /**
     * The end of the game is remembered with the winner and the final states.
     */
    @Test
    void theGameCanEnd() {
        GameModel model = programming(0);
        RobotState winner = model.myRobot();

        model.apply(new GameOver(ME, List.of(winner), 15));

        assertEquals(Stage.OVER, model.stage());
        assertEquals(ME, model.winnerRobotId());
    }

    /**
     * Every change counts up the revision, so a screen knows when to redraw, and messages that do not concern the game
     * change nothing.
     */
    @Test
    void theRevisionCountsChanges() {
        GameModel model = newGame();
        int before = model.revision();

        model.apply("not a game message");
        assertEquals(before, model.revision());
        model.apply(turn(1, List.of(0, 1, 2)));

        assertEquals(before + 1, model.revision());
    }

    /**
     * The texts of the screen follow the stage, the locks and the damage.
     */
    @Test
    void theTextsOfTheScreen() {
        GameModel model = newGame();
        assertEquals("Get ready", model.headline());
        model.apply(turn(1, List.of(0, 1, 2)));
        model.apply(new HandDealt(1, cards(3), cards(2), false, false));

        assertEquals("Program your robot", model.headline());
        assertEquals("4, 5", model.lockedRegistersText());
        assertEquals("Registers 4 and 5 are locked by damage. They repeat last turn's cards, so you fill 3 registers with 3 "
            + "cards.", model.lockedHint());
        assertEquals("Waiting for Ann and Cy", model.waitingForText());
        model.apply(new PlayerConfirmed(0));
        assertEquals("Waiting for Cy", model.waitingForText());
        model.apply(new PlayerConfirmed(2));
        assertEquals("Waiting for the turn to start", model.waitingForText());
        assertEquals("9 cards (9 − 0)", model.handText());
    }

    /**
     * One locked register and a fully locked robot are worded in the singular and as nothing to fill.
     */
    @Test
    void lockedHintsInTheSingularAndWhenEverythingIsLocked() {
        GameModel one = newGame();
        one.apply(turn(1, List.of(0, 1, 2)));
        one.apply(new HandDealt(1, cards(4), cards(1), false, false));
        assertEquals("Register 5 is locked by damage. It repeats last turn's card, so you fill 4 registers with 4 cards.",
            one.lockedHint());

        GameModel all = newGame();
        all.apply(turn(1, List.of(0, 1, 2)));
        all.apply(new HandDealt(1, List.of(), cards(5), false, false));
        assertEquals("1, 2, 3, 4, 5", all.lockedRegistersText());
        assertTrue(all.lockedHint().endsWith("so there is nothing to fill: just confirm."));
        assertEquals("", programming(0).lockedHint());
        assertEquals("None", programming(0).lockedRegistersText());
    }

    /**
     * When the server fills the registers because time ran out, the player was not the one who sent a program: the model says
     * so, the registers are not shown as if the player had chosen them, and the title says time is up.
     */
    @Test
    void aProgramFilledInByTheServerIsNotShownAsTheirs() {
        GameModel model = programming(0);
        model.draft().place(model.draft().hand().get(0));

        model.apply(new PlayerConfirmed(ME));

        assertEquals(Stage.SUBMITTED, model.stage());
        assertFalse(model.programVisible());
        assertEquals("Time's up", model.headline());
        assertTrue(model.lockedInNote().startsWith("Time ran out"));
    }

    /**
     * A program the player locked in themselves is shown, and the confirmation of the server does not turn it into a random
     * fill.
     */
    @Test
    void aProgramTheyLockedInIsShown() {
        GameModel model = programming(0);
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        model.submit();

        model.apply(new PlayerConfirmed(ME));

        assertTrue(model.programVisible());
        assertEquals("Program locked in", model.headline());
        assertTrue(model.lockedInNote().startsWith("A confirmed program is final"));
    }

    /**
     * A refused program is not treated as sent, so the registers show again as the player's own only once a program is
     * really locked in.
     */
    @Test
    void aRefusedProgramIsNotVisible() {
        GameModel model = programming(0);
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        model.submit();
        model.submissionRefused();

        assertFalse(model.programVisible());
    }

    /**
     * A program that was locked in before the player came back has hidden cards, and the note says so.
     */
    @Test
    void aProgramLockedInBeforeComingBackIsHidden() {
        GameModel model = newGame();
        model.apply(turn(3, List.of(0, 1, 2)));
        model.apply(new HandDealt(3, List.of(), List.of(), false, false));

        assertFalse(model.programVisible());
        assertTrue(model.lockedInNote().startsWith("Your program was locked in before"));
        assertEquals("Program locked in", model.headline());
    }

    /**
     * Once the server reveals what it filled in at random, the registers show the actual cards, in the normal (not
     * locked) look, and the program stays marked as a random fill.
     */
    @Test
    void aRevealedRandomFillShowsTheActualCards() {
        GameModel model = programming(0);
        List<Card> filled = cards(5);
        model.apply(new PlayerConfirmed(ME));

        model.apply(new ProgramRevealed(1, filled));

        assertTrue(model.programVisible());
        assertEquals("Time's up", model.headline());
        for (int i = 0; i < filled.size(); i++) {
            ProgramDraft.RegisterView view = model.draft().registers().get(i);
            assertEquals(filled.get(i), view.card());
            assertFalse(view.locked(), "a randomly filled register is not damage-locked");
        }
    }

    /**
     * A program locked in before the player came back is revealed the same way, with the real damage-locked tail
     * shown as locked and the rest as the normal look, and the headline stays "Program locked in", not "Time's up" —
     * reconnecting must not be mistaken for a live random fill.
     */
    @Test
    void aRevealedProgramFromBeforeReconnectingShowsFreeAndLockedRegistersCorrectly() {
        GameModel model = newGame();
        List<Card> lockedTail = cards(2);
        model.apply(turn(3, List.of(0, 1, 2)));
        model.apply(new HandDealt(3, List.of(), lockedTail, false, false));
        List<Card> free = List.of(new Card(CardType.MOVE_2, 700), new Card(CardType.ROTATE_LEFT, 701),
            new Card(CardType.ROTATE_RIGHT, 702));

        List<Card> allFive = new ArrayList<>(free);
        allFive.addAll(lockedTail);
        model.apply(new ProgramRevealed(3, allFive));

        assertTrue(model.programVisible());
        assertEquals("Program locked in", model.headline());
        List<ProgramDraft.RegisterView> registers = model.draft().registers();
        for (int i = 0; i < free.size(); i++) {
            assertEquals(free.get(i), registers.get(i).card());
            assertFalse(registers.get(i).locked());
        }
        for (int i = 0; i < lockedTail.size(); i++) {
            assertEquals(lockedTail.get(i), registers.get(free.size() + i).card());
            assertTrue(registers.get(free.size() + i).locked());
        }
    }

    /**
     * A reveal for a turn that has since moved on is ignored, so a message delayed behind a new turn cannot show the
     * wrong cards.
     */
    @Test
    void aRevealOfAnOldTurnIsIgnored() {
        GameModel model = programming(0);
        model.apply(turn(2, List.of(0, 1, 2)));
        model.apply(new HandDealt(2, cards(9), List.of(), false, false));

        model.apply(new ProgramRevealed(1, cards(5)));

        assertFalse(model.programVisible());
    }

    // ---------------------------------------------------------------------------------------------- ghost path

    /**
     * A small open board with no walls or pits, wide enough to walk five squares east from seat 1's start square
     * without falling off, for ghost-path tests that need to reason about exact squares — {@link #newGame()}'s real
     * board is the wrong tool for that, since its exact layout is free to change.
     */
    private static final String OPEN_BOARD = """
        {"formatVersion": 1, "id": "g", "name": "Ghost Test Board", "width": 10, "height": 5,
         "flags": [{"x": 9, "y": 4}],
         "startSquares": [{"x": 0, "y": 0, "facing": "NORTH"}, {"x": 2, "y": 2, "facing": "EAST"},
                          {"x": 9, "y": 0, "facing": "NORTH"}]}
        """;

    /**
     * Builds a model on {@link #OPEN_BOARD}, dealt a hand for turn 1 with the given number of damage-locked registers.
     *
     * @param locked how many registers are locked by damage
     * @return the model, in the programming stage
     */
    private static GameModel programmingOnOpenBoard(int locked) {
        List<PlayerInfo> players = List.of(new PlayerInfo(0, "Ann", false, true, true),
            new PlayerInfo(1, "Bo", true, true, false), new PlayerInfo(2, "Cy", true, true, false));
        GameModel model = new GameModel(new GameStarted(OPEN_BOARD, players, ME));
        model.apply(turn(1, List.of(0, 1, 2)));
        int handSize = 9 - (locked == 0 ? 0 : locked + 4);
        model.apply(new HandDealt(1, cards(handSize), cards(locked), false, false));
        return model;
    }

    /**
     * Before any card is placed there is nothing to preview.
     */
    @Test
    void ghostPathIsEmptyBeforeAnyCardIsPlaced() {
        GameModel model = programmingOnOpenBoard(0);

        assertTrue(model.ghostPath().isEmpty());
    }

    /**
     * The path grows by one step as each card is placed, from the robot's actual start square and facing (seat 1
     * starts at (2,2) facing east on the test board).
     */
    @Test
    void ghostPathGrowsAsCardsArePlaced() {
        GameModel model = programmingOnOpenBoard(0);

        model.draft().place(model.draft().hand().get(0));
        assertEquals(List.of(new MovementPreview.Step(new Position(3, 2), Direction.EAST)), model.ghostPath());

        model.draft().place(model.draft().hand().get(1));
        assertEquals(List.of(new MovementPreview.Step(new Position(3, 2), Direction.EAST),
            new MovementPreview.Step(new Position(4, 2), Direction.EAST)), model.ghostPath());
    }

    /**
     * A damage-locked register's card is known, but the preview still stops at the first empty free register instead
     * of skipping over the gap to it: a path that jumped over an unknown register would misrepresent what happens
     * there.
     */
    @Test
    void ghostPathStopsAtTheFirstEmptyRegisterEvenWithALockedTailKnown() {
        GameModel model = programmingOnOpenBoard(2);

        assertEquals(3, model.draft().freeRegisterCount());
        model.draft().place(model.draft().hand().get(0));

        assertEquals(1, model.ghostPath().size(), "two free registers are still empty before the known locked tail");
    }

    /**
     * Filling every free register extends the preview into the now-reachable, already-known locked tail.
     */
    @Test
    void ghostPathReachesTheLockedTailOnceEveryFreeRegisterIsFilled() {
        GameModel model = programmingOnOpenBoard(2);

        model.draft().hand().subList(0, model.draft().freeRegisterCount()).forEach(model.draft()::place);

        assertEquals(5, model.ghostPath().size());
    }

    /**
     * The path is empty once the program is confirmed: it is not the programming stage any more.
     */
    @Test
    void ghostPathIsEmptyOnceConfirmed() {
        GameModel model = programmingOnOpenBoard(0);
        model.draft().hand().subList(0, 5).forEach(model.draft()::place);
        model.submit();

        model.apply(new PlayerConfirmed(ME));

        assertTrue(model.ghostPath().isEmpty());
    }

    /**
     * Another robot's square never blocks the preview, exactly like {@link MovementPreviewTest} already verifies for
     * {@link MovementPreview} alone; this only checks that {@link GameModel} actually never passes the other robots'
     * squares to it at all.
     */
    @Test
    void ghostPathIgnoresOtherRobots() {
        GameModel model = programmingOnOpenBoard(0);
        model.apply(new StateSnapshot(1, List.of(
            new RobotState(0, new Position(0, 0), Direction.NORTH, 0, 3, 0, new Position(0, 0), RobotStatus.ACTIVE, false, false),
            new RobotState(1, new Position(2, 2), Direction.EAST, 0, 3, 0, new Position(2, 2), RobotStatus.ACTIVE, false, false),
            new RobotState(2, new Position(3, 2), Direction.NORTH, 0, 3, 0, new Position(4, 0), RobotStatus.ACTIVE, false, false)),
            false, -1));

        model.draft().place(model.draft().hand().get(0));

        assertEquals(List.of(new MovementPreview.Step(new Position(3, 2), Direction.EAST)), model.ghostPath(),
            "walked straight onto the square seat 2 sits on, since another robot's own program is unpredictable");
    }

    /**
     * A robot that just re-entered previews from the facing chosen in the dialog, not the server's last-known facing,
     * since that is what will actually be submitted.
     */
    @Test
    void ghostPathUsesAChosenRespawnFacing() {
        GameModel model = programmingOnOpenBoard(0);

        model.chooseRespawnFacing(Direction.NORTH);
        model.draft().place(model.draft().hand().get(0));

        assertEquals(List.of(new MovementPreview.Step(new Position(2, 3), Direction.NORTH)), model.ghostPath());
    }

    /**
     * A robot that just re-entered turns to face the way its player picked as soon as the server broadcasts it, so
     * every client's board shows it right away — not just the seat that made the choice.
     */
    @Test
    void aRemoteRobotTurnsWhenItsPlayerPicksARespawnFacing() {
        GameModel model = programmingOnOpenBoard(0);
        RobotState before = model.robots().stream().filter(robot -> robot.robotId() == 0).findFirst().orElseThrow();
        assertEquals(Direction.NORTH, before.facing());

        model.apply(new RespawnFacingChosen(0, Direction.SOUTH));

        RobotState after = model.robots().stream().filter(robot -> robot.robotId() == 0).findFirst().orElseThrow();
        assertEquals(Direction.SOUTH, after.facing());
        assertEquals(before.position(), after.position(), "only the facing changes");
    }

    private static PlayerRow row(GameModel model, int seat) {
        return model.playerRows().stream().filter(row -> row.seat() == seat).findFirst().orElseThrow();
    }
}
