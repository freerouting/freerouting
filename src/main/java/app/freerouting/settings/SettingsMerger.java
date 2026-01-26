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
 * 7. Environment variables (priority 55)
 * 8. CLI settings (priority 60)
 * 9. API settings (priority 70) - highest priority, overrides all others
 *
 * The merger uses reflection to copy only non-null fields from each source,
 * allowing higher-priority sources to override lower-priority ones.
 */
public class SettingsMerger {

    private final List<SettingsSource> sources = new ArrayList<>();

    /**
     * Creates a SettingsMerger with the specified list of settings sources.
     *
     * @param sources List of settings sources to merge
     */
    public SettingsMerger(SettingsSource... sources) {
        this.addOrReplaceSources(sources);
    }

    /**
     * Creates a SettingsMerger with the specified list of settings sources.
     *
     * @param sources List of settings sources to merge
     */
    public SettingsMerger(List<SettingsSource> sources) {
        this.addOrReplaceSources(sources);
    }

    /**
     * Adds or replaces settings sources in the merger.
     *
     * @param sources Variable number of settings sources
     */
    public void addOrReplaceSources(SettingsSource... sources) {
        addOrReplaceSources(List.of(sources));
    }

    /**
     * Adds or replaces settings sources in the merger.
     * If a source of the same class already exists, it is replaced.
     *
     * @param newSources List of new settings sources to add or replace
     */
    public void addOrReplaceSources(List<SettingsSource> newSources)
    {
        for (SettingsSource newSource : newSources) {
            boolean replaced = false;
            for (int i = 0; i < sources.size(); i++) {
                SettingsSource existingSource = sources.get(i);
                if (existingSource.getClass().equals(newSource.getClass())) {
                    sources.set(i, newSource);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                sources.add(newSource);
            }
        }
    }

    /**
     * Merges router settings from multiple sources according to their priority.
     *
     * @return Merged RouterSettings object with all settings resolved
     */
    public RouterSettings merge() {
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
        mergedSettings.validate();

        FRLogger.info("Settings merged successfully from " + sortedSources.size() + " sources");
        return mergedSettings;
    }
}