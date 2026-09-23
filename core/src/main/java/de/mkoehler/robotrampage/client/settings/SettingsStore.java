package de.mkoehler.robotrampage.client.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes the {@link ClientSettings} as a JSON file. The file is not the player's to depend on: if it is missing
 * or cannot be read, the defaults are used, and if it cannot be written, the game carries on without remembering.
 *
 * @author Mario Koehler
 */
public final class SettingsStore {

    private static final String DIRECTORY = ".robot-rampage";
    private static final String FILE = "client-settings.json";

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;

    /**
     * Creates a store for a given file.
     *
     * @param file where the settings are kept; the folder is created when saving
     */
    public SettingsStore(Path file) {
        this.file = file;
    }

    /**
     * Creates the store the game uses: a file in a hidden folder in the home directory of the player.
     *
     * @return the store
     */
    public static SettingsStore inHomeDirectory() {
        return new SettingsStore(Path.of(System.getProperty("user.home"), DIRECTORY, FILE));
    }

    /**
     * Loads the settings, merged onto {@link ClientSettings#defaults()}: only the properties the file actually has
     * override a default, so a file saved by an older version (before, say, {@code volume} existed) still gets the
     * real default for what it is missing, not {@code 0}/{@code false} from an empty JSON value. {@code ClientSettings}
     * is a record, so this cannot be done by updating an existing instance in place; it is done by merging JSON trees
     * instead.
     *
     * @return the saved settings, merged onto the defaults, or the defaults alone if there is no readable file
     */
    public ClientSettings load() {
        if (!Files.isRegularFile(file)) {
            return ClientSettings.defaults();
        }
        try {
            JsonNode loaded = mapper.readTree(file.toFile());
            if (!(loaded instanceof ObjectNode loadedObject)) {
                return ClientSettings.defaults();
            }
            ObjectNode merged = mapper.valueToTree(ClientSettings.defaults());
            merged.setAll(loadedObject);
            return mapper.treeToValue(merged, ClientSettings.class);
        } catch (IOException | RuntimeException e) {
            return ClientSettings.defaults();
        }
    }

    /**
     * Reports whether a settings file has ever been saved. {@link #load()} cannot distinguish "no file yet" from "a file
     * that happens to hold exactly the defaults", but the caller needs that distinction once: on a genuinely first run,
     * {@link ClientSettings#defaults()}'s hardcoded 1920x1080 window size must not override the window size the launcher
     * already picked for a smaller monitor.
     *
     * @return {@code true} if the settings file exists
     */
    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /**
     * Saves the settings. The file is written in full to a neighbour first and then moved into place, so a crash cannot leave
     * half a file behind.
     *
     * @param settings what to remember
     * @return {@code true} if the settings were written, {@code false} if writing failed
     */
    public boolean save(ClientSettings settings) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(FILE + ".tmp");
            mapper.writeValue(temporary.toFile(), settings);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
