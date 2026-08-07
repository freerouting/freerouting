package app.freerouting.management.analytics;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.dto.Properties;
import app.freerouting.management.analytics.dto.Traits;
import app.freerouting.util.gson.GsonProvider;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A class to manage analytics for the application. */
public final class FRAnalytics {

  private static final HashMap<String, String> appLocationTable;

  static {
    appLocationTable = new HashMap<String, String>();
    appLocationTable.put("app.freerouting.gui.BoardFrame", "app.freerouting.gui/Board");
    appLocationTable.put(
        "app.freerouting.gui.WindowVisibility", "app.freerouting.gui/Appearance/Visibility");
    appLocationTable.put(
        "app.freerouting.gui.ColorManager", "app.freerouting.gui/Appearance/Colors");
    appLocationTable.put(
        "app.freerouting.gui.WindowDisplayMisc", "app.freerouting.gui/Appearance/Misc");
    appLocationTable.put(
        "app.freerouting.gui.WindowSelectParameter", "app.freerouting.gui/Settings/Selection");
    appLocationTable.put(
        "app.freerouting.gui.WindowRouteParameter", "app.freerouting.gui/Settings/Routing");
    appLocationTable.put(
        "app.freerouting.gui.WindowAutorouteParameter", "app.freerouting.gui/Settings/Auto-router");
    appLocationTable.put(
        "app.freerouting.gui.WindowAutorouteDetailParameter",
        "app.freerouting.gui/Settings/Auto-router/Details");
    appLocationTable.put(
        "app.freerouting.gui.WindowMoveParameter", "app.freerouting.gui/Settings/Controls");
    appLocationTable.put(
        "app.freerouting.gui.WindowClearanceMatrix", "app.freerouting.gui/Rules/ClearanceMatrix");
    appLocationTable.put("app.freerouting.gui.WindowVia", "app.freerouting.gui/Rules/Vias");
    appLocationTable.put("app.freerouting.gui.WindowNets", "app.freerouting.gui/Rules/Nets");
    appLocationTable.put(
        "app.freerouting.gui.WindowNetClasses", "app.freerouting.gui/Rules/NetClasses");
    appLocationTable.put(
        "app.freerouting.gui.WindowPackages", "app.freerouting.gui/Information/LibraryPackages");
    appLocationTable.put(
        "app.freerouting.gui.WindowPadstacks", "app.freerouting.gui/Information/LibraryPadstacks");
    appLocationTable.put(
        "app.freerouting.gui.WindowComponents", "app.freerouting.gui/Information/PlacedComponents");
    appLocationTable.put(
        "app.freerouting.gui.WindowIncompletes", "app.freerouting.gui/Information/Incompletes");
    appLocationTable.put(
        "app.freerouting.gui.WindowLengthViolations",
        "app.freerouting.gui/Information/LengthViolations");
    appLocationTable.put(
        "app.freerouting.gui.WindowClearanceViolations",
        "app.freerouting.gui/Information/ClearanceViolations");
    appLocationTable.put(
        "app.freerouting.gui.WindowUnconnectedRoute",
        "app.freerouting.gui/Information/UnconnectedRoutes");
    appLocationTable.put(
        "app.freerouting.gui.WindowRouteStubs", "app.freerouting.gui/Information/RouteStubs");

    appLocationTable.put("app.freerouting.gui.WindowAbout", "app.freerouting.gui/Help/About");
    appLocationTable.put("select_button", "app.freerouting.gui/Board/Toolbar/Select");
    appLocationTable.put("route_button", "app.freerouting.gui/Board/Toolbar/Route");
    appLocationTable.put("drag_button", "app.freerouting.gui/Board/Toolbar/Drag");
    appLocationTable.put("autoroute_button", "app.freerouting.gui/Board/Toolbar/Autorouter");
    appLocationTable.put("undo_button", "app.freerouting.gui/Board/Toolbar/Undo");
    appLocationTable.put("redo_button", "app.freerouting.gui/Board/Toolbar/Redo");
    appLocationTable.put("incompletes_button", "app.freerouting.gui/Board/Toolbar/Incompletes");
    appLocationTable.put("violation_button", "app.freerouting.gui/Board/Toolbar/Violations");
    appLocationTable.put("display_all_button", "app.freerouting.gui/Board/Toolbar/ZoomAll");
    appLocationTable.put("display_region_button", "app.freerouting.gui/Board/Toolbar/ZoomRegion");
    appLocationTable.put("file_save_menuitem", "app.freerouting.gui/Board/Menu/File/Save");
    appLocationTable.put(
        "file_save_and_exit_menuitem", "app.freerouting.gui/Board/Menu/File/SaveAndExit");
    appLocationTable.put(
        "file_cancel_and_exit_menuitem", "app.freerouting.gui/Board/Menu/File/CancelAndExit");
    appLocationTable.put("file_save_as_menuitem", "app.freerouting.gui/Board/Menu/File/SaveAs");
    appLocationTable.put(
        "file_write_logfile_menuitem", "app.freerouting.gui/Board/Menu/File/MacroRecording");
    appLocationTable.put(
        "file_replay_logfile_menuitem", "app.freerouting.gui/Board/Menu/File/MacroPlayback");
    appLocationTable.put(
        "file_save_settings_menuitem", "app.freerouting.gui/Board/Menu/File/SaveGUISettings");
    appLocationTable.put(
        "file_write_session_file_menuitem", "app.freerouting.gui/Board/Menu/File/ExportAsSpecctra");
    appLocationTable.put(
        "file_write_eagle_session_script_menuitem",
        "app.freerouting.gui/Board/Menu/File/ExportAsEagleScript");
    appLocationTable.put(
        "display_visibility_menuitem", "app.freerouting.gui/Board/Menu/Appearance/Visibility");
    appLocationTable.put(
        "display_colors_menuitem", "app.freerouting.gui/Board/Menu/Appearance/Colors");
    appLocationTable.put(
        "display_miscellaneous_menuitem",
        "app.freerouting.gui/Board/Menu/Appearance/Miscellaneous");
    appLocationTable.put(
        "settings_selection_menuitem", "app.freerouting.gui/Board/Menu/Settings/Selection");
    appLocationTable.put(
        "settings_routing_menuitem", "app.freerouting.gui/Board/Menu/Settings/Routing");
    appLocationTable.put(
        "settings_autorouter_menuitem", "app.freerouting.gui/Board/Menu/Settings/AutoRouter");
    appLocationTable.put(
        "settings_controls_menuitem", "app.freerouting.gui/Board/Menu/Settings/Controls");
    appLocationTable.put(
        "rules_clearance_menuitem", "app.freerouting.gui/Board/Menu/Rules/ClearanceMatrix");
    appLocationTable.put("rules_vias_menuitem", "app.freerouting.gui/Board/Menu/Rules/Vias");
    appLocationTable.put("rules_nets_menuitem", "app.freerouting.gui/Board/Menu/Rules/Nets");
    appLocationTable.put(
        "rules_net_class_menuitem", "app.freerouting.gui/Board/Menu/Rules/NetClasses");
    appLocationTable.put("info_packages_menuitem", "app.freerouting.gui/Board/Menu/Info/Packages");
    appLocationTable.put(
        "info_padstacks_menuitem", "app.freerouting.gui/Board/Menu/Info/Padstacks");
    appLocationTable.put(
        "info_components_menuitem", "app.freerouting.gui/Board/Menu/Info/Components");
    appLocationTable.put(
        "info_incompletes_menuitem", "app.freerouting.gui/Board/Menu/Info/IncompleteRoutes");
    appLocationTable.put(
        "info_length_violations_menuitem", "app.freerouting.gui/Board/Menu/Info/LengthViolations");
    appLocationTable.put(
        "info_clearance_violations_menuitem",
        "app.freerouting.gui/Board/Menu/Info/ClearanceViolations");
    appLocationTable.put(
        "info_unconnected_routes_menuitem",
        "app.freerouting.gui/Board/Menu/Info/UnconnectedRoutes");
    appLocationTable.put(
        "info_route_stubs_menuitem", "app.freerouting.gui/Board/Menu/Info/RoutedStubs");

    appLocationTable.put(
        "other_delete_all_tracks_menuitem",
        "app.freerouting.gui/Board/Menu/Other/DeleteAllTracksAndVias");
    appLocationTable.put("help_about_menuitem", "app.freerouting.gui/Board/Menu/Help/About");
  }

