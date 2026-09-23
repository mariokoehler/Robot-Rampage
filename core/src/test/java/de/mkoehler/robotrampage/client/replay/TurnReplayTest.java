package de.mkoehler.robotrampage.client.replay;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.replay.TurnReplay.Beat;
import de.mkoehler.robotrampage.client.replay.TurnReplay.Line;
import de.mkoehler.robotrampage.client.replay.TurnReplay.LineKind;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.EventLog;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.LaserSource;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Respawner;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.SubPhase;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TurnReplay}: how events are cut into moments, what is said about them, how the clock moves, and above
 * all that a replay ends where the rules engine ended.
 *
 * @author Mario Koehler
 */
class TurnReplayTest {

    private static final Card MOVE_2 = new Card(CardType.MOVE_2, 700);
    private static final Card MOVE_3 = new Card(CardType.MOVE_3, 830);
    private static final Card RIGHT = new Card(CardType.ROTATE_RIGHT, 240);

    private static String name(int robotId) {
        return List.of("Sophie", "Mario", "Kenji", "Amira").get(robotId);
    }

    private static RobotState robot(int id, int x, int y, Direction facing) {
        return new RobotState(id, new Position(x, y), facing, 0, 3, 0, new Position(x, y), RobotStatus.ACTIVE, false, false);
    }

    private static LoggedEvent at(int register, SubPhase phase, GameEvent event) {
        return new LoggedEvent(register, phase, event);
    }

    private static TurnReplay replay(List<RobotState> before, LoggedEvent... events) {
        return new TurnReplay(before, List.of(events), TurnReplayTest::name);
    }

