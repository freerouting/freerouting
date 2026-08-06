package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Extracts router settings from SES (Specctra Session) files.
 * Only the settings present in the SES file will be non-null.
 */
public class SesFileSettings implements SettingsSource {

    private static final int PRIORITY = 30;
    private final RouterSettings settings;
    private final String fileName;

    /**
     * Creates a SesFileSettings source from a SES file.
     * 
     * @param fileName Name of the SES file (for logging)
     */
    public SesFileSettings(String fileName) {
        this.fileName = fileName;
        this.settings = loadSettings();
    }

    private RouterSettings loadSettings() {
        try {
            // SES files carry routing results, not router configuration; this source is
            // a structural placeholder in the SettingsMerger priority ladder.
            return new RouterSettings();
        } catch (Exception e) {
            FRLogger.warn("Failed to load settings from SES file: " + fileName + ": " + e.getMessage());
            return new RouterSettings();
        }
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "SES file: " + fileName;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
