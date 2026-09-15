package app.freerouting.core.scoring;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.util.gson.GsonProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-performance bulk score recalculator for benchmark runs.
 *
 * <p>Processes multi-megabyte {@code benchmarks.json} files via streaming JSON to compute canonical
 * V2 scores across all historical and current runs without mutating original benchmark files.
 */
public final class BenchmarkScoreCalculator {

  private BenchmarkScoreCalculator() {}

  /** Score result record for an individual benchmark run. */
  public static class BenchmarkScoreResult implements Serializable {

    @SerializedName("router_score")
    public float routerScore;

    @SerializedName("is_failed")
    public boolean isFailed;

    public BenchmarkScoreResult() {}

    public BenchmarkScoreResult(float routerScore, boolean isFailed) {
      this.routerScore = routerScore;
      this.isFailed = isFailed;
    }
  }

  /**
   * Recalculates benchmark scores from {@code inputPath} and writes the JSON map to {@code
   * outputPath}.
   *
   * @param inputPath path to the input benchmarks JSON file
   * @param outputPath path to the output normalized scores JSON file
   * @return 0 on success, non-zero on error
   */
  public static int run(Path inputPath, Path outputPath) {
    if (inputPath == null || !Files.exists(inputPath)) {
      FRLogger.error("Benchmark input file does not exist: " + inputPath, null);
      return 1;
    }
    if (outputPath == null) {
      FRLogger.error("Benchmark output path must not be null", null);
      return 1;
    }

    try {
      long startTime = System.currentTimeMillis();
      Map<String, BenchmarkScoreResult> scores = calculateScores(inputPath);

      if (outputPath.getParent() != null) {
        Files.createDirectories(outputPath.getParent());
      }

      Path tempFile =
          Files.createTempFile(
              outputPath.getParent() != null ? outputPath.getParent() : Path.of("."),
              "scores-",
              ".tmp");

      try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
        GsonProvider.GSON.toJson(scores, writer);
      }

      Files.move(
          tempFile,
          outputPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);

      long elapsed = System.currentTimeMillis() - startTime;
      FRLogger.info(
          "Successfully recalculated V2 scores for "
              + scores.size()
              + " benchmark runs in "
              + elapsed
              + " ms -> "
              + outputPath);
      return 0;
    } catch (Exception e) {
      FRLogger.error("Failed to calculate benchmark scores: " + e.getMessage(), e);
      return 1;
    }
  }

  /**
   * Calculates scores for all benchmark runs contained in {@code inputPath}.
   *
   * @param inputPath path to the benchmark JSON
   * @return map of cache_key to BenchmarkScoreResult
   * @throws IOException on I/O error
   */
  public static Map<String, BenchmarkScoreResult> calculateScores(Path inputPath)
      throws IOException {
    Map<String, BenchmarkScoreResult> results = new LinkedHashMap<>();
    RouterSettings defaultSettings = new DefaultSettings().getSettings();

    try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      jsonReader.setStrictness(com.google.gson.Strictness.LENIENT);

      if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
          String name = jsonReader.nextName();
          if ("runs".equals(name) && jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
            processRunsArray(jsonReader, defaultSettings, results);
          } else {
            jsonReader.skipValue();
          }
        }
        jsonReader.endObject();
      } else if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
        processRunsArray(jsonReader, defaultSettings, results);
      }
    }

    return results;
  }

  private static void processRunsArray(
      JsonReader jsonReader,
      RouterSettings defaultSettings,
      Map<String, BenchmarkScoreResult> results)
      throws IOException {
    jsonReader.beginArray();
    while (jsonReader.hasNext()) {
      JsonObject runObj = GsonProvider.GSON.fromJson(jsonReader, JsonObject.class);
      if (runObj == null) {
        continue;
      }

      String cacheKey = extractCacheKey(runObj);
      if (cacheKey == null || cacheKey.isBlank()) {
        continue;
      }

      boolean isFailed = isRunFailed(runObj);
      float routerScore = 0.0f;

      if (!isFailed) {
        BoardStatistics stats = extractBoardStatistics(runObj);
        if (stats != null) {
          stats.ensureDifficulty();
          routerScore = stats.getRouterScore(defaultSettings);
        } else {
          isFailed = true;
          routerScore = 0.0f;
        }
      }

      // Normalise to 2 decimal places and ensure non-negative bounds
      float roundedScore = Math.max(0.0f, Math.round(routerScore * 100.0f) / 100.0f);
      results.put(cacheKey, new BenchmarkScoreResult(roundedScore, isFailed));
    }
    jsonReader.endArray();
  }

  private static String extractCacheKey(JsonObject runObj) {
    if (runObj.has("cache_key") && !runObj.get("cache_key").isJsonNull()) {
      return runObj.get("cache_key").getAsString();
    }
    return null;
  }

  private static boolean isRunFailed(JsonObject runObj) {
    if (!runObj.has("exit") || runObj.get("exit").isJsonNull()) {
      return false;
    }
    JsonObject exit = runObj.getAsJsonObject("exit");
    if (exit.has("crashed")
        && !exit.get("crashed").isJsonNull()
        && exit.get("crashed").getAsBoolean()) {
      return true;
    }
    if (exit.has("code") && !exit.get("code").isJsonNull() && exit.get("code").getAsInt() != 0) {
      return true;
    }
    if (exit.has("state") && !exit.get("state").isJsonNull()) {
      String state = exit.get("state").getAsString();
      if ("FAILED".equalsIgnoreCase(state)) {
        return true;
      }
    }
    return false;
  }

  private static BoardStatistics extractBoardStatistics(JsonObject runObj) {
    if (!runObj.has("phases") || runObj.get("phases").isJsonNull()) {
      return null;
    }
    JsonObject phases = runObj.getAsJsonObject("phases");

    // autorouter.after is the primary authority for router score
    BoardStatistics stats = getStatsFromPhase(phases, "autorouter", "after");
    if (stats != null) {
      return stats;
    }

    // fallback to optimizer.after or fanout.after if autorouter is absent
    stats = getStatsFromPhase(phases, "optimizer", "after");
    if (stats != null) {
      return stats;
    }

    return getStatsFromPhase(phases, "fanout", "after");
  }

  private static BoardStatistics getStatsFromPhase(
      JsonObject phases, String phaseName, String stage) {
    if (!phases.has(phaseName) || phases.get(phaseName).isJsonNull()) {
      return null;
    }
    JsonObject phase = phases.getAsJsonObject(phaseName);
    if (!phase.has(stage) || phase.get(stage).isJsonNull()) {
      return null;
    }
    JsonObject stageObj = phase.getAsJsonObject(stage);
    if (!stageObj.has("board_statistics") || stageObj.get("board_statistics").isJsonNull()) {
      return null;
    }
    try {
      return GsonProvider.GSON.fromJson(
          stageObj.getAsJsonObject("board_statistics"), BoardStatistics.class);
    } catch (Exception e) {
      FRLogger.warn("Failed to deserialize board_statistics for phase " + phaseName + ": " + e);
      return null;
    }
  }
}