  private static AnalyticsClient analytics;
  private static String permanent_user_id;
  private static String permanent_user_email;
  private static String appPreviousLocation = "";
  private static String appCurrentLocation = "";
  private static String appWindowTitle = "";
  private static long appStartedAt;
  private static int sessionCount;
  private static long totalAutorouterRuntime;
  private static long totalRouteOptimizerRuntime;
  private static long autorouterStartedAt;
  private static long routeOptimizerStartedAt;
  private static String sessionId;

  private FRAnalytics() {}

  public static void setAccessKey(String libraryVersion, String key) {
    // analytics = new SegmentClient(libraryVersion, key);
    // analytics = new BigQueryClient(libraryVersion, key);
    analytics = new FreeroutingAnalyticsClient(libraryVersion, key);
  }

  public static void setUserId(String userId, String userEmail) {
    permanent_user_id = userId;
    permanent_user_email = userEmail;
  }

  private static void identifyUser(String userId, Map<String, String> traits) {
    if (analytics == null) {
      return;
    }

    try {
      Traits t = new Traits();
      t.putAll(traits);

      analytics.identify(userId, null, t);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.identifyUser: " + e.getMessage(), e);
    }
  }

  private static void identifyAnonymous(String anonymousId, Map<String, String> traits) {
    if (analytics == null) {
      return;
    }

    try {
      Traits t = new Traits();
      t.putAll(traits);

      analytics.identify(null, anonymousId, t);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.identifyAnonymous: " + e.getMessage(), e);
    }
  }

