package app.freerouting.settings;

import app.freerouting.logger.FRLogger;

/** Centralized deprecation warnings for legacy command-line flags and automatic file discovery. */
public final class CliDeprecationWarnings {

  private CliDeprecationWarnings() {}

  /**
   * Logs a deprecation warning for a command-line flag that has a long-form replacement.
   *
   * @param flag deprecated flag (e.g. {@code -di})
   * @param replacement recommended long-form setter (e.g. {@code --gui.input_directory=<dir>})
   */
  public static void warnDeprecatedFlag(String flag, String replacement) {
    FRLogger.warn(
        "The '"
            + flag
            + "' command-line flag is deprecated and will be removed in a future release; use '"
            + replacement
            + "' instead.");
  }

  /**
   * Logs a deprecation warning for a command-line flag without a direct long-form setter.
   *
   * @param flag deprecated flag (e.g. {@code --compare-boards=})
   */
  public static void warnDeprecatedFlagWithoutReplacement(String flag) {
    FRLogger.warn(
        "The '"
            + flag
            + "' command-line flag is deprecated and will be removed in a future release.");
  }

  /**
   * Logs a deprecation warning when an adjacent {@code <boardname>.rules} file is automatically
   * discovered and loaded.
   *
   * @param rulesPath path to the auto-discovered rules file
   */
  public static void warnAutoDiscoveredRulesFile(String rulesPath) {
    FRLogger.warn(
        "Automatically discovered and loaded adjacent rules file '"
            + rulesPath
            + "'. Automatic '.rules' loading is deprecated and will be removed in a future"
            + " release; pass the rules file explicitly via '-de <design.dsn>+<rules.rules>'"
            + " instead.");
  }
}
