package app.freerouting.settings.sources;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides router settings from API endpoints.
 * Only the settings specified via API will be non-null.
 * This has the highest priority and overrides all other settings.
 */
public class ApiSettings implements SettingsSource {

    private static final int PRIORITY = 70;
    private final RouterSettings settings;

    /**
     * Creates an ApiSettings source with the specified settings.
     * 
     * @param settings Router settings from the API (should have only specified
     *                 fields non-null)
     */
    public ApiSettings(RouterSettings settings) {
        this.settings = settings != null ? settings : new RouterSettings();
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "API Settings";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
