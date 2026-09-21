package de.mkoehler.robotrampage.lwjgl3;

import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardLoader;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.Deck;
import de.mkoehler.robotrampage.rules.GameState;
import de.mkoehler.robotrampage.rules.Programming;
import de.mkoehler.robotrampage.rules.Robot;
import de.mkoehler.robotrampage.rules.TurnResolver;
import de.mkoehler.robotrampage.rules.TurnResult;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.client.replay.TurnReplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A development helper: plays the first turn of a game on the first board with the real rules engine and random programs,
 * and returns the messages a server would send for it. It gives the tools real events, with lasers, belts, pushes and
 * damage, instead of ones written by hand.
 *
 * @author Mario Koehler
 */
public final class SampleTurn {

    /**
     * The first turn of a game.
     *
     * @param boardJson the board as the server sends it
     * @param players   the players
     * @param messages  the turn's messages in the order the server sends them: the start of the turn, the resolved turn and
     *                  the state after it
     * @param after     the state of the robots after the turn
     * @param laserSecond a time, in seconds from the start of the replay at normal speed, at which the first laser volley
     *                  is shining, or 0 if no laser fired
     */
    public record Sample(String boardJson, List<PlayerInfo> players, List<Object> messages, List<RobotState> after,
                         float laserSecond) {
    }

    /**
     * Not instantiable; this class only holds a static helper.
     */
    private SampleTurn() {
    }

    /**
     * Plays a turn.
     *
     * @param seed       the seed of the deck and of the programs
     * @param robotCount the number of robots, at most the number of start squares
     * @param names      the display names, one per robot
     * @return the messages of the turn
     */
    public static Sample first(long seed, int robotCount, List<String> names) {
        var loaded = BoardLoader.loadResource("boards/proving-grounds.json");
        Board board = loaded.board();
        List<Robot> robots = new ArrayList<>();
        List<PlayerInfo> players = new ArrayList<>();
        List<Integer> awaited = new ArrayList<>();
        for (int id = 0; id < robotCount; id++) {
            StartSquare start = board.startSquares().get(id);
            robots.add(new Robot(id, start.position(), start.facing()));
            players.add(new PlayerInfo(id, names.get(id), true, true, id == 0));
            awaited.add(id);
        }
        GameState state = new GameState(board, robots, Deck.standard(seed));
        Random random = new Random(seed);
        for (Map.Entry<Integer, List<Card>> entry : Programming.deal(state).entrySet()) {
            List<Card> shuffled = new ArrayList<>(entry.getValue());
            Collections.shuffle(shuffled, random);
            Programming.submit(state, entry.getKey(), entry.getValue(), shuffled.subList(0, Robot.REGISTER_COUNT), false);
        }
        TurnResult result = TurnResolver.resolve(state);
        List<RobotState> after = new ArrayList<>();
        result.state().robots().forEach(robot -> after.add(RobotState.of(robot)));
        List<Object> messages = new ArrayList<>();
        messages.add(new TurnStarted(1, List.of(), awaited, 90));
        messages.add(new TurnResolved(1, result.events()));
        messages.add(new StateSnapshot(1, after, result.state().isOver(), result.state().winnerId()));
        List<RobotState> before = new ArrayList<>();
        for (int id = 0; id < robotCount; id++) {
            StartSquare start = board.startSquares().get(id);
            before.add(new RobotState(id, start.position(), start.facing(), 0, Robot.STARTING_LIVES, 0, start.position(),
                RobotStatus.ACTIVE, false, false));
        }
        float laserSecond = 0f;
        float elapsed = 0f;
        for (TurnReplay.Beat beat : new TurnReplay(before, result.events(), id -> "R" + id).beats()) {
            if (!beat.beams().isEmpty() && !beat.tags().isEmpty()) {
                laserSecond = elapsed + beat.duration() * 0.4f;
                break;
            }
            elapsed += beat.duration();
        }
        return new Sample(BoardLoader.toJson(loaded.definition()), players, messages, after, laserSecond);
    }
}
