package app.freerouting.settings.sources;

import app.freerouting.io.specctra.DsnReadResult;
import app.freerouting.io.specctra.DsnReader;
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
     * Performs a lightweight metadata-only parse that stops after the
     * {@code (structure ...)} scope — significantly faster than a full board load.
     *
     * @param inputStream DSN file input stream (closed by this constructor)
     * @param filename    Name of the DSN file (for logging)
     */
    public DsnFileSettings(InputStream inputStream, String filename) {
        this.filename = filename;
        DsnReadResult result = DsnReader.readMetadata(inputStream);

        RouterSettings extracted = null;
        int layerCount = 0;

        if (result instanceof DsnReadResult.Success s && s.metadata() != null) {
            extracted = s.metadata().routerSettings(); // null when no (autoroute_settings …) block
            layerCount = s.metadata().layerCount();
        }

        // Start with whatever the DSN's autoroute block provided (or a blank slate).
        RouterSettings rs = (extracted != null) ? extracted : new RouterSettings();

        // Always seed the layer arrays from the actual DSN layer count so that any board
        // with more or fewer than 2 layers gets correctly-sized arrays in the merged result –
        // well before applyBoardSpecificOptimizations() is called.
        if (layerCount > 0 && rs.getLayerCount() == 0) {
            rs.setLayerCount(layerCount);
        }

        this.settings = rs;
        FRLogger.debug("Loaded router settings from DSN file: " + filename
            + " (" + layerCount + " layers)");
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