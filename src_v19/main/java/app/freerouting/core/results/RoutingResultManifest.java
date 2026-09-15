package app.freerouting.core.results;

import app.freerouting.board.BasicBoard;
import app.freerouting.constants.Constants;
import app.freerouting.core.RouterJobResourceUsage;
import app.freerouting.core.scoring.BoardStatistics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** Machine-readable summary of a headless routing run for v1.9 benchmark parity. */
public final class RoutingResultManifest {

  public static final int SCHEMA_VERSION = 1;
  private static final Gson GSON =
      new GsonBuilder()
          .setPrettyPrinting()
          .registerTypeAdapter(Float.class, new TwoDecimalFloatAdapter())
          .registerTypeAdapter(float.class, new TwoDecimalFloatAdapter())
          .registerTypeAdapter(Double.class, new TwoDecimalDoubleAdapter())
          .registerTypeAdapter(double.class, new TwoDecimalDoubleAdapter())
          .create();

  public RoutingResultManifest() {}

  @SerializedName("schema_version")
  public int schemaVersion = SCHEMA_VERSION;

  @SerializedName("generated_at")
  public String generatedAt;

  @SerializedName("app_version")
  public String appVersion;

  @SerializedName("git_sha")
  public String gitSha;

  @SerializedName("fixture")
  public FixtureInfo fixture;

  @SerializedName("phases")
  public PhaseMetrics phases = new PhaseMetrics();

  @SerializedName("board_statistics")
  public BoardStatistics boardStatistics;

  @SerializedName("normalized_score")
  public Float normalizedScore;

  @SerializedName("optimizer_score")
  public Float optimizerScore;

  @SerializedName("resource_usage")
  public RouterJobResourceUsage resourceUsage;

  @SerializedName("final_state")
  public String finalState;

  @SerializedName("exit_code")
  public int exitCode;

  @SerializedName("output_written")
  public boolean outputWritten;

  public static class FixtureInfo {
    @SerializedName("filename")
    public String filename;

    @SerializedName("sha256")
    public String sha256;
  }

  public static class PhaseMetrics {
    @SerializedName("fanout")
    public PhaseDetail fanout = new PhaseDetail();

    @SerializedName("autorouter")
    public PhaseDetail autorouter = new PhaseDetail();

    @SerializedName("optimizer")
    public PhaseDetail optimizer = new PhaseDetail();
  }

  public static class PhaseDetail {
    @SerializedName("before")
    public PhaseSnapshot before;

    @SerializedName("after")
    public PhaseSnapshot after;

    @SerializedName("duration_seconds")
    public Float durationSeconds;

    @SerializedName("cpu_seconds")
    public Float cpuSeconds;

    @SerializedName("passes_completed")
    public Integer passesCompleted;

    @SerializedName("total_allocated_gb")
    public Float totalAllocatedGb;

    @SerializedName("peak_heap_mb")
    public Float peakHeapMb;
  }

  /** Board metrics and the native score at a phase boundary. */
  public static class PhaseSnapshot {
    @SerializedName("board_statistics")
    public BoardStatistics boardStatistics;

    @SerializedName("score")
    public Float score;

    @SerializedName("score_source")
    public String scoreSource;

    @SerializedName("current_router_score")
    public Float currentRouterScore;

    @SerializedName("current_optimizer_score")
    public Float currentOptimizerScore;

    @SerializedName("current_score_source")
    public String currentScoreSource;
  }

  public static RoutingResultManifest create(
      BasicBoard board,
      String inputFilePath,
      boolean outputWritten,
      int exitCode,
      String finalState,
      PhaseMetrics phases,
      RouterJobResourceUsage resourceUsage) {
    RoutingResultManifest manifest = new RoutingResultManifest();
    manifest.generatedAt = Instant.now().toString();
    manifest.appVersion = Constants.FREEROUTING_VERSION;
    manifest.gitSha = resolveGitSha();
    manifest.fixture = new FixtureInfo();
    if (inputFilePath != null) {
      try {
        Path inputPath = Path.of(inputFilePath);
        manifest.fixture.filename = inputPath.getFileName().toString();
        manifest.fixture.sha256 = sha256Hex(inputPath);
      } catch (Exception ignored) {
        manifest.fixture.filename = inputFilePath;
      }
    }
    manifest.finalState = finalState != null ? finalState : (exitCode == 0 ? "COMPLETED" : "FAILED");
    manifest.exitCode = exitCode;
    manifest.outputWritten = outputWritten;
    manifest.phases = phases != null ? phases : new PhaseMetrics();
    manifest.resourceUsage = resourceUsage != null ? resourceUsage : new RouterJobResourceUsage();

    if (board != null) {
      manifest.boardStatistics = new BoardStatistics(board);
      manifest.normalizedScore = manifest.boardStatistics.calculateNormalizedScore();
      manifest.optimizerScore =
          manifest.phases.optimizer.after != null
              ? manifest.phases.optimizer.after.score
              : null;
    }
    return manifest;
  }

  public static void write(Path targetPath, RoutingResultManifest manifest) throws IOException {
    if (targetPath.getParent() != null) {
      Files.createDirectories(targetPath.getParent());
    }
    Files.writeString(targetPath, GSON.toJson(manifest), StandardCharsets.UTF_8);
  }

  private static String sha256Hex(Path path) {
    if (path == null || !Files.exists(path)) {
      return "";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(Files.readAllBytes(path));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return "";
    }
  }

  private static String resolveGitSha() {
    try {
      Process process =
          new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
              .redirectErrorStream(true)
              .start();
      try (var reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
        String line = reader.readLine();
        if (line != null && !line.isBlank()) {
          return line.trim();
        }
      }
    } catch (Exception ignored) {
    }
    return "unknown";
  }

  private static final class TwoDecimalFloatAdapter extends TypeAdapter<Float> {
    @Override
    public void write(JsonWriter out, Float value) throws IOException {
      if (value == null || !Float.isFinite(value)) {
        out.nullValue();
      } else {
        out.value(new BigDecimal(String.format(Locale.ROOT, "%.2f", value)));
      }
    }

    @Override
    public Float read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return (float) in.nextDouble();
    }
  }

  private static final class TwoDecimalDoubleAdapter extends TypeAdapter<Double> {
    @Override
    public void write(JsonWriter out, Double value) throws IOException {
      if (value == null || !Double.isFinite(value)) {
        out.nullValue();
      } else {
        out.value(new BigDecimal(String.format(Locale.ROOT, "%.2f", value)));
      }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return in.nextDouble();
    }
  }
}
