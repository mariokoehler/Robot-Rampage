package de.mkoehler.robotrampage.client.game;

import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.RobotStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The results of a finished game: who won, how the players rank, and the words the Game Over screen says about each of them.
 * It has no graphics, so the ranking and the wording can be tested.
 * <p>
 * The winner is always first. The others are ranked by the flags they touched, then by the lives they have left, then by
 * seat; a robot that was eliminated or whose player left counts as having no lives left. The line under a name is a fact the
 * client only knows if it watched the game: a player who joined late or came back has seen no turns, so when the turn of a
 * flag or of an elimination is not known the line says less, and never guesses.
 *
 * @author Mario Koehler
 */
public final class Standings {

    /**
     * The last flag a robot touched, as seen in a replayed turn.
     *
     * @param flag     the number of the flag, starting at 1
     * @param turn     the turn
     * @param register the register of the turn, 1 to 5
     */
    public record FlagTouch(int flag, int turn, int register) {
    }

    /**
     * One line of the standings.
     *
     * @param rank      the place, starting at 1
     * @param seat      the seat, which is also the robot's id
     * @param name      the display name
     * @param you       whether this is the player looking at the screen
     * @param winner    whether this player won
     * @param flags     the number of the highest flag the robot touched, 0 for none
     * @param flagTotal the number of flags on the board
     * @param lives     the lives the robot has left, 0 if it was eliminated or its player left
     * @param maxLives  the lives every robot started with
     * @param detail    what to say under the name, or an empty text for nothing
     */
    public record Row(int rank, int seat, String name, boolean you, boolean winner, int flags, int flagTotal, int lives,
                      int maxLives, String detail) {
    }

    private final int winnerSeat;
    private final String winnerName;
    private final int flagTotal;
    private final List<Row> rows;
    private final boolean winnerTouchedEveryFlag;

