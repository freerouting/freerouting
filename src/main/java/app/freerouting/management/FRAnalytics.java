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
  private static final HashMap<String, String> appLocationTable = new HashMap<String, String>()
  {{
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
  }};

  public static void setWriteKey(String libraryVersion, String writeKey)
  {
    analytics = new SegmentClient(libraryVersion, writeKey);
  }

  public static void setUserId(String userId)
  {
    permanent_user_id = userId;
  }

  private static void identifyUser(String userId, Map<String, String> traits)
  {
    try {
      Traits t = new Traits();
      t.putAll(traits);

      analytics.identify(userId, null, t);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.identifyUser: " + e.getMessage(), e);
    }
  }
  private static void identifyAnonymous(String anonymousId, Map<String, String> traits)
  {
    try {
      Traits t = new Traits();
      t.putAll(traits);

      analytics.identify(null, anonymousId, t);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.identifyAnonymous: " + e.getMessage(), e);
    }
  }

  private static void trackAnonymousAction(String anonymousId, String action, Map<String, String> properties)
  {
    try {
      Properties p = new Properties();
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

  public static void identify()
  {
    Map<String, String> traits = new HashMap<>();
    traits.put("anonymous", "true");
    //identifyUser(permanent_user_id, traits);
    identifyAnonymous(permanent_user_id, traits);
  }

  public static void setAppLocation(String appLocation, String windowTitle)
  {
    appLocation = translateWindowClassToUrl(appLocation);

    if (Objects.equals(appPreviousLocation, appLocation))
    {
      return;
    }

    appPreviousLocation = appCurrentLocation;
    appCurrentLocation = appLocation;
    appWindowTitle = windowTitle;

    Properties p = new Properties();
    trackAnonymousAction(permanent_user_id, "Window Changed", p);
  }

  private static String translateWindowClassToUrl(String appLocation) {
    if (appLocationTable.containsKey(appLocation))
    {
      return appLocationTable.get(appLocation);
    } else
    {
      return appLocation.replace("app.freerouting.gui.", "app.freerouting.gui/");
    }
  }

  public static void appStarted(String freeroutingVersion, String freeroutingBuildDate, String commandLineArguments,
      String osName, String osArchitecture, String osVersion,
      String javaVersion, String javaVendor,
      Locale systemLanguage, Locale guiLanguage,
      int cpuCoreCount, long ramAmount,
      Instant currentUtcTime,
      String host)
  {
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
    properties.put("current_time_utc", currentUtcTime.toString());
    properties.put("host", host);
    trackAnonymousAction(permanent_user_id, "Application Started", properties);
  }

  public static void setEnabled(boolean enabled) {
    analytics.setEnabled(enabled);
  }

  public static void appClosed() {
    trackAnonymousAction(permanent_user_id, "Application Closed", null);
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
