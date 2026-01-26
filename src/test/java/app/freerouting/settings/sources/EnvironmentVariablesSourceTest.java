package app.freerouting.settings.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EnvironmentVariablesSource.
 */
class EnvironmentVariablesSourceTest {

    @Test
    void testPriority() {
        EnvironmentVariablesSource source = new EnvironmentVariablesSource(new HashMap<>());
        assertEquals(55, source.getPriority());
    }

    @Test
    void testSourceName() {
        EnvironmentVariablesSource source = new EnvironmentVariablesSource(new HashMap<>());
        assertEquals("Environment Variables", source.getSourceName());
    }

    @Test
    void testEmptyEnvironment() {
        Map<String, String> env = new HashMap<>();
        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);

        RouterSettings settings = source.getSettings();
        assertNotNull(settings);
        assertEquals(0, source.getParsedCount());
    }

    @Test
    void testSimpleRouterSetting() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(100, settings.maxPasses);
        assertEquals(1, source.getParsedCount());
    }

    @Test
    void testNestedRouterSetting() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "8");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(8, settings.optimizer.maxThreads);
        assertEquals(1, source.getParsedCount());
    }

    @Test
    void testBooleanSetting() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__VIAS_ALLOWED", "false");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertFalse(settings.vias_allowed);
        assertEquals(1, source.getParsedCount());
    }

    @Test
    void testMultipleSettings() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "50");
        env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "4");
        env.put("FREEROUTING__ROUTER__VIAS_ALLOWED", "true");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(50, settings.maxPasses);
        assertEquals(4, settings.optimizer.maxThreads);
        assertTrue(settings.vias_allowed);
        assertEquals(3, source.getParsedCount());
    }

    @Test
    void testIgnoresNonRouterVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__GUI__INPUT_DIRECTORY", "/some/path");
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");
        env.put("PATH", "/usr/bin");
        env.put("HOME", "/home/user");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(100, settings.maxPasses);
        // Only router settings should be parsed
        assertEquals(1, source.getParsedCount());
    }

    @Test
    void testIgnoresVariablesWithoutPrefix() {
        Map<String, String> env = new HashMap<>();
        env.put("MAX_PASSES", "100");
        env.put("ROUTER__MAX_PASSES", "200");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        // Null value should remain since no valid env vars were found
        assertEquals(null, settings.maxPasses);
        assertEquals(0, source.getParsedCount());
    }

    @Test
    void testInvalidPropertyName() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__INVALID_PROPERTY", "value");
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(100, settings.maxPasses);
        // Only valid property should be parsed
        assertEquals(1, source.getParsedCount());
    }

    @Test
    void testInvalidValue() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "not_a_number");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        // Should keep null value since parsing failed
        assertEquals(null, settings.maxPasses);
        assertEquals(0, source.getParsedCount());
    }

    @Test
    void testGetParsedVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");
        env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "4");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        source.getSettings(); // Trigger parsing

        Map<String, String> parsed = source.getParsedVariables();
        assertEquals(2, parsed.size());
        assertEquals("100", parsed.get("FREEROUTING__ROUTER__MAX_PASSES"));
        assertEquals("4", parsed.get("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS"));
    }

    @Test
    void testCaseInsensitivePropertyNames() {
        Map<String, String> env = new HashMap<>();
        // Environment variables are case-sensitive, but property names are converted to
        // lowercase
        env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals(100, settings.maxPasses);
    }

    @Test
    void testStringSettings() {
        Map<String, String> env = new HashMap<>();
        env.put("FREEROUTING__ROUTER__ALGORITHM", "freerouting-router-v19");

        EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
        RouterSettings settings = source.getSettings();

        assertNotNull(settings);
        assertEquals("freerouting-router-v19", settings.algorithm);
        assertEquals(1, source.getParsedCount());
    }
}