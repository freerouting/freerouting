package app.freerouting.settings.sources;

import app.freerouting.designforms.specctra.LayerStructure;
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
    private final String fileName;

    /**
     * Creates a DsnFileSettings source from a DSN file input stream.
     * 
     * @param inputStream    DSN file input stream
     * @param fileName       Name of the DSN file (for logging)
     * @param layerStructure Layer structure from the DSN file
     */
    public DsnFileSettings(InputStream inputStream, String fileName, LayerStructure layerStructure) {
        this.fileName = fileName;
        this.settings = loadSettings(inputStream, layerStructure);
    }

    private RouterSettings loadSettings(InputStream inputStream, LayerStructure layerStructure) {
        try {
            // Use existing AutorouteSettings.read_scope to parse DSN autoroute settings
            // This will be refactored to return a RouterSettings object with nullable
            // fields
            // For now, we'll create an empty RouterSettings and populate it
            // TODO: Update AutorouteSettings.read_scope to work with new architecture

            RouterSettings settings = new RouterSettings();
            // The actual parsing will be done by the existing DSN parser
            // We'll integrate this properly when we refactor AutorouteSettings

            FRLogger.debug("Loaded router settings from DSN file: " + fileName);
            return settings;
        } catch (Exception e) {
            FRLogger.warn("Failed to load settings from DSN file: " + fileName + ": " + e.getMessage());
            return new RouterSettings();
        }
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "DSN file: " + fileName;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
