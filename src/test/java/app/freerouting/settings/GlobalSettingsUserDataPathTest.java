package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.*;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the user-data-path resolution and {@code freerouting.json} file placement.
 *
 * <h2>Covered scenarios</h2>
 * <ul>
 *   <li>Default path points to a sub-directory under the system temp folder.</li>
 *   <li>{@code setUserDataPath()} updates both {@code getUserDataPath()} and
 *       {@code getConfigurationFilePath()} atomically.</li>
 *   <li>After {@code lockUserDataPath()}, further {@code setUserDataPath()} calls
 *       are silently ignored (path stays at the last value before the lock).</li>
 *   <li>{@code saveAsJson()} writes {@code freerouting.json} to the path that was
 *       registered with {@code setUserDataPath()}, creating the directory if needed.</li>
 *   <li>{@code load()} reads from the same registered path.</li>
 *   <li>When {@code setUserDataPath()} is called with a non-existent directory,
 *       {@code saveAsJson()} still creates the file (lazy directory creation).</li>
 * </ul>
 *
 * <p><strong>Regression:</strong> Prior to the fix, {@code setUserDataPath()} was guarded
 * by {@code userdataPath.toFile().exists()}: if the target directory did not exist yet
 * and {@code mkdirs()} failed silently, {@code setUserDataPath()} was never called and
 * the path was permanently locked to the default temp directory without any error message.
 */
class GlobalSettingsUserDataPathTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Reset static state so each test starts from a clean slate.
        GlobalSettings.resetForTesting();
        FRLogger.getLogEntries().clear();
    }

    @AfterEach
    void tearDown() {
        // Always reset after each test to avoid polluting other test classes that
        // run in the same JVM.
        GlobalSettings.resetForTesting();
    }

    // -------------------------------------------------------------------------
    // 1. Default state
    // -------------------------------------------------------------------------

    @Test
    void defaultUserDataPathIsUnderSystemTempDir() {
        Path defaultPath = GlobalSettings.getUserDataPath();
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir"));

        // The default user-data path must be a child of the system temp directory.
        assertTrue(defaultPath.startsWith(systemTemp),
                "Default user-data path should be under java.io.tmpdir, but was: " + defaultPath);
    }

    @Test
    void defaultConfigurationFilePathPointsToDefaultUserDataDir() {
        Path defaultUserData = GlobalSettings.getUserDataPath();
        Path configPath = GlobalSettings.getConfigurationFilePath();

        assertEquals(defaultUserData.resolve("freerouting.json"), configPath,
                "Default config file path should be <userDataPath>/freerouting.json");
    }

    // -------------------------------------------------------------------------
    // 2. setUserDataPath – happy path
    // -------------------------------------------------------------------------

    @Test
    void setUserDataPathUpdatesUserDataPathAccessor() {
        Path customDir = tempDir.resolve("custom");

        GlobalSettings.setUserDataPath(customDir);

        assertEquals(customDir, GlobalSettings.getUserDataPath(),
                "getUserDataPath() should return the custom path after setUserDataPath()");
    }

    @Test
    void setUserDataPathUpdatesConfigurationFilePath() {
        Path customDir = tempDir.resolve("custom");

        GlobalSettings.setUserDataPath(customDir);

        assertEquals(customDir.resolve("freerouting.json"),
                GlobalSettings.getConfigurationFilePath(),
                "getConfigurationFilePath() should point inside the custom user-data directory");
    }

    @Test
    void setUserDataPathDoesNotRequireDirectoryToExistFirst() {
        // Regression test: the bug was that setUserDataPath() was guarded by
        // userdataPath.toFile().exists(), so a non-existent directory caused the
        // path to be silently ignored.
        Path nonExistentDir = tempDir.resolve("does-not-exist-yet");
        assertFalse(Files.exists(nonExistentDir), "Pre-condition: directory must not exist");

        // Must NOT throw and must actually update the path.
        assertDoesNotThrow(() -> GlobalSettings.setUserDataPath(nonExistentDir));
        assertEquals(nonExistentDir, GlobalSettings.getUserDataPath(),
                "setUserDataPath() must register the path even when the directory does not exist yet");
    }

    // -------------------------------------------------------------------------
    // 3. Lock behaviour
    // -------------------------------------------------------------------------

    @Test
    void lockPreventsSubsequentPathChanges() {
        Path firstDir = tempDir.resolve("first");
        Path secondDir = tempDir.resolve("second");

        GlobalSettings.setUserDataPath(firstDir);
        GlobalSettings.lockUserDataPath();
        GlobalSettings.setUserDataPath(secondDir); // must be ignored

        assertEquals(firstDir, GlobalSettings.getUserDataPath(),
                "setUserDataPath() after lock must be silently ignored");
        assertEquals(firstDir.resolve("freerouting.json"),
                GlobalSettings.getConfigurationFilePath(),
                "configurationFilePath must still point to the pre-lock directory");
    }

    @Test
    void lockWithoutPriorSetUserDataPathKeepsDefault() {
        Path defaultPath = GlobalSettings.getUserDataPath();

        GlobalSettings.lockUserDataPath();
        GlobalSettings.setUserDataPath(tempDir.resolve("irrelevant")); // must be ignored

        assertEquals(defaultPath, GlobalSettings.getUserDataPath(),
                "Path must remain the default when locked before any setUserDataPath() call");
    }

    // -------------------------------------------------------------------------
    // 4. saveAsJson writes to the registered path (+ lazy directory creation)
    // -------------------------------------------------------------------------

    @Test
    void saveAsJsonWritesToRegisteredPath() throws IOException {
        Path customDir = tempDir.resolve("save-test");
        Files.createDirectories(customDir); // this directory exists

        GlobalSettings.setUserDataPath(customDir);

        GlobalSettings settings = new GlobalSettings();
        settings.version = "test-version";
        GlobalSettings.saveAsJson(settings);

        Path expectedFile = customDir.resolve("freerouting.json");
        assertTrue(Files.exists(expectedFile),
                "freerouting.json must be created inside the registered user-data directory");
    }

    @Test
    void saveAsJsonCreatesDirectoryLazily() {
        // Non-existent directory: saveAsJson must create it on first write.
        Path nonExistentDir = tempDir.resolve("lazy-create");
        assertFalse(Files.exists(nonExistentDir), "Pre-condition: directory must not exist");

        GlobalSettings.setUserDataPath(nonExistentDir);

        GlobalSettings settings = new GlobalSettings();
        settings.version = "test-lazy";
        assertDoesNotThrow(() -> GlobalSettings.saveAsJson(settings),
                "saveAsJson() must not throw even when the directory does not exist yet");

        assertTrue(Files.exists(nonExistentDir.resolve("freerouting.json")),
                "freerouting.json must be created after saveAsJson() even when the directory was absent");
    }

    @Test
    void saveAsJsonDoesNotWriteToDefaultPathWhenCustomPathIsSet() throws IOException {
        Path customDir = tempDir.resolve("custom-save");
        Files.createDirectories(customDir);

        GlobalSettings.setUserDataPath(customDir);

        GlobalSettings settings = new GlobalSettings();
        settings.version = "test-custom";
        GlobalSettings.saveAsJson(settings);

        // File must exist under the custom path.
        assertTrue(Files.exists(customDir.resolve("freerouting.json")),
                "freerouting.json must exist at the custom path");

        // File must NOT exist under the default path (different directory).
        Path defaultDir = Path.of(System.getProperty("java.io.tmpdir"), "freerouting");
        Path defaultFile = defaultDir.resolve("freerouting.json");
        if (Files.exists(defaultFile)) {
            // If a stale file already existed, verify it is NOT the one we just wrote
            // by checking that its content differs from our test content.
            String defaultContent = Files.readString(defaultFile);
            String customContent = Files.readString(customDir.resolve("freerouting.json"));
            assertNotEquals(customContent, defaultContent,
                    "The file at the default path must not be the same file we just wrote");
        }
        // If the default file doesn't exist at all, the test passes trivially.
    }

    // -------------------------------------------------------------------------
    // 5. load() reads from the registered path
    // -------------------------------------------------------------------------

    @Test
    void loadReadsFromRegisteredPath() throws IOException {
        Path customDir = tempDir.resolve("load-test");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Write valid settings to the custom directory.
        GlobalSettings original = new GlobalSettings();
        original.version = "test-sentinel";
        GlobalSettings.saveAsJson(original);

        // Verify the file is at the expected location before calling load().
        assertTrue(Files.exists(customDir.resolve("freerouting.json")),
                "freerouting.json must have been written to the custom directory by saveAsJson()");

        // load() must find the file at the registered path and not throw.
        // NOTE: load() normalises .version to getReleaseSafeVersion(), so we can only
        // assert non-null (file found) rather than the sentinel version string.
        GlobalSettings loaded = assertDoesNotThrow(GlobalSettings::load,
                "load() must not throw when freerouting.json exists at the registered path");
        assertNotNull(loaded, "load() must return a non-null object when the file exists at the registered path");
        // The version in memory must always be the release-safe version.
        assertEquals(GlobalSettings.getReleaseSafeVersion(), loaded.version,
                "load() must normalise the in-memory version to the release-safe version string");
    }

    @Test
    void loadThrowsWhenFileAbsentAtRegisteredPath() {
        Path emptyDir = tempDir.resolve("empty");
        // Do NOT create the directory or file – load() must throw.
        GlobalSettings.setUserDataPath(emptyDir);

        assertThrows(IOException.class, GlobalSettings::load,
                "load() must throw IOException when freerouting.json does not exist at the registered path");
    }

    // -------------------------------------------------------------------------
    // 6. Regression: setUserDataPath() called with non-existent directory +
    //    saveAsJson() chain (the exact Docker failure scenario)
    // -------------------------------------------------------------------------

    @Test
    void fullChain_nonExistentDirectory_isCreatedOnSave() throws IOException {
        // Simulate the Docker scenario where --user_data_path points to a
        // directory that does not exist yet when the process starts (e.g. a bind
        // mount that hasn't been created yet or a path that mkdirs() failed to
        // create before the path was registered).
        Path target = tempDir.resolve("docker-volume");
        assertFalse(Files.exists(target));

        // Old (buggy) code would skip setUserDataPath() when the dir was absent.
        // New code always calls setUserDataPath().
        GlobalSettings.setUserDataPath(target);
        GlobalSettings.lockUserDataPath();

        // Later at startup, saveAsJson() is called.
        GlobalSettings settings = new GlobalSettings();
        settings.version = "2.2.1";
        GlobalSettings.saveAsJson(settings);

        // The directory and file must now exist.
        assertTrue(Files.exists(target), "Directory must have been created by saveAsJson()");
        assertTrue(Files.exists(target.resolve("freerouting.json")),
                "freerouting.json must exist at the target path after saveAsJson()");

        // And load() must be able to read it back without throwing.
        // NOTE: load() normalises the in-memory version to getReleaseSafeVersion()
        // regardless of what was stored, so we only assert non-null here.
        GlobalSettings.resetForTesting();
        GlobalSettings.setUserDataPath(target);
        GlobalSettings loaded = assertDoesNotThrow(GlobalSettings::load,
                "load() must not throw when freerouting.json exists at the registered path");
        assertNotNull(loaded, "load() must return a non-null object when the file exists");
        assertEquals(GlobalSettings.getReleaseSafeVersion(), loaded.version,
                "load() must normalise the in-memory version to the release-safe version");
    }

    // -------------------------------------------------------------------------
    // 7. Release-safe version: getReleaseSafeVersion() and saveAsJson() normalization
    // -------------------------------------------------------------------------

    @Test
    void getReleaseSafeVersion_stripsSnapshotSuffix() {
        // The contract: anything after and including "-SNAPSHOT" is removed.
        // We can only verify the invariant (no -SNAPSHOT in the result) without
        // knowing the actual build-time version string.
        String releaseVersion = GlobalSettings.getReleaseSafeVersion();
        assertFalse(releaseVersion.contains("-SNAPSHOT"),
                "getReleaseSafeVersion() must not contain '-SNAPSHOT', got: " + releaseVersion);
        assertFalse(releaseVersion.isBlank(),
                "getReleaseSafeVersion() must not be blank");
    }

    @Test
    void saveAsJson_alwaysWritesReleaseSafeVersion() throws IOException {
        Path customDir = tempDir.resolve("version-save");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Set a SNAPSHOT version string on the settings object to simulate a
        // developer build scenario.
        GlobalSettings settings = new GlobalSettings();
        settings.version = "9.9.9-SNAPSHOT";  // raw build-system version

        GlobalSettings.saveAsJson(settings);

        // Read the raw JSON content and confirm it does not contain "-SNAPSHOT".
        String json = Files.readString(customDir.resolve("freerouting.json"));
        assertFalse(json.contains("-SNAPSHOT"),
                "freerouting.json must not contain '-SNAPSHOT' in the version field");
        assertTrue(json.contains(GlobalSettings.getReleaseSafeVersion()),
                "freerouting.json must contain the release-safe version string");
    }

    @Test
    void load_normalizesVersionToReleaseSafe() throws IOException {
        Path customDir = tempDir.resolve("version-load");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Write a file that has the raw SNAPSHOT version (as if saved by an older
        // code path that did not normalise).
        String rawVersion = GlobalSettings.getReleaseSafeVersion() + "-SNAPSHOT";
        String jsonContent = "{\"version\":\"" + rawVersion + "\"}";
        Files.writeString(customDir.resolve("freerouting.json"), jsonContent);

        GlobalSettings loaded = GlobalSettings.load();

        assertNotNull(loaded);
        assertEquals(GlobalSettings.getReleaseSafeVersion(), loaded.version,
                "load() must normalise a stored SNAPSHOT version to the release-safe version");
    }

    @Test
    void load_treatsNullFileVersionAsVersionChange() throws IOException {
        // Simulate a very old config file that has no version field at all.
        Path customDir = tempDir.resolve("version-null");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        String jsonWithoutVersion = "{\"profile\":{\"user_id\":\"test-user\"}}";
        Files.writeString(customDir.resolve("freerouting.json"), jsonWithoutVersion);

        // load() must not throw a NullPointerException and must produce a valid object.
        GlobalSettings loaded = assertDoesNotThrow(GlobalSettings::load,
                "load() must handle a config file without a version field gracefully");
        assertNotNull(loaded);
        assertEquals(GlobalSettings.getReleaseSafeVersion(), loaded.version,
                "load() must set the release-safe version when the file had none");
    }

    // -------------------------------------------------------------------------
    // 8. compareVersionStrings()
    // -------------------------------------------------------------------------

    @Test
    void compareVersionStrings_equalVersionsReturnZero() {
        assertEquals(0, GlobalSettings.compareVersionStrings("2.2.0", "2.2.0"));
        assertEquals(0, GlobalSettings.compareVersionStrings("1.0.0", "1.0.0"));
    }

    @Test
    void compareVersionStrings_olderVersionIsNegative() {
        assertTrue(GlobalSettings.compareVersionStrings("2.1.0", "2.2.0") < 0);
        assertTrue(GlobalSettings.compareVersionStrings("1.9.9", "2.0.0") < 0);
        assertTrue(GlobalSettings.compareVersionStrings("2.2.0", "2.2.1") < 0);
    }

    @Test
    void compareVersionStrings_newerVersionIsPositive() {
        assertTrue(GlobalSettings.compareVersionStrings("2.3.0", "2.2.0") > 0);
        assertTrue(GlobalSettings.compareVersionStrings("2.2.1", "2.2.0") > 0);
        assertTrue(GlobalSettings.compareVersionStrings("10.0.0", "9.99.99") > 0);
    }

    @Test
    void compareVersionStrings_nullHandling() {
        assertTrue(GlobalSettings.compareVersionStrings(null, "1.0.0") < 0,
                "null v1 should be treated as less-than any non-null v2");
        assertTrue(GlobalSettings.compareVersionStrings("1.0.0", null) > 0,
                "non-null v1 should be greater than null v2");
        assertEquals(0, GlobalSettings.compareVersionStrings(null, null));
    }

    // -------------------------------------------------------------------------
    // 9. load() warning messages on version change
    // -------------------------------------------------------------------------

    @Test
    void load_emitsWarnOnOlderFileVersion() throws IOException {
        Path customDir = tempDir.resolve("warn-older");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Write a file with a version far older than any real current version.
        Files.writeString(customDir.resolve("freerouting.json"), "{\"version\":\"1.0.0\"}");

        FRLogger.getLogEntries().clear();
        GlobalSettings.load();

        // Expect at least one WARN about the older version.
        assertTrue(FRLogger.getLogEntries().getWarningCount() > 0,
                "load() must emit a warning when the config file version is older than the current version");
        assertTrue(
                java.util.Arrays.stream(FRLogger.getLogEntries().get())
                        .anyMatch(s -> s.contains("1.0.0") && s.contains("older")),
                "The warning must mention the file version and describe it as 'older'");
    }

    @Test
    void load_emitsWarnOnNewerFileVersion() throws IOException {
        Path customDir = tempDir.resolve("warn-newer");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Write a file with a version far newer than any real current version.
        Files.writeString(customDir.resolve("freerouting.json"), "{\"version\":\"999.0.0\"}");

        FRLogger.getLogEntries().clear();
        GlobalSettings.load();

        // Expect at least one WARN about the newer version.
        assertTrue(FRLogger.getLogEntries().getWarningCount() > 0,
                "load() must emit a warning when the config file version is newer than the current version");
        assertTrue(
                java.util.Arrays.stream(FRLogger.getLogEntries().get())
                        .anyMatch(s -> s.contains("999.0.0") && s.contains("newer")),
                "The warning must mention the file version and describe it as 'newer'");
    }

    @Test
    void load_emitsWarnOnNullFileVersion() throws IOException {
        Path customDir = tempDir.resolve("warn-null-ver");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Config file with no version field.
        Files.writeString(customDir.resolve("freerouting.json"), "{\"profile\":{}}");

        FRLogger.getLogEntries().clear();
        GlobalSettings.load();

        assertTrue(FRLogger.getLogEntries().getWarningCount() > 0,
                "load() must emit a warning when the config file has no version field");
        assertTrue(
                java.util.Arrays.stream(FRLogger.getLogEntries().get())
                        .anyMatch(s -> s.contains("no version field")),
                "The warning must mention 'no version field'");
    }

    @Test
    void load_returnsNullAndEmitsWarnOnCorruptJson() throws IOException {
        Path customDir = tempDir.resolve("corrupt-json");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        // Write deliberately corrupt JSON.
        Files.writeString(customDir.resolve("freerouting.json"), "{ this is NOT valid json !!! }");

        FRLogger.getLogEntries().clear();
        // load() must not throw — it must return null and log a warning.
        GlobalSettings result = assertDoesNotThrow(GlobalSettings::load,
                "load() must not throw on corrupt JSON; it should return null and log a warning");
        assertNull(result, "load() must return null when the JSON cannot be parsed");
        assertTrue(FRLogger.getLogEntries().getWarningCount() > 0,
                "load() must emit a warning when the JSON is corrupt");
        assertTrue(
                java.util.Arrays.stream(FRLogger.getLogEntries().get())
                        .anyMatch(s -> s.contains("corrupt") || s.contains("cannot be parsed")),
                "The warning must describe the JSON as corrupt or unparseable");
    }

    @Test
    void load_noWarnOnMatchingVersion() throws IOException {
        // When the file version matches the current release-safe version, no
        // version-change warning should be emitted.
        Path customDir = tempDir.resolve("no-warn");
        Files.createDirectories(customDir);
        GlobalSettings.setUserDataPath(customDir);

        String currentVersion = GlobalSettings.getReleaseSafeVersion();
        Files.writeString(customDir.resolve("freerouting.json"),
                "{\"version\":\"" + currentVersion + "\"}");

        FRLogger.getLogEntries().clear();
        GlobalSettings.load();

        // Only assert on version-related warnings, not on unrelated ReflectionUtil
        // warnings that may be emitted during copyFields (e.g. "No default constructor
        // found for field: filterByNet").
        long versionWarnings = java.util.Arrays.stream(FRLogger.getLogEntries().get())
                .filter(s -> s.contains("older") || s.contains("newer") || s.contains("no version field"))
                .count();
        assertEquals(0, versionWarnings,
                "load() must not emit any version-change warning when the file version matches the current version");
    }
}
