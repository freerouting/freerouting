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

        settings.enabled = true;
        settings.algorithm = RouterSettings.ALGORITHM_CURRENT;
        settings.jobTimeoutString = "12:00:00";
        settings.maxPasses = 9999;
        settings.maxItems = Integer.MAX_VALUE;
        settings.trace_pull_tight_accuracy = 500;
        settings.vias_allowed = true;
        settings.automatic_neckdown = true;
        settings.save_intermediate_stages = false;
        settings.ignoreNetClasses = new String[0];
        settings.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        settings.setLayerCount(2);

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