package de.mkoehler.robotrampage.client.lobby;

import de.mkoehler.robotrampage.bot.BotDifficulty;
import de.mkoehler.robotrampage.net.messages.BoardChoice;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies what {@link LobbyView} works out from a lobby state, above all when the host may start the game.
 *
 * @author Mario Koehler
 */
class LobbyViewTest {

    private static PlayerInfo player(int seat, String name, boolean ready, boolean host) {
        return new PlayerInfo(seat, name, ready, true, host);
    }

    private static LobbyState lobby(PlayerInfo... players) {
        return new LobbyState(List.of(players), "First Board", 8, 2, 12, 12, 3, 3, 90, "first-board", "{}",
            List.of(new BoardChoice("first-board", "First Board", 8), new BoardChoice("small", "Small", 2)));
    }

    /**
     * There is a row for every seat of the board, in seat order; the taken ones carry the player and the free ones are
     * marked, and the row of the viewer is the only one that is "you".
     */
    @Test
    void rowsCoverEverySeat() {
        LobbyView view = new LobbyView(lobby(player(0, "Sophie", true, true), player(2, "Kenji", false, false)), 2);

        List<LobbyView.Row> rows = view.rows();

        assertEquals(8, rows.size());
        assertEquals(new LobbyView.Row(0, true, "Sophie", "Bolt", true, false, true, false, "Normal", false, false),
            rows.get(0));
        assertEquals(new LobbyView.Row(1, false, "", "Twin", false, false, false, false, "", false, false), rows.get(1));
        assertEquals(new LobbyView.Row(2, true, "Kenji", "Cog", false, true, false, false, "Normal", false, false),
            rows.get(2));
        assertFalse(rows.get(7).occupied());
        assertEquals("Stack", rows.get(7).robotName());
    }

    /**
     * The counter and the facts are worded as the design shows them.
     */
    @Test
    void textsAreWordedForTheScreen() {
        LobbyView view = new LobbyView(lobby(player(0, "A", false, true), player(1, "B", false, false)), 0);

        assertEquals("2 of 8", view.countText());
        assertEquals("First Board · 12 × 12", view.boardText());
        assertEquals("3, in order", view.flagsText());
        assertEquals("3 per robot", view.livesText());
        assertEquals("90 seconds", view.programmingTimeText());
    }

    /**
     * A board with one flag says just that.
     */
    @Test
    void oneFlagIsNotInOrder() {
        LobbyState one = new LobbyState(List.of(), "B", 4, 2, 5, 30, 1, 3, 90, "b", "{}", List.of());

        assertEquals("1", new LobbyView(one, 0).flagsText());
    }

    /**
     * The host may start when enough players are seated and every other player is ready, and whether the host is ready
     * makes no difference, exactly as the server decides.
     */
    @Test
    void theHostMayStartWhenEveryoneElseIsReady() {
        LobbyView hostNotReady = new LobbyView(lobby(player(0, "Host", false, true), player(1, "B", true, false)), 0);
        LobbyView hostReady = new LobbyView(lobby(player(0, "Host", true, true), player(1, "B", true, false)), 0);

        assertTrue(hostNotReady.canStart());
        assertTrue(hostReady.canStart());
        assertEquals("Start game", hostNotReady.startLabel());
        assertEquals("Everyone is ready. Start the game when you like.", hostNotReady.hint());
    }

    /**
     * The host may not start while somebody is not ready, and the hint says why.
     */
    @Test
    void theHostMayNotStartWhileSomebodyIsNotReady() {
        LobbyView view = new LobbyView(lobby(player(0, "Host", true, true), player(1, "B", false, false)), 0);

        assertFalse(view.canStart());
        assertEquals("Waiting for everyone to be ready.", view.hint());
    }

    /**
     * The host may not start alone, and the hint names the number of players needed.
     */
    @Test
    void theHostMayNotStartWithTooFewPlayers() {
        LobbyView view = new LobbyView(lobby(player(0, "Host", true, true)), 0);

        assertFalse(view.canStart());
        assertEquals("At least 2 players are needed to start. Add a bot to play alone.", view.hint());
    }

