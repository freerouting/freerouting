package app.freerouting.startup;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Parses logging parameters from environment variables and command-line arguments, configures
 * Log4j2 system properties, and reconfigures Log4j2.
 */
public final class LoggingBootstrap {

  /** Configuration snapshot produced by logging initialization. */
  public record LoggingConfig(
      boolean fileLoggingEnabled,
      boolean consoleLoggingEnabled,
      String fileLoggingLevel,
      String consoleLoggingLevel,
      String fileLoggingLocation,
      String fileLoggingPattern) {}

  private LoggingBootstrap() {}

  /**
   * Initializes Log4j2 based on environment variables, CLI arguments, and resolved user data path.
   *
   * @param args command line arguments
   * @param userdataPath resolved user data directory
   * @param userdataPathSource source of the user data directory
   * @return the active logging configuration
   */
  public static LoggingConfig initialize(
      String[] args, Path userdataPath, String userdataPathSource) {
    boolean fileLoggingEnabled = true;
    boolean consoleLoggingEnabled = true;
    String fileLoggingLevel = "DEBUG";

    if (System.getenv("FREEROUTING__LOGGING__FILE__ENABLED") != null) {
      fileLoggingEnabled =
          Boolean.parseBoolean(System.getenv("FREEROUTING__LOGGING__FILE__ENABLED"));
    }
    String consoleLoggingLevel = "INFO";
    if (System.getenv("FREEROUTING__LOGGING__CONSOLE__ENABLED") != null) {
      consoleLoggingEnabled =
          Boolean.parseBoolean(System.getenv("FREEROUTING__LOGGING__CONSOLE__ENABLED"));
    }
    if (System.getenv("FREEROUTING__LOGGING__FILE__LEVEL") != null) {
      fileLoggingLevel = System.getenv("FREEROUTING__LOGGING__FILE__LEVEL");
    }
    if (System.getenv("FREEROUTING__LOGGING__CONSOLE__LEVEL") != null) {
      consoleLoggingLevel = System.getenv("FREEROUTING__LOGGING__CONSOLE__LEVEL");
    }
    String fileLoggingLocation = null;
    if (System.getenv("FREEROUTING__LOGGING__FILE__LOCATION") != null) {
      fileLoggingLocation = System.getenv("FREEROUTING__LOGGING__FILE__LOCATION");
    }
    String fileLoggingPattern = null;
    if (System.getenv("FREEROUTING__LOGGING__FILE__PATTERN") != null) {
      fileLoggingPattern = System.getenv("FREEROUTING__LOGGING__FILE__PATTERN");
    }

    if (args != null && args.length > 0) {
      for (String arg : args) {
        if (arg.startsWith("--logging.file.enabled=")) {
          fileLoggingEnabled =
              Boolean.parseBoolean(arg.substring("--logging.file.enabled=".length()));
        } else if (arg.startsWith("--logging.console.enabled=")) {
          consoleLoggingEnabled =
              Boolean.parseBoolean(arg.substring("--logging.console.enabled=".length()));
        } else if (arg.startsWith("--logging.file.level=")) {
          fileLoggingLevel = arg.substring("--logging.file.level=".length());
        } else if (arg.startsWith("--logging.console.level=")) {
          consoleLoggingLevel = arg.substring("--logging.console.level=".length());
        } else if (arg.startsWith("--logging.file.location=")) {
          fileLoggingLocation = arg.substring("--logging.file.location=".length());
        } else if (arg.startsWith("-l=")) {
          fileLoggingLocation = arg.substring("-l=".length());
        } else if ("-l".equals(arg)) {
          int index = Arrays.asList(args).indexOf("-l");
          if (index >= 0 && index < args.length - 1) {
            fileLoggingLocation = args[index + 1];
          }
        } else if (arg.startsWith("--logging.file.pattern=")) {
          fileLoggingPattern = arg.substring("--logging.file.pattern=".length());
        } else if (arg.startsWith("--debug.enable_detailed_logging=")) {
          boolean detailed =
              Boolean.parseBoolean(arg.substring("--debug.enable_detailed_logging=".length()));
          if (detailed) {
            fileLoggingLevel = "TRACE";
            FRLogger.granularTraceEnabled = true;
          }
        } else if ("-dl".equals(arg)) {
          fileLoggingEnabled = false;
        } else if ("-ll".equals(arg)) {
          int index = Arrays.asList(args).indexOf("-ll");
          if (index >= 0 && index < args.length - 1) {
            consoleLoggingLevel = args[index + 1];
          }
        }
      }
    }

    Path defaultLogDir =
        (userdataPathSource != null && userdataPathSource.startsWith("default"))
            ? AppPaths.getDefaultLogDirectory()
            : userdataPath;
    if (fileLoggingLocation == null || fileLoggingLocation.isBlank()) {
      fileLoggingLocation = resolveLogPath(null, defaultLogDir).toString();
    } else {
      fileLoggingLocation = resolveLogPath(fileLoggingLocation, defaultLogDir).toString();
    }

    System.setProperty("log4j2.disableJndi", "true");
    System.setProperty(
        "log4j2.configurationFactory", "app.freerouting.logger.Log4j2ConfigurationFactory");
    System.setProperty(
        "freerouting.logging.console.enabled", String.valueOf(consoleLoggingEnabled));
    System.setProperty("freerouting.logging.console.level", consoleLoggingLevel);
    System.setProperty("freerouting.logging.file.enabled", String.valueOf(fileLoggingEnabled));
    System.setProperty("freerouting.logging.file.level", fileLoggingLevel);
    System.setProperty("freerouting.logging.file.location", fileLoggingLocation);

    if (fileLoggingPattern != null) {
      System.setProperty("freerouting.logging.file.pattern", fileLoggingPattern);
    }

    ((LoggerContext) LogManager.getContext(false)).reconfigure();

    return new LoggingConfig(
        fileLoggingEnabled,
        consoleLoggingEnabled,
        fileLoggingLevel,
        consoleLoggingLevel,
        fileLoggingLocation,
        fileLoggingPattern);
  }

  /**
   * Resolves a relative or absolute log path against the default directory.
   *
   * @param input optional specified log location
   * @param defaultDir fallback directory
   * @return normalized absolute log path
   */
  public static Path resolveLogPath(String input, Path defaultDir) {
    if (input == null || input.isBlank()) {
      return defaultDir.resolve("freerouting.log").normalize().toAbsolutePath();
    }

    // In Windows the leading "." character means current directory
    if (input.startsWith(".")) {
      var currentDir = Path.of(System.getProperty("user.dir"));
      input = currentDir + input.substring(1);
    }

    Path path = Path.of(input).normalize().toAbsolutePath();
    boolean isFile = path.getFileName().toString().toLowerCase().endsWith(".log");
    String filename = isFile ? path.getFileName().toString() : "freerouting.log";
    Path folderPath = isFile ? path.getParent() : path;

    // Check if the directory exists, and create it if needed
    if (folderPath != null && !folderPath.toFile().exists()) {
      try {
        Files.createDirectories(folderPath);
      } catch (IOException e) {
        // Failed to create directory, fallback to default
        return defaultDir.resolve(filename).normalize().toAbsolutePath();
      }
    }

    return folderPath != null
        ? folderPath.resolve(filename).normalize().toAbsolutePath()
        : defaultDir.resolve(filename).normalize().toAbsolutePath();
  }
}
