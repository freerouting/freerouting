package app.freerouting.management;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.segment.Properties;
import app.freerouting.management.segment.Traits;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FRAnalytics {
  private static SegmentClient analytics;
  private static String permanent_user_id;
  private static String appPreviousLocation = "";
  private static String appCurrentLocation = "";
  private static String appWindowTitle = "";
  private static final HashMap<String, String> appLocationTable =
      new HashMap<String, String>() {
        {
          put("app.freerouting.gui.BoardFrame", "app.freerouting.gui/Board");
          put("app.freerouting.gui.WindowObjectVisibility", "app.freerouting.gui/Appearance/ObjectTransparency");
          put("app.freerouting.gui.WindowLayerVisibility", "app.freerouting.gui/Appearance/LayerTransparency");
          put("app.freerouting.gui.ColorManager", "app.freerouting.gui/Appearance/Colors");
          put("app.freerouting.gui.WindowDisplayMisc", "app.freerouting.gui/Appearance/Misc");
          put("app.freerouting.gui.WindowSelectParameter", "app.freerouting.gui/Settings/Selection");
          put("app.freerouting.gui.WindowRouteParameter", "app.freerouting.gui/Settings/Routing");
          put("app.freerouting.gui.WindowAutorouteParameter", "app.freerouting.gui/Settings/Auto-router");
          put("app.freerouting.gui.WindowAutorouteDetailParameter", "app.freerouting.gui/Settings/Auto-router/Details");
          put("app.freerouting.gui.WindowMoveParameter", "app.freerouting.gui/Settings/Controls");
          put("app.freerouting.gui.WindowClearanceMatrix", "app.freerouting.gui/Rules/ClearanceMatrix");
          put("app.freerouting.gui.WindowVia", "app.freerouting.gui/Rules/Vias");
          put("app.freerouting.gui.WindowNets", "app.freerouting.gui/Rules/Nets");
          put("app.freerouting.gui.WindowNetClasses", "app.freerouting.gui/Rules/NetClasses");
          put("app.freerouting.gui.WindowPackages", "app.freerouting.gui/Information/LibraryPackages");
          put("app.freerouting.gui.WindowPadstacks", "app.freerouting.gui/Information/LibraryPadstacks");
          put("app.freerouting.gui.WindowComponents", "app.freerouting.gui/Information/PlacedComponents");
          put("app.freerouting.gui.WindowIncompletes", "app.freerouting.gui/Information/Incompletes");
          put("app.freerouting.gui.WindowLengthViolations", "app.freerouting.gui/Information/LengthViolations");
          put("app.freerouting.gui.WindowClearanceViolations", "app.freerouting.gui/Information/ClearanceViolations");
          put("app.freerouting.gui.WindowUnconnectedRoute", "app.freerouting.gui/Information/UnconnectedRoutes");
          put("app.freerouting.gui.WindowRouteStubs", "app.freerouting.gui/Information/RouteStubs");
          put("app.freerouting.gui.WindowSnapshot", "app.freerouting.gui/Other/Snapshots");
          put("app.freerouting.gui.WindowSnapshotSettings", "app.freerouting.gui/Other/Snapshots/Settings");
          put("app.freerouting.gui.WindowAbout", "app.freerouting.gui/Help/About");
          put("select_button", "app.freerouting.gui/Board/Toolbar/Select");
          put("route_button", "app.freerouting.gui/Board/Toolbar/Route");
          put("drag_button", "app.freerouting.gui/Board/Toolbar/Drag");
          put("autoroute_button", "app.freerouting.gui/Board/Toolbar/Autorouter");
          put("undo_button", "app.freerouting.gui/Board/Toolbar/Undo");
          put("redo_button", "app.freerouting.gui/Board/Toolbar/Redo");
          put("incompletes_button", "app.freerouting.gui/Board/Toolbar/Incompletes");
          put("violation_button", "app.freerouting.gui/Board/Toolbar/Violations");
          put("display_all_button", "app.freerouting.gui/Board/Toolbar/ZoomAll");
          put("display_region_button", "app.freerouting.gui/Board/Toolbar/ZoomRegion");
          put("file_save_menuitem", "app.freerouting.gui/Board/Menu/File/Save");
          put("file_save_and_exit_menuitem", "app.freerouting.gui/Board/Menu/File/SaveAndExit");
          put("file_cancel_and_exit_menuitem", "app.freerouting.gui/Board/Menu/File/CancelAndExit");
          put("file_save_as_menuitem", "app.freerouting.gui/Board/Menu/File/SaveAs");
          put("file_write_logfile_menuitem", "app.freerouting.gui/Board/Menu/File/MacroRecording");
          put("file_replay_logfile_menuitem", "app.freerouting.gui/Board/Menu/File/MacroPlayback");
          put("file_save_settings_menuitem", "app.freerouting.gui/Board/Menu/File/SaveGUISettings");
          put("file_write_session_file_menuitem", "app.freerouting.gui/Board/Menu/File/ExportAsSpecctra");
          put("file_write_eagle_session_script_menuitem", "app.freerouting.gui/Board/Menu/File/ExportAsEagleScript");
          put("display_object_visibility_menuitem", "app.freerouting.gui/Board/Menu/Appearance/ObjectVisibility");
          put("display_layer_visibility_menuitem", "app.freerouting.gui/Board/Menu/Appearance/LayerVisibility");
          put("display_colors_menuitem", "app.freerouting.gui/Board/Menu/Appearance/Colors");
          put("display_miscellaneous_menuitem", "app.freerouting.gui/Board/Menu/Appearance/Miscellaneous");
          put("settings_selection_menuitem", "app.freerouting.gui/Board/Menu/Settings/Selection");
          put("settings_routing_menuitem", "app.freerouting.gui/Board/Menu/Settings/Routing");
          put("settings_autorouter_menuitem", "app.freerouting.gui/Board/Menu/Settings/AutoRouter");
          put("settings_controls_menuitem", "app.freerouting.gui/Board/Menu/Settings/Controls");
          put("rules_clearance_menuitem", "app.freerouting.gui/Board/Menu/Rules/ClearanceMatrix");
          put("rules_vias_menuitem", "app.freerouting.gui/Board/Menu/Rules/Vias");
          put("rules_nets_menuitem", "app.freerouting.gui/Board/Menu/Rules/Nets");
          put("rules_net_class_menuitem", "app.freerouting.gui/Board/Menu/Rules/NetClasses");
          put("info_packages_menuitem", "app.freerouting.gui/Board/Menu/Info/Packages");
          put("info_padstacks_menuitem", "app.freerouting.gui/Board/Menu/Info/Padstacks");
          put("info_components_menuitem", "app.freerouting.gui/Board/Menu/Info/Components");
          put("info_incompletes_menuitem", "app.freerouting.gui/Board/Menu/Info/IncompleteRoutes");
          put("info_length_violations_menuitem", "app.freerouting.gui/Board/Menu/Info/LengthViolations");
          put("info_clearance_violations_menuitem", "app.freerouting.gui/Board/Menu/Info/ClearanceViolations");
          put("info_unconnected_routes_menuitem", "app.freerouting.gui/Board/Menu/Info/UnconnectedRoutes");
          put("info_route_stubs_menuitem", "app.freerouting.gui/Board/Menu/Info/RoutedStubs");
          put("other_snapshots_menuitem", "app.freerouting.gui/Board/Menu/Other/Snapshots");
          put("other_delete_all_tracks_menuitem", "app.freerouting.gui/Board/Menu/Other/DeleteAllTracksAndVias");
          put("help_about_menuitem", "app.freerouting.gui/Board/Menu/Help/About");
        }
      };
  private static long appStartedAt;
  private static int sessionCount = 0;
  private static long totalAutorouterRuntime = 0;
  private static long totalRouteOptimizerRuntime = 0;
  private static long autorouterStartedAt;
  private static long routeOptimizerStartedAt;

  public static void setWriteKey(String libraryVersion, String writeKey) {
    analytics = new SegmentClient(libraryVersion, writeKey);
  }

  public static void setUserId(String userId) {
    permanent_user_id = userId;
  }

  private static void identifyUser(String userId, Map<String, String> traits) {
    try {
      Traits t = new Traits();
      t.putAll(traits);

      analytics.identify(userId, null, t);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.identifyUser: " + e.getMessage(), e);
    }
  }

  private static void identifyAnonymous(String anonymousId, Map<String, String> traits) {
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
    try {
      Properties p = new Properties();
      p.put("current_time_utc", Instant.now().toString());
      p.put("app_current_location", appCurrentLocation);
      p.put("app_previous_location", appPreviousLocation);
      p.put("app_window_title", appWindowTitle);
      if (properties != null) {
        p.putAll(properties);
      }

      analytics.track(null, anonymousId, action, p);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.trackAnonymousAction: " + e.getMessage(), e);
    }
  }

  public static void identify() {
    Map<String, String> traits = new HashMap<>();
    traits.put("anonymous", "true");
    // identifyUser(permanent_user_id, traits);
    identifyAnonymous(permanent_user_id, traits);
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
      int width, int height, int dpi) {
    appStartedAt = Instant.now().getEpochSecond();

    Map<String, String> properties = new HashMap<>();
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
    properties.put("session_count", String.valueOf(sessionCount));
    properties.put("total_autorouter_runtime", String.valueOf(totalAutorouterRuntime));
    properties.put("total_route_optimizer_runtime", String.valueOf(totalRouteOptimizerRuntime));
    properties.put("application_runtime", String.valueOf((appClosedAt - appStartedAt)));

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
    properties.put("session_count", String.valueOf(sessionCount));

    trackAnonymousAction(permanent_user_id, "Auto-router Started", properties);
  }

  public static void autorouterFinished() {
    long autorouterFinishedAt = Instant.now().getEpochSecond();
    long autorouterRuntime = autorouterFinishedAt - autorouterStartedAt;
    totalAutorouterRuntime += autorouterRuntime;

    Map<String, String> properties = new HashMap<>();
    properties.put("session_count", String.valueOf(sessionCount));
    properties.put("autorouter_runtime", String.valueOf(autorouterRuntime));

    trackAnonymousAction(permanent_user_id, "Auto-router Finished", properties);
  }

  public static void routeOptimizerStarted() {
    routeOptimizerStartedAt = Instant.now().getEpochSecond();

    Map<String, String> properties = new HashMap<>();
    properties.put("session_count", String.valueOf(sessionCount));
    trackAnonymousAction(permanent_user_id, "Route Optimizer Started", properties);
  }

  public static void routeOptimizerFinished() {
    long routeOptimizerFinishedAt = Instant.now().getEpochSecond();
    long routeOptimizerRuntime = routeOptimizerFinishedAt - routeOptimizerStartedAt;
    totalRouteOptimizerRuntime += routeOptimizerRuntime;

    Map<String, String> properties = new HashMap<>();
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

  public static void boardLoaded(String hostName, String hostVersion, int layerCount, int componentCount, int netCount) {
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
}