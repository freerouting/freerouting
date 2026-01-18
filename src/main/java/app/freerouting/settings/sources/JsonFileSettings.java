package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads router settings from the freerouting.json file in the user data folder.
 * Only the settings present in the JSON file will be non-null.
 */
public class JsonFileSettings implements SettingsSource {

    private static final int PRIORITY = 10;
    private final Path jsonFilePath;
    private RouterSettings settings;

    /**
     * Creates a JsonFileSettings source using the default configuration file path.
     */
    public JsonFileSettings() {
        this(GlobalSettings.getUserDataPath().resolve("freerouting.json"));
    }

    /**
     * Creates a JsonFileSettings source from a specific JSON file.
     * 
     * @param jsonFilePath Path to the JSON configuration file
     */
    public JsonFileSettings(Path jsonFilePath) {
        this.jsonFilePath = jsonFilePath;
        this.settings = loadSettings();
    }

    private RouterSettings loadSettings() {
        if (!Files.exists(jsonFilePath)) {
            FRLogger.debug("JSON settings file not found: " + jsonFilePath);
            return new RouterSettings(); // Return empty settings (all nulls after we update RouterSettings)
        }

        try (Reader reader = Files.newBufferedReader(jsonFilePath, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            GlobalSettings globalSettings = gson.fromJson(reader, GlobalSettings.class);

            if (globalSettings != null && globalSettings.routerSettings != null) {
                FRLogger.debug("Loaded router settings from: " + jsonFilePath);
                return globalSettings.routerSettings;
            }
        } catch (IOException e) {
            FRLogger.warn("Failed to load settings from JSON file: " + jsonFilePath + ": " + e.getMessage());
        }

        return new RouterSettings(); // Return empty settings on error
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "freerouting.json";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