  private static void trackAnonymousAction(
      String anonymousId, String action, Map<String, String> properties) {
    if (analytics == null) {
      return;
    }

    if (!isEventTrackingEnabled(action)) {
      return;
    }

    try {
      Properties p = new Properties();
      p.put("current_time_utc", Instant.now().toString());
      p.put("user_id", permanent_user_id);
      p.put("user_email", permanent_user_email);
      p.put("app_current_location", appCurrentLocation);
      p.put("app_previous_location", appPreviousLocation);
      p.put("app_window_title", appWindowTitle);
      if (sessionId != null) {
        p.put("session_id", sessionId);
      }
      if (properties != null) {
        p.putAll(properties);
      }

      analytics.track(null, anonymousId, action, p);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.trackAnonymousAction: " + e.getMessage(), e);
    }
  }

  private static boolean isEventTrackingEnabled(String action) {
    return switch (action) {
      case "Window Changed" -> globalSettings.usageAndDiagnosticData.trackWindowChanged;
      case "Button Clicked" -> globalSettings.usageAndDiagnosticData.trackButtonClicked;
      default -> true;
    };
  }

  public static void identify() {
    identifyAnonymous(permanent_user_id, buildIdentifyTraits());
  }

  /**
   * Re-sends the current profile traits after the user updates email or consent settings. Keeps
   * {@code first_seen} stable so returning users are not misclassified as new.
   */
  public static void refreshIdentity() {
    identifyAnonymous(permanent_user_id, buildIdentifyTraits());
  }

  private static Map<String, String> buildIdentifyTraits() {
    Map<String, String> traits = new HashMap<>();
    traits.put("anonymous", "true");
    traits.put("user_id", permanent_user_id);
    traits.put("user_email", permanent_user_email);
    String firstSeen = globalSettings.statistics.startTime;
    if (firstSeen == null || firstSeen.isBlank()) {
      firstSeen = Instant.now().toString();
    }
    traits.put("first_seen", firstSeen);
    traits.put("client_version", globalSettings.version);
    traits.put("os_name", System.getProperty("os.name"));
    traits.put("os_version", System.getProperty("os.version"));
    traits.put("system_language", Locale.getDefault().toString());
    traits.put("gui_language", globalSettings.currentLocale.toString());
    traits.put(
        "allow_telemetry", Boolean.toString(globalSettings.userProfileSettings.isTelemetryAllowed));
    traits.put(
        "allow_contact", Boolean.toString(globalSettings.userProfileSettings.isContactAllowed));
    return traits;
  }

