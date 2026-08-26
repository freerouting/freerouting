package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

  @TempDir Path tempDir;

  @Test
  void windowsPathResolutionWithEnvVars() {
    Map<String, String> env =
        Map.of(
            "APPDATA", "C:\\Users\\TestUser\\AppData\\Roaming",
            "LOCALAPPDATA", "C:\\Users\\TestUser\\AppData\\Local");

    Path config = AppPaths.resolveConfigDirectory("Windows 11", "C:\\Users\\TestUser", env);
    Path data = AppPaths.resolveDataDirectory("Windows 11", "C:\\Users\\TestUser", env);
    Path logs = AppPaths.resolveLogDirectory("Windows 11", "C:\\Users\\TestUser", env);
    Path cache = AppPaths.resolveCacheDirectory("Windows 11", "C:\\Users\\TestUser", env);

    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Roaming\\freerouting"), config);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Roaming\\freerouting\\data"), data);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Local\\freerouting\\logs"), logs);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Local\\freerouting\\cache"), cache);
  }

  @Test
  void windowsPathResolutionFallbackWithoutEnvVars() {
    Map<String, String> env = Collections.emptyMap();

    Path config = AppPaths.resolveConfigDirectory("Windows 10", "C:\\Users\\TestUser", env);
    Path data = AppPaths.resolveDataDirectory("Windows 10", "C:\\Users\\TestUser", env);
    Path logs = AppPaths.resolveLogDirectory("Windows 10", "C:\\Users\\TestUser", env);
    Path cache = AppPaths.resolveCacheDirectory("Windows 10", "C:\\Users\\TestUser", env);

    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Roaming\\freerouting"), config);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Roaming\\freerouting\\data"), data);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Local\\freerouting\\logs"), logs);
    assertEquals(Path.of("C:\\Users\\TestUser\\AppData\\Local\\freerouting\\cache"), cache);
  }

  @Test
  void macOsPathResolution() {
    Map<String, String> env = Collections.emptyMap();

    Path config = AppPaths.resolveConfigDirectory("Mac OS X", "/Users/testuser", env);
    Path data = AppPaths.resolveDataDirectory("Mac OS X", "/Users/testuser", env);
    Path logs = AppPaths.resolveLogDirectory("Mac OS X", "/Users/testuser", env);
    Path cache = AppPaths.resolveCacheDirectory("Mac OS X", "/Users/testuser", env);

    assertEquals(Path.of("/Users/testuser/Library/Application Support/freerouting"), config);
    assertEquals(Path.of("/Users/testuser/Library/Application Support/freerouting/data"), data);
    assertEquals(Path.of("/Users/testuser/Library/Logs/freerouting"), logs);
    assertEquals(Path.of("/Users/testuser/Library/Caches/freerouting"), cache);
  }

  @Test
  void linuxPathResolutionWithXdgEnvVars() {
    Map<String, String> env =
        Map.of(
            "XDG_CONFIG_HOME", "/home/testuser/custom_config",
            "XDG_DATA_HOME", "/home/testuser/custom_data",
            "XDG_STATE_HOME", "/home/testuser/custom_state",
            "XDG_CACHE_HOME", "/home/testuser/custom_cache");

    Path config = AppPaths.resolveConfigDirectory("Linux", "/home/testuser", env);
    Path data = AppPaths.resolveDataDirectory("Linux", "/home/testuser", env);
    Path logs = AppPaths.resolveLogDirectory("Linux", "/home/testuser", env);
    Path cache = AppPaths.resolveCacheDirectory("Linux", "/home/testuser", env);

    assertEquals(Path.of("/home/testuser/custom_config/freerouting"), config);
    assertEquals(Path.of("/home/testuser/custom_data/freerouting"), data);
    assertEquals(Path.of("/home/testuser/custom_state/freerouting/logs"), logs);
    assertEquals(Path.of("/home/testuser/custom_cache/freerouting"), cache);
  }

  @Test
  void linuxPathResolutionFallbackWithoutXdgEnvVars() {
    Map<String, String> env = Collections.emptyMap();

    Path config = AppPaths.resolveConfigDirectory("Linux", "/home/testuser", env);
    Path data = AppPaths.resolveDataDirectory("Linux", "/home/testuser", env);
    Path logs = AppPaths.resolveLogDirectory("Linux", "/home/testuser", env);
    Path cache = AppPaths.resolveCacheDirectory("Linux", "/home/testuser", env);

    assertEquals(Path.of("/home/testuser/.config/freerouting"), config);
    assertEquals(Path.of("/home/testuser/.local/share/freerouting"), data);
    assertEquals(Path.of("/home/testuser/.local/state/freerouting/logs"), logs);
    assertEquals(Path.of("/home/testuser/.cache/freerouting"), cache);
  }

  @Test
  void migrateLegacyDirectoryCopiesFilesAndCleansUp() throws IOException {
    Path legacyDir = tempDir.resolve("legacy_tmp/freerouting");
    Path targetDir = tempDir.resolve("target_appdata/freerouting");

    Files.createDirectories(legacyDir);
    Files.writeString(legacyDir.resolve("freerouting.json"), "{\"version\":\"2.3.0\"}");

    Path legacyData = legacyDir.resolve("data/user1");
    Files.createDirectories(legacyData);
    Files.writeString(legacyData.resolve("job.json"), "{\"job\":1}");

    boolean migrated = AppPaths.migrateLegacyDirectory(legacyDir, targetDir);

    assertTrue(migrated, "Migration should report true when legacy files were copied");
    assertTrue(Files.exists(targetDir.resolve("freerouting.json")));
    assertEquals(
        "{\"version\":\"2.3.0\"}", Files.readString(targetDir.resolve("freerouting.json")));
    assertTrue(Files.exists(targetDir.resolve("data/user1/job.json")));
  }

  @Test
  void migrateLegacyDirectorySkipsIfTargetAlreadyExists() throws IOException {
    Path legacyDir = tempDir.resolve("legacy_tmp/freerouting");
    Path targetDir = tempDir.resolve("target_appdata/freerouting");

    Files.createDirectories(legacyDir);
    Files.writeString(legacyDir.resolve("freerouting.json"), "{\"version\":\"old\"}");

    Files.createDirectories(targetDir);
    Files.writeString(targetDir.resolve("freerouting.json"), "{\"version\":\"new\"}");

    boolean migrated = AppPaths.migrateLegacyDirectory(legacyDir, targetDir);

    assertFalse(migrated, "Migration should skip if target file already exists");
    assertEquals("{\"version\":\"new\"}", Files.readString(targetDir.resolve("freerouting.json")));
  }
}
