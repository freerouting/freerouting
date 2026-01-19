package app.freerouting.settings.sources;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.ReflectionUtil;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides router settings from environment variables.
 * 
 * Environment variables must start with "FREEROUTING__ROUTER__" prefix.
 * Double underscores are converted to dots for nested properties.
 * 
 * Examples:
 * - FREEROUTING__ROUTER__MAX_PASSES=100 → router.max_passes = 100
 * - FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS=4 →
 * router.optimizer.max_threads = 4
 * - FREEROUTING__ROUTER__VIAS_ALLOWED=false → router.vias_allowed = false
 * 
 * Priority: 55 (between GUI and CLI)
 * - Higher than GUI (50): Environment variables override interactive GUI
 * settings
 * - Lower than CLI (60): Command-line arguments override environment variables
 * - Lower than API (70): API calls have highest priority
 */
public class EnvironmentVariablesSource implements SettingsSource {

    private static final int PRIORITY = 55;
    private static final String ENV_PREFIX = "FREEROUTING__";
    private static final String ROUTER_PREFIX = "ROUTER__";
    private final RouterSettings settings;
    private final Map<String, String> parsedVariables;

    /**
     * Creates an EnvironmentVariablesSource by parsing system environment
     * variables.
     */
    public EnvironmentVariablesSource() {
        this(System.getenv());
    }

    /**
     * Creates an EnvironmentVariablesSource from a custom environment map.
     * Useful for testing.
     * 
     * @param environment Map of environment variables
     */
    public EnvironmentVariablesSource(Map<String, String> environment) {
        this.parsedVariables = new HashMap<>();
        this.settings = parseEnvironmentVariables(environment);
    }

    private RouterSettings parseEnvironmentVariables(Map<String, String> environment) {
        RouterSettings settings = new RouterSettings();
        int parsedCount = 0;

        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Only process variables starting with FREEROUTING__ROUTER__
            if (!key.startsWith(ENV_PREFIX + ROUTER_PREFIX)) {
                continue;
            }

            // Remove the FREEROUTING__ROUTER__ prefix and convert to property path
            String propertyPath = key
                    .substring((ENV_PREFIX + ROUTER_PREFIX).length())
                    .toLowerCase()
                    .replace("__", ".");

            // Try to set the value using reflection
            try {
                ReflectionUtil.setFieldValue(settings, propertyPath, value);
                parsedVariables.put(key, value);
                parsedCount++;
                FRLogger.debug("Parsed environment variable: " + key + " → " + propertyPath + " = " + value);
            } catch (NoSuchFieldException e) {
                FRLogger.warn(
                        "Unknown router setting in environment variable: " + key + " (property: " + propertyPath + ")");
            } catch (Exception e) {
                FRLogger.warn("Failed to parse environment variable: " + key + " = " + value + ": " + e.getMessage());
            }
        }

        if (parsedCount > 0) {
            FRLogger.info("Parsed " + parsedCount + " router settings from environment variables");
        } else {
            FRLogger.debug("No router settings found in environment variables");
        }

        return settings;
    }

    @Override
    public RouterSettings getSettings() {
        return settings;
    }

    @Override
    public String getSourceName() {
        return "Environment Variables";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /**
     * Gets the parsed environment variables for debugging/logging.
     * 
     * @return Map of environment variable names to values
     */
    public Map<String, String> getParsedVariables() {
        return new HashMap<>(parsedVariables);
    }

    /**
     * Gets the number of environment variables that were successfully parsed.
     * 
     * @return Count of parsed variables
     */
    public int getParsedCount() {
        return parsedVariables.size();
    }
}
