package de.mkoehler.robotrampage.client.replay;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.client.board.Beam;
import de.mkoehler.robotrampage.client.board.BoardGeometry;
import de.mkoehler.robotrampage.client.board.RobotPose;
import de.mkoehler.robotrampage.client.game.CardLook;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.LaserSource;
import de.mkoehler.robotrampage.rules.SubPhase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * A turn played back: the events the server sent for the turn, cut into moments ("beats") that each take a short time, and a
 * clock that moves through them. At any time it says where every robot is, which beams are shining, what has just happened
 * in words, and which register and step the turn has reached.
 * <p>
 * The replay has no graphics and never runs the rules. It starts from the robot states before the turn and applies the
 * events one beat at a time, so the state at the end is the state the server reached. Everything that happens together in
 * the rules (all lasers, all belts of a step) is one beat; the robots' cards are one beat each, in the order they are played.
 *
 * @author Mario Koehler
 */
public final class TurnReplay {

    /**
     * What kind of thing a line of the "what happened" list is about, which picks its color.
     */
    public enum LineKind {
        /** A card played. */
        CARD,
        /** Lasers and the damage they do. */
        LASER,
        /** Belts. */
        BELT,
        /** Gears. */
        GEAR,
        /** Pushers and robots pushed. */
        PUSH,
        /** Flags and the end of the game. */
        FLAG,
        /** A robot lost. */
        DESTROYED,
        /** Repairs and power. */
        REPAIR
    }

    /**
     * One line of the list of what happened.
     *
     * @param kind   what it is about
     * @param title  what happened, for example {@code Kenji takes 2 damage}
     * @param detail how or why, for example {@code Board laser, then Sophie · damage 5 of 9}
     */
    public record Line(LineKind kind, String title, String detail) {
    }

    /**
     * A card played in a register.
     *
     * @param robotId the robot that plays it
     * @param card    the card
     */
    public record Play(int robotId, Card card) {
    }

    /**
     * One moment of the turn.
     *
     * @param register the register it belongs to, 1 to 5, or 0 for the clean-up after the last register
     * @param phase    the step of the register
     * @param duration the time it takes at normal speed, in seconds
     * @param lines    what happened, as lines; the first is the headline of the moment
     * @param events   the events that happen in it
     * @param tags     the damage taken in it, by robot id
     * @param beams    the laser beams that shine in it
     */
    public record Beat(int register, SubPhase phase, float duration, List<Line> lines, List<GameEvent> events,
                       Map<Integer, Integer> tags, List<Beam> beams) {
    }

    /**
     * What to draw at one point in time.
     *
     * @param poses the robots that are on the board, by robot id
     * @param beams the laser beams that shine
     */
    public record Frame(List<RobotPose> poses, List<Beam> beams) {
    }

    /**
     * How a robot stands: the working state the replay moves along.
     */
    private static final class Sim {
        private float x;
        private float y;
        private float rotation;
        private float alpha;

        /**
         * Copies the state.
         *
         * @return the copy
         */
        Sim copy() {
            Sim copy = new Sim();
            copy.x = x;
            copy.y = y;
            copy.rotation = rotation;
            copy.alpha = alpha;
            return copy;
        }
    }

    /** How much longer every beat lasts than its base duration; the server's turn pause is sized for it. */
    private static final float PACE = 2f;
    private static final float REVEAL_SECONDS = 0.5f * PACE;
    private static final float ACTION_SECONDS = 0.4f * PACE;
    private static final float PUSH_SECONDS = 0.1f * PACE;
    private static final float BELT_SECONDS = 0.35f * PACE;
    private static final float LASER_SECONDS = 0.6f * PACE;
    private static final float OTHER_SECONDS = 0.4f * PACE;
    private static final float CLEANUP_SECONDS = 0.5f * PACE;

    private final IntFunction<String> names;
    private final List<Beat> beats = new ArrayList<>();
    private final List<Map<Integer, Sim>> states = new ArrayList<>();
    private final Map<Integer, List<Play>> playsByRegister = new TreeMap<>();
    private int index;
    private float elapsed;

