package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the cleanup phase (design.md 2.3, 2.5, 2.8, 2.9): repair sites, power-down, and
 * discarding cards while keeping locked registers.
 *
 * @author Mario Koehler
 */
class CleanupResolverTest {

    /**
     * Runs the cleanup phase.
     *
     * @param state the game state to mutate
     * @return the events of the phase
     */
    private static EventLog cleanup(GameState state) {
        EventLog log = new EventLog();
        new CleanupResolver(state, log).run();
        return log;
    }

    /**
     * Fills all five registers of a robot with cards.
     *
     * @param state   the game state
     * @param robotId the robot
     */
    private static void fillRegisters(GameState state, int robotId) {
        Programs.program(state, robotId, 100, CardType.MOVE_1, CardType.MOVE_1, CardType.MOVE_1, CardType.MOVE_1,
            CardType.MOVE_1);
    }

    /**
     * A robot ending the turn on a repair site loses one damage and moves its archive marker
     * there.
     */
    @Test
    void repairSiteHealsOneDamageAndMovesTheArchiveMarker() {
        GameState state = AsciiBoard.state(". +", "0 .");
        state.robot(0).setPosition(new Position(1, 0));
        state.robot(0).setDamage(3);

        EventLog log = cleanup(state);

        assertEquals(2, state.robot(0).damage());
        assertEquals(new Position(1, 0), state.robot(0).archiveMarker());
        assertEquals(List.of(
            new GameEvent.RobotRepaired(0, 1, 2),
            new GameEvent.ArchiveMarkerMoved(0, new Position(1, 0))), log.events());
    }

    /**
     * An undamaged robot on a repair site still gets its archive marker moved, but nothing is
     * healed.
     */
    @Test
    void repairSiteMovesTheArchiveMarkerOfAnUndamagedRobot() {
        GameState state = AsciiBoard.state("+ .", "0 .");
        state.robot(0).setArchiveMarker(new Position(1, 0));

        EventLog log = cleanup(state);

        assertEquals(new Position(0, 0), state.robot(0).archiveMarker());
        assertEquals(1, log.events().size());
    }

    /**
     * All cards in unlocked registers go to the discard pile and the registers are emptied.
     */
    @Test
    void unlockedCardsAreDiscarded() {
        GameState state = AsciiBoard.state(".", "0");
        fillRegisters(state, 0);

        cleanup(state);

        assertEquals(5, state.deck().discardPileSize());
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            assertNull(state.robot(0).register(index));
        }
    }

    /**
     * Cards in registers that are still locked by damage stay where they are.
     */
    @Test
    void lockedRegistersKeepTheirCards() {
        GameState state = AsciiBoard.state(".", "0");
        fillRegisters(state, 0);
        state.robot(0).setDamage(7);

        cleanup(state);

        assertEquals(2, state.deck().discardPileSize());
        assertNull(state.robot(0).register(0));
        assertNull(state.robot(0).register(1));
        assertNotNull(state.robot(0).register(2));
        assertNotNull(state.robot(0).register(3));
        assertNotNull(state.robot(0).register(4));
    }

    /**
     * A register that a repair has just unlocked releases its card straight away: going from 7
     * to 6 damage frees register 3 but leaves 4 and 5 locked.
     */
    @Test
    void repairReleasesTheCardOfAFreshlyUnlockedRegister() {
        GameState state = AsciiBoard.state("+", "0");
        fillRegisters(state, 0);
        state.robot(0).setDamage(7);

        cleanup(state);

        assertEquals(6, state.robot(0).damage());
        assertEquals(3, state.deck().discardPileSize());
        assertNull(state.robot(0).register(2));
        assertNotNull(state.robot(0).register(3));
        assertNotNull(state.robot(0).register(4));
    }

    /**
     * A robot that announced a power-down shuts down, is fully repaired, and its formerly locked
     * cards are released too.
     */
    @Test
    void announcedPowerDownShutsTheRobotDownAndRepairsItFully() {
        GameState state = AsciiBoard.state(".", "0");
        fillRegisters(state, 0);
        state.robot(0).setDamage(7);
        state.robot(0).setPowerDownAnnounced(true);

        EventLog log = cleanup(state);

        Robot robot = state.robot(0);
        assertTrue(robot.isPoweredDown());
        assertFalse(robot.isPowerDownAnnounced());
        assertEquals(0, robot.damage());
        assertEquals(5, state.deck().discardPileSize());
        assertEquals(List.of(
            new GameEvent.RobotPoweredDown(0),
            new GameEvent.RobotRepaired(0, 7, 0)), log.events());
    }

    /**
     * A robot that was powered down comes back on at the end of the turn, unless it announced
     * another power-down.
     */
    @Test
    void poweredDownRobotsComeBackUnlessTheyAnnounceAgain() {
        GameState comesBack = AsciiBoard.state(".", "0");
        comesBack.robot(0).setPoweredDown(true);
        EventLog log = cleanup(comesBack);
        assertFalse(comesBack.robot(0).isPoweredDown());
        assertEquals(List.of(new GameEvent.RobotPoweredUp(0)), log.events());

        GameState staysDown = AsciiBoard.state(".", "0");
        staysDown.robot(0).setPoweredDown(true);
        staysDown.robot(0).setPowerDownAnnounced(true);
        assertTrue(cleanup(staysDown).events().isEmpty());
        assertTrue(staysDown.robot(0).isPoweredDown());
        assertFalse(staysDown.robot(0).isPowerDownAnnounced());
    }

    /**
     * Robots that are not on the board are left alone.
     */
    @Test
    void destroyedRobotsAreIgnored() {
        GameState state = AsciiBoard.state(".", "0");
        state.robot(0).setStatus(RobotStatus.DESTROYED);
        state.robot(0).setPosition(null);
        state.robot(0).setPowerDownAnnounced(true);

        assertTrue(cleanup(state).events().isEmpty());
    }
}
