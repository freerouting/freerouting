package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.LegacyRouterSettingsBridge;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;
import app.freerouting.util.ReflectionUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides router settings from command-line arguments. Only the settings specified via CLI will be
 * non-null.
 */
public class CliSettings implements SettingsSource {

  private static final int PRIORITY = 60;
  private final RouterSettings settings;
  private final Map<String, String> parsedArguments;

  /**
   * Creates a CliSettings source by parsing command-line arguments.
   *
   * @param args Command-line arguments array
   */
  public CliSettings(String[] args) {
    this.parsedArguments = new HashMap<>();
    this.settings = parseArguments(args);
  }

  private RouterSettings parseArguments(String[] args) {
    RouterSettings settings = new RouterSettings();
    boolean hasDesignInputArgument = false;
    boolean hasDesignOutputArgument = false;
    boolean hasExplicitRouterEnabledArgument = false;

    // Parse command-line arguments and populate only the specified settings
    // This uses the same logic as GlobalSettings.applyCommandLineArguments
    // but only for router-related settings

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if (arg.startsWith("--")) {
        // Handle --property=value format
        if (arg.contains("=")) {
          String[] parts = arg.substring(2).split("=", 2);
          String propertyName = parts[0];
          String value = parts.length > 1 ? parts[1] : "";

          if ("router.enabled".equals(propertyName)
              || "router.autorouter.enabled".equals(propertyName)) {
            hasExplicitRouterEnabledArgument = true;
          }

          if ("scoring-version".equals(propertyName)) {
            applyRouterSetting(settings, "router.scoring.version", value);
            applyRouterSetting(settings, "optimizer.scoring.version", value);
          } else if (propertyName.startsWith("router.")
              || propertyName.startsWith("optimizer.")
              || "router-scoring-version".equals(propertyName)
              || "optimizer-scoring-version".equals(propertyName)) {
            String normalizedProperty =
                switch (propertyName) {
                  case "router-scoring-version" -> "router.scoring.version";
                  case "optimizer-scoring-version" -> "optimizer.scoring.version";
                  default -> propertyName;
                };
            applyRouterSetting(settings, normalizedProperty, value);
          }
        }
      } else if (arg.startsWith("-")) {
        // Handle -flag value format
        String flag = arg.substring(1);
        String value = i + 1 < args.length && !args[i + 1].startsWith("-") ? args[++i] : "";

        if ("de".equals(flag)) {
          hasDesignInputArgument = true;
        } else if ("do".equals(flag)) {
          hasDesignOutputArgument = true;
        }

        if ("oit".equals(flag)) {
          FRLogger.warn(
              "The '-oit' command-line flag is deprecated; use"
                  + " '--router.optimizer.improvement_threshold' instead.");
        }

        // Map short flags to router settings
        String propertyName = mapFlagToProperty(flag);
        if (propertyName != null
            && (propertyName.startsWith("router.")
                || propertyName.startsWith("optimizer.")
                || "scoring-version".equals(propertyName))) {
          applyRouterSetting(settings, propertyName, value);
        }
      }
    }

    // Legacy batch invocation (`-de ... -do ...`) is expected to route immediately.
    // Force router enabled unless the caller explicitly set --router.enabled=... .
    if (hasDesignInputArgument && hasDesignOutputArgument && !hasExplicitRouterEnabledArgument) {
      settings.autorouter.enabled = true;
      FRLogger.debug(
          "Applied CLI router setting: router.autorouter.enabled = true"
              + " (implicit from -de/-do batch mode)");
    }

    return settings;
  }

  private void applyRouterSetting(RouterSettings settings, String propertyName, String value) {
    try {
      if ("scoring-version".equals(propertyName)) {
        applyRouterSetting(settings, "router.scoring.version", value);
        applyRouterSetting(settings, "optimizer.scoring.version", value);
        return;
      }
      // Remove "router." prefix if present
      String fieldPath =
          propertyName.startsWith("router.") ? propertyName.substring(7) : propertyName;
      if ("scoring.version".equals(fieldPath)) {
        fieldPath = "routerScoring.version";
      } else if ("optimizer.scoring.version".equals(propertyName)) {
        fieldPath = "optimizerScoring.version";
      } else if (propertyName.startsWith("router.")) {
        String relative = propertyName.substring("router.".length());
        String canonical = LegacyRouterSettingsBridge.canonicalCliPath(relative);
        if (LegacyRouterSettingsBridge.isDeprecatedFlatAutorouterPath(relative)) {
          LegacyRouterSettingsBridge.warnDeprecatedPath(
              "router." + relative, "router." + canonical);
        }
        fieldPath = canonical;
      }
      if (fieldPath.endsWith(".version")) {
        value =
            switch (value.trim().toLowerCase()) {
              case "v1", "legacy" -> "V1_LEGACY";
              case "v2", "continuous" ->
                  fieldPath.startsWith("optimizer") ? "V2_LOWER_BOUND" : "V2_CONTINUOUS";
              case "lower_bound", "lower-bound" -> "V2_LOWER_BOUND";
              default -> value;
            };
      }
      if ("router.optimizer.improvement_threshold".equals(propertyName)
          || fieldPath.endsWith("improvement_threshold")
          || fieldPath.endsWith("optimizationImprovementThreshold")) {
        try {
          float parsed = Float.parseFloat(value.trim());
          if (parsed > 0.0f && parsed < 1.0f) {
            value = String.valueOf(parsed * 100.0f);
          }
        } catch (NumberFormatException ignored) {
          // Fall back to raw string value if parsing as float fails
        }
      }

      ReflectionUtil.setFieldValue(settings, fieldPath, value);
      parsedArguments.put(propertyName, value);
      FRLogger.debug("Applied CLI router setting: " + propertyName + " = " + value);
    } catch (Exception e) {
      FRLogger.warn("Failed to apply CLI router setting: " + propertyName + ": " + e.getMessage());
    }
  }

  private String mapFlagToProperty(String flag) {
    // Map short flags to full property names
    return switch (flag) {
      case "mp" -> "router.max_passes";
      case "mt" -> "router.max_threads";
      case "oit" -> "router.optimizer.improvement_threshold";
      case "router-scoring-version" -> "router.scoring.version";
      case "optimizer-scoring-version" -> "optimizer.scoring.version";
      case "scoring-version" -> "scoring-version";
      // Add more mappings as needed
      default -> null;
    };
  }

  @Override
  public RouterSettings getSettings() {
    return settings;
  }

  @Override
  public String getSourceName() {
    return "CLI Arguments";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  /**
   * Gets the parsed arguments for debugging/logging.
   *
   * @return Map of property names to values
   */
  public Map<String, String> getParsedArguments() {
    return new HashMap<>(parsedArguments);
  }
}
