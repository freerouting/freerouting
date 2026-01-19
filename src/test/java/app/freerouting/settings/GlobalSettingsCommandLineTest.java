package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for command line argument parsing in GlobalSettings, specifically for
 * the -de argument
 * that supports multiple file types (DSN, SES, RULES).
 */
class GlobalSettingsCommandLineTest {

    private GlobalSettings settings;

    @BeforeEach
    void setUp() {
        settings = new GlobalSettings();
    }

    @Test
    void testSingleDsnFile() {
        String[] args = { "-de", "myboard.dsn" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertNull(settings.design_session_filename);
        assertNull(settings.design_rules_filename);
    }

    @Test
    void testDsnAndSesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertNull(settings.design_rules_filename);
    }

    @Test
    void testDsnAndRulesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertNull(settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testAllThreeFilesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.ses+myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testFilesInDifferentOrder() {
        String[] args = { "-de", "myboard.rules+myboard.dsn+myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testSpaceSeparatedFiles() {
        String[] args = { "-de", "myboard.dsn", "myboard.ses", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testMixedSeparators() {
        String[] args = { "-de", "myboard.dsn+myboard.ses", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testFilesWithPaths() {
        String[] args = { "-de", "/path/to/myboard.dsn+/path/to/myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("/path/to/myboard.dsn", settings.design_input_filename);
        assertEquals("/path/to/myboard.ses", settings.design_session_filename);
    }

    @Test
    void testCaseInsensitiveExtensions() {
        String[] args = { "-de", "myboard.DSN+myboard.SES+myboard.RULES" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.DSN", settings.design_input_filename);
        assertEquals("myboard.SES", settings.design_session_filename);
        assertEquals("myboard.RULES", settings.design_rules_filename);
    }

    @Test
    void testMultipleDsnFilesUsesLast() {
        String[] args = { "-de", "board1.dsn+board2.dsn" };
        settings.applyCommandLineArguments(args);

        // Should use the last DSN file
        assertEquals("board2.dsn", settings.design_input_filename);
    }

    @Test
    void testOnlySesFile() {
        String[] args = { "-de", "myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.design_input_filename);
        assertEquals("myboard.ses", settings.design_session_filename);
    }

    @Test
    void testOnlyRulesFile() {
        String[] args = { "-de", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.design_input_filename);
        assertNull(settings.design_session_filename);
        assertEquals("myboard.rules", settings.design_rules_filename);
    }

    @Test
    void testEmptyArgument() {
        String[] args = { "-de" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.design_input_filename);
        assertNull(settings.design_session_filename);
        assertNull(settings.design_rules_filename);
    }

    @Test
    void testWithOtherArguments() {
        String[] args = { "-de", "myboard.dsn+myboard.ses", "-do", "output.ses", "-mp", "10" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("output.ses", settings.initialOutputFile);
        assertEquals(10, settings.routerSettings.maxPasses);
    }
}
