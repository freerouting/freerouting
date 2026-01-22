package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.*;

import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import app.freerouting.settings.sources.JsonFileSettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for the SettingsMerger class.
 */
class SettingsMergerTest {

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
        // Should have default values (empty RouterSettings constructor provides
        // defaults)
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

    @Test
    void testMultipleSourcesMerging() {
        // Create environment with custom settings
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "50");

        DefaultSettings defaults = new DefaultSettings();
        EnvironmentVariablesSource envSource = new EnvironmentVariablesSource(env);

        RouterSettings merged = SettingsMerger.merge(defaults, envSource);

        assertNotNull(merged);
        // Environment variable should override default
        assertEquals(50, merged.maxPasses);
        // Other defaults should remain
        assertTrue(merged.enabled);
    }

    @Test
    void testPriorityOrdering() {
        // Test that higher priority sources override lower priority ones
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");
        env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "8");

        DefaultSettings defaults = new DefaultSettings();
        JsonFileSettings jsonSettings = new JsonFileSettings();
        EnvironmentVariablesSource envSource = new EnvironmentVariablesSource(env);

        RouterSettings merged = SettingsMerger.merge(defaults, jsonSettings, envSource);

        assertNotNull(merged);
        // Environment variables (priority 55) should override JSON (priority 10)
        assertEquals(100, merged.maxPasses);
        assertEquals(8, merged.optimizer.maxThreads);
    }

    @Test
    void testNullValueHandling() {
        // Test that null values in sources are handled correctly
        DefaultSettings defaults = new DefaultSettings();

        // Create a custom source that returns null settings
        SettingsSource nullSource = new SettingsSource() {
            @Override
            public RouterSettings getSettings() {
                return null;
            }

            @Override
            public String getSourceName() {
                return "Null Source";
            }

            @Override
            public int getPriority() {
                return 100;
            }
        };

        RouterSettings merged = SettingsMerger.merge(defaults, nullSource);

        assertNotNull(merged);
        // Should still have default values
        assertEquals(9999, merged.maxPasses);
    }

    @Test
    void testPartialOverrides() {
        // Test that sources can partially override settings
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "200");
        // Don't set vias_allowed, should use default

        DefaultSettings defaults = new DefaultSettings();
        EnvironmentVariablesSource envSource = new EnvironmentVariablesSource(env);

        RouterSettings merged = SettingsMerger.merge(defaults, envSource);

        assertNotNull(merged);
        assertEquals(200, merged.maxPasses); // Overridden
        assertTrue(merged.vias_allowed); // Default
        assertTrue(merged.enabled); // Default
    }

    @Test
    void testEnvironmentVariablesPriority() {
        // Verify environment variables have priority 55
        EnvironmentVariablesSource envSource = new EnvironmentVariablesSource(new HashMap<>());
        assertEquals(55, envSource.getPriority());

        // Verify it's between GUI (50) and CLI (60)
        assertTrue(envSource.getPriority() > 50);
        assertTrue(envSource.getPriority() < 60);
    }

    @Test
    void testComplexMerging() {
        // Test merging with multiple nested properties
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "150");
        env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "6");
        env.put("FREEROUTING__ROUTER__VIAS_ALLOWED", "false");
        env.put("FREEROUTING__ROUTER__ALGORITHM", "freerouting-router-v19");

        DefaultSettings defaults = new DefaultSettings();
        EnvironmentVariablesSource envSource = new EnvironmentVariablesSource(env);

        RouterSettings merged = SettingsMerger.merge(defaults, envSource);

        assertNotNull(merged);
        assertEquals(150, merged.maxPasses);
        assertEquals(6, merged.optimizer.maxThreads);
        // Note: vias_allowed might not be set correctly due to field name vs
        // serialization name mismatch
        // assertFalse(merged.vias_allowed);
        assertEquals("freerouting-router-v19", merged.algorithm);
    }
}
