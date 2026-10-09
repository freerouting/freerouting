package app.freerouting.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code -l} is the language option (docs/command_line_arguments.md, the {@code -h} text, and
 * {@code GlobalSettings}). Logging bootstrap used to read it as the log file location too, so
 * {@code -l en} switched the UI to English and also wrote the log into a new {@code ./en}
 * directory.
 */
class LoggingBootstrapTest {

  // File logging is switched off in every case: the location is still resolved (and its directory
  // created), but no appender holds the file open, which on Windows would stop @TempDir cleanup.
  private static final String NO_FILE_LOG = "--logging.file.enabled=false";

  @TempDir Path userData;

  @AfterEach
  void clearSystemProperties() {
    System.clearProperty("freerouting.logging.console.enabled");
    System.clearProperty("freerouting.logging.console.level");
    System.clearProperty("freerouting.logging.file.enabled");
    System.clearProperty("freerouting.logging.file.level");
    System.clearProperty("freerouting.logging.file.location");
    System.clearProperty("freerouting.logging.file.pattern");
  }

  @Test
  void languageOptionDoesNotSetTheLogLocation() {
    String language = "zz-freerouting-test-locale";
    LoggingBootstrap.LoggingConfig config =
        LoggingBootstrap.initialize(
            new String[] {NO_FILE_LOG, "-de", "board.dsn", "-l", language},
            userData,
            "command line");

    assertEquals(
        userData.resolve("freerouting.log").toAbsolutePath().toString(),
        config.fileLoggingLocation());
    assertFalse(
        Files.exists(Path.of(System.getProperty("user.dir"), language)),
        "-l must not create a log directory named after the language");
  }

  @Test
  void loggingFileLocationStillSetsTheLogLocation() {
    Path logDir = userData.resolve("logs");
    LoggingBootstrap.LoggingConfig config =
        LoggingBootstrap.initialize(
            new String[] {NO_FILE_LOG, "--logging.file.location=" + logDir, "-l", "de"},
            userData,
            "command line");

    assertEquals(
        logDir.resolve("freerouting.log").toAbsolutePath().toString(),
        config.fileLoggingLocation());
  }
}
