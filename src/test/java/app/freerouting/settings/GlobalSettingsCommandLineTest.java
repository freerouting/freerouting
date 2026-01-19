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

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertNull(settings.design_session_filename);
        assertNull(settings.initialRulesFile);
    }

    @Test
    void testDsnAndSesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertNull(settings.initialRulesFile);
    }

    @Test
    void testDsnAndRulesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertNull(settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testAllThreeFilesWithPlusSeparator() {
        String[] args = { "-de", "myboard.dsn+myboard.ses+myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testFilesInDifferentOrder() {
        String[] args = { "-de", "myboard.rules+myboard.dsn+myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testSpaceSeparatedFiles() {
        String[] args = { "-de", "myboard.dsn", "myboard.ses", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testMixedSeparators() {
        String[] args = { "-de", "myboard.dsn+myboard.ses", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.dsn", settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testFilesWithPaths() {
        String[] args = { "-de", "/path/to/myboard.dsn+/path/to/myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertEquals("/path/to/myboard.dsn", settings.initialInputFile);
        assertEquals("/path/to/myboard.ses", settings.design_session_filename);
    }

    @Test
    void testCaseInsensitiveExtensions() {
        String[] args = { "-de", "myboard.DSN+myboard.SES+myboard.RULES" };
        settings.applyCommandLineArguments(args);

        assertEquals("myboard.DSN", settings.initialInputFile);
        assertEquals("myboard.SES", settings.design_session_filename);
        assertEquals("myboard.RULES", settings.initialRulesFile);
    }

    @Test
    void testMultipleDsnFilesUsesLast() {
        String[] args = { "-de", "board1.dsn+board2.dsn" };
        settings.applyCommandLineArguments(args);

        // Should use the last DSN file
        assertEquals("board2.dsn", settings.initialInputFile);
    }

    @Test
    void testOnlySesFile() {
        String[] args = { "-de", "myboard.ses" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.initialInputFile);
        assertEquals("myboard.ses", settings.design_session_filename);
    }

    @Test
    void testOnlyRulesFile() {
        String[] args = { "-de", "myboard.rules" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.initialInputFile);
        assertNull(settings.design_session_filename);
        assertEquals("myboard.rules", settings.initialRulesFile);
    }

    @Test
    void testEmptyArgument() {
        String[] args = { "-de" };
        settings.applyCommandLineArguments(args);

        assertNull(settings.initialInputFile);
        assertNull(settings.design_session_filename);
        assertNull(settings.initialRulesFile);
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
