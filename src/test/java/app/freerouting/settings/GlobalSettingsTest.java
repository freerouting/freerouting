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
  void applyCommandLineArgumentsValidArguments() {
    GlobalSettings settings = new GlobalSettings();
    String[] args =
        new String[] {
          "-mp", "10",
          "-di", "/tmp",
          "-dl"
        };

    settings.applyCommandLineArguments(args);

    assertEquals(10, settings.getMaxPasses());
    assertEquals("/tmp", settings.guiSettings.inputDirectory);

    assertFalse(settings.logging.file.enabled);

    // -mp is still valid but deprecated in favour of --router.autorouter.max_passes
    assertEquals(
        1,
        FRLogger.getLogEntries().getWarningCount(),
        "Deprecated -mp / --router.max_passes should warn");
  }

  @Test
  void applyCommandLineArgumentsLogLevel() {
    GlobalSettings settings = new GlobalSettings();
    String[] args = new String[] {"-ll", "DEBUG"};

    settings.applyCommandLineArguments(args);

    assertEquals("DEBUG", settings.logging.console.level);
  }

  @Test
  void applyCommandLineArgumentsUnknownFlag() {
    GlobalSettings settings = new GlobalSettings();
    String[] args = new String[] {"-unknownFlag"};

    settings.applyCommandLineArguments(args);

    // Expect a warning (this will fail until implemented)
    assertEquals(
        1, FRLogger.getLogEntries().getWarningCount(), "Should have 1 warning for unknown flag");
    assertTrue(
        Arrays.stream(FRLogger.getLogEntries().get())
            .anyMatch(s -> s.contains("Unknown command line argument: -unknownFlag")));
  }

  @Test
  void applyCommandLineArgumentsUnknownValue() {
    GlobalSettings settings = new GlobalSettings();
    // "-mp 10" consumes 2 args. "extraValue" is loose.
    String[] args = new String[] {"-mp", "10", "extraValue"};

    settings.applyCommandLineArguments(args);

    // Expect a deprecation warning for -mp plus a warning for "extraValue"
    assertEquals(
        2,
        FRLogger.getLogEntries().getWarningCount(),
        "Should warn for deprecated -mp and the extra value");
    assertTrue(
        Arrays.stream(FRLogger.getLogEntries().get())
            .anyMatch(s -> s.contains("Unknown command line argument: extraValue")));
  }

  @Test
  void applyCommandLineArgumentsValidDoubleHyphen() {
    GlobalSettings settings = new GlobalSettings();
    String[] args = new String[] {"--router.max_passes=20"};

    settings.applyCommandLineArguments(args);

    assertEquals(20, settings.getMaxPasses());
    assertEquals(
        1,
        FRLogger.getLogEntries().getWarningCount(),
        "Deprecated --router.max_passes should warn");
  }

  @Test
  void applyCommandLineArgumentsNestedAutorouterMaxThreads() {
    GlobalSettings settings = new GlobalSettings();
    String[] args =
        new String[] {"--router.autorouter.max_threads=1", "--router.optimizer.max_threads=4"};

    settings.applyCommandLineArguments(args);

    assertEquals(1, settings.routerSettings.autorouter.maxThreads);
    assertEquals(4, settings.routerSettings.optimizer.maxThreads);
    assertEquals(0, FRLogger.getLogEntries().getWarningCount());
  }

  @Test
  void setUnknownProperty() {
    GlobalSettings settings = new GlobalSettings();
    // This should log a warning instead of incorrect stack trace
    settings.setValue("unknown_settings.unknown_field", "true");

    assertEquals(
        1, FRLogger.getLogEntries().getWarningCount(), "Should log a warning for unknown property");
    assertEquals(
        0, FRLogger.getLogEntries().getErrorCount(), "Should NOT log an error with stack trace");
    assertTrue(
        Arrays.stream(FRLogger.getLogEntries().get())
            .anyMatch(
                s -> s.contains("Unknown settings property: unknown_settings.unknown_field")));
  }

  @Test
  void applyCommandLineArgumentsMalformedDoubleHyphen() {
    GlobalSettings settings = new GlobalSettings();
    String[] args = new String[] {"--someflag"};

    settings.applyCommandLineArguments(args);

    assertEquals(
        1,
        FRLogger.getLogEntries().getWarningCount(),
        "Should warn on double-dash arg without equals");
    assertTrue(
        Arrays.stream(FRLogger.getLogEntries().get())
            .anyMatch(s -> s.contains("Unknown command line argument: --someflag")));
  }
}
