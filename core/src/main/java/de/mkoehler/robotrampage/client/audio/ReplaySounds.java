package de.mkoehler.robotrampage.client.audio;

import de.mkoehler.robotrampage.client.audio.AudioKit.Clip;
import de.mkoehler.robotrampage.client.replay.TurnReplay;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.SubPhase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decides which sound effects a beat of the turn replay plays, heard from this player's point of view: only their own
 * robot drives, turns, rides belts and gears, hits walls, is shoved, crushed, dropped or repaired audibly — the others
 * move silently unless they push this player's robot. A laser volley, any robot being destroyed and any robot touching a
 * flag are heard whoever it concerns. Each clip plays at most once per beat. Free of libGDX, so it can be tested.
 *
 * @author Mario Koehler
 */
public final class ReplaySounds {

    private static final Clip[] FLAG_BEEPS = {Clip.BEEP_1, Clip.BEEP_2, Clip.BEEP_3, Clip.BEEP_4};

    /**
     * Not instantiated.
     */
    private ReplaySounds() {
    }

    /**
     * Returns the clips a beat plays when it starts.
     *
     * @param beat    the beat that starts
     * @param myRobot this player's robot id
     * @param myCard  the card this player's robot plays in the beat's register, or {@code null} if it plays none
     * @param random  picks the flag beep
     * @return the clips, in no particular order and without repeats
     */
    public static List<Clip> forBeat(TurnReplay.Beat beat, int myRobot, Card myCard, Random random) {
        Set<Clip> clips = new LinkedHashSet<>();
        if (beat.phase() == SubPhase.LASERS) {
            clips.add(Clip.LASER);
        }
        boolean myAction = beat.phase() == SubPhase.ROBOT_MOVEMENT && actsByCard(beat, myRobot);
        boolean poweredDown = beat.events().stream()
            .anyMatch(event -> event instanceof GameEvent.RobotPoweredDown down && down.robotId() == myRobot);
        int mySteps = 0;
        boolean myRobotLost = false;
        for (GameEvent event : beat.events()) {
            switch (event) {
                case GameEvent.RobotMoved moved when moved.robotId() == myRobot -> {
                    switch (moved.cause()) {
                        case CARD -> mySteps++;
                        case PUSHED -> clips.add(Clip.ROBOT_PUSHES_ANOTHER_ROBOT);
                        case BELT -> clips.add(Clip.CONVEYOR_BELT_MOVES);
                        case PUSHER -> clips.add(Clip.ROBOT_IS_PUSHED_BY_PUSHER);
                    }
                }
                case GameEvent.RobotMoved moved when moved.cause() == MoveCause.PUSHED && myAction ->
                    clips.add(Clip.ROBOT_PUSHES_ANOTHER_ROBOT);
                case GameEvent.RobotRotated rotated when rotated.robotId() == myRobot -> {
                    if (rotated.cause() == RotationCause.CARD) {
                        clips.add(Clip.ROBOT_TURNS);
                    } else if (rotated.cause() == RotationCause.GEAR) {
                        clips.add(Clip.ROBOT_IS_TURNED_BY_GEAR);
                    }
                }
                case GameEvent.RobotDestroyed destroyed -> {
                    clips.add(Clip.ROBOT_DIES);
                    if (destroyed.robotId() == myRobot) {
                        myRobotLost = true;
                        if (destroyed.cause() == DestructionCause.PIT) {
                            clips.add(Clip.ROBOT_DROPS_INTO_PIT);
                        } else if (destroyed.cause() == DestructionCause.CRUSHER) {
                            clips.add(Clip.ROBOT_IS_CRUSHED_BY_CRUSHER);
                        }
                    }
                }
                case GameEvent.FlagTouched ignored -> clips.add(FLAG_BEEPS[random.nextInt(FLAG_BEEPS.length)]);
                case GameEvent.RobotRepaired repaired when repaired.robotId() == myRobot && !poweredDown ->
                    clips.add(Clip.ROBOT_IS_REPAIRED_ON_HEALTH_TILE);
                default -> {
                }
            }
        }
        if (mySteps > 0) {
            boolean backwards = myCard != null && myCard.type() == CardType.BACK_UP;
            clips.add(backwards ? Clip.ROBOT_DRIVES_BACKWARDS : Clip.ROBOT_DRIVES_FORWARD);
        }
        if (myAction && myCard != null && !myRobotLost && mySteps > 0 && mySteps < steps(myCard.type())) {
            clips.add(Clip.ROBOT_HITS_A_WALL);
        }
        return new ArrayList<>(clips);
    }

    /**
     * Returns whether a robot is the one whose card a movement beat plays.
     *
     * @param beat  the beat
     * @param robot the robot
     * @return {@code true} if the beat holds a card move or turn of the robot
     */
    private static boolean actsByCard(TurnReplay.Beat beat, int robot) {
        for (GameEvent event : beat.events()) {
            if (event instanceof GameEvent.RobotMoved moved && moved.cause() == MoveCause.CARD && moved.robotId() == robot
                || event instanceof GameEvent.RobotRotated rotated && rotated.cause() == RotationCause.CARD
                && rotated.robotId() == robot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns how many squares a card moves a robot when nothing is in the way.
     *
     * @param type the card type
     * @return the squares, 0 for a turn
     */
    private static int steps(CardType type) {
        return switch (type) {
            case MOVE_1, BACK_UP -> 1;
            case MOVE_2 -> 2;
            case MOVE_3 -> 3;
            case ROTATE_LEFT, ROTATE_RIGHT, U_TURN -> 0;
        };
    }
}
