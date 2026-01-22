package app.freerouting.settings.sources;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides router settings from GUI windows (e.g., WindowAutorouteParameter).
 * Only the settings modified by the user in the GUI will be non-null.
 */
public class GuiSettings implements SettingsSource {

    private static final int PRIORITY = 50;
    private final RouterSettings settings;

    /**
     * Creates a GuiSettings source with the specified settings.
     * 
     * @param settings Router settings from the GUI (should have only modified
     *                 fields non-null)
     */
    public GuiSettings(RouterSettings settings) {
        this.settings = settings != null ? settings : new RouterSettings();
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "GUI Settings";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
