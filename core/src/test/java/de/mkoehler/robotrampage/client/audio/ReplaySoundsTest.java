package de.mkoehler.robotrampage.client.audio;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.client.audio.AudioKit.Clip;
import de.mkoehler.robotrampage.client.replay.TurnReplay;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;
import de.mkoehler.robotrampage.testsupport.AsciiBoard;
import de.mkoehler.robotrampage.testsupport.Programs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ReplaySounds} on turns resolved by the real rules engine, so the clips follow the order in which the
 * engine actually logs events, not an order a test assumed.
 *
 * @author Mario Koehler
 */
class ReplaySoundsTest {

    /** This player's robot in every test. */
    private static final int ME = 0;

    /**
     * Resolves one turn and returns the clips each of its beats plays, heard as robot {@link #ME}.
     *
     * @param state the state to resolve; not changed
     * @return the clips, one list per beat, in beat order
     */
    private static List<List<Clip>> sounds(GameState state) {
        List<RobotState> before = new ArrayList<>();
        state.robots().forEach(robot -> before.add(RobotState.of(robot)));
        TurnResult result = TurnResolver.resolve(state);
        TurnReplay replay = new TurnReplay(before, result.events(), id -> "R" + id);
        List<List<Clip>> sounds = new ArrayList<>();
        for (TurnReplay.Beat beat : replay.beats()) {
            Card myCard = beat.register() == 0 ? null : state.robot(ME).register(beat.register() - 1);
            sounds.add(ReplaySounds.forBeat(beat, ME, myCard, new Random(1)));
        }
        return sounds;
    }

    /**
     * Returns every clip a turn plays, whichever beat it is in.
     *
     * @param sounds the clips per beat
     * @return the clips
     */
    private static Set<Clip> all(List<List<Clip>> sounds) {
        Set<Clip> all = new HashSet<>();
        sounds.forEach(all::addAll);
        return all;
    }

    /**
     * Returns the one beat that plays a clip.
     *
     * @param sounds the clips per beat
     * @param clip   the clip
     * @return the clips of that beat
     */
    private static List<Clip> beatWith(List<List<Clip>> sounds, Clip clip) {
        List<List<Clip>> matching = sounds.stream().filter(beat -> beat.contains(clip)).toList();
        assertEquals(1, matching.size(), "exactly one beat plays " + clip + ": " + sounds);
        return matching.get(0);
    }

    /**
     * Faces a robot east.
     *
     * @param state the state
     * @param robot the robot id
     * @return the same state
     */
    private static GameState east(GameState state, int robot) {
        state.robot(robot).setFacing(Direction.EAST);
        return state;
    }

    /**
     * A Move 3 is one drive sound, not one per square.
     */
    @Test
    void myMoveThreeDrivesOnce() {
        GameState state = east(AsciiBoard.state(". . . .", "0 . . ."), ME);
        Programs.program(state, ME, 100, CardType.MOVE_3);

        assertEquals(List.of(List.of(), List.of(Clip.ROBOT_DRIVES_FORWARD)), sounds(state));
    }

    /**
     * Backing up and turning have their own sounds.
     */
    @Test
    void backingUpAndTurning() {
        GameState state = east(AsciiBoard.state(". . .", ". 0 ."), ME);
        Programs.program(state, ME, 100, CardType.BACK_UP, CardType.U_TURN);

        Set<Clip> all = all(sounds(state));

        assertTrue(all.contains(Clip.ROBOT_DRIVES_BACKWARDS));
        assertTrue(all.contains(Clip.ROBOT_TURNS));
        assertFalse(all.contains(Clip.ROBOT_DRIVES_FORWARD));
    }

    /**
     * A move a wall cuts short drives and then hits the wall, in the same beat.
     */
    @Test
    void aWallCuttingAMoveShortIsHeard() {
        GameState state = east(AsciiBoard.state(". . | . .", "0 . . ."), ME);
        Programs.program(state, ME, 100, CardType.MOVE_3);

        assertTrue(beatWith(sounds(state), Clip.ROBOT_DRIVES_FORWARD).contains(Clip.ROBOT_HITS_A_WALL));
    }

    /**
     * A move a pit ends is a fall, not a wall.
     */
    @Test
    void drivingIntoAPitIsAFallNotAWall() {
        GameState state = east(AsciiBoard.state(". o . .", "0 . . ."), ME);
        Programs.program(state, ME, 100, CardType.MOVE_2);

        List<Clip> beat = beatWith(sounds(state), Clip.ROBOT_DRIVES_FORWARD);

        assertTrue(beat.contains(Clip.ROBOT_DROPS_INTO_PIT));
        assertTrue(beat.contains(Clip.ROBOT_DIES));
        assertFalse(beat.contains(Clip.ROBOT_HITS_A_WALL));
    }

    /**
     * Another robot pushing this player's robot into a pit is heard as the push and the fall, in the beat of the other
     * robot's card; its own driving stays silent.
     */
    @Test
    void beingPushedIntoAPitByAnotherRobot() {
        GameState state = east(AsciiBoard.state(". . o", "1 0 ."), 1);
        Programs.program(state, 1, 100, CardType.MOVE_1);

        List<Clip> beat = beatWith(sounds(state), Clip.ROBOT_PUSHES_ANOTHER_ROBOT);

        assertEquals(Set.of(Clip.ROBOT_PUSHES_ANOTHER_ROBOT, Clip.ROBOT_DROPS_INTO_PIT, Clip.ROBOT_DIES), Set.copyOf(beat));
    }

