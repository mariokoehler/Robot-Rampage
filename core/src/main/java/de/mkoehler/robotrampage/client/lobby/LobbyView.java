package de.mkoehler.robotrampage.client.lobby;

import de.mkoehler.robotrampage.bot.BotDifficulty;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * What the lobby screen shows, worked out from the server's {@link LobbyState} and the seat of the player looking at it.
 * It holds no widgets and no state of its own, so the screen can be rebuilt from any lobby state at any time, and every
 * rule about what is shown, and whether the game can be started, can be tested.
 *
 * @author Mario Koehler
 */
public final class LobbyView {

    /**
     * One line of the player list: a seat with a player, or a free seat.
     *
     * @param seat      the seat, 0 to 7
     * @param occupied  whether a player sits here
     * @param name      the player's display name, empty for a free seat
     * @param robotName the name of the robot of the seat, such as {@code Bolt}
     * @param host      whether the player is the host
     * @param you       whether the player is the one looking at the screen
     * @param ready     whether the player is ready to start
     * @param bot        whether the seat is played by the computer
     * @param difficulty the bot's difficulty label ({@code "Easy"}, {@code "Normal"} or {@code "Hard"}), meaningless for
     *                   a human
     * @param addBot     whether this free seat offers the host to add a bot; only the first free seat does, since that is
     *                   the seat the server puts a new bot on
     * @param removable  whether the host may take the bot on this seat away, or cycle its difficulty
     */
    public record Row(int seat, boolean occupied, String name, String robotName, boolean host, boolean you,
                      boolean ready, boolean bot, String difficulty, boolean addBot, boolean removable) {
    }

    /**
     * One board the host can choose.
     *
     * @param id         the board's identifier, to send in {@code SelectBoard}
     * @param name       the board's name
     * @param maxPlayers how many players it seats
     * @param selected   whether it is the board chosen now
     * @param fits       whether it has a start square for every seat already taken; a board that does not fit cannot be
     *                   chosen, the server would refuse it
     */
    public record BoardOption(String id, String name, int maxPlayers, boolean selected, boolean fits) {
    }

    private final LobbyState state;
    private final int mySeat;
    private final PlayerInfo me;

    /**
     * Creates the view.
     *
     * @param state  the latest lobby state
     * @param mySeat the seat of this client's player
     */
    public LobbyView(LobbyState state, int mySeat) {
        this.state = state;
        this.mySeat = mySeat;
        this.me = state.players().stream().filter(player -> player.seat() == mySeat).findFirst().orElse(null);
    }

    /**
     * Returns one row for every seat of the board, in seat order, with free seats marked as such.
     *
     * @return the rows
     */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        boolean offered = false;
        for (int seat = 0; seat < state.maxPlayers(); seat++) {
            PlayerInfo player = playerAt(seat);
            String robot = RobotLook.name(seat);
            if (player == null) {
                rows.add(new Row(seat, false, "", robot, false, false, false, false, "", iAmHost() && !offered, false));
                offered = true;
            } else {
                rows.add(new Row(seat, true, player.name(), robot, player.host(), seat == mySeat, player.ready(),
                    player.bot(), difficultyLabel(player.difficulty()), false, iAmHost() && player.bot()));
            }
        }
        return rows;
    }

    /**
     * Returns how many seats are taken, in words for the counter next to the heading.
     *
     * @return for example {@code 6 of 8}
     */
    public String countText() {
        return state.players().size() + " of " + state.maxPlayers();
    }

    /**
     * Returns whether this client's player is the host.
     *
     * @return {@code true} for the host
     */
    public boolean iAmHost() {
        return me != null && me.host();
    }

    /**
     * Returns whether this client's player is ready.
     *
     * @return {@code true} if the server has them marked as ready
     */
    public boolean iAmReady() {
        return me != null && me.ready();
    }

    /**
     * Returns whether the host may start the game now. It mirrors the server's conditions exactly: the player is the host,
     * enough players are seated, and every player except the host is ready. Whether the host is ready themselves does not
     * matter.
     *
     * @return {@code true} if the start button should be enabled
     */
    public boolean canStart() {
        return iAmHost() && state.players().size() >= state.minPlayers() && othersAreReady();
    }

    /**
     * Returns the label of the start button: the action for the host, and what everybody else is doing for the rest.
     *
     * @return the label
     */
    public String startLabel() {
        return iAmHost() ? "Start game" : "Waiting for the host";
    }

    /**
     * Returns how many seats computer-controlled robots take.
     *
     * @return the number of bots
     */
    public int botCount() {
        return (int) state.players().stream().filter(PlayerInfo::bot).count();
    }

    /**
     * Returns the line that says what happens next, or what is still missing.
     *
     * @return a sentence for the panel next to the board
     */
    public String hint() {
        if (!iAmHost()) {
            return "Only the host can start the game. Waiting for the host.";
        }
        if (state.players().size() < state.minPlayers()) {
            return "At least " + state.minPlayers() + " players are needed to start. Add a bot to play alone.";
        }
        if (!othersAreReady()) {
            return "Waiting for everyone to be ready.";
        }
        return "Everyone is ready. Start the game when you like.";
    }

    /**
     * Returns the board's name and size.
     *
     * @return for example {@code First Board · 12 × 12}
     */
    public String boardText() {
        return state.boardName() + " · " + state.boardWidth() + " × " + state.boardHeight();
    }

    /**
     * Returns how many flags there are.
     *
     * @return for example {@code 3, in order}
     */
    public String flagsText() {
        return state.flagCount() == 1 ? "1" : state.flagCount() + ", in order";
    }

    /**
     * Returns how many lives each robot has.
     *
     * @return for example {@code 3 per robot}
     */
    public String livesText() {
        return state.lives() + " per robot";
    }

    /**
     * Returns whether the player looking at the screen may choose the board: only the host may.
     *
     * @return {@code true} for the host
     */
    public boolean canChooseBoard() {
        return iAmHost();
    }

    /**
     * Returns the boards the server offers, in its order. A board fits if it has a start square for the highest seat
     * already taken, since seats are start squares; the server refuses one that does not.
     *
     * @return the boards
     */
    public List<BoardOption> boardOptions() {
        int highestSeat = state.players().stream().mapToInt(PlayerInfo::seat).max().orElse(-1);
        return state.boards().stream().map(board -> new BoardOption(board.id(), board.name(), board.maxPlayers(),
            board.id().equals(state.boardId()), highestSeat < board.maxPlayers())).toList();
    }

    /**
     * Returns how long the players get to program.
     *
     * @return for example {@code 90 seconds}
     */
    public String programmingTimeText() {
        return state.programmingSeconds() + " seconds";
    }

    /**
     * Returns a bot difficulty the way the lobby shows it.
     *
     * @param difficulty the difficulty
     * @return {@code "Easy"}, {@code "Normal"} or {@code "Hard"}
     */
    private static String difficultyLabel(BotDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "Easy";
            case NORMAL -> "Normal";
            case HARD -> "Hard";
        };
    }

    /**
     * Finds the player on a seat.
     *
     * @param seat the seat
     * @return the player, or {@code null} for a free seat
     */
    private PlayerInfo playerAt(int seat) {
        return state.players().stream().filter(player -> player.seat() == seat).findFirst().orElse(null);
    }

    /**
     * Checks that every player except the host is ready, the condition the server sets for starting.
     *
     * @return {@code true} if nobody but the host is still not ready
     */
    private boolean othersAreReady() {
        return state.players().stream().allMatch(player -> player.host() || player.ready());
    }
}
