package app.freerouting.settings.sources;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Settings source for unit tests.
 * Allows injecting specific settings needed for testing with high priority.
 */
public class TestingSettings implements SettingsSource {

    private final RouterSettings settings;

    public TestingSettings() {
        this.settings = new RouterSettings();
        // Set complex objects to null to avoid overwriting them with defaults during
        // merge
        // (RouterSettings constructor initializes them)
        this.settings.optimizer = null;
        this.settings.scoring = null;
    }

    public void setMaxItems(int maxItems) {
        this.settings.maxItems = maxItems;
    }

    public void setMaxPasses(int maxPasses) {
        if (this.settings.maxPasses == null) {
            this.settings.maxPasses = maxPasses;
        }
    }

    public void setJobTimeoutString(String jobTimeoutString) {
        if (this.settings.jobTimeoutString == null) {
            this.settings.jobTimeoutString = jobTimeoutString;
        }
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "Testing Settings";
    }

    @Override
    public int getPriority() {
        // High priority to override other sources (API is 70)
        return 80;
    }
}