    /**
     * Being pushed into a pit after this player's robot has already acted in the register is still heard in the pusher's
     * beat; this player's own beat only has its own card.
     */
    @Test
    void beingPushedIntoAPitAfterActingFirst() {
        GameState state = east(AsciiBoard.state(". . o", "1 0 ."), 1);
        Programs.program(state, ME, 900, CardType.ROTATE_RIGHT);
        Programs.program(state, 1, 100, CardType.MOVE_1);

        List<List<Clip>> sounds = sounds(state);

        assertEquals(List.of(Clip.ROBOT_TURNS), beatWith(sounds, Clip.ROBOT_TURNS));
        assertEquals(Set.of(Clip.ROBOT_PUSHES_ANOTHER_ROBOT, Clip.ROBOT_DROPS_INTO_PIT, Clip.ROBOT_DIES),
            Set.copyOf(beatWith(sounds, Clip.ROBOT_PUSHES_ANOTHER_ROBOT)));
    }

    /**
     * This player's robot pushing another is heard with its own drive.
     */
    @Test
    void pushingAnotherRobot() {
        GameState state = east(AsciiBoard.state(". . . .", "0 1 . ."), ME);
        Programs.program(state, ME, 100, CardType.MOVE_1);

        assertEquals(Set.of(Clip.ROBOT_DRIVES_FORWARD, Clip.ROBOT_PUSHES_ANOTHER_ROBOT),
            Set.copyOf(beatWith(sounds(state), Clip.ROBOT_DRIVES_FORWARD)));
    }

    /**
     * Other robots driving, riding belts and turning on gears are silent.
     */
    @Test
    void otherRobotsMoveSilently() {
        GameState state = AsciiBoard.state(". . c .\n> . . .\n. . . .", ". . 2 .\n1 . . .\n0 . . 3");
        Programs.program(state, 3, 100, CardType.MOVE_1);

        assertEquals(Set.of(), all(sounds(state)));
    }

    /**
     * Belts, gears and pushers are heard when they move this player's robot.
     */
    @Test
    void boardElementsMovingMyRobot() {
        assertTrue(all(sounds(AsciiBoard.state(". > .", ". 0 ."))).contains(Clip.CONVEYOR_BELT_MOVES));
        assertTrue(all(sounds(AsciiBoard.state(". c .", ". 0 ."))).contains(Clip.ROBOT_IS_TURNED_BY_GEAR));
        GameState pushed = AsciiBoard.state(". . .", "0 . .",
            builder -> builder.pusher(new Position(0, 0), Direction.WEST, 1));
        assertTrue(all(sounds(pushed)).contains(Clip.ROBOT_IS_PUSHED_BY_PUSHER));
    }

    /**
     * A crusher is heard as being crushed, on top of the robot being lost.
     */
    @Test
    void beingCrushed() {
        List<Clip> beat = beatWith(sounds(AsciiBoard.state("x .", "0 .")), Clip.ROBOT_IS_CRUSHED_BY_CRUSHER);

        assertTrue(beat.contains(Clip.ROBOT_DIES));
    }

    /**
     * A repair site's repair is heard; the full repair of powering down is not.
     */
    @Test
    void repairSitesButNotPowerDowns() {
        GameState repaired = AsciiBoard.state("+ .", "0 .");
        repaired.robot(ME).setDamage(2);
        assertEquals(List.of(Clip.ROBOT_IS_REPAIRED_ON_HEALTH_TILE), beatWith(sounds(repaired),
            Clip.ROBOT_IS_REPAIRED_ON_HEALTH_TILE));

        GameState poweringDown = AsciiBoard.state(". .", "0 .");
        poweringDown.robot(ME).setDamage(2);
        poweringDown.robot(ME).setPowerDownAnnounced(true);
        assertFalse(all(sounds(poweringDown)).contains(Clip.ROBOT_IS_REPAIRED_ON_HEALTH_TILE));
    }

    /**
     * Any robot touching a flag beeps, with one of the four beeps.
     */
    @Test
    void anyRobotTouchingAFlagBeeps() {
        GameState state = AsciiBoard.state("1 .", "1 0");

        Set<Clip> beeps = Set.of(Clip.BEEP_1, Clip.BEEP_2, Clip.BEEP_3, Clip.BEEP_4);
        List<List<Clip>> beeping = sounds(state).stream().filter(beat -> beat.stream().anyMatch(beeps::contains))
            .toList();

        assertEquals(1, beeping.size());
        assertEquals(1, beeping.get(0).size(), "one beep, nothing else");
    }

    /**
     * A laser volley is heard whoever it hits.
     */
    @Test
    void laserVolleysAreHeardForEveryone() {
        GameState state = AsciiBoard.state(". . .\n. . .", ". 1 .\n0 . .",
            builder -> builder.laser(new Position(0, 1), Direction.WEST, 1));

        assertTrue(all(sounds(state)).contains(Clip.LASER));
    }
}
