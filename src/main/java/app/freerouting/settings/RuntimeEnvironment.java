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

  @SerializedName("cpu_score")
  public int cpuScore;

  @SerializedName("host")
  public transient String host = "N/A";

  /**
   * Measures a single-threaded CPU throughput score by running a lightweight synthetic
   * micro-benchmark (~15 ms) exercising EDA geometric operations (2D bounding-box overlap, 2D
   * cross-product orientation, and distance step accumulators).
   *
   * @return throughput score normalized to iterations per millisecond
   */
  public static int measureCpuScore() {
    long start = System.nanoTime();
    long targetDurationNs = 15_000_000L; // 15 ms budget
    long iterations = 0;

    int x1 = 120;
    int y1 = 250;
    int x2 = 800;
    int y2 = 950;
    int a1 = 300;
    int b1 = 400;
    int a2 = 750;
    int b2 = 880;
    int px = 500;
    int py = 600;
    int acc = 0;

    while (System.nanoTime() - start < targetDurationNs) {
      for (int i = 0; i < 500; i++) {
        // 1. 2D Bounding-Box overlap check (ShapeSearchTree primitive)
        boolean overlap = (x1 <= a2 && x2 >= a1 && y1 <= b2 && y2 >= b1);
        acc += overlap ? 1 : 0;

        // 2. 2D Cross-product orientation (Polygon expansion primitive)
        int cross = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1);
        acc += (cross > 0) ? 1 : -1;

        // 3. Manhattan distance step (MazeSearchEngine primitive)
        int dist = Math.abs(x2 - a1) + Math.abs(y2 - b1);
        acc += (dist & 1);

        // Perturb coordinates to prevent loop unrolling / dead-code elimination
        a1 = (a1 + acc) & 0x3FF;
        b1 = (b1 + dist) & 0x3FF;
      }
      iterations += 500;
    }

    long elapsedNs = System.nanoTime() - start;
    long elapsedMs = Math.max(1, elapsedNs / 1_000_000L);
    return (int) Math.max(1, iterations / elapsedMs);
  }

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