  /**
   * Emitted when the user saves profile settings. Uses explicit fields instead of serialising the
   * full settings object.
   */
  public static void profileUpdated() {
    Map<String, String> properties = new HashMap<>();
    properties.put("user_email", permanent_user_email);
    properties.put(
        "allow_contact", Boolean.toString(globalSettings.userProfileSettings.isContactAllowed));
    properties.put(
        "allow_telemetry", Boolean.toString(globalSettings.userProfileSettings.isTelemetryAllowed));
    trackAnonymousAction(permanent_user_id, "Profile Updated", properties);
  }

  public static void setAppLocation(String windowClassName, String windowTitle) {
    windowClassName = translateClassNameToUrl(windowClassName);

    if (Objects.equals(appPreviousLocation, windowClassName)) {
      return;
    }

    appPreviousLocation = appCurrentLocation;
    appCurrentLocation = windowClassName;
    appWindowTitle = windowTitle;

    Properties p = new Properties();
    trackAnonymousAction(permanent_user_id, "Window Changed", p);
  }

  public static void buttonClicked(String buttonClassName, String buttonText) {
    buttonClassName = translateClassNameToUrl(buttonClassName);

    Properties p = new Properties();
    p.put("button_name", buttonClassName);
    p.put("button_text", buttonText);
    trackAnonymousAction(permanent_user_id, "Button Clicked", p);
  }

  private static String translateClassNameToUrl(String appLocation) {
    if (appLocationTable.containsKey(appLocation)) {
      return appLocationTable.get(appLocation);
    } else {
      return appLocation.replace("app.freerouting.gui.", "app.freerouting.gui/");
    }
  }

  public static void setEnabled(boolean enabled) {
    if (analytics == null) {
      return;
    }

    analytics.setEnabled(enabled);
  }

  public static void appStarted(
      String freeroutingVersion,
      String freeroutingBuildDate,
      String commandLineArguments,
      String osName,
      String osArchitecture,
      String osVersion,
      String javaVersion,
      String javaVendor,
      Locale systemLanguage,
      Locale guiLanguage,
      int cpuCoreCount,
      long ramAmount,
      String host,
      int width,
      int height,
      int dpi) {
    sessionId = UUID.randomUUID().toString();
    appStartedAt = Instant.now().getEpochSecond();

    Map<String, String> properties = new HashMap<>();
    properties.put("session_id", sessionId);
    properties.put("build_version", freeroutingVersion);
    properties.put("build_date", freeroutingBuildDate);
    properties.put("command_line_arguments", commandLineArguments);
    properties.put("os_name", osName);
    properties.put("os_architecture", osArchitecture);
    properties.put("os_version", osVersion);
    properties.put("java_version", javaVersion);
    properties.put("java_vendor", javaVendor);
    properties.put("system_language", systemLanguage.toString());
    properties.put("gui_language", guiLanguage.toString());
    properties.put("cpu_core_count", Integer.toString(cpuCoreCount));
    properties.put("ram_amount", Long.toString(ramAmount));
    properties.put("host", host);
    properties.put("screen_width", Integer.toString(width));
    properties.put("screen_height", Integer.toString(height));
    properties.put("screen_dpi", Integer.toString(dpi));
    trackAnonymousAction(permanent_user_id, "Application Started", properties);
  }

