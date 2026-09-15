package app.freerouting.settings;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility for resolving platform-standard application directories (Config, Data, Logs, and Cache)
 * according to OS conventions (Windows AppData, macOS Library, and Linux XDG Base Directory
 * specification).
 */
public final class AppPaths {

  private static final String APP_DIR_NAME = "freerouting";

  private AppPaths() {
    // Utility class
  }

  /**
   * Returns the default directory for persistent user configuration and settings (where {@code
   * freerouting.json} resides).
   *
   * <ul>
   *   <li>Windows: {@code %APPDATA%\freerouting} (Roaming)
   *   <li>macOS: {@code ~/Library/Application Support/freerouting}
   *   <li>Linux / Other: {@code $XDG_CONFIG_HOME/freerouting} (default {@code
   *       ~/.config/freerouting})
   * </ul>
   */
  public static Path getDefaultUserDataPath() {
    return resolveConfigDirectory(
        System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
  }

  /**
   * Returns the default directory for persistent user data (e.g. saved routing jobs).
   *
   * <ul>
   *   <li>Windows: {@code %APPDATA%\freerouting\data}
   *   <li>macOS: {@code ~/Library/Application Support/freerouting/data}
   *   <li>Linux / Other: {@code $XDG_DATA_HOME/freerouting} (default {@code
   *       ~/.local/share/freerouting})
   * </ul>
   */
  public static Path getDefaultDataDirectory() {
    return resolveDataDirectory(
        System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
  }

  /**
   * Returns the default directory for log files (where {@code freerouting.log} resides).
   *
   * <ul>
   *   <li>Windows: {@code %LOCALAPPDATA%\freerouting\logs}
   *   <li>macOS: {@code ~/Library/Logs/freerouting}
   *   <li>Linux / Other: {@code $XDG_STATE_HOME/freerouting/logs} (default {@code
   *       ~/.local/state/freerouting/logs})
   * </ul>
   */
  public static Path getDefaultLogDirectory() {
    return resolveLogDirectory(
        System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
  }

  /**
   * Returns the default directory for cached data (e.g. downloaded runtimes or indices).
   *
   * <ul>
   *   <li>Windows: {@code %LOCALAPPDATA%\freerouting\cache}
   *   <li>macOS: {@code ~/Library/Caches/freerouting}
   *   <li>Linux / Other: {@code $XDG_CACHE_HOME/freerouting} (default {@code ~/.cache/freerouting})
   * </ul>
   */
  public static Path getDefaultCacheDirectory() {
    return resolveCacheDirectory(
        System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
  }

  /**
   * Returns the legacy temporary directory used by older Freerouting versions ({@code
   * <java.io.tmpdir>/freerouting}).
   */
  public static Path getLegacyTempDirectory() {
    return Path.of(System.getProperty("java.io.tmpdir"), APP_DIR_NAME);
  }

  /** Resolves the config directory for the given OS name, user home, and environment. */
  public static Path resolveConfigDirectory(
      String osName, String userHome, Map<String, String> env) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String appData = env.get("APPDATA");
      if (appData != null && !appData.isBlank()) {
        return Path.of(appData, APP_DIR_NAME);
      }
      return Path.of(userHome, "AppData", "Roaming", APP_DIR_NAME);
    } else if (os.contains("mac") || os.contains("darwin")) {
      return Path.of(userHome, "Library", "Application Support", APP_DIR_NAME);
    } else {
      String xdgConfig = env.get("XDG_CONFIG_HOME");
      if (xdgConfig != null && !xdgConfig.isBlank()) {
        return Path.of(xdgConfig, APP_DIR_NAME);
      }
      return Path.of(userHome, ".config", APP_DIR_NAME);
    }
  }

  /** Resolves the data directory for the given OS name, user home, and environment. */
  public static Path resolveDataDirectory(String osName, String userHome, Map<String, String> env) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String appData = env.get("APPDATA");
      if (appData != null && !appData.isBlank()) {
        return Path.of(appData, APP_DIR_NAME, "data");
      }
      return Path.of(userHome, "AppData", "Roaming", APP_DIR_NAME, "data");
    } else if (os.contains("mac") || os.contains("darwin")) {
      return Path.of(userHome, "Library", "Application Support", APP_DIR_NAME, "data");
    } else {
      String xdgData = env.get("XDG_DATA_HOME");
      if (xdgData != null && !xdgData.isBlank()) {
        return Path.of(xdgData, APP_DIR_NAME);
      }
      return Path.of(userHome, ".local", "share", APP_DIR_NAME);
    }
  }

