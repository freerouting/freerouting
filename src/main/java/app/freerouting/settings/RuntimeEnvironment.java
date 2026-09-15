package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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

  @SerializedName("pipeline_type")
  public String pipelineType;

  @SerializedName("actor_type")
  public String actorType;

  /** Discarded JIT / turbo warm-up before scoring samples. */
  private static final long CPU_SCORE_WARMUP_NS = 20_000_000L;

  /** Timed window per sample. */
  private static final long CPU_SCORE_SAMPLE_NS = 40_000_000L;

  /** Odd sample count so the median is a measured value. */
  private static final int CPU_SCORE_SAMPLE_COUNT = 5;

  /** Keep the persisted score in a compact range while retaining relative host performance. */
  private static final int CPU_SCORE_SCALE = 1_000;

  /**
   * Measures a single-threaded CPU throughput score by running a synthetic micro-benchmark of EDA
   * geometric operations (2D bounding-box overlap, 2D cross-product orientation, and Manhattan
   * distance steps).
   *
   * <p>A short warm-up is discarded, then several samples are taken and the <em>median</em> scaled
   * iterations/ms is returned so turbo boost, GC, and other processes do not dominate one
   * measurement window. The raw throughput is divided by {@value #CPU_SCORE_SCALE}.
   *
   * @return scaled throughput score in iterations per millisecond (at least 1)
   */
  public static int measureCpuScore() {
    runCpuScoreKernel(CPU_SCORE_WARMUP_NS);
    int[] samples = new int[CPU_SCORE_SAMPLE_COUNT];
    for (int i = 0; i < CPU_SCORE_SAMPLE_COUNT; i++) {
      samples[i] = measureCpuScoreSample(CPU_SCORE_SAMPLE_NS);
    }
    Arrays.sort(samples);
    return Math.max(1, Math.round(samples[CPU_SCORE_SAMPLE_COUNT / 2] / (float) CPU_SCORE_SCALE));
  }

  private static int measureCpuScoreSample(long targetDurationNs) {
    long start = System.nanoTime();
    long iterations = runCpuScoreKernel(targetDurationNs);
    long elapsedNs = Math.max(1L, System.nanoTime() - start);
    return (int) Math.max(1L, (iterations * 1_000_000L) / elapsedNs);
  }

  /**
   * Consumed by {@link #runCpuScoreKernel} so the geometric loop cannot be deleted as dead code.
   */
  private static int cpuScoreSink;

  private static long runCpuScoreKernel(long targetDurationNs) {
    long start = System.nanoTime();
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
        boolean overlap = (x1 <= a2 && x2 >= a1 && y1 <= b2 && y2 >= b1);
        acc += overlap ? 1 : 0;
        int cross = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1);
        acc += (cross > 0) ? 1 : -1;
        int dist = Math.abs(x2 - a1) + Math.abs(y2 - b1);
        acc += (dist & 1);
        a1 = (a1 + acc) & 0x3FF;
        b1 = (b1 + dist) & 0x3FF;
      }
      iterations += 500;
    }
    cpuScoreSink += acc;
    return iterations;
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
