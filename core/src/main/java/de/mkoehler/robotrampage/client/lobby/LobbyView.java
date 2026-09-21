package de.mkoehler.robotrampage.client.lobby;

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
     */
    public record Row(int seat, boolean occupied, String name, String robotName, boolean host, boolean you,
                      boolean ready) {
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
        for (int seat = 0; seat < state.maxPlayers(); seat++) {
            PlayerInfo player = playerAt(seat);
            String robot = RobotLook.name(seat);
            rows.add(player == null
                ? new Row(seat, false, "", robot, false, false, false)
                : new Row(seat, true, player.name(), robot, player.host(), seat == mySeat, player.ready()));
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
     * Returns the line that says what happens next, or what is still missing.
     *
     * @return a sentence for the panel next to the board
     */
    public String hint() {
        if (!iAmHost()) {
            return "Only the host can start the game. Waiting for the host.";
        }
        if (state.players().size() < state.minPlayers()) {
            return "At least " + state.minPlayers() + " players are needed to start.";
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
     * Returns how long the players get to program.
     *
     * @return for example {@code 90 seconds}
     */
    public String programmingTimeText() {
        return state.programmingSeconds() + " seconds";
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
