package app.freerouting.settings.sources;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides hardcoded default values for all router settings.
 * This has the lowest priority and serves as the base for all other settings.
 */
public class DefaultSettings implements SettingsSource {

    private static final int PRIORITY = 0;

    @Override
    public RouterSettings getSettings() {
        // Create a RouterSettings object with all default values
        // These are the same defaults currently used in RouterSettings constructor
        RouterSettings settings = new RouterSettings();
        return settings;
    }

    @Override
    public String getSourceName() {
        return "Default Settings";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
