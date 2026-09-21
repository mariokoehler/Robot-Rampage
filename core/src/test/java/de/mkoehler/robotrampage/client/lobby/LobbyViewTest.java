package de.mkoehler.robotrampage.client.lobby;

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
        return new LobbyState(List.of(players), "First Board", 8, 2, 12, 12, 3, 3, 90);
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
        assertEquals(new LobbyView.Row(0, true, "Sophie", "Bolt", true, false, true), rows.get(0));
        assertEquals(new LobbyView.Row(1, false, "", "Twin", false, false, false), rows.get(1));
        assertEquals(new LobbyView.Row(2, true, "Kenji", "Cog", false, true, false), rows.get(2));
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
        LobbyState one = new LobbyState(List.of(), "B", 4, 2, 5, 30, 1, 3, 90);

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
        assertEquals("At least 2 players are needed to start.", view.hint());
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
}
