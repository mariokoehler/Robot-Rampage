package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.mkoehler.robotrampage.testsupport.AsciiBoard.assertRobots;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies whole turns: the order of registers and sub-phases (design.md 2.4), how a turn ends
 * (2.10) and that the cleanup phase runs, with all the individual rules already covered by the
 * resolver tests.
 *
 * @author Mario Koehler
 */
class TurnResolverTest {

    private static final CardType M1 = CardType.MOVE_1;

    /**
     * Returns the logged events of one type, in order.
     *
     * @param result the turn result
     * @param type   the event class to select
     * @param <T>    the event type
     * @return the matching logged events
     */
    private static <T extends GameEvent> List<LoggedEvent> ofType(TurnResult result, Class<T> type) {
        return result.events().stream().filter(entry -> type.isInstance(entry.event())).toList();
    }

    /**
     * Faces robot 0 east.
     *
     * @param state the game state
     * @return the same state, for chaining
     */
    private static GameState eastward(GameState state) {
        state.robot(0).setFacing(Direction.EAST);
        return state;
    }

    /**
     * Resolving a turn leaves the state that was passed in untouched and returns a new one.
     */
    @Test
    void resolvingNeverModifiesTheInputState() {
        GameState input = eastward(AsciiBoard.state(". . .", "0 . ."));
        Programs.program(input, 0, 100, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertRobots(input, "0 . .");
        assertNotNull(input.robot(0).register(0));
        assertRobots(result.state(), ". 0 .");
    }

    /**
     * The five registers run in order, and every event is stamped with the register it belongs
     * to.
     */
    @Test
    void fiveRegistersRunInOrder() {
        GameState input = eastward(AsciiBoard.state(". . . . . .", "0 . . . . ."));
        Programs.program(input, 0, 100, M1, M1, M1, M1, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertRobots(result.state(), ". . . . . 0");
        List<LoggedEvent> moves = ofType(result, GameEvent.RobotMoved.class);
        assertEquals(List.of(1, 2, 3, 4, 5), moves.stream().map(LoggedEvent::register).toList());
        assertTrue(moves.stream().allMatch(move -> move.phase() == SubPhase.ROBOT_MOVEMENT));
    }

    /**
     * Within a register the robot with the higher card priority moves first, whatever its id.
     */
    @Test
    void higherPriorityCardsMoveFirst() {
        GameState input = AsciiBoard.state(". .\n. .", ". .\n0 1");
        Programs.program(input, 0, 100, M1);
        Programs.program(input, 1, 500, M1);

        TurnResult result = TurnResolver.resolve(input);

        List<Integer> movers = ofType(result, GameEvent.RobotMoved.class).stream()
            .map(entry -> ((GameEvent.RobotMoved) entry.event()).robotId()).toList();
        assertEquals(List.of(1, 0), movers);
    }

    /**
     * Sub-phases happen in the order of design.md 2.4: a card move, then the express belt it lands
     * on, in one register.
     */
    @Test
    void subPhasesRunInTheOrderOfTheRules() {
        GameState input = eastward(AsciiBoard.state(". E . .", "0 . . ."));
        Programs.program(input, 0, 100, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertRobots(result.state(), ". . 0 .");
        List<LoggedEvent> moves = ofType(result, GameEvent.RobotMoved.class);
        assertEquals(SubPhase.ROBOT_MOVEMENT, moves.get(0).phase());
        assertEquals(SubPhase.EXPRESS_BELTS, moves.get(1).phase());
        assertEquals(2, moves.size());
    }

    /**
     * A card that is revealed is logged before it is executed.
     */
    @Test
    void cardsAreRevealedBeforeTheyAreExecuted() {
        GameState input = eastward(AsciiBoard.state(". .", "0 ."));
        Programs.program(input, 0, 100, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertEquals(new GameEvent.RegisterRevealed(0, new Card(M1, 100)), result.events().get(0).event());
        assertEquals(SubPhase.REVEAL, result.events().get(0).phase());
    }

    /**
     * A flag counts only if the robot ends the register on it: walking over it does nothing.
     */
    @Test
    void flagsCountOnlyWhereARegisterEnds() {
        GameState pass = eastward(AsciiBoard.state(". 1 . .", "0 . . ."));
        Programs.program(pass, 0, 100, CardType.MOVE_2);
        assertEquals(0, TurnResolver.resolve(pass).state().robot(0).flagsTouched());

        GameState land = eastward(AsciiBoard.state(". 1 . .", "0 . . ."));
        Programs.program(land, 0, 100, M1);
        TurnResult result = TurnResolver.resolve(land);
        assertEquals(1, result.state().robot(0).flagsTouched());
        assertEquals(SubPhase.CHECKPOINTS, ofType(result, GameEvent.FlagTouched.class).get(0).phase());
    }

    /**
     * Touching the final flag ends the game at the end of that register: later registers are not
     * played and there is no cleanup, so the unplayed cards stay in the registers.
     */
    @Test
    void winningStopsTheTurnAtTheEndOfThatRegister() {
        GameState input = eastward(AsciiBoard.state(". 1 2 .", "0 . . ."));
        Programs.program(input, 0, 100, M1, M1, M1, M1, M1);

        TurnResult result = TurnResolver.resolve(input);

        GameState state = result.state();
        assertTrue(state.isOver());
        assertEquals(0, state.winnerId());
        assertRobots(state, ". . 0 .");
        assertTrue(result.events().stream().noneMatch(entry -> entry.register() >= 3));
        assertNotNull(state.robot(0).register(2));
        assertEquals(0, state.deck().discardPileSize());
    }

    /**
     * A finished game is terminal: resolving its final state again is refused instead of
     * replaying registers on a game that has already been won.
     */
    @Test
    void aFinishedGameCannotBeResolvedAgain() {
        GameState finished = AsciiBoard.state("1", "0");
        finished.endGame(0);

        assertThrows(IllegalStateException.class, () -> TurnResolver.resolve(finished));
    }

    /**
     * A robot destroyed in the first register does nothing in the registers that follow, and its
     * cards are back in the discard pile.
     */
    @Test
    void aDestroyedRobotSkipsTheRestOfTheTurn() {
        GameState input = eastward(AsciiBoard.state(". o . .", "0 . . ."));
        Programs.program(input, 0, 100, M1, M1, M1, M1, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertEquals(RobotStatus.DESTROYED, result.state().robot(0).status());
        assertEquals(1, ofType(result, GameEvent.RobotMoved.class).size());
        assertEquals(1, ofType(result, GameEvent.RegisterRevealed.class).size());
        assertEquals(5, result.state().deck().discardPileSize());
        assertNull(result.state().robot(0).register(4));
    }

    /**
     * When every robot but one is eliminated, the last one wins at the end of that register.
     */
    @Test
    void lastRobotStandingWins() {
        GameState input = AsciiBoard.state(". . o", "0 1 .");
        input.robot(1).setFacing(Direction.EAST);
        input.robot(1).setLives(1);
        Programs.program(input, 1, 100, M1);

        TurnResult result = TurnResolver.resolve(input);

        assertTrue(result.state().isOver());
        assertEquals(0, result.state().winnerId());
        assertEquals(RobotStatus.ELIMINATED, result.state().robot(1).status());
        assertTrue(result.events().contains(new LoggedEvent(1, SubPhase.CHECKPOINTS, new GameEvent.GameEnded(0))));
    }

    /**
     * A powered-down robot ignores its registers but is still carried by a belt.
     */
    @Test
    void poweredDownRobotsDoNothingButRideBelts() {
        GameState input = AsciiBoard.state("> . .", "0 . .");
        input.robot(0).setPoweredDown(true);
        Programs.program(input, 0, 100, CardType.MOVE_3);

        TurnResult result = TurnResolver.resolve(input);

        assertTrue(ofType(result, GameEvent.RegisterRevealed.class).isEmpty());
        assertEquals(new Position(1, 0), result.state().robot(0).position());
    }

    /**
     * After the fifth register the cleanup phase runs: cards are discarded, locked registers keep
     * theirs, and the events are stamped with register 0 and the cleanup sub-phase.
     */
    @Test
    void cleanupRunsAfterTheFifthRegister() {
        GameState input = AsciiBoard.state(".", "0");
        input.robot(0).setDamage(6);
        Programs.program(input, 0, 100, CardType.ROTATE_LEFT, CardType.ROTATE_LEFT, CardType.ROTATE_LEFT,
            CardType.ROTATE_LEFT, CardType.ROTATE_LEFT);

        TurnResult result = TurnResolver.resolve(input);

        Robot robot = result.state().robot(0);
        assertEquals(3, result.state().deck().discardPileSize());
        assertNull(robot.register(0));
        assertNotNull(robot.register(3));
        assertNotNull(robot.register(4));
        assertEquals(Direction.WEST, robot.facing());
    }

    /**
     * The same input always gives the same result: the resolver contains no randomness.
     */
    @Test
    void resolutionIsDeterministic() {
        GameState input = AsciiBoard.state(". . . .\n. . . .", ". . . 1\n0 . . .");
        input.robot(0).setFacing(Direction.EAST);
        input.robot(1).setFacing(Direction.WEST);
        Programs.program(input, 0, 100, M1, M1, CardType.ROTATE_LEFT, M1, M1);
        Programs.program(input, 1, 500, M1, CardType.U_TURN, M1, M1, M1);

        TurnResult first = TurnResolver.resolve(input);
        TurnResult second = TurnResolver.resolve(input);

        assertEquals(first.events(), second.events());
        assertEquals(AsciiBoard.renderRobots(first.state()), AsciiBoard.renderRobots(second.state()));
    }

    /**
     * A whole game turn end to end: deal, program, resolve and respawn never lose or create a
     * card, even when a robot is destroyed on the way.
     */
    @Test
    void aFullTurnConservesAllCards() {
        GameState state = AsciiBoard.state(". . o .", "0 . . 1");
        state.robot(0).setFacing(Direction.EAST);
        state.robot(1).setFacing(Direction.WEST);
        Respawner.respawn(state, Map.of(), new EventLog());

        Map<Integer, List<Card>> hands = Programming.deal(state);
        for (Map.Entry<Integer, List<Card>> hand : hands.entrySet()) {
            Programming.submit(state, hand.getKey(), hand.getValue(), hand.getValue().subList(0, 5), false);
        }
        assertEquals(84, Programs.cardsInPlay(state));

        TurnResult result = TurnResolver.resolve(state);
        assertEquals(84, Programs.cardsInPlay(result.state()));

        Respawner.respawn(result.state(), Map.of(), new EventLog());
        assertEquals(84, Programs.cardsInPlay(result.state()));
    }
}
