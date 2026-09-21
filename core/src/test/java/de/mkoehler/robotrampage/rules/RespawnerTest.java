package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how destroyed robots re-enter the board (design.md 2.9), including the cases
 * where their archive marker is taken.
 *
 * @author Mario Koehler
 */
class RespawnerTest {

    /**
     * Destroys a robot through the normal path so its destruction order is recorded.
     *
     * @param state the game state
     * @param id    the robot to destroy
     */
    private static void destroy(GameState state, int id) {
        Destruction.destroy(state, state.robot(id), DestructionCause.PIT, new EventLog());
    }

    /**
     * Runs the respawn phase.
     *
     * @param state   the game state to mutate
     * @param facings the chosen facings by robot id
     * @return the events of the phase
     */
    private static EventLog respawn(GameState state, Map<Integer, Direction> facings) {
        EventLog log = new EventLog();
        Respawner.respawn(state, facings, log);
        return log;
    }

    /**
     * A destroyed robot re-enters on its archive marker, undamaged, facing the chosen direction.
     */
    @Test
    void robotReturnsToItsArchiveMarkerUndamagedFacingItsChoice() {
        GameState state = AsciiBoard.state(". . .", ". 0 .");
        state.robot(0).setDamage(8);
        state.robot(0).setArchiveMarker(new Position(2, 0));
        destroy(state, 0);

        EventLog log = respawn(state, Map.of(0, Direction.WEST));

        Robot robot = state.robot(0);
        assertEquals(RobotStatus.ACTIVE, robot.status());
        assertEquals(new Position(2, 0), robot.position());
        assertEquals(Direction.WEST, robot.facing());
        assertEquals(0, robot.damage());
        assertEquals(List.of(new GameEvent.RobotRespawned(0, new Position(2, 0), Direction.WEST)), log.events());
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            org.junit.jupiter.api.Assertions.assertNull(robot.register(index));
        }
    }

    /**
     * Without a chosen facing the robot keeps the direction it had when it was destroyed.
     */
    @Test
    void withoutAChoiceTheRobotKeepsItsFacing() {
        GameState state = AsciiBoard.state(".", "0");
        state.robot(0).setFacing(Direction.SOUTH);
        destroy(state, 0);

        respawn(state, Map.of());

        assertEquals(Direction.SOUTH, state.robot(0).facing());
    }

    /**
     * Eliminated robots do not return.
     */
    @Test
    void eliminatedRobotsStayOut() {
        GameState state = AsciiBoard.state(".", "0");
        state.robot(0).setLives(1);
        destroy(state, 0);

        EventLog log = respawn(state, Map.of());

        assertEquals(RobotStatus.ELIMINATED, state.robot(0).status());
        assertTrue(log.events().isEmpty());
    }

    /**
     * If the archive square is occupied, the robot appears on the nearest free square; among
     * equally near squares north is tried first.
     */
    @Test
    void occupiedArchiveSquareSendsTheRobotToTheNearestFreeSquare() {
        GameState state = AsciiBoard.state(". . .\n. . .\n. . .", ". . .\n. 1 .\n. . .");
        state.robots().add(new Robot(0, new Position(1, 1), Direction.NORTH));
        state.robots().sort(java.util.Comparator.comparingInt(Robot::id));
        state.robot(0).setStatus(RobotStatus.DESTROYED);
        state.robot(0).setPosition(null);
        state.robot(0).setDestructionOrder(state.nextDestructionOrder());

        respawn(state, Map.of());

        assertEquals(new Position(1, 2), state.robot(0).position());
    }

    /**
     * A pit is never a place to return to; the search moves on to the next free square.
     */
    @Test
    void pitsAreNotFreeSquares() {
        GameState state = AsciiBoard.state("o\n.", "0\n.");
        state.robot(0).setPosition(new Position(0, 1));
        state.robot(0).setArchiveMarker(new Position(0, 1));
        destroy(state, 0);

        respawn(state, Map.of());

        assertEquals(new Position(0, 0), state.robot(0).position());
    }

    /**
     * When two robots return to the same archive square, the one destroyed first gets it and
     * the other is placed next to it.
     */
    @Test
    void theRobotDestroyedFirstGetsTheArchiveSquare() {
        GameState state = AsciiBoard.state(". . .", "0 1 .");
        state.robot(0).setArchiveMarker(new Position(1, 0));
        state.robot(1).setArchiveMarker(new Position(1, 0));
        destroy(state, 1);
        destroy(state, 0);

        respawn(state, Map.of());

        assertEquals(new Position(1, 0), state.robot(1).position());
        assertTrue(state.robot(0).position().equals(new Position(2, 0)) || state.robot(0).position().equals(new Position(0, 0)));
    }
}