  public static void appClosed() {
    long appClosedAt = Instant.now().getEpochSecond();

    Map<String, String> properties = new HashMap<>();
    if (sessionId != null) {
      properties.put("session_id", sessionId);
    }
    properties.put("session_count", String.valueOf(sessionCount));
    properties.put("total_autorouter_runtime", String.valueOf(totalAutorouterRuntime));
    properties.put("total_route_optimizer_runtime", String.valueOf(totalRouteOptimizerRuntime));
    properties.put("application_runtime", String.valueOf(appClosedAt - appStartedAt));
    properties.put("statistics_start_time", globalSettings.statistics.startTime);
    properties.put("statistics_end_time", globalSettings.statistics.endTime);
    properties.put(
        "statistics_sessions_total", String.valueOf(globalSettings.statistics.sessionsTotal));
    properties.put(
        "statistics_jobs_started", String.valueOf(globalSettings.statistics.jobsStarted));
    properties.put(
        "statistics_jobs_completed", String.valueOf(globalSettings.statistics.jobsCompleted));

    trackAnonymousAction(permanent_user_id, "Application Closed", properties);
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void autorouterStarted() {
    autorouterStartedAt = Instant.now().getEpochSecond();
    sessionCount++;

    Map<String, String> properties = new HashMap<>();
    properties.put("settings", GsonProvider.GSON.toJson(globalSettings));
    properties.put("session_count", String.valueOf(sessionCount));

    trackAnonymousAction(permanent_user_id, "Auto-router Started", properties);
  }

  public static void autorouterFinished(
      Integer netsTotal,
      Integer netsIncomplete,
      Integer clearanceViolations,
      String boardHash,
      Float normalizedScore) {
    long autorouterFinishedAt = Instant.now().getEpochSecond();
    long autorouterRuntime = autorouterFinishedAt - autorouterStartedAt;
    totalAutorouterRuntime += autorouterRuntime;

    Map<String, String> properties = new HashMap<>();
    properties.put("session_count", String.valueOf(sessionCount));
    properties.put("autorouter_runtime", String.valueOf(autorouterRuntime));
    if (netsTotal != null) {
      properties.put("nets_total", Integer.toString(netsTotal));
    }
    if (netsIncomplete != null) {
      properties.put("nets_incomplete", Integer.toString(netsIncomplete));
    }
    if (clearanceViolations != null) {
      properties.put("clearance_violations", Integer.toString(clearanceViolations));
    }
    if (boardHash != null) {
      properties.put("board_hash", boardHash);
    }
    if (normalizedScore != null) {
      properties.put("normalized_score", Float.toString(normalizedScore));
    }

    trackAnonymousAction(permanent_user_id, "Auto-router Finished", properties);
  }

  public static void routeOptimizerStarted() {
    routeOptimizerStartedAt = Instant.now().getEpochSecond();

    Map<String, String> properties = new HashMap<>();
    properties.put("settings", GsonProvider.GSON.toJson(globalSettings));
    properties.put("session_count", String.valueOf(sessionCount));
    trackAnonymousAction(permanent_user_id, "Route Optimizer Started", properties);
  }

  public static void routeOptimizerFinished() {
    long routeOptimizerFinishedAt = Instant.now().getEpochSecond();
    long routeOptimizerRuntime = routeOptimizerFinishedAt - routeOptimizerStartedAt;
    totalRouteOptimizerRuntime += routeOptimizerRuntime;

    Map<String, String> properties = new HashMap<>();
    properties.put("settings", GsonProvider.GSON.toJson(globalSettings));
    properties.put("session_count", String.valueOf(sessionCount));
    properties.put("route_optimizer_runtime", String.valueOf(routeOptimizerRuntime));

    trackAnonymousAction(permanent_user_id, "Route Optimizer Finished", properties);
  }

  public static void fileLoaded(String fileFormat, String fileDetails) {
    Map<String, String> properties = new HashMap<>();
    properties.put("file_format", fileFormat);
    properties.put("file_details", fileDetails);

    trackAnonymousAction(permanent_user_id, "File Loaded", properties);
  }

  public static void boardLoaded(
      String hostName, String hostVersion, int layerCount, int componentCount, int netCount) {
    Map<String, String> properties = new HashMap<>();
    properties.put("host_name", hostName);
    properties.put("host_version", hostVersion);
    properties.put("layer_count", Integer.toString(layerCount));
    properties.put("component_count", Integer.toString(componentCount));
    properties.put("net_count", Integer.toString(netCount));

    trackAnonymousAction(permanent_user_id, "Board Loaded", properties);
  }

  public static void fileSaved(String fileFormat, String fileDetails) {
    Map<String, String> properties = new HashMap<>();
    properties.put("file_format", fileFormat);
    properties.put("file_details", fileDetails);

    trackAnonymousAction(permanent_user_id, "File Saved", properties);
  }

  public static void exceptionThrown(String localizedMessage, Throwable e) {
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement ste : e.getStackTrace()) {
      sb.append(ste.toString());
      sb.append("\n");
    }

    Map<String, String> properties = new HashMap<>();
    properties.put("exception_message", localizedMessage);
    properties.put("exception_details", e.toString());
    properties.put("exception_stacktrace", sb.toString());

    trackAnonymousAction(permanent_user_id, "Exception Thrown", properties);
  }

