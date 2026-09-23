package de.mkoehler.robotrampage.client.settings;

import de.mkoehler.robotrampage.net.NetworkConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SettingsStore} keeps the {@link ClientSettings} between runs and never lets a bad file get in the
 * way of starting the game.
 *
 * @author Mario Koehler
 */
class SettingsStoreTest {

    @TempDir
    Path folder;

    /**
     * Without a file the defaults come back, and the name in them fits the name limit.
     */
    @Test
    void aMissingFileGivesTheDefaults() {
        ClientSettings loaded = new SettingsStore(folder.resolve("none.json")).load();

        assertEquals(ClientSettings.defaults(), loaded);
        assertEquals("NAS5714.myqnapcloud.com:" + NetworkConstants.TCP_PORT, loaded.serverAddress());
        assertTrue(loaded.displayName().length() <= NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
    }

    /**
     * Saved settings are read back unchanged, and the folder is created on the way.
     */
    @Test
    void savedSettingsComeBack() {
        SettingsStore store = new SettingsStore(folder.resolve("nested").resolve("settings.json"));
        ClientSettings settings = ClientSettings.defaults().withServerAddress("robots.example.org:4000")
            .withDisplayName("Łukasz").withSessionToken("token-abc").withPreferences(0.4f, true, false, 1600, 900, 4f,
                false);

        assertTrue(store.save(settings));

        assertEquals(settings, store.load());
    }

    /**
     * Saving again replaces the earlier content.
     */
    @Test
    void savingAgainReplacesTheFile() {
        SettingsStore store = new SettingsStore(folder.resolve("settings.json"));
        store.save(ClientSettings.defaults().withServerAddress("a:1").withDisplayName("A"));

        store.save(ClientSettings.defaults().withServerAddress("b:2").withDisplayName("B"));

        assertEquals(ClientSettings.defaults().withServerAddress("b:2").withDisplayName("B"), store.load());
    }

    /**
     * A file that is not JSON is ignored in favour of the defaults.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void aBrokenFileGivesTheDefaults() throws IOException {
        Path broken = folder.resolve("broken.json");
        Files.writeString(broken, "this is { not json");

        assertEquals(ClientSettings.defaults(), new SettingsStore(broken).load());
    }

    /**
     * An unknown property (such as one a later version removed) is ignored, and a property the file simply never had
     * (such as one a later version added) is merged in from the real defaults instead of coming back as a bare
     * {@code 0}/{@code false}/{@code null} — the point of loading onto {@link ClientSettings#defaults()} rather than
     * parsing the file alone.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void unknownAndMissingPropertiesAreTolerated() throws IOException {
        Path future = folder.resolve("future.json");
        Files.writeString(future, "{\"serverAddress\":\"h:1\",\"musicVolume\":0.5}");

        ClientSettings loaded = new SettingsStore(future).load();

        ClientSettings expected = ClientSettings.defaults().withServerAddress("h:1");
        assertEquals(expected, loaded);
        assertEquals(expected.volume(), loaded.volume(), "an unknown property must not disturb a real default");
    }

    /**
     * When the file cannot be written, saving says so instead of throwing.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void anUnwritableLocationReportsFailure() throws IOException {
        Path blocker = folder.resolve("blocker");
        Files.writeString(blocker, "a file where a folder is needed");

        assertFalse(new SettingsStore(blocker.resolve("settings.json")).save(ClientSettings.defaults()));
    }
}
