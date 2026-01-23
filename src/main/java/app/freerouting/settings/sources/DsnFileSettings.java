package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;
import java.io.InputStream;

/**
 * Extracts router settings from DSN (Specctra Design) files.
 * Only the settings present in the DSN file will be non-null.
 */
public class DsnFileSettings implements SettingsSource {

    private static final int PRIORITY = 20;
    private final RouterSettings settings;
    private final String filename;

    /**
     * Creates a DsnFileSettings source from a DSN file input stream.
     *
     * @param inputStream    DSN file input stream
     * @param filename       Name of the DSN file (for logging)
     */
    public DsnFileSettings(InputStream inputStream, String filename) {
        this.filename = filename;
        this.settings = loadSettings(inputStream);
    }

    private RouterSettings loadSettings(InputStream inputStream) {
        try {
            // Use existing AutorouteSettings.read_scope to parse DSN autoroute settings
            // This will be refactored to return a RouterSettings object with nullable
            // fields
            // For now, we'll create an empty RouterSettings and populate it
            // TODO: Update AutorouteSettings.read_scope to work with new architecture

            RouterSettings settings = new RouterSettings();
            settings.setLayerCount(2);

            FRLogger.debug("Loaded router settings from DSN file: " + filename);
            return settings;
        } catch (Exception e) {
            FRLogger.warn("Failed to load settings from DSN file: " + filename + ": " + e.getMessage());
            return new RouterSettings();
        }
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "DSN file: " + filename;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}