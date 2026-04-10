package app.freerouting.settings.sources;

import app.freerouting.designforms.specctra.io.DsnReadResult;
import app.freerouting.designforms.specctra.io.DsnReader;
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
        this.settings = (result instanceof DsnReadResult.Success s && s.metadata() != null
            && s.metadata().routerSettings() != null)
            ? s.metadata().routerSettings()
            : new RouterSettings();
        FRLogger.debug("Loaded router settings from DSN file: " + filename);
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