    /**
     * Prepares the replay of a turn.
     *
     * @param before the state of every robot before the turn
     * @param events the events of the turn, in order
     * @param names  the display name of a robot, by robot id
     */
    public TurnReplay(List<RobotState> before, List<LoggedEvent> events, IntFunction<String> names) {
        this.names = names;
        Map<Integer, Sim> start = new TreeMap<>();
        for (RobotState robot : before) {
            Sim sim = new Sim();
            boolean onBoard = robot.position() != null && robot.status() == RobotStatus.ACTIVE;
            if (onBoard) {
                sim.x = robot.position().x();
                sim.y = robot.position().y();
                sim.rotation = BoardGeometry.rotation(robot.facing());
                sim.alpha = 1f;
            }
            start.put(robot.robotId(), sim);
        }
        buildBeats(events);
        states.add(start);
        for (Beat beat : beats) {
            Map<Integer, Sim> next = copyOf(states.get(states.size() - 1));
            beat.events().forEach(event -> apply(next, event));
            states.add(next);
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------------------------------------------

    /**
     * Lets time pass in the replay.
     *
     * @param seconds the seconds to advance, already multiplied by the playback speed
     */
    public void advance(float seconds) {
        if (isDone()) {
            return;
        }
        elapsed += seconds;
        while (!isDone() && elapsed >= beats.get(index).duration()) {
            elapsed -= beats.get(index).duration();
            index++;
        }
        if (isDone()) {
            elapsed = 0f;
        }
    }

    /**
     * Jumps to the end of the turn.
     */
    public void skipToEnd() {
        index = beats.size();
        elapsed = 0f;
    }

    /**
     * Returns whether every beat has been played.
     *
     * @return {@code true} at the end of the turn, and for a turn without events
     */
    public boolean isDone() {
        return index >= beats.size();
    }

    /**
     * Returns the time the whole turn takes at normal speed.
     *
     * @return the seconds
     */
    public float totalDuration() {
        float total = 0f;
        for (Beat beat : beats) {
            total += beat.duration();
        }
        return total;
    }

    // ------------------------------------------------------------------------------------------------------
    // What to show
    // ------------------------------------------------------------------------------------------------------

    /**
     * Returns all beats of the turn.
     *
     * @return the beats in order
     */
    public List<Beat> beats() {
        return List.copyOf(beats);
    }

    /**
     * Returns the number of the beat being played.
     *
     * @return the index, equal to the number of beats when the turn is over
     */
    public int beatIndex() {
        return index;
    }

    /**
     * Returns the beat being played, or the last one when the turn is over.
     *
     * @return the beat, or {@code null} for a turn without events
     */
    public Beat currentBeat() {
        return beats.isEmpty() ? null : beats.get(Math.min(index, beats.size() - 1));
    }

    /**
     * Returns the register being played.
     *
     * @return 1 to 5, or 0 for the clean-up at the end of the turn
     */
    public int register() {
        Beat beat = currentBeat();
        return beat == null ? 0 : beat.register();
    }

    /**
     * Returns the step of the register being played.
     *
     * @return the step
     */
    public SubPhase phase() {
        Beat beat = currentBeat();
        return beat == null ? SubPhase.CLEANUP : beat.phase();
    }

    /**
     * Returns the cards played in the register being played, the highest priority first, which is the order they act in.
     *
     * @return the plays
     */
    public List<Play> plays() {
        List<Play> plays = new ArrayList<>(playsByRegister.getOrDefault(register(), List.of()));
        plays.sort(Comparator.comparingInt((Play play) -> play.card().priority()).reversed());
        return plays;
    }

    /**
     * Returns what has happened in the register being played, newest first: the lines of the beat being played come first,
     * then those of the beats before it in the same register.
     *
     * @return the lines
     */
    public List<Line> feed() {
        List<Line> feed = new ArrayList<>();
        if (beats.isEmpty()) {
            return feed;
        }
        int current = Math.min(index, beats.size() - 1);
        int register = beats.get(current).register();
        for (int i = current; i >= 0 && beats.get(i).register() == register; i--) {
            feed.addAll(beats.get(i).lines());
        }
        return feed;
    }

    /**
     * Returns what to draw now: the robots between where they were and where they go, and the beams that shine.
     *
     * @return the frame
     */
    public Frame frame() {
        if (beats.isEmpty()) {
            return new Frame(poses(states.get(0), states.get(0), 0f, Map.of()), List.of());
        }
        if (isDone()) {
            return new Frame(poses(states.get(beats.size()), states.get(beats.size()), 0f, Map.of()), List.of());
        }
        Beat beat = beats.get(index);
        float progress = Math.min(1f, elapsed / beat.duration());
        return new Frame(poses(states.get(index), states.get(index + 1), progress, beat.tags()), beat.beams());
    }

    /**
     * Places every robot between two states.
     *
     * @param from     the state at the start of the beat
     * @param to       the state at its end
     * @param progress how far through the beat, from 0 to 1
     * @param tags     the damage tags of the beat
     * @return the poses of the robots that can be seen
     */
    private List<RobotPose> poses(Map<Integer, Sim> from, Map<Integer, Sim> to, float progress, Map<Integer, Integer> tags) {
        float eased = progress * progress * (3f - 2f * progress);
        List<RobotPose> poses = new ArrayList<>();
        for (Map.Entry<Integer, Sim> entry : from.entrySet()) {
            Sim a = entry.getValue();
            Sim b = to.get(entry.getKey());
            float alpha = a.alpha + (b.alpha - a.alpha) * eased;
            if (alpha > 0.01f) {
                poses.add(new RobotPose(entry.getKey(), a.x + (b.x - a.x) * eased, a.y + (b.y - a.y) * eased,
                    a.rotation + (b.rotation - a.rotation) * eased, alpha, tags.getOrDefault(entry.getKey(), 0), true));
            }
        }
        return poses;
    }

    // ------------------------------------------------------------------------------------------------------
    // Applying events
    // ------------------------------------------------------------------------------------------------------

    /**
     * Copies the working states of all robots.
     *
     * @param source the states
     * @return the copy
     */
    private static Map<Integer, Sim> copyOf(Map<Integer, Sim> source) {
        Map<Integer, Sim> copy = new TreeMap<>();
        source.forEach((id, sim) -> copy.put(id, sim.copy()));
        return copy;
    }

    /**
     * Applies one event to the working states.
     *
     * @param robots the states
     * @param event  the event
     */
    private static void apply(Map<Integer, Sim> robots, GameEvent event) {
        if (event instanceof GameEvent.RobotMoved moved) {
            Sim sim = robots.get(moved.robotId());
            sim.x = moved.to().x();
            sim.y = moved.to().y();
        } else if (event instanceof GameEvent.RobotRotated rotated) {
            int quarters = rotated.from().clockwiseQuarterTurnsTo(rotated.to());
            robots.get(rotated.robotId()).rotation += quarters == 3 ? 90f : -90f * quarters;
        } else if (event instanceof GameEvent.RobotDestroyed destroyed) {
            robots.get(destroyed.robotId()).alpha = 0f;
        } else if (event instanceof GameEvent.RobotRespawned respawned) {
            Sim sim = robots.get(respawned.robotId());
            sim.x = respawned.position().x();
            sim.y = respawned.position().y();
            sim.rotation = BoardGeometry.rotation(respawned.facing());
            sim.alpha = 1f;
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Cutting the events into beats
    // ------------------------------------------------------------------------------------------------------

    /**
     * Cuts the events into beats, one step of one register at a time.
     *
     * @param events the events of the turn
     */
    private void buildBeats(List<LoggedEvent> events) {
        int start = 0;
        Map<Integer, Card> revealed = new LinkedHashMap<>();
        int revealedRegister = -1;
        while (start < events.size()) {
            int register = events.get(start).register();
            SubPhase phase = events.get(start).phase();
            int end = start;
            List<GameEvent> step = new ArrayList<>();
            while (end < events.size() && events.get(end).register() == register && events.get(end).phase() == phase) {
                step.add(events.get(end).event());
                end++;
            }
            if (register != revealedRegister) {
                revealed = new LinkedHashMap<>();
                revealedRegister = register;
            }
            buildStep(register, phase, step, revealed);
            start = end;
        }
    }

    /**
     * Cuts one step of one register into beats.
     *
     * @param register the register
     * @param phase    the step
     * @param step     the events of the step
     * @param revealed the cards revealed in this register so far
     */
    private void buildStep(int register, SubPhase phase, List<GameEvent> step, Map<Integer, Card> revealed) {
        switch (phase) {
            case REVEAL -> reveal(register, step, revealed);
            case ROBOT_MOVEMENT -> movement(register, step, revealed);
            case LASERS -> lasers(register, step);
            case EXPRESS_BELTS, ALL_BELTS, PUSHERS, GEARS, CRUSHERS, CHECKPOINTS, CLEANUP, RESPAWN ->
                simultaneous(register, phase, step);
        }
    }

    /**
     * Makes the beat that reveals the cards of a register.
     *
     * @param register the register
     * @param step     the events of the step
     * @param revealed receives the revealed cards by robot
     */
    private void reveal(int register, List<GameEvent> step, Map<Integer, Card> revealed) {
        List<Play> plays = playsByRegister.computeIfAbsent(register, r -> new ArrayList<>());
        for (GameEvent event : step) {
            if (event instanceof GameEvent.RegisterRevealed card) {
                revealed.put(card.robotId(), card.card());
                plays.add(new Play(card.robotId(), card.card()));
            }
        }
        beats.add(new Beat(register, SubPhase.REVEAL, REVEAL_SECONDS,
            List.of(new Line(LineKind.CARD, "Cards revealed", "Register " + register)), step, Map.of(), List.of()));
    }

    /**
     * Makes one beat for every card played in the robot-movement step, with the pushes it causes.
     *
     * @param register the register
     * @param step     the events of the step
     * @param revealed the cards revealed in this register
     */
    private void movement(int register, List<GameEvent> step, Map<Integer, Card> revealed) {
        List<List<GameEvent>> actions = new ArrayList<>();
        List<GameEvent> current = null;
        int currentRobot = GameEvent.NO_ROBOT;
        for (GameEvent event : step) {
            int cardRobot = cardRobot(event);
            if (cardRobot != GameEvent.NO_ROBOT) {
                if (current == null || cardRobot != currentRobot) {
                    current = new ArrayList<>();
                    actions.add(current);
                    currentRobot = cardRobot;
                }
            } else {
                if (current == null) {
                    current = new ArrayList<>();
                    actions.add(current);
                    currentRobot = GameEvent.NO_ROBOT;
                }
            }
            current.add(event);
        }
        for (List<GameEvent> action : actions) {
            List<Line> lines = new ArrayList<>();
            int pushes = 0;
            for (GameEvent event : action) {
                int robot = cardRobot(event);
                if (robot != GameEvent.NO_ROBOT) {
                    if (lines.isEmpty()) {
                        Card card = revealed.get(robot);
                        lines.add(new Line(LineKind.CARD, name(robot) + " plays " + (card == null ? "a card"
                            : CardLook.name(card.type())), card == null ? "" : "Priority " + card.priority()));
                    }
                } else if (event instanceof GameEvent.RobotMoved moved && moved.cause() == MoveCause.PUSHED) {
                    pushes++;
                    lines.add(new Line(LineKind.PUSH, name(moved.robotId()) + " is pushed", "By another robot"));
                } else if (event instanceof GameEvent.RobotDestroyed destroyed) {
                    lines.add(destroyedLine(destroyed));
                }
            }
            if (lines.isEmpty()) {
                lines.add(new Line(LineKind.CARD, "A robot moves", ""));
            }
            beats.add(new Beat(register, SubPhase.ROBOT_MOVEMENT, ACTION_SECONDS + PUSH_SECONDS * pushes, lines, action,
                Map.of(), List.of()));
        }
    }

    /**
     * Returns the robot that a card action of the events belongs to.
     *
     * @param event an event
     * @return the robot if the event is a move or turn made by a card, otherwise {@link GameEvent#NO_ROBOT}
     */
    private static int cardRobot(GameEvent event) {
        if (event instanceof GameEvent.RobotMoved moved && moved.cause() == MoveCause.CARD) {
            return moved.robotId();
        }
        if (event instanceof GameEvent.RobotRotated rotated && rotated.cause() == RotationCause.CARD) {
            return rotated.robotId();
        }
        return GameEvent.NO_ROBOT;
    }

    /**
     * Makes the beat of a laser volley: every laser fires at once, damage is merged per robot, and robots that are lost are
     * listed. A volley that hits nobody produces no beat at all — the lasers fire on the server every register regardless
     * (design.md 2.4 item 7), but with nobody standing in the line of fire there is nothing worth spending replay time
     * animating or playing a sound for.
     *
     * @param register the register
     * @param step     the events of the step
     */
    private void lasers(int register, List<GameEvent> step) {
        List<Beam> beams = new ArrayList<>();
        Map<Integer, List<String>> sources = new LinkedHashMap<>();
        Map<Integer, Integer> amounts = new LinkedHashMap<>();
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        List<Line> destroyed = new ArrayList<>();
        Set<Integer> hit = new LinkedHashSet<>();
        for (GameEvent event : step) {
            if (event instanceof GameEvent.LaserFired fired) {
                beams.add(new Beam(fired.from(), fired.direction(), fired.to(), fired.source() == LaserSource.BOARD,
                    fired.beams()));
                if (fired.hitRobotId() != GameEvent.NO_ROBOT) {
                    hit.add(fired.hitRobotId());
                    sources.computeIfAbsent(fired.hitRobotId(), id -> new ArrayList<>()).add(
                        fired.source() == LaserSource.BOARD ? "Board laser" : name(fired.sourceRobotId()) + "'s laser");
                }
            } else if (event instanceof GameEvent.RobotDamaged damaged) {
                amounts.merge(damaged.robotId(), damaged.amount(), Integer::sum);
                totals.put(damaged.robotId(), damaged.totalDamage());
            } else if (event instanceof GameEvent.RobotDestroyed lost) {
                destroyed.add(destroyedLine(lost));
            }
        }
        if (hit.isEmpty()) {
            return;
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LineKind.LASER, "Laser volley: " + hit.size() + (hit.size() == 1 ? " robot hit" : " robots hit"),
            "Board lasers and robot lasers fire together"));
        for (Map.Entry<Integer, Integer> entry : amounts.entrySet()) {
            int robot = entry.getKey();
            String by = String.join(", then ", sources.getOrDefault(robot, List.of("Laser")));
            lines.add(new Line(LineKind.LASER, name(robot) + " takes " + entry.getValue() + " damage",
                by + " · damage " + totals.get(robot) + " of 9"));
        }
        lines.addAll(destroyed);
        beats.add(new Beat(register, SubPhase.LASERS, LASER_SECONDS, lines, step, Map.copyOf(amounts), beams));
    }

    /**
     * Makes the beat of a step in which everything happens at once.
     *
     * @param register the register, or 0 for the clean-up
     * @param phase    the step
     * @param step     the events of the step
     */
    private void simultaneous(int register, SubPhase phase, List<GameEvent> step) {
        List<Line> lines = new ArrayList<>();
        for (GameEvent event : step) {
            Line line = lineOf(register, phase, event);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            lines.add(new Line(LineKind.CARD, phaseName(phase), ""));
        }
        float seconds = phase == SubPhase.CLEANUP ? CLEANUP_SECONDS
            : phase == SubPhase.EXPRESS_BELTS || phase == SubPhase.ALL_BELTS || phase == SubPhase.PUSHERS
            || phase == SubPhase.GEARS ? BELT_SECONDS : OTHER_SECONDS;
        beats.add(new Beat(register, phase, seconds, lines, step, Map.of(), List.of()));
    }

    /**
     * Words an event of a step in which everything happens at once.
     *
     * @param register the register
     * @param phase    the step
     * @param event    the event
     * @return the line, or {@code null} for an event that says nothing new
     */
    private Line lineOf(int register, SubPhase phase, GameEvent event) {
        if (event instanceof GameEvent.RobotMoved moved) {
            return switch (phase) {
                case EXPRESS_BELTS -> new Line(LineKind.BELT, name(moved.robotId()) + " rides a belt",
                    "Express belt · moves one square");
                case ALL_BELTS -> new Line(LineKind.BELT, name(moved.robotId()) + " rides a belt", "Moves one square");
                case PUSHERS -> new Line(LineKind.PUSH, name(moved.robotId()) + " is pushed", "Pusher · register " + register);
                default -> new Line(LineKind.PUSH, name(moved.robotId()) + " is pushed", "By another robot");
            };
        }
        if (event instanceof GameEvent.RobotRotated rotated) {
            String side = rotated.from().clockwiseQuarterTurnsTo(rotated.to()) == 3 ? "left" : "right";
            return phase == SubPhase.GEARS
                ? new Line(LineKind.GEAR, name(rotated.robotId()) + " rotates " + side, "Gear · register " + register)
                : new Line(LineKind.BELT, name(rotated.robotId()) + " turns on a belt", "Belt corner");
        }
        if (event instanceof GameEvent.RobotDestroyed destroyed) {
            return destroyedLine(destroyed);
        }
        if (event instanceof GameEvent.FlagTouched flag) {
            return new Line(LineKind.FLAG, name(flag.robotId()) + " touches flag " + flag.flagNumber(),
                "The archive marker moves here");
        }
        if (event instanceof GameEvent.ArchiveMarkerMoved marker) {
            return phase == SubPhase.CHECKPOINTS ? null
                : new Line(LineKind.REPAIR, name(marker.robotId()) + " sets a new archive marker", "Repair site");
        }
        if (event instanceof GameEvent.RobotRepaired repaired) {
            return new Line(LineKind.REPAIR, name(repaired.robotId()) + " is repaired",
                "Damage " + repaired.totalDamage() + " of 9");
        }
        if (event instanceof GameEvent.RobotPoweredDown down) {
            return new Line(LineKind.REPAIR, name(down.robotId()) + " powers down", "Rests for a turn");
        }
        if (event instanceof GameEvent.RobotPoweredUp up) {
            return new Line(LineKind.REPAIR, name(up.robotId()) + " powers up", "Back in action");
        }
        if (event instanceof GameEvent.RobotRespawned respawned) {
            return new Line(LineKind.REPAIR, name(respawned.robotId()) + " re-enters", "At the archive marker");
        }
        if (event instanceof GameEvent.GameEnded ended) {
            return new Line(LineKind.FLAG, ended.winnerRobotId() == GameEvent.NO_ROBOT ? "The game is over"
                : name(ended.winnerRobotId()) + " wins the game", "The last flag was touched");
        }
        return null;
    }

    /**
     * Words the loss of a robot.
     *
     * @param destroyed the event
     * @return the line
     */
    private Line destroyedLine(GameEvent.RobotDestroyed destroyed) {
        String how = switch (destroyed.cause()) {
            case PIT -> "Fell into a pit";
            case LEFT_BOARD -> "Left the board";
            case DAMAGE -> "Too much damage";
            case CRUSHER -> "Crushed";
            case FORFEIT -> "Left the game";
        };
        return new Line(LineKind.DESTROYED, name(destroyed.robotId()) + " is destroyed", how);
    }

    /**
     * Returns the name of a step.
     *
     * @param phase the step
     * @return the name, for example {@code Gears}
     */
    private static String phaseName(SubPhase phase) {
        return switch (phase) {
            case REVEAL -> "Reveal";
            case ROBOT_MOVEMENT -> "Robot movement";
            case EXPRESS_BELTS -> "Express belts";
            case ALL_BELTS -> "All belts";
            case PUSHERS -> "Pushers";
            case GEARS -> "Gears";
            case LASERS -> "Lasers";
            case CRUSHERS -> "Crushers";
            case CHECKPOINTS -> "Checkpoints";
            case CLEANUP -> "Clean-up";
            case RESPAWN -> "Re-entry";
        };
    }

    /**
     * Returns a robot's display name.
     *
     * @param robotId the robot
     * @return the name
     */
    private String name(int robotId) {
        return names.apply(robotId);
    }

    /**
     * Returns the direction a robot faces after a turn, for tests that compare the replay with the rules.
     *
     * @param rotation a heading from a pose
     * @return the direction closest to it
     */
    public static Direction facingOf(float rotation) {
        int quarters = Math.floorMod(Math.round(rotation / 90f), 4);
        return switch (quarters) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.SOUTH;
            default -> Direction.EAST;
        };
    }
}
