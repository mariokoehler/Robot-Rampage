package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.game.Standings.FlagTouch;
import de.mkoehler.robotrampage.client.game.Standings.Row;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.RobotStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the ranking of the players at the end of a game and the words the Game Over screen says about them.
 *
 * @author Mario Koehler
 */
class StandingsTest {

    private static final List<PlayerInfo> PLAYERS = List.of(new PlayerInfo(0, "Sophie", true, true, true),
        new PlayerInfo(1, "Mario", true, true, false), new PlayerInfo(2, "Kenji", true, true, false),
        new PlayerInfo(3, "Lukasz", true, true, false), new PlayerInfo(4, "Amira", true, true, false));

    /**
     * Builds the final state of a robot.
     *
     * @param seat   the seat
     * @param flags  the flags touched
     * @param lives  the lives left
     * @param status the status
     * @return the state
     */
    private static RobotState robot(int seat, int flags, int lives, RobotStatus status) {
        return new RobotState(seat, new Position(seat, 0), Direction.NORTH, 0, lives, flags, new Position(seat, 0), status,
            false, false);
    }

    /**
     * Works out the standings of a game of three flags.
     *
     * @param winner the winning seat
     * @param me     the seat of the viewer
     * @param robots the final states
     * @param flags  the flags the client saw touched
     * @param out    the turns of the eliminations the client saw
     * @param left   the seats that left
     * @return the standings
     */
    private static Standings standings(int winner, int me, List<RobotState> robots, Map<Integer, FlagTouch> flags,
                                       Map<Integer, Integer> out, Set<Integer> left) {
        return Standings.of(PLAYERS, robots, winner, me, 3, 3, flags, out, left);
    }

    /**
     * The winner is first whatever the others have, and the rest rank by flags, then lives, then seat.
     */
    @Test
    void theWinnerIsFirstThenFlagsThenLivesThenSeat() {
        List<RobotState> robots = List.of(robot(0, 3, 1, RobotStatus.ACTIVE), robot(1, 2, 2, RobotStatus.ACTIVE),
            robot(2, 2, 3, RobotStatus.ACTIVE), robot(3, 2, 3, RobotStatus.ACTIVE), robot(4, 1, 3, RobotStatus.ACTIVE));

        List<Row> rows = standings(0, 1, robots, Map.of(), Map.of(), Set.of()).rows();

        assertEquals(List.of(0, 2, 3, 1, 4), rows.stream().map(Row::seat).toList());
        assertEquals(List.of(1, 2, 3, 4, 5), rows.stream().map(Row::rank).toList());
        assertEquals(List.of("", "One flag short", "One flag short", "One flag short", ""),
            rows.stream().map(Row::detail).toList());
        assertTrue(rows.get(0).winner());
        assertTrue(rows.get(3).you());
        assertFalse(rows.get(0).you());
    }

    /**
     * A winner who did not touch every flag, because the others are out, still ranks first.
     */
    @Test
    void aWinnerWithFewerFlagsStillRanksFirst() {
        List<RobotState> robots = List.of(robot(0, 1, 2, RobotStatus.ACTIVE), robot(1, 2, 0, RobotStatus.ELIMINATED));

        Standings standings = standings(0, 0, robots, Map.of(), Map.of(1, 5), Set.of());

        assertEquals(0, standings.rows().get(0).seat());
        assertEquals("Last robot standing", standings.rows().get(0).detail());
        assertEquals("Sophie wins!", standings.headline());
        assertEquals("The last robot standing. Everybody else can go again from the lobby.", standings.subline());
    }

    /**
     * The winner's line says where the last flag was touched when the client saw it; the headline names the flag.
     */
    @Test
    void theWinnerTouchedTheLastFlagInATurnAndRegister() {
        List<RobotState> robots = List.of(robot(0, 3, 3, RobotStatus.ACTIVE), robot(1, 2, 3, RobotStatus.ACTIVE));

        Standings standings = standings(0, 1, robots, Map.of(0, new FlagTouch(3, 11, 4)), Map.of(), Set.of());

        assertEquals("Touched flag 3 in turn 11, register 4", standings.rows().get(0).detail());
        assertEquals("First to touch flag 3. Everybody else can go again from the lobby.", standings.subline());
        assertEquals("One flag short", standings.rows().get(1).detail());
    }

    /**
     * A player who did not see the game has no turn to name, and the screen says nothing it cannot know instead of guessing.
     */
    @Test
    void whatWasNotSeenIsLeftOut() {
        List<RobotState> robots = List.of(robot(0, 3, 3, RobotStatus.ACTIVE), robot(1, 0, 0, RobotStatus.ELIMINATED),
            robot(2, 1, 3, RobotStatus.ACTIVE));

        List<Row> rows = standings(0, 2, robots, Map.of(), Map.of(), Set.of()).rows();

        assertEquals("", rows.get(0).detail());
        assertEquals("Eliminated", rows.get(2).detail());
        assertEquals("", rows.get(1).detail());
    }

    /**
     * An eliminated robot and a player who left have no lives in the standings, and the two are told apart.
     */
    @Test
    void eliminatedAndLeftPlayersHaveNoLives() {
        List<RobotState> robots = List.of(robot(0, 3, 3, RobotStatus.ACTIVE), robot(1, 1, 0, RobotStatus.ELIMINATED),
            robot(2, 1, 2, RobotStatus.ELIMINATED), robot(3, 1, 3, RobotStatus.ACTIVE));

        List<Row> rows = standings(0, 0, robots, Map.of(), Map.of(1, 9), Set.of(2)).rows();

        assertEquals(List.of(0, 3, 1, 2), rows.stream().map(Row::seat).toList());
        assertEquals("Eliminated in turn 9", rows.get(2).detail());
        assertEquals("Left the game", rows.get(3).detail());
        assertEquals(0, rows.get(3).lives());
        assertEquals(3, rows.get(1).lives());
    }

    /**
     * A game that ends with nobody on the board has no winner and says so.
     */
    @Test
    void aGameWithoutAWinner() {
        List<RobotState> robots = new ArrayList<>(List.of(robot(0, 1, 0, RobotStatus.ELIMINATED),
            robot(1, 2, 0, RobotStatus.ELIMINATED)));

        Standings standings = standings(GameEvent.NO_ROBOT, 0, robots, Map.of(), Map.of(), Set.of());

        assertFalse(standings.hasWinner());
        assertEquals("No winner", standings.headline());
        assertEquals(1, standings.rows().get(0).seat());
        assertFalse(standings.rows().get(0).winner());
    }
}
