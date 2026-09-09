package app.freerouting.settings;

import app.freerouting.logger.FRLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;

/**
 * Maps deprecated flat {@code router.*} autorouter keys onto {@code router.autorouter.*} and logs a
 * deprecation warning. Nested values win when both are present.
 */
public final class LegacyRouterSettingsBridge {

  static final Map<String, String> FLAT_JSON_TO_NESTED =
      Map.of(
          "enabled", "enabled",
          "algorithm", "algorithm",
          "max_passes", "maxPasses",
          "max_items", "maxItems",
          "save_intermediate_stages", "saveIntermediateStages",
          "ignore_net_classes", "ignoreNetClasses");

  private LegacyRouterSettingsBridge() {}

  /**
   * CLI or env path relative to {@code router.} (for example {@code max_passes} or {@code
   * autorouter.max_passes}).
   */
  public static String canonicalCliPath(String routerRelativePath) {
    String path = normalizeRelativePath(routerRelativePath);
    return switch (path) {
      case "enabled",
          "max_passes",
          "algorithm",
          "max_items",
          "save_intermediate_stages",
          "ignore_net_classes" ->
          "autorouter." + path;
      default -> path;
    };
  }

  /** Returns true when {@code routerRelativePath} is a deprecated flat autorouter key. */
  public static boolean isDeprecatedFlatAutorouterPath(String routerRelativePath) {
    return FLAT_JSON_TO_NESTED.containsKey(normalizeRelativePath(routerRelativePath));
  }

  /** Logs that {@code oldPath} is deprecated in favour of {@code newPath}. */
  public static void warnDeprecatedPath(String oldPath, String newPath) {
    FRLogger.warn(
        "Deprecated settings path '"
            + oldPath
            + "'; use '"
            + newPath
            + "' instead. The old path will be removed in a future release.");
  }

  /**
   * Copies legacy flat JSON keys into {@code settings.autorouter} when the nested field is still
   * null. Warns once per object that used a legacy key.
   */
  public static void absorbLegacyJson(JsonObject json, RouterSettings settings) {
    if (json == null || settings == null) {
      return;
    }
    if (settings.autorouter == null) {
      settings.autorouter = new AutorouterSettings();
    }
    AutorouterSettings autorouter = settings.autorouter;
    boolean warned = false;
    if (autorouter.enabled == null && json.has("enabled") && !json.get("enabled").isJsonNull()) {
      autorouter.enabled = json.get("enabled").getAsBoolean();
      warned = true;
    }
    if (autorouter.algorithm == null
        && json.has("algorithm")
        && !json.get("algorithm").isJsonNull()) {
      autorouter.algorithm = json.get("algorithm").getAsString();
      warned = true;
    }
    if (autorouter.maxPasses == null
        && json.has("max_passes")
        && !json.get("max_passes").isJsonNull()) {
      autorouter.maxPasses = json.get("max_passes").getAsInt();
      warned = true;
    }
    if (autorouter.maxItems == null
        && json.has("max_items")
        && !json.get("max_items").isJsonNull()) {
      autorouter.maxItems = json.get("max_items").getAsInt();
      warned = true;
    }
    if (autorouter.saveIntermediateStages == null
        && json.has("save_intermediate_stages")
        && !json.get("save_intermediate_stages").isJsonNull()) {
      autorouter.saveIntermediateStages = json.get("save_intermediate_stages").getAsBoolean();
      warned = true;
    }
    if (autorouter.ignoreNetClasses == null
        && json.has("ignore_net_classes")
        && json.get("ignore_net_classes").isJsonArray()) {
      JsonArray array = json.getAsJsonArray("ignore_net_classes");
      String[] names = new String[array.size()];
      int index = 0;
      for (JsonElement element : array) {
        names[index++] = element.isJsonNull() ? null : element.getAsString();
      }
      autorouter.ignoreNetClasses = names;
      warned = true;
    }
    if (warned) {
      warnDeprecatedPath(
          "router.max_passes (and other flat autorouter keys)", "router.autorouter.max_passes");
    }
  }

  private static String normalizeRelativePath(String routerRelativePath) {
    String path =
        routerRelativePath.startsWith("router.")
            ? routerRelativePath.substring("router.".length())
            : routerRelativePath;
    return path.toLowerCase(Locale.ROOT);
  }
}
