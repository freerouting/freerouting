package app.freerouting.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Log4j2ConfigurationFactoryTest {

    private Log4j2ConfigurationFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Log4j2ConfigurationFactory();
        clearSystemProperties();
    }

    @AfterEach
    void tearDown() {
        clearSystemProperties();
    }

    private void clearSystemProperties() {
        System.clearProperty("freerouting.logging.console.enabled");
        System.clearProperty("freerouting.logging.console.level");
        System.clearProperty("freerouting.logging.file.enabled");
        System.clearProperty("freerouting.logging.file.level");
        System.clearProperty("freerouting.logging.file.location");
    }

    @Test
    void testDefaultConfiguration() {
        Configuration config = factory.getConfiguration(null, "TestConfig", URI.create("test"));

        assertNotNull(config);
        assertEquals("FreeroutingConfiguration", config.getName());

        // Check Console Appender (enabled by default)
        assertNotNull(config.getAppender("Console"));

        // Check File Appender (enabled by default, but needs location)
        // By default file appender should NOT be present if location is not set.
        assertNull(config.getAppender("File"));

        // Check stderr Appender
        assertNotNull(config.getAppender("stderr"));

        // Check Root Logger
        LoggerConfig rootLogger = config.getRootLogger();
        assertNotNull(rootLogger);

        // Console should be attached
        assertTrue(rootLogger.getAppenderRefs().stream().anyMatch(ref -> "Console".equals(ref.getRef())));
        // stderr should be attached
        assertTrue(rootLogger.getAppenderRefs().stream().anyMatch(ref -> "stderr".equals(ref.getRef())));
    }

    @Test
    void testCustomConfiguration() {
        // Use a simple path that definitely exists or can be created
        String tempDir = System.getProperty("java.io.tmpdir");
        String logFile = tempDir + "/freerouting_test_" + System.currentTimeMillis() + ".log";

        try {
            System.setProperty("freerouting.logging.console.enabled", "false");
            System.setProperty("freerouting.logging.file.enabled", "true");
            System.setProperty("freerouting.logging.file.level", "TRACE");
            System.setProperty("freerouting.logging.file.location", logFile);

            Configuration config = factory.getConfiguration(null, "TestConfig", URI.create("test"));

            // Console should be disabled
            assertNull(config.getAppender("Console"));

            // File Appender should exist
            assertNotNull(config.getAppender("File"));

            // Root Logger check
            LoggerConfig rootLogger = config.getRootLogger();

            // Console should NOT be attached
            assertTrue(rootLogger.getAppenderRefs().stream().noneMatch(ref -> "Console".equals(ref.getRef())));

            // File SHOULD be attached with TRACE level
            assertTrue(rootLogger.getAppenderRefs().stream()
                    .anyMatch(ref -> "File".equals(ref.getRef()) && Level.TRACE.equals(ref.getLevel())));
        } finally {
            // cleanup if possible
            try {
                new java.io.File(logFile).delete();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void testConsoleOnlyConfiguration() {
        System.setProperty("freerouting.logging.console.enabled", "true");
        System.setProperty("freerouting.logging.console.level", "WARN");
        System.setProperty("freerouting.logging.file.enabled", "false");

        Configuration config = factory.getConfiguration(null, "TestConfig", URI.create("test"));

        assertNotNull(config.getAppender("Console"));
        assertNull(config.getAppender("File"));

        LoggerConfig rootLogger = config.getRootLogger();
        // Console should be attached with WARN level
        assertTrue(rootLogger.getAppenderRefs().stream()
                .anyMatch(ref -> "Console".equals(ref.getRef()) && Level.WARN.equals(ref.getLevel())));
    }
}