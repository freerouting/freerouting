package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.logger.FRLogger;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalSettingsTest {

    @BeforeEach
    void setUp() {
        FRLogger.getLogEntries().clear();
    }

    @Test
    void testApplyCommandLineArguments_ValidArguments() {
        GlobalSettings settings = new GlobalSettings();
        String[] args = new String[] {
                "-mp", "10",
                "-di", "/tmp",
                "-im", "0",
                "-dl"
        };

        settings.applyCommandLineArguments(args);

        assertEquals(10, settings.routerSettings.maxPasses);
        assertEquals("/tmp", settings.guiSettings.inputDirectory);
        assertFalse(settings.featureFlags.snapshots);
        assertFalse(settings.featureFlags.logging);

        // Should be no warnings
        assertEquals(0, FRLogger.getLogEntries().getWarningCount(), "Should have no warnings for valid args");
    }

    @Test
    void testApplyCommandLineArguments_UnknownFlag() {
        GlobalSettings settings = new GlobalSettings();
        String[] args = new String[] {
                "-unknownFlag"
        };

        settings.applyCommandLineArguments(args);

        // Expect a warning (this will fail until implemented)
        assertEquals(1, FRLogger.getLogEntries().getWarningCount(), "Should have 1 warning for unknown flag");
        assertTrue(Arrays.stream(FRLogger.getLogEntries().get())
                .anyMatch(s -> s.contains("Unknown command line argument: -unknownFlag")));
    }

    @Test
    void testApplyCommandLineArguments_UnknownValue() {
        GlobalSettings settings = new GlobalSettings();
        // "-mp 10" consumes 2 args. "extraValue" is loose.
        String[] args = new String[] {
                "-mp", "10",
                "extraValue"
        };

        settings.applyCommandLineArguments(args);

        // Expect a warning for "extraValue"
        assertEquals(1, FRLogger.getLogEntries().getWarningCount(), "Should have 1 warning for extra value");
        assertTrue(Arrays.stream(FRLogger.getLogEntries().get())
                .anyMatch(s -> s.contains("Unknown command line argument: extraValue")));
    }

    @Test
    void testApplyCommandLineArguments_ValidDoubleHyphen() {
        GlobalSettings settings = new GlobalSettings();
        String[] args = new String[] {
                "--router.max_passes=20"
        };

        settings.applyCommandLineArguments(args);

        assertEquals(20, settings.routerSettings.maxPasses);
        assertEquals(0, FRLogger.getLogEntries().getWarningCount());
    }

    @Test
    void testSetUnknownProperty() {
        GlobalSettings settings = new GlobalSettings();
        // This should log a warning instead of incorrect stack trace
        settings.setValue("unknown_settings.unknown_field", "true");

        assertEquals(1, FRLogger.getLogEntries().getWarningCount(), "Should log a warning for unknown property");
        assertEquals(0, FRLogger.getLogEntries().getErrorCount(), "Should NOT log an error with stack trace");
        assertTrue(Arrays.stream(FRLogger.getLogEntries().get())
                .anyMatch(s -> s.contains("Unknown settings property: unknown_settings.unknown_field")));
    }

    @Test
    void testApplyCommandLineArguments_MalformedDoubleHyphen() {
        GlobalSettings settings = new GlobalSettings();
        String[] args = new String[] {
                "--someflag"
        };

        settings.applyCommandLineArguments(args);

        assertEquals(1, FRLogger.getLogEntries().getWarningCount(), "Should warn on double-dash arg without equals");
        assertTrue(Arrays.stream(FRLogger.getLogEntries().get())
                .anyMatch(s -> s.contains("Unknown command line argument: --someflag")));
    }
}
