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
      p.put("app_current_location", appCurrentLocation);
      p.put("app_previous_location", appPreviousLocation);
      p.put("app_window_title", appWindowTitle);
      p.putAll(properties);

      analytics.track(null, anonymousId, action, p);
    } catch (Exception e) {
      FRLogger.error("Exception in FRAnalytics.trackAnonymousAction: " + e.getMessage(), e);
    }
  }

  public static void identify()
  {
    Map<String, String> traits = new HashMap<>();
    traits.put("anonymous", "true");
    //identifyUser(permament_user_id, traits);
    identifyAnonymous(permanent_user_id, traits);
  }

  public static void setAppLocation(String appLocation, String windowTitle)
  {
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

  public static void appStart(String freeroutingVersion, String freeroutingBuildDate, String commandLineArguments,
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
}
