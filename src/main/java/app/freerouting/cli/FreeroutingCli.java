package app.freerouting.cli;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import app.freerouting.settings.sources.JsonFileSettings;
import app.freerouting.startup.AnalyticsBootstrap;
import app.freerouting.startup.GlobalSettingsBootstrap;
import app.freerouting.startup.LoggingBootstrap;
import app.freerouting.startup.UserDataPathResolver;
import app.freerouting.util.TextManager;
import app.freerouting.util.VersionChecker;

/**
 * Headless CLI entry point for Freerouting.
 *
 * <p>Contains only command-line argument parsing, settings resolution, DRC, and autorouting logic.
 * Excludes Swing GUI, Jetty/Jersey API, and cloud telemetry libraries for optimal GraalVM Native
 * Image compilation.
 */
public final class FreeroutingCli {

  private FreeroutingCli() {}

  /**
   * Main entry point for the standalone headless CLI binary.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    UserDataPathResolver.ResolvedUserDataPath userdataPath =
        UserDataPathResolver.resolveAndLock(args);

    LoggingBootstrap.LoggingConfig loggingConfig =
        LoggingBootstrap.initialize(args, userdataPath.path(), userdataPath.source());

    FRLogger.traceEntry("FreeroutingCli.main()");
    FRLogger.info(
        "Freerouting CLI v"
            + Constants.FREEROUTING_VERSION
            + " (build-date: "
            + Constants.FREEROUTING_BUILD_DATE
            + ")");

    GlobalSettings globalSettings =
        GlobalSettingsBootstrap.initialize(args, loggingConfig.fileLoggingLocation());

    // Headless CLI explicitly disables GUI and server modes
    globalSettings.guiSettings.isEnabled = false;
    globalSettings.apiServerSettings.isEnabled = false;
    globalSettings.mcpServerSettings.isEnabled = false;

    // Initialize telemetry asynchronously with zero startup delay
    AnalyticsBootstrap.initialize(globalSettings, args, false, 0, 0, 0);

    // Asynchronous version check daemon thread
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    Thread versionThread = new Thread(checker, "version-checker");
    versionThread.setDaemon(true);
    versionThread.start();

    if (globalSettings.showHelpOption) {
      TextManager ctm = new TextManager(FreeroutingCli.class, globalSettings.currentLocale);
      System.out.print(ctm.getText("command_line_help"));
      System.exit(0);
    }

    if (globalSettings.compareFile1 != null && globalSettings.compareFile2 != null) {
      boolean success =
          BenchmarkAndCompareCommands.compareBoardFiles(
              globalSettings.compareFile1, globalSettings.compareFile2);
      System.exit(success ? 0 : 1);
    }

    if (globalSettings.calculateBenchmarkScores) {
      int exitCode =
          BenchmarkAndCompareCommands.calculateBenchmarkScores(
              globalSettings.benchmarkScoresInput, globalSettings.benchmarkScoresOutput);
      System.exit(exitCode);
    }

    globalSettings.settingsMergerProtype =
        new SettingsMerger(
            new DefaultSettings(),
            new JsonFileSettings(),
            new CliSettings(args),
            new EnvironmentVariablesSource());

    int exitCode;
    if (globalSettings.drcReportFile != null) {
      boolean drcOk = DrcRunner.initializeDrc(globalSettings);
      exitCode = drcOk ? 0 : 1;
    } else if (globalSettings.initialInputFile != null) {
      CliRunner.initializeCli(globalSettings);
      exitCode = globalSettings.cliExitCode;
    } else {
      FRLogger.error("No input design file specified. Use --help for usage information.", null);
      exitCode = 1;
    }

    FRAnalytics.appClosed();
    FRLogger.traceExit("FreeroutingCli.main()");
    System.exit(exitCode);
  }
}