    /**
     * The cards revealed in a register are one moment, and the list of cards shows them from the highest priority to the
     * lowest, which is the order they act in.
     */
    @Test
    void revealedCardsAreListedByPriority() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH), robot(1, 2, 1, Direction.NORTH)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(1, MOVE_2)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_3)));

        assertEquals(1, replay.beats().size());
        assertEquals("Cards revealed", replay.beats().get(0).lines().get(0).title());
        assertEquals(List.of(new TurnReplay.Play(0, MOVE_3), new TurnReplay.Play(1, MOVE_2)), replay.plays());
        assertEquals(1, replay.register());
        assertEquals(SubPhase.REVEAL, replay.phase());
    }

    /**
     * Every card played in the movement step is a moment of its own, worded with the card and its priority, and a robot that
     * is pushed along is named in the same moment.
     */
    @Test
    void everyCardIsAMomentAndPushesJoinIt() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH), robot(1, 1, 2, Direction.NORTH)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_3)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(1, RIGHT)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.CARD)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(1, new Position(1, 2), new Position(1, 3), MoveCause.PUSHED)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 2), new Position(1, 3), MoveCause.CARD)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotRotated(1, Direction.NORTH, Direction.EAST, RotationCause.CARD)));

        List<Beat> beats = replay.beats();

        assertEquals(3, beats.size(), "the reveal, Sophie's move with the push, and Mario's turn");
        assertEquals(List.of(new Line(LineKind.CARD, "Sophie plays Move 3", "Priority 830"),
            new Line(LineKind.PUSH, "Mario is pushed", "By another robot")), beats.get(1).lines());
        assertEquals("Mario plays Rotate Right", beats.get(2).lines().get(0).title());
    }

    /**
     * A laser volley is a single moment: damage of one robot from several lasers is merged and says where it came from, only
     * the beams that actually hit somebody are shown (a third laser in this volley misses and is dropped), and the damage
     * tags are those of the volley.
     */
    @Test
    void aLaserVolleyIsOneMomentWithMergedDamageAndOnlyHittingBeamsShown() {
        Position a = new Position(0, 3);
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH), robot(2, 4, 3, Direction.WEST)),
            at(3, SubPhase.LASERS, new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, a, Direction.EAST,
                new Position(4, 3), 2, 1)),
            at(3, SubPhase.LASERS, new GameEvent.LaserFired(LaserSource.ROBOT, 0, new Position(1, 1), Direction.NORTH,
                new Position(1, 3), GameEvent.NO_ROBOT, 1)),
            at(3, SubPhase.LASERS, new GameEvent.LaserFired(LaserSource.ROBOT, 0, new Position(4, 1), Direction.NORTH,
                new Position(4, 3), 2, 1)),
            at(3, SubPhase.LASERS, new GameEvent.RobotDamaged(2, 1, 4, LaserSource.BOARD)),
            at(3, SubPhase.LASERS, new GameEvent.RobotDamaged(2, 1, 5, LaserSource.ROBOT)));

        Beat beat = replay.beats().get(0);

        assertEquals(new Line(LineKind.LASER, "Laser volley: 1 robot hit", "Board lasers and robot lasers fire together"),
            beat.lines().get(0));
        assertEquals(new Line(LineKind.LASER, "Kenji takes 2 damage", "Board laser, then Sophie's laser · damage 5 of 9"),
            beat.lines().get(1));
        assertEquals(2, beat.beams().size(), "the middle laser missed and is not drawn");
        assertTrue(beat.beams().get(0).board());
        assertEquals(Map.of(2, 2), beat.tags());
        assertEquals(2, replay.frame().poses().stream().filter(pose -> pose.tag() == 2 || pose.tag() == 0).count());
        assertEquals(2, replay.frame().poses().stream().filter(pose -> pose.seat() == 2).findFirst().orElseThrow().tag());
    }

    /**
     * A volley that hits nobody produces no beat at all — nothing worth animating or a sound for happened — and a robot
     * that is lost in a volley that does hit somebody is named.
     */
    @Test
    void aVolleyWithoutHitsIsSkippedAndALostRobotIsNamed() {
        TurnReplay none = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(2, SubPhase.LASERS, new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 0),
                Direction.EAST, new Position(3, 0), GameEvent.NO_ROBOT, 1)));
        assertTrue(none.beats().isEmpty(), "a laser volley that hits nobody is skipped entirely");

        TurnReplay lost = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(2, SubPhase.LASERS, new GameEvent.LaserFired(LaserSource.BOARD, GameEvent.NO_ROBOT, new Position(0, 0),
                Direction.EAST, new Position(3, 0), 0, 1)),
            at(2, SubPhase.LASERS, new GameEvent.RobotDamaged(0, 1, 10, LaserSource.BOARD)),
            at(2, SubPhase.LASERS, new GameEvent.RobotDestroyed(0, DestructionCause.DAMAGE)));
        assertTrue(lost.beats().get(0).lines().contains(new Line(LineKind.DESTROYED, "Sophie is destroyed", "Too much damage")));
    }

    /**
     * Steps in which everything happens together, such as belts and gears, are one moment with a line for every robot.
     */
    @Test
    void beltsAndGearsHappenTogether() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH), robot(1, 2, 1, Direction.NORTH)),
            at(4, SubPhase.ALL_BELTS, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.BELT)),
            at(4, SubPhase.ALL_BELTS, new GameEvent.RobotMoved(1, new Position(2, 1), new Position(2, 2), MoveCause.BELT)),
            at(4, SubPhase.GEARS, new GameEvent.RobotRotated(0, Direction.NORTH, Direction.WEST, RotationCause.GEAR)));

        assertEquals(2, replay.beats().size());
        assertEquals(2, replay.beats().get(0).lines().size());
        assertEquals("Sophie rides a belt", replay.beats().get(0).lines().get(0).title());
        assertEquals("Sophie rotates left", replay.beats().get(1).lines().get(0).title());
        assertEquals("Gear · register 4", replay.beats().get(1).lines().get(0).detail());
    }

    /**
     * Touching a flag and ending the game are told in words.
     */
    @Test
    void flagsAndTheEndOfTheGame() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(5, SubPhase.CHECKPOINTS, new GameEvent.FlagTouched(0, 3, new Position(1, 1))),
            at(5, SubPhase.CHECKPOINTS, new GameEvent.ArchiveMarkerMoved(0, new Position(1, 1))),
            at(5, SubPhase.CHECKPOINTS, new GameEvent.GameEnded(0)));

        List<Line> lines = replay.beats().get(0).lines();

        assertEquals(2, lines.size(), "the archive marker is folded into the flag");
        assertEquals("Sophie touches flag 3", lines.get(0).title());
        assertEquals("Sophie wins the game", lines.get(1).title());
    }

    /**
     * The clock moves through the moments at the speed it is given, and the robots are between their squares in the middle
     * of a moment.
     */
    @Test
    void theClockMovesRobotsBetweenSquares() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 3), MoveCause.CARD)));
        float duration = replay.beats().get(0).duration();

        assertEquals(1f, replay.frame().poses().get(0).y());
        replay.advance(duration / 2f);
        assertEquals(2f, replay.frame().poses().get(0).y(), 0.001f);
        assertFalse(replay.isDone());
        replay.advance(duration);
        assertTrue(replay.isDone());
        assertEquals(3f, replay.frame().poses().get(0).y());
        assertTrue(replay.frame().beams().isEmpty());
    }

    /**
     * Time carries over from one moment into the next, and the position in the turn follows: register, step and moment.
     */
    @Test
    void timeCarriesOverAndTheStepIsTracked() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_2)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.CARD)),
            at(2, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_3)));
        float first = replay.beats().get(0).duration();

        replay.advance(first + 0.01f);

        assertEquals(1, replay.beatIndex());
        assertEquals(SubPhase.ROBOT_MOVEMENT, replay.phase());
        replay.advance(replay.beats().get(1).duration());
        assertEquals(2, replay.register());
        assertEquals(SubPhase.REVEAL, replay.phase());
        assertEquals(1, replay.plays().size());
        assertEquals(MOVE_3, replay.plays().get(0).card());
    }

    /**
     * Skipping goes straight to the end, and a turn without events is over at once.
     */
    @Test
    void skippingAndEmptyTurns() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.CARD)));

        replay.skipToEnd();

        assertTrue(replay.isDone());
        assertEquals(2f, replay.frame().poses().get(0).y());
        TurnReplay empty = replay(List.of(robot(0, 1, 1, Direction.NORTH)));
        assertTrue(empty.isDone());
        assertEquals(1, empty.frame().poses().size());
        assertNull(empty.currentBeat());
        assertEquals(0f, empty.totalDuration());
    }

    /**
     * Robots turn the short way and the way the card says: right turns clockwise, left counter-clockwise, a U-turn half a
     * circle.
     */
    @Test
    void robotsTurnTheRightWay() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(1, SubPhase.GEARS, new GameEvent.RobotRotated(0, Direction.NORTH, Direction.EAST, RotationCause.GEAR)),
            at(2, SubPhase.GEARS, new GameEvent.RobotRotated(0, Direction.EAST, Direction.NORTH, RotationCause.GEAR)),
            at(3, SubPhase.GEARS, new GameEvent.RobotRotated(0, Direction.NORTH, Direction.SOUTH, RotationCause.GEAR)));

        replay.advance(replay.beats().get(0).duration() / 2f);
        assertEquals(-45f, replay.frame().poses().get(0).rotation(), 0.01f);
        replay.advance(replay.beats().get(0).duration() / 2f + replay.beats().get(1).duration());
        assertEquals(0f, replay.frame().poses().get(0).rotation(), 0.01f);
        replay.skipToEnd();
        assertEquals(-180f, replay.frame().poses().get(0).rotation(), 0.01f);
        assertEquals(Direction.SOUTH, TurnReplay.facingOf(-180f));
        assertEquals(Direction.EAST, TurnReplay.facingOf(-90f));
        assertEquals(Direction.WEST, TurnReplay.facingOf(90f));
        assertEquals(Direction.NORTH, TurnReplay.facingOf(360f));
    }

    /**
     * A robot that is destroyed fades out and is gone at the end of its moment.
     */
    @Test
    void aDestroyedRobotFadesOut() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(2, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.CARD)),
            at(2, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotDestroyed(0, DestructionCause.PIT)));

        replay.advance(replay.beats().get(0).duration() / 2f);
        RobotPose half = replay.frame().poses().get(0);
        assertEquals(0.5f, half.alpha(), 0.001f);
        replay.skipToEnd();

        assertTrue(replay.frame().poses().isEmpty());
    }

    /**
     * The list of what happened shows the newest moment first and only the register being played.
     */
    @Test
    void theFeedIsNewestFirstAndPerRegister() {
        TurnReplay replay = replay(List.of(robot(0, 1, 1, Direction.NORTH)),
            at(1, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_2)),
            at(1, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 1), new Position(1, 2), MoveCause.CARD)),
            at(2, SubPhase.REVEAL, new GameEvent.RegisterRevealed(0, MOVE_3)),
            at(2, SubPhase.ROBOT_MOVEMENT, new GameEvent.RobotMoved(0, new Position(1, 2), new Position(1, 3), MoveCause.CARD)));

        replay.advance(replay.beats().get(0).duration() + replay.beats().get(1).duration() + 0.01f);
        assertEquals(List.of("Cards revealed"), replay.feed().stream().map(Line::title).toList());
        replay.advance(replay.beats().get(2).duration());
        assertEquals(List.of("Sophie plays Move 3", "Cards revealed"), replay.feed().stream().map(Line::title).toList());
    }

    /**
     * The main check: a replay of a real turn ends with every robot where the rules engine put it, facing the way it faces,
     * and with lost robots gone. Many random games on the first board, every turn.
     */
    @Test
    void aReplayEndsWhereTheRulesEnded() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        int turnsChecked = 0;
        for (long seed = 1; seed <= 12; seed++) {
            GameState state = newGame(board, seed);
            Random random = new Random(seed);
            for (int turn = 1; turn <= 40 && !state.isOver(); turn++) {
                respawn(state, random);
                program(state, random);
                List<RobotState> before = new ArrayList<>();
                state.robots().forEach(robot -> before.add(RobotState.of(robot)));
                TurnResult result = TurnResolver.resolve(state);
                state = result.state();

                TurnReplay replay = new TurnReplay(before, result.events(), id -> "R" + id);
                float elapsed = 0f;
                while (!replay.isDone()) {
                    replay.advance(0.05f);
                    elapsed += 0.05f;
                    assertTrue(elapsed < replay.totalDuration() + 1f, "the replay must finish");
                }
                assertMatches(state, replay, "game " + seed + ", turn " + turn);
                turnsChecked++;
            }
        }
        assertTrue(turnsChecked > 100);
    }

    /**
     * Skipping at any moment lands in the same end state as playing through.
     */
    @Test
    void skippingLandsInTheSameEndState() {
        Board board = BoardLoader.loadResource("boards/proving-grounds.json").board();
        GameState state = newGame(board, 5);
        Random random = new Random(5);
        respawn(state, random);
        program(state, random);
        List<RobotState> before = new ArrayList<>();
        state.robots().forEach(robot -> before.add(RobotState.of(robot)));
        TurnResult result = TurnResolver.resolve(state);

        TurnReplay played = new TurnReplay(before, result.events(), id -> "R" + id);
        TurnReplay skipped = new TurnReplay(before, result.events(), id -> "R" + id);
        skipped.advance(1.3f);
        skipped.skipToEnd();
        while (!played.isDone()) {
            played.advance(0.1f);
        }

        assertEquals(played.frame().poses(), skipped.frame().poses());
        assertNotNull(skipped.frame());
    }

    private static void assertMatches(GameState state, TurnReplay replay, String context) {
        Map<Integer, RobotPose> poses = new HashMap<>();
        replay.frame().poses().forEach(pose -> poses.put(pose.seat(), pose));
        for (Robot robot : state.robots()) {
            RobotPose pose = poses.get(robot.id());
            if (robot.isActive()) {
                assertNotNull(pose, context + ": robot " + robot.id() + " should be on the board");
                assertEquals(robot.position().x(), pose.x(), 0.001f, context + ": x of " + robot.id());
                assertEquals(robot.position().y(), pose.y(), 0.001f, context + ": y of " + robot.id());
                assertEquals(robot.facing(), TurnReplay.facingOf(pose.rotation()), context + ": facing of " + robot.id());
            } else {
                assertNull(pose, context + ": robot " + robot.id() + " should be gone");
            }
        }
    }

    private static GameState newGame(Board board, long seed) {
        List<Robot> robots = new ArrayList<>();
        for (int id = 0; id < board.startSquares().size(); id++) {
            StartSquare start = board.startSquares().get(id);
            robots.add(new Robot(id, start.position(), start.facing()));
        }
        return new GameState(board, robots, Deck.standard(seed));
    }

    private static void respawn(GameState state, Random random) {
        Map<Integer, Direction> facings = new HashMap<>();
        for (Robot robot : state.robots()) {
            facings.put(robot.id(), Direction.values()[random.nextInt(4)]);
        }
        Respawner.respawn(state, facings, new EventLog());
    }

    private static void program(GameState state, Random random) {
        for (Map.Entry<Integer, List<Card>> entry : Programming.deal(state).entrySet()) {
            Robot robot = state.robot(entry.getKey());
            List<Card> shuffled = new ArrayList<>(entry.getValue());
            Collections.shuffle(shuffled, random);
            int unlocked = Robot.REGISTER_COUNT - robot.lockedRegisterCount();
            Programming.submit(state, robot.id(), entry.getValue(), shuffled.subList(0, unlocked), random.nextInt(10) == 0);
        }
    }
}