    /**
     * Another player cannot start the game, however ready everybody is, and sees that the host has to.
     */
    @Test
    void onlyTheHostCanStart() {
        LobbyView view = new LobbyView(lobby(player(0, "Host", true, true), player(1, "B", true, false)), 1);

        assertFalse(view.iAmHost());
        assertFalse(view.canStart());
        assertEquals("Waiting for the host", view.startLabel());
        assertTrue(view.iAmReady());
    }

    /**
     * A viewer whose seat is not in the state, for example just after the host left, is not the host and not ready.
     */
    @Test
    void aViewerWithoutASeatHasNoPowers() {
        LobbyView view = new LobbyView(lobby(player(0, "Host", true, true)), 5);

        assertFalse(view.iAmHost());
        assertFalse(view.iAmReady());
        assertFalse(view.canStart());
    }

    /**
     * Only the host may choose the board; every offered board is listed with the chosen one marked, and a board with too
     * few start squares for the seats already taken does not fit.
     */
    @Test
    void theHostChoosesAmongTheBoardsThatFit() {
        LobbyState state = lobby(player(0, "Host", false, true), player(1, "B", false, false));
        LobbyView host = new LobbyView(state, 0);
        LobbyView guest = new LobbyView(state, 1);

        assertTrue(host.canChooseBoard());
        assertFalse(guest.canChooseBoard());
        assertEquals(List.of(new LobbyView.BoardOption("first-board", "First Board", 8, true, true),
            new LobbyView.BoardOption("small", "Small", 2, false, true)), host.boardOptions());

        LobbyView seatThreeTaken = new LobbyView(lobby(player(0, "Host", false, true), player(2, "C", false, false)), 0);
        assertFalse(seatThreeTaken.boardOptions().get(1).fits(), "seat 3 is taken, a two-seat board cannot hold it");
    }
    /**
     * The host is offered a bot on the first free seat only, which is the seat the server fills, and may remove bots but
     * never humans; everybody else gets neither. A bot counts as a ready player, so the host can start with bots alone.
     */
    @Test
    void theHostManagesBots() {
        PlayerInfo bot = new PlayerInfo(1, "WALL-E", true, true, false, true);
        LobbyState state = lobby(player(0, "Sophie", false, true), bot, player(3, "Kenji", true, false));

        List<LobbyView.Row> host = new LobbyView(state, 0).rows();
        List<LobbyView.Row> guest = new LobbyView(state, 3).rows();

        assertTrue(host.get(1).bot() && host.get(1).removable());
        assertEquals("Normal", host.get(1).difficulty(), "a new bot starts at normal difficulty");
        assertFalse(host.get(3).removable(), "a human is never removable");
        assertTrue(host.get(2).addBot());
        assertFalse(host.get(4).addBot(), "only the first free seat offers a bot");
        assertTrue(guest.stream().noneMatch(row -> row.addBot() || row.removable()));
        assertEquals(1, new LobbyView(state, 0).botCount());
        assertTrue(new LobbyView(lobby(player(0, "Sophie", false, true), bot), 0).canStart());
    }

    /**
     * The row shows whatever difficulty the server sent for that bot, in words.
     */
    @Test
    void theHostSeesABotsDifficulty() {
        PlayerInfo easy = new PlayerInfo(1, "WALL-E", true, true, false, true, BotDifficulty.EASY);
        PlayerInfo hard = new PlayerInfo(2, "HAL 9000", true, true, false, true, BotDifficulty.HARD);
        LobbyState state = lobby(player(0, "Sophie", false, true), easy, hard);

        List<LobbyView.Row> rows = new LobbyView(state, 0).rows();

        assertEquals("Easy", rows.get(1).difficulty());
        assertEquals("Hard", rows.get(2).difficulty());
    }
}
