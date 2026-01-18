package app.freerouting.settings;

/**
 * Base interface for all settings sources. Each source provides router settings
 * from a specific origin (defaults, JSON file, DSN file, CLI arguments, etc.).
 * 
 * Settings sources use nullable fields to indicate which settings they provide.
 * A null value means the source does not specify that particular setting.
 * 
 * The SettingsMerger uses the priority to determine the order in which settings
 * are applied. Lower priority numbers are applied first (defaults), higher
 * priority numbers override earlier settings (API, CLI).
 */
public interface SettingsSource {

    /**
     * Gets the router settings provided by this source.
     * Only the settings that this source explicitly provides should be non-null.
     * 
     * @return RouterSettings object with nullable fields
     */
    RouterSettings getSettings();

    /**
     * Gets a human-readable name for this settings source.
     * Used for logging and debugging.
     * 
     * @return Source name (e.g., "Default Settings", "freerouting.json", "CLI
     *         Arguments")
     */
    String getSourceName();

    /**
     * Gets the priority of this settings source.
     * Lower numbers = lower priority (applied first)
     * Higher numbers = higher priority (override earlier settings)
     * 
     * Priority order:
     * 0 = Default settings
     * 10 = JSON file settings
     * 20 = DSN file settings
     * 30 = SES file settings
     * 40 = RULES file settings
     * 50 = GUI settings
     * 60 = CLI settings
     * 70 = API settings
     * 
     * @return Priority value (0-100)
     */
    int getPriority();
}
