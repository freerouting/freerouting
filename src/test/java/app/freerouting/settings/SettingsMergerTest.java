package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.*;

import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.Test;

/**
 * Basic unit tests for the SettingsMerger class.
 */
class SettingsMergerBasicTest {

    @Test
    void testDefaultSettingsOnly() {
        // Test that default settings work correctly
        DefaultSettings defaultSettings = new DefaultSettings();
        RouterSettings merged = SettingsMerger.merge(defaultSettings);

        assertNotNull(merged);
        // Verify default values
        assertEquals(9999, merged.maxPasses);
        assertTrue(merged.enabled);
        assertTrue(merged.vias_allowed);
    }

    @Test
    void testEmptySourcesList() {
        // Test that empty sources list returns default settings
        RouterSettings merged = SettingsMerger.merge();

        assertNotNull(merged);
        // Should have default values
        assertEquals(9999, merged.maxPasses);
    }

    @Test
    void testSourcePriorities() {
        // Test that priorities are correctly ordered
        DefaultSettings defaultSettings = new DefaultSettings();

        // Verify priority is set
        assertEquals(0, defaultSettings.getPriority());
        assertEquals("Default Settings", defaultSettings.getSourceName());
    }
}
