package app.freerouting.core.results;

import app.freerouting.constants.Constants;
import app.freerouting.core.RouterJobResourceUsage;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.RouterSettings;
import app.freerouting.util.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Machine-readable summary of a headless CLI routing run for benchmark and autopilot harnesses. */
public final class RoutingResultManifest {

  public static final int SCHEMA_VERSION = 1;

  /** Creates an empty manifest for Gson deserialization. */
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

  @SerializedName("settings_snapshot")
  public RouterSettings settingsSnapshot;

  @SerializedName("phases")
  public PhaseMetrics phases = new PhaseMetrics();

  @SerializedName("board_statistics")
  public BoardStatistics boardStatistics;

  @SerializedName("normalized_score")
  public Float normalizedScore;

  @SerializedName("resource_usage")
  public RouterJobResourceUsage resourceUsage;

  @SerializedName("final_state")
  public String finalState;

  @SerializedName("exit_code")
  public int exitCode;

  @SerializedName("output_written")
  public boolean outputWritten;

  /** Input fixture identity for the run. */
  public static class FixtureInfo {
    @SerializedName("filename")
    public String filename;

    @SerializedName("sha256")
    public String sha256;
  }

  /** Per-stage duration and pass counts. */
  public static class PhaseMetrics {
    @SerializedName("fanout")
    public PhaseDetail fanout = new PhaseDetail();

    @SerializedName("autorouter")
    public PhaseDetail autorouter = new PhaseDetail();

    @SerializedName("optimizer")
    public PhaseDetail optimizer = new PhaseDetail();
  }

  /** Duration and pass count for one routing stage. */
  public static class PhaseDetail {
    @SerializedName("duration_seconds")
    public Float durationSeconds;

    @SerializedName("passes_completed")
    public Integer passesCompleted;
  }

  /** Builds a manifest from a completed routing job. */
  public static RoutingResultManifest fromJob(
      RoutingJob job, String inputFilePath, boolean outputWritten, int exitCode) {
    RoutingResultManifest manifest = new RoutingResultManifest();
    manifest.generatedAt = Instant.now().toString();
    manifest.appVersion = Constants.FREEROUTING_VERSION;
    manifest.gitSha = resolveGitSha();
    manifest.fixture = new FixtureInfo();
    if (inputFilePath != null) {
      Path inputPath = Path.of(inputFilePath);
      manifest.fixture.filename = inputPath.getFileName().toString();
      manifest.fixture.sha256 = sha256Hex(inputPath);
    }
    manifest.settingsSnapshot = job.routerSettings;
    manifest.finalState = job.state != null ? job.state.name() : RoutingJobState.INVALID.name();
    manifest.exitCode = exitCode;
    manifest.outputWritten = outputWritten;
    manifest.resourceUsage = job.resourceUsage;

    if (job.board != null) {
      manifest.boardStatistics = new BoardStatistics(job.board);
      if (job.routerSettings != null && job.routerSettings.scoring != null) {
        manifest.normalizedScore =
            manifest.boardStatistics.getNormalizedScore(job.routerSettings.scoring);
      }
    }

    if (job.getCurrentPass() > 0) {
      manifest.phases.autorouter.passesCompleted = job.getCurrentPass();
    }

    if (job.startedAt != null && job.finishedAt != null) {
      float totalSeconds =
          (float) (java.time.Duration.between(job.startedAt, job.finishedAt).toMillis() / 1000.0);
      manifest.phases.autorouter.durationSeconds = totalSeconds;
    }

    return manifest;
  }

  /** Writes the manifest as UTF-8 JSON to {@code targetPath}. */
  public static void write(Path targetPath, RoutingResultManifest manifest) throws IOException {
    if (targetPath.getParent() != null) {
      Files.createDirectories(targetPath.getParent());
    }
    String json = GsonProvider.GSON.toJson(manifest);
    Files.writeString(targetPath, json, StandardCharsets.UTF_8);
  }

  /** Resolves git SHA from harness environment or returns {@code unknown}. */
  public static String resolveGitSha() {
    String env = System.getenv("FREEROUTING_GIT_SHA");
    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    String prop = System.getProperty("freerouting.git.sha");
    if (prop != null && !prop.isBlank()) {
      return prop.trim();
    }
    String legacyProp = System.getProperty("FREEROUTING_GIT_SHA");
    if (legacyProp != null && !legacyProp.isBlank()) {
      return legacyProp.trim();
    }
    return "unknown";
  }

  private static String sha256Hex(Path path) {
    try {
      byte[] bytes = Files.readAllBytes(path);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (IOException | NoSuchAlgorithmException e) {
      return null;
    }
  }
}