  /** Resolves the log directory for the given OS name, user home, and environment. */
  public static Path resolveLogDirectory(String osName, String userHome, Map<String, String> env) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String localAppData = env.get("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        return Path.of(localAppData, APP_DIR_NAME, "logs");
      }
      return Path.of(userHome, "AppData", "Local", APP_DIR_NAME, "logs");
    } else if (os.contains("mac") || os.contains("darwin")) {
      return Path.of(userHome, "Library", "Logs", APP_DIR_NAME);
    } else {
      String xdgState = env.get("XDG_STATE_HOME");
      if (xdgState != null && !xdgState.isBlank()) {
        return Path.of(xdgState, APP_DIR_NAME, "logs");
      }
      return Path.of(userHome, ".local", "state", APP_DIR_NAME, "logs");
    }
  }

  /** Resolves the cache directory for the given OS name, user home, and environment. */
  public static Path resolveCacheDirectory(
      String osName, String userHome, Map<String, String> env) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String localAppData = env.get("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        return Path.of(localAppData, APP_DIR_NAME, "cache");
      }
      return Path.of(userHome, "AppData", "Local", APP_DIR_NAME, "cache");
    } else if (os.contains("mac") || os.contains("darwin")) {
      return Path.of(userHome, "Library", "Caches", APP_DIR_NAME);
    } else {
      String xdgCache = env.get("XDG_CACHE_HOME");
      if (xdgCache != null && !xdgCache.isBlank()) {
        return Path.of(xdgCache, APP_DIR_NAME);
      }
      return Path.of(userHome, ".cache", APP_DIR_NAME);
    }
  }

  /**
   * Migrates legacy configuration and data files from {@code legacyDir} to {@code targetDir} if the
   * legacy configuration exists and target configuration does not yet exist.
   *
   * <p>After successful migration, attempts a safe, best-effort cleanup of the legacy directory.
   *
   * @param legacyDir legacy directory (typically {@code <tmpdir>/freerouting})
   * @param targetDir target user data directory (typically OS standard user-data path)
   * @return {@code true} if migration was performed, {@code false} otherwise
   */
  public static boolean migrateLegacyDirectory(Path legacyDir, Path targetDir) {
    if (legacyDir == null || targetDir == null || Objects.equals(legacyDir, targetDir)) {
      return false;
    }

    Path legacyConfig = legacyDir.resolve("freerouting.json");
    Path targetConfig = targetDir.resolve("freerouting.json");

    if (!Files.exists(legacyConfig) || Files.exists(targetConfig)) {
      return false;
    }

    try {
      Files.createDirectories(targetDir);
      Files.copy(legacyConfig, targetConfig, StandardCopyOption.REPLACE_EXISTING);
      FRLogger.info(
          "Migrated configuration from legacy temporary location '"
              + legacyConfig
              + "' to '"
              + targetConfig
              + "'.");

      // Migrate legacy data folder if present
      Path legacyData = legacyDir.resolve("data");
      Path targetData = targetDir.resolve("data");
      if (Files.exists(legacyData) && !Files.exists(targetData)) {
        try {
          copyDirectory(legacyData, targetData);
        } catch (Exception e) {
          FRLogger.warn(
              "Could not copy legacy data directory to '" + targetData + "': " + e.getMessage());
        }
      }

      // Best-effort cleanup of legacy files
      safeCleanupLegacyDirectory(legacyDir);
      return true;
    } catch (Exception e) {
      FRLogger.warn(
          "Failed to migrate legacy settings from '" + legacyConfig + "': " + e.getMessage());
      return false;
    }
  }

  private static void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> stream = Files.walk(source)) {
      stream.forEach(
          src -> {
            try {
              Path dest = target.resolve(source.relativize(src));
              if (Files.isDirectory(src)) {
                if (!Files.exists(dest)) {
                  Files.createDirectories(dest);
                }
              } else {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (IOException ex) {
              throw new RuntimeException(ex);
            }
          });
    } catch (RuntimeException re) {
      if (re.getCause() instanceof IOException ioe) {
        throw ioe;
      }
      throw re;
    }
  }

  /**
   * Safely attempts to clean up files in the legacy directory without throwing on locked or
   * permission-restricted files.
   */
  public static void safeCleanupLegacyDirectory(Path legacyDir) {
    if (!Files.exists(legacyDir)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(legacyDir)) {
      stream
          .sorted((a, b) -> b.compareTo(a)) // Delete children before parents
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (DirectoryNotEmptyException | AccessDeniedException ignored) {
                  // Skip locked or non-empty dirs gracefully
                } catch (IOException e) {
                  FRLogger.debug("Could not delete legacy file '" + path + "': " + e.getMessage());
                }
              });
    } catch (Exception e) {
      FRLogger.debug("Could not clean legacy directory '" + legacyDir + "': " + e.getMessage());
    }
  }
}
