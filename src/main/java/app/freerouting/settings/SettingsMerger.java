package app.freerouting.settings;

import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Central class responsible for merging router settings from multiple sources
 * into a single, resolved {@link RouterSettings} instance.
 *
 * <h2>How merging works</h2>
 * Sources are sorted by ascending priority and applied one on top of another.
 * {@link DefaultSettings} (priority 0) always provides the initial base.
 * Every subsequent source calls {@link RouterSettings#applyNewValuesFrom}, which
 * delegates to {@link app.freerouting.management.ReflectionUtil#copyFields}.
 * That method iterates the public, non-static fields of the incoming
 * {@code RouterSettings} object and copies a field into the target <em>only when
 * the source value is non-null and not equal to the field's Java default value</em>.
 *
 * <h2>Why all {@code RouterSettings} fields must be nullable (no default initializers)</h2>
 * The null-check inside {@code copyFields} is the sole mechanism that
 * distinguishes "this source intentionally sets a value" from "this source has no
 * opinion about this field".  If a field were initialised to a non-null default
 * (e.g. {@code public Integer maxPasses = 9999;}), every source's
 * {@code RouterSettings} object would carry that default, and the merger would
 * treat it as an intentional override — meaning a low-priority source (e.g. the
 * JSON file) would silently override a higher-priority source (e.g. the API)
 * whenever the user left that setting unspecified.  Keeping all fields as
 * {@code null} by default ensures that only fields explicitly populated by a
 * source can win at merge time.
 *
 * <h2>Priority order</h2>
 * <ol>
 *   <li>Default settings (priority 0) — lowest priority, applied first as the base</li>
 *   <li>JSON file settings (priority 10) — {@code freerouting.json} in user-data folder</li>
 *   <li>DSN file settings (priority 20) — metadata extracted from the Specctra design file</li>
 *   <li>SES file settings (priority 30) — metadata from Specctra session files</li>
 *   <li>RULES file settings (priority 40) — explicit routing-rule overrides</li>
 *   <li>GUI settings (priority 50) — values changed interactively by the user</li>
 *   <li>Environment variables (priority 55) — {@code FREEROUTING__ROUTER__*} env vars</li>
 *   <li>CLI settings (priority 60) — {@code --router.*} command-line arguments</li>
 *   <li>API settings (priority 70) — highest priority, supplied by a REST API caller</li>
 * </ol>
 *
 * <h2>Adding a new source</h2>
 * Implement {@link SettingsSource}, choose a priority that fits the desired
 * override order, and register the instance via
 * {@link #addOrReplaceSources(SettingsSource...)} before calling {@link #merge()}.
 * Remember that the {@code RouterSettings} object returned by the new source must
 * only populate fields that the source actually provides; all other fields must
 * remain {@code null}.
 */
public class SettingsMerger implements Cloneable {

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

        FRLogger.info("Settings merged successfully from " + sortedSources.size() + " source(s)");
        return mergedSettings;
    }

    @Override
    public SettingsMerger clone() {
        return new SettingsMerger(this.sources);
    }
}