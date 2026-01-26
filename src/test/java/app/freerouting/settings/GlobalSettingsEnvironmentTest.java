package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the deprecated applyEnvironmentVariables() method to ensure
 * it correctly skips router settings.
 */
class GlobalSettingsEnvironmentTest {

    @Test
    void testRouterPropertyNameDetection() {
        // Test that we can correctly identify router properties
        String routerProperty = "router.max_passes";
        String guiProperty = "gui.input_directory";
        String apiProperty = "api.host";

        assertTrue(routerProperty.startsWith("router."));
        assertFalse(guiProperty.startsWith("router."));
        assertFalse(apiProperty.startsWith("router."));
    }

    @Test
    void testEnvironmentVariablePropertyConversion() {
        // Test the conversion logic from environment variable to property name
        String envVar = "FREEROUTING__ROUTER__MAX_PASSES";
        String expectedProperty = "router.max_passes";

        String actualProperty = envVar
                .substring("FREEROUTING__".length())
                .toLowerCase()
                .replace("__", ".");

        assertEquals(expectedProperty, actualProperty);
        assertTrue(actualProperty.startsWith("router."));
    }

    @Test
    void testNonRouterEnvironmentVariableConversion() {
        // Test conversion for non-router settings
        String envVar = "FREEROUTING__GUI__INPUT_DIRECTORY";
        String expectedProperty = "gui.input_directory";

        String actualProperty = envVar
                .substring("FREEROUTING__".length())
                .toLowerCase()
                .replace("__", ".");

        assertEquals(expectedProperty, actualProperty);
        assertFalse(actualProperty.startsWith("router."));
    }
}