  public static void apiEndpointCalled(String apiMethod, String requestBody, String responseBody) {
    apiEndpointCalled(apiMethod, requestBody, responseBody, null);
  }

  /**
   * Tracks an API endpoint call, attributing it to the authenticated caller identified by {@code
   * userId}. When {@code userId} is non-null it is used as both the {@code anonymous_id} sent to
   * the analytics backend and the {@code user_id} property stored in BigQuery, so that
   * API-originated events can be correlated per caller even in headless/API-only deployments where
   * the static {@link #permanent_user_id} is never set.
   */
  public static void apiEndpointCalled(
      String apiMethod, String requestBody, String responseBody, UUID userId) {
    Map<String, String> properties = new HashMap<>();
    properties.put("api_method", apiMethod);
    properties.put("api_request", requestBody);
    properties.put("api_response", responseBody);
    String environmentHost = AnalyticsRequestContext.getEnvironmentHost();
    if (environmentHost != null) {
      properties.put("environment_host", environmentHost);
    }

    // Determine the effective identity: prefer the per-request caller UUID over the
    // static permanent_user_id (which is always null in headless / API-only mode).
    String effectiveUserId = userId != null ? userId.toString() : permanent_user_id;

    // Inject the resolved user_id into the properties map so that it overrides the
    // null permanent_user_id that trackAnonymousAction would otherwise write.
    if (effectiveUserId != null) {
      properties.put("user_id", effectiveUserId);
    }

    trackAnonymousAction(effectiveUserId, "API Endpoint Called", properties);
  }

  /**
   * Records one canonical API usage row for billing and quota analytics. Emitted by {@link
   * app.freerouting.api.ApiUsageFilter} once per HTTP request/response cycle.
   */
  public static void apiUsageRecorded(
      String httpMethod,
      String apiPath,
      String apiRouteNormalized,
      int httpStatus,
      long durationMs,
      String apiKeyHash,
      String profileId,
      String profileEmail,
      String environmentHost,
      Long requestBytes,
      Long responseBytes,
      UUID profileUuid) {
    Map<String, String> properties = new HashMap<>();
    properties.put("http_method", httpMethod);
    properties.put("api_path", apiPath);
    properties.put("api_route", apiRouteNormalized);
    properties.put("http_status", Integer.toString(httpStatus));
    properties.put("duration_ms", Long.toString(durationMs));
    if (apiKeyHash != null) {
      properties.put("api_key_hash", apiKeyHash);
    }
    if (profileId != null) {
      properties.put("profile_id", profileId);
    }
    if (profileEmail != null) {
      properties.put("profile_email", profileEmail);
    }
    if (environmentHost != null) {
      properties.put("environment_host", environmentHost);
    }
    if (requestBytes != null) {
      properties.put("request_bytes", Long.toString(requestBytes));
    }
    if (responseBytes != null) {
      properties.put("response_bytes", Long.toString(responseBytes));
    }

    String effectiveUserId = profileUuid != null ? profileUuid.toString() : profileId;
    if (effectiveUserId != null) {
      properties.put("user_id", effectiveUserId);
    }

    trackAnonymousAction(effectiveUserId, "API Usage", properties);
  }
}
