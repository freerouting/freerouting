package app.freerouting.startup;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.analytics.NetworkProxyConfig;
import app.freerouting.analytics.ProcessEnvironmentDetector;
import app.freerouting.analytics.model.ActorType;
import app.freerouting.analytics.model.PipelineType;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import java.util.Locale;

/** Initializes network proxies, configures telemetry, and sets execution context. */
public final class AnalyticsBootstrap {

  private AnalyticsBootstrap() {}

  /**
   * Configures and starts analytics reporting.
   *
   * @param globalSettings active global settings
   * @param args command line arguments
   * @param synchronousDelay whether to sleep 1 second for analytics startup (used in GUI mode;
   *     skipped in CLI)
   * @param screenWidth screen width in pixels (0 for headless)
   * @param screenHeight screen height in pixels (0 for headless)
   * @param screenDpi screen DPI (0 for headless)
   */
  public static void initialize(
      GlobalSettings globalSettings,
      String[] args,
      boolean synchronousDelay,
      int screenWidth,
      int screenHeight,
      int screenDpi) {
    NetworkProxyConfig.configure(globalSettings.networkSettings);

    FRAnalytics.setAccessKey(
        Constants.FREEROUTING_VERSION, globalSettings.usageAndDiagnosticData.loggerKey);

    boolean allowAnalytics =
        !globalSettings.usageAndDiagnosticData.disableAnalytics
            && (globalSettings.userProfileSettings.isTelemetryAllowed);

    if (!allowAnalytics) {
      FRLogger.debug("Analytics are disabled");
    }
    FRAnalytics.setEnabled(allowAnalytics);
    FRAnalytics.setUserId(
        globalSettings.userProfileSettings.userId, globalSettings.userProfileSettings.userEmail);

    boolean isMcpEnabled = globalSettings.mcpServerSettings.isEnabled;
    boolean isMcpStdio = Boolean.TRUE.equals(globalSettings.mcpServerSettings.isStdioMode);
    boolean isApiEnabled = globalSettings.apiServerSettings.isEnabled;
    boolean isGuiEnabled = globalSettings.guiSettings.isEnabled;
    boolean hasInitialInput = globalSettings.initialInputFile != null;

    PipelineType detectedPipeline =
        ProcessEnvironmentDetector.detectPipelineType(
            isMcpEnabled, isMcpStdio, isApiEnabled, isGuiEnabled, hasInitialInput);

    ActorType detectedActor =
        ProcessEnvironmentDetector.detectActorType(
            detectedPipeline,
            isGuiEnabled,
            screenWidth == 0 && screenHeight == 0,
            String.join(" ", args));

    String detectedHost =
        (globalSettings.runtimeEnvironment.host != null
                && !globalSettings.runtimeEnvironment.host.isBlank()
                && !"N/A".equals(globalSettings.runtimeEnvironment.host))
            ? globalSettings.runtimeEnvironment.host
            : "Freerouting";

    globalSettings.runtimeEnvironment.pipelineType = detectedPipeline.name();
    globalSettings.runtimeEnvironment.actorType = detectedActor.name();

    FRAnalytics.setExecutionContext(detectedPipeline, detectedActor, detectedHost, "");

    FRAnalytics.identify();
    if (!globalSettings.userProfileSettings.userEmail.isBlank()) {
      FRAnalytics.refreshIdentity();
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(() -> FRAnalytics.flush(1500), "freerouting-analytics-shutdown-flush"));

    if (synchronousDelay) {
      try {
        Thread.sleep(1000);
      } catch (Exception _) {
        // Ignore interruption during the analytics startup delay.
      }
    }

    FRAnalytics.setAppLocation("app.freerouting.gui", "Freerouting");
    FRAnalytics.appStarted(
        Constants.FREEROUTING_VERSION,
        Constants.FREEROUTING_BUILD_DATE + " 00:00",
        String.join(" ", args),
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        System.getProperty("os.version"),
        System.getProperty("java.version"),
        System.getProperty("java.vendor"),
        Locale.getDefault(),
        globalSettings.currentLocale,
        globalSettings.runtimeEnvironment.cpuCores,
        globalSettings.runtimeEnvironment.ram,
        globalSettings.runtimeEnvironment.cpuScore,
        globalSettings.runtimeEnvironment.host,
        screenWidth,
        screenHeight,
        screenDpi);
  }
}
