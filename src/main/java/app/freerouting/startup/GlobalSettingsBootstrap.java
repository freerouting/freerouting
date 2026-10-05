package app.freerouting.startup;

import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RuntimeEnvironment;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Loads or initialises GlobalSettings, binds environment variables, records runtime environment
 * metrics, and parses command-line arguments into GlobalSettings.
 */
public final class GlobalSettingsBootstrap {

  private GlobalSettingsBootstrap() {}

  /**
   * Loads GlobalSettings from disk or defaults, initializes hardware parameters, and applies CLI
   * arguments.
   *
   * @param args command line arguments
   * @param fileLoggingLocation active log file path
   * @return initialized GlobalSettings object
   */
  public static GlobalSettings initialize(String[] args, String fileLoggingLocation) {
    return initialize(args, fileLoggingLocation, false);
  }

  /**
   * Loads GlobalSettings from disk or defaults, initializes hardware parameters, and applies CLI
   * arguments.
   *
   * @param args command line arguments
   * @param fileLoggingLocation active log file path
   * @param deferCpuCalibration whether to defer synthetic CPU benchmark calibration
   * @return initialized GlobalSettings object
   */
  public static GlobalSettings initialize(
      String[] args, String fileLoggingLocation, boolean deferCpuCalibration) {
    GlobalSettings globalSettings = null;

    try {
      globalSettings = GlobalSettings.load();
      FRLogger.debug("Settings loaded from '" + GlobalSettings.getConfigurationFilePath() + "'.");
    } catch (NoSuchFileException _) {
      FRLogger.debug(
          "No freerouting.json found at '"
              + GlobalSettings.getConfigurationFilePath()
              + "' — will create one with default settings.");
    } catch (AccessDeniedException e) {
      FRLogger.warn(
          "Cannot read freerouting.json at '"
              + GlobalSettings.getConfigurationFilePath()
              + "': "
              + e.getReason()
              + ". The file and/or its parent directory may have incorrect permissions. "
              + "Check that the process has read access. "
              + "In Docker deployments, verify the volume mount configuration. "
              + "Freerouting will start with default settings.");
    } catch (IOException e) {
      FRLogger.warn(
          "Failed to load freerouting.json from '"
              + GlobalSettings.getConfigurationFilePath()
              + "': "
              + e.getMessage()
              + ". Freerouting will start with default settings.");
    }

    if (globalSettings != null
        && globalSettings.logging.file.location != null
        && !globalSettings.logging.file.location.isBlank()
        && !globalSettings.logging.file.location.equals(fileLoggingLocation)) {
      FRLogger.warn(
          "[startup] freerouting.json contains a stale 'logging.file.location' value: '"
              + globalSettings.logging.file.location
              + "'. "
              + "The actual log file is being written to '"
              + fileLoggingLocation
              + "' (as resolved at startup). "
              + "The stale value will be corrected in freerouting.json on next save. "
              + "If you see this in Docker, the old JSON was written by an earlier version that "
              + "stored the host path; the fix is to delete freerouting.json so it is regenerated "
              + "with the correct path.");
    }

    if (globalSettings != null) {
      globalSettings.logging.file.location = fileLoggingLocation;
    }

    if ((globalSettings == null)
        || !GlobalSettings.getReleaseSafeVersion().equals(globalSettings.version)) {
      String userId =
          globalSettings == null
              ? UUID.randomUUID().toString()
              : globalSettings.userProfileSettings.userId;

      globalSettings = new GlobalSettings();
      globalSettings.userProfileSettings.userId = userId;
      globalSettings.version = GlobalSettings.getReleaseSafeVersion();
      globalSettings.logging.file.location = fileLoggingLocation;

      try {
        GlobalSettings.saveAsJson(globalSettings);
        FRLogger.debug(
            "Default settings saved to '" + GlobalSettings.getConfigurationFilePath() + "'.");
      } catch (AccessDeniedException e) {
        FRLogger.warn(
            "Cannot write freerouting.json to '"
                + GlobalSettings.getConfigurationFilePath()
                + "': "
                + e.getReason()
                + ". The directory and/or file may have incorrect permissions. "
                + "Check that the process has write access. "
                + "In Docker deployments, verify the volume mount configuration. "
                + "Settings won't be persisted across restarts.");
      } catch (IOException e) {
        FRLogger.warn(
            "Failed to save freerouting.json to '"
                + GlobalSettings.getConfigurationFilePath()
                + "': "
                + e.getMessage()
                + ". Settings won't be persisted across restarts.");
      }
    }

    globalSettings.applyNonRouterEnvironmentVariables();

    globalSettings.runtimeEnvironment.freeroutingVersion =
        Constants.FREEROUTING_VERSION + "," + Constants.FREEROUTING_BUILD_DATE;
    globalSettings.runtimeEnvironment.appStartedAt = Instant.now();
    globalSettings.runtimeEnvironment.commandLineArguments =
        RuntimeEnvironment.sanitizeCommandLineArguments(args);
    globalSettings.runtimeEnvironment.architecture =
        System.getProperty("os.name")
            + ","
            + System.getProperty("os.arch")
            + ","
            + System.getProperty("os.version");
    globalSettings.runtimeEnvironment.java =
        System.getProperty("java.version") + "," + System.getProperty("java.vendor");
    globalSettings.runtimeEnvironment.systemLanguage =
        Locale.getDefault().getLanguage() + "," + Locale.getDefault();
    globalSettings.runtimeEnvironment.cpuCores = Runtime.getRuntime().availableProcessors();
    globalSettings.runtimeEnvironment.ram = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
    if (!deferCpuCalibration) {
      globalSettings.runtimeEnvironment.cpuScore = RuntimeEnvironment.measureCpuScore();
      FRLogger.info(
          "Hardware: "
              + globalSettings.runtimeEnvironment.cpuCores
              + " CPU cores, "
              + globalSettings.runtimeEnvironment.cpuScore
              + " CPU score, "
              + globalSettings.runtimeEnvironment.ram
              + " MB RAM");
    } else {
      FRLogger.info(
          "Hardware: "
              + globalSettings.runtimeEnvironment.cpuCores
              + " CPU cores, "
              + globalSettings.runtimeEnvironment.ram
              + " MB RAM");
    }
    FRLogger.debug("UTC Time: " + globalSettings.runtimeEnvironment.appStartedAt);

    globalSettings.applyCommandLineArguments(args);
    GlobalSettings.setCurrent(globalSettings);

    return globalSettings;
  }
}