    /**
     * Works out the standings.
     *
     * @param players         the players of the game
     * @param robots          the final state of every robot
     * @param winnerSeat      the winning robot, or {@link GameEvent#NO_ROBOT} if nobody won
     * @param mySeat          the seat of the player looking at the screen
     * @param flagTotal       the number of flags on the board
     * @param maxLives        the lives every robot started with
     * @param lastFlags       the last flag each robot touched in a turn the client replayed
     * @param eliminatedTurns the turn in which each robot was eliminated, for the eliminations the client replayed
     * @param left            the seats of the players who left the game
     * @return the standings
     */
    public static Standings of(List<PlayerInfo> players, List<RobotState> robots, int winnerSeat, int mySeat, int flagTotal,
                               int maxLives, Map<Integer, FlagTouch> lastFlags, Map<Integer, Integer> eliminatedTurns,
                               Set<Integer> left) {
        List<RobotState> ranked = new ArrayList<>(robots);
        ranked.sort(Comparator.comparing((RobotState robot) -> robot.robotId() != winnerSeat)
            .thenComparing(Comparator.comparingInt(RobotState::flagsTouched).reversed())
            .thenComparing(Comparator.comparingInt((RobotState robot) -> livesLeft(robot, left)).reversed())
            .thenComparingInt(RobotState::robotId));
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RobotState robot = ranked.get(index);
            int seat = robot.robotId();
            boolean winner = seat == winnerSeat;
            String detail = detail(robot, winner, flagTotal, lastFlags.get(seat), eliminatedTurns.get(seat), left.contains(seat));
            rows.add(new Row(index + 1, seat, nameOf(players, seat), seat == mySeat, winner, robot.flagsTouched(), flagTotal,
                livesLeft(robot, left), maxLives, detail));
        }
        boolean everyFlag = winnerSeat != GameEvent.NO_ROBOT && robots.stream()
            .anyMatch(robot -> robot.robotId() == winnerSeat && robot.flagsTouched() >= flagTotal);
        return new Standings(winnerSeat, nameOf(players, winnerSeat), flagTotal, rows, everyFlag);
    }

    /**
     * Creates the standings from their parts.
     *
     * @param winnerSeat             the winning robot, or {@link GameEvent#NO_ROBOT}
     * @param winnerName             the winner's name
     * @param flagTotal              the number of flags
     * @param rows                   the ranked lines
     * @param winnerTouchedEveryFlag whether the winner won by touching the last flag
     */
    private Standings(int winnerSeat, String winnerName, int flagTotal, List<Row> rows, boolean winnerTouchedEveryFlag) {
        this.winnerSeat = winnerSeat;
        this.winnerName = winnerName;
        this.flagTotal = flagTotal;
        this.rows = List.copyOf(rows);
        this.winnerTouchedEveryFlag = winnerTouchedEveryFlag;
    }

    /**
     * Returns the lives a robot has left for the purpose of ranking and showing.
     *
     * @param robot the robot
     * @param left  the seats of the players who left
     * @return the lives, 0 for a robot that is eliminated or whose player left
     */
    private static int livesLeft(RobotState robot, Set<Integer> left) {
        return robot.status() == RobotStatus.ELIMINATED || left.contains(robot.robotId()) ? 0 : robot.lives();
    }

    /**
     * Finds the name of a player.
     *
     * @param players the players
     * @param seat    the seat
     * @return the name, or an empty text if nobody sat there
     */
    private static String nameOf(List<PlayerInfo> players, int seat) {
        return players.stream().filter(player -> player.seat() == seat).map(PlayerInfo::name).findFirst().orElse("");
    }

    /**
     * Words what happened to a robot, as far as the client knows.
     *
     * @param robot          the robot
     * @param winner         whether it won
     * @param flagTotal      the number of flags
     * @param lastFlag       the last flag it touched in a replayed turn, or {@code null}
     * @param eliminatedTurn the turn it was eliminated in, or {@code null} if that was not seen
     * @param left           whether its player left the game
     * @return the words, or an empty text
     */
    private static String detail(RobotState robot, boolean winner, int flagTotal, FlagTouch lastFlag, Integer eliminatedTurn,
                                 boolean left) {
        if (winner) {
            if (robot.flagsTouched() < flagTotal) {
                return "Last robot standing";
            }
            return lastFlag != null && lastFlag.flag() == flagTotal
                ? "Touched flag " + flagTotal + " in turn " + lastFlag.turn() + ", register " + lastFlag.register() : "";
        }
        if (left) {
            return "Left the game";
        }
        if (robot.status() == RobotStatus.ELIMINATED) {
            return eliminatedTurn == null ? "Eliminated" : "Eliminated in turn " + eliminatedTurn;
        }
        return flagTotal > 1 && robot.flagsTouched() == flagTotal - 1 ? "One flag short" : "";
    }

    /**
     * Returns the players, best first.
     *
     * @return one row per robot
     */
    public List<Row> rows() {
        return rows;
    }

    /**
     * Returns whether somebody won.
     *
     * @return {@code false} if the game ended without a winner
     */
    public boolean hasWinner() {
        return winnerSeat != GameEvent.NO_ROBOT;
    }

    /**
     * Returns the big line of the screen.
     *
     * @return for example {@code Sophie wins!}, or {@code No winner} if nobody won
     */
    public String headline() {
        return hasWinner() ? winnerName + " wins!" : "No winner";
    }

    /**
     * Returns the sentence under the big line.
     *
     * @return what decided the game, and what happens next
     */
    public String subline() {
        if (!hasWinner()) {
            return "Nobody is left on the board. Everybody can go again from the lobby.";
        }
        String reason = winnerTouchedEveryFlag ? "First to touch flag " + flagTotal + "." : "The last robot standing.";
        return reason + " Everybody else can go again from the lobby.";
    }

    /**
     * Returns the sentence that says how the players are ranked.
     *
     * @return the ranking rule
     */
    public String rankingRule() {
        return "Ranked by flags touched, then lives left.";
    }
}
