package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Extracts router settings from RULES files.
 * Only the settings present in the RULES file will be non-null.
 */
public class RulesFileSettings implements SettingsSource {

    private static final int PRIORITY = 40;
    private final RouterSettings settings;
    private final String fileName;

    /**
     * Creates a RulesFileSettings source from a RULES file.
     *
     * @param fileName Name of the RULES file (for logging)
     */
    public RulesFileSettings(String fileName) {
        this.fileName = fileName;
        this.settings = loadSettings();
    }

    private RouterSettings loadSettings() {
        try {
            // Design rules from .rules files are applied to the board via RulesReader;
            // this source is a structural placeholder for behavioral RouterSettings.
            return new RouterSettings();
        } catch (Exception e) {
            FRLogger.warn("Failed to load settings from RULES file: " + fileName + ": " + e.getMessage());
            return new RouterSettings();
        }
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "RULES file: " + fileName;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
