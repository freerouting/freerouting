package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stores runtime environment information about the application execution context. This is NOT for
 * configuration from environment variables - use EnvironmentVariablesSource for that.
 *
 * <p>This class captures system information like Java version, CPU cores, RAM, etc. that are
 * determined at runtime and cannot be configured.
 */
public class RuntimeEnvironment implements Serializable {

  @SerializedName("freerouting_version")
  public String freeroutingVersion;

  @SerializedName("app_started_at")
  public Instant appStartedAt;

  @SerializedName("command_line_arguments")
  public String commandLineArguments;

  @SerializedName("architecture")
  public String architecture;

  @SerializedName("java")
  public String java;

  @SerializedName("system_language")
  public String systemLanguage;

  @SerializedName("cpu_cores")
  public int cpuCores;

  @SerializedName("ram")
  public int ram;

  @SerializedName("host")
  public transient String host = "N/A";

  /**
   * Sanitizes command-line arguments by redacting sensitive values (keys, tokens, passwords,
   * secrets) before recording in runtime environment or telemetry.
   *
   * @param args raw command-line arguments
   * @return sanitized argument string
   */
  public static String sanitizeCommandLineArguments(String[] args) {
    if (args == null || args.length == 0) {
      return "";
    }
    List<String> sanitized = new ArrayList<>(args.length);
    boolean redactNext = false;
    for (String arg : args) {
      if (arg == null) {
        continue;
      }
      if (redactNext) {
        sanitized.add("[REDACTED]");
        redactNext = false;
        continue;
      }
      String lower = arg.toLowerCase(Locale.ROOT);
      if (lower.startsWith("-")
          && (lower.contains("key")
              || lower.contains("secret")
              || lower.contains("token")
              || lower.contains("password"))) {
        if (arg.contains("=")) {
          int eq = arg.indexOf('=');
          sanitized.add(arg.substring(0, eq + 1) + "[REDACTED]");
        } else {
          sanitized.add(arg);
          redactNext = true;
        }
      } else {
        sanitized.add(arg);
      }
    }
    return String.join(" ", sanitized);
  }
}
