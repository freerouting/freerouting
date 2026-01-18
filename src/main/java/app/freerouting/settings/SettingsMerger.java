package app.freerouting.settings;

import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Central class responsible for merging router settings from multiple sources.
 * 
 * Settings are merged according to priority order:
 * 1. Default settings (priority 0) - lowest priority, applied first
 * 2. JSON file settings (priority 10)
 * 3. DSN file settings (priority 20)
 * 4. SES file settings (priority 30)
 * 5. RULES file settings (priority 40)
 * 6. GUI settings (priority 50)
 * 7. CLI settings (priority 60)
 * 8. API settings (priority 70) - highest priority, overrides all others
 * 
 * The merger uses reflection to copy only non-null fields from each source,
 * allowing higher-priority sources to override lower-priority ones.
 */
public class SettingsMerger {

    /**
     * Merges router settings from multiple sources according to their priority.
     * 
     * @param sources List of settings sources to merge
     * @return Merged RouterSettings object with all settings resolved
     */
    public static RouterSettings merge(List<SettingsSource> sources) {
        if (sources == null || sources.isEmpty()) {
            FRLogger.warn("No settings sources provided, using defaults");
            return new RouterSettings();
        }

        // Sort sources by priority (lowest to highest)
        List<SettingsSource> sortedSources = new ArrayList<>(sources);
        sortedSources.sort(Comparator.comparingInt(SettingsSource::getPriority));

        // Start with an empty RouterSettings object
        // The first source (usually DefaultSettings) will provide the base
        RouterSettings mergedSettings = null;

        FRLogger.debug("Merging settings from " + sortedSources.size() + " sources:");

        for (SettingsSource source : sortedSources) {
            RouterSettings sourceSettings = source.getSettings();

            if (sourceSettings == null) {
                FRLogger.debug(
                        "  - " + source.getSourceName() + " (priority " + source.getPriority() + "): no settings");
                continue;
            }

            if (mergedSettings == null) {
                // First source provides the base settings
                mergedSettings = sourceSettings.clone();
                FRLogger.debug(
                        "  - " + source.getSourceName() + " (priority " + source.getPriority() + "): base settings");
            } else {
                // Apply this source's settings on top of the merged settings
                int fieldsChanged = mergedSettings.applyNewValuesFrom(sourceSettings);
                FRLogger.debug("  - " + source.getSourceName() + " (priority " + source.getPriority() + "): "
                        + fieldsChanged + " fields changed");
            }
        }

        if (mergedSettings == null) {
            FRLogger.warn("No valid settings found in any source, using defaults");
            mergedSettings = new RouterSettings();
        }

        // Validate the merged settings
        validateSettings(mergedSettings);

        FRLogger.info("Settings merged successfully from " + sortedSources.size() + " sources");
        return mergedSettings;
    }

    /**
     * Validates the merged settings to ensure they are within acceptable ranges.
     * 
     * @param settings Settings to validate
     */
    private static void validateSettings(RouterSettings settings) {
        // Validate maxPasses
        if (settings.maxPasses < 1 || settings.maxPasses > 9999) {
            FRLogger.warn("Invalid maxPasses value: " + settings.maxPasses + ", using default 9999");
            settings.maxPasses = 9999;
        }

        // Validate maxThreads
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (settings.maxThreads < 1 || settings.maxThreads > availableProcessors) {
            FRLogger.warn("Invalid maxThreads value: " + settings.maxThreads + ", using "
                    + Math.max(1, availableProcessors - 1));
            settings.maxThreads = Math.max(1, availableProcessors - 1);
        }

        // Validate trace_pull_tight_accuracy
        if (settings.trace_pull_tight_accuracy < 1) {
            FRLogger.warn("Invalid trace_pull_tight_accuracy value: " + settings.trace_pull_tight_accuracy
                    + ", using default 500");
            settings.trace_pull_tight_accuracy = 500;
        }

        // Add more validations as needed
    }

    /**
     * Convenience method to merge settings from a variable number of sources.
     * 
     * @param sources Variable number of settings sources
     * @return Merged RouterSettings object
     */
    public static RouterSettings merge(SettingsSource... sources) {
        return merge(List.of(sources));
    }
}
