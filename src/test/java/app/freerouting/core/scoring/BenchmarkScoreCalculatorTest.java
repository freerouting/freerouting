package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkScoreCalculatorTest {

  @Test
  void testCalculateScoresFromBenchmarkJson(@TempDir Path tempDir) throws IOException {
    String sampleJson =
        """
        {
          "total_runs": 4,
          "runs": [
            {
              "cache_key": "run-perfect",
              "exit": { "code": 0, "state": "COMPLETED", "crashed": false, "timed_out": false },
              "phases": {
                "autorouter": {
                  "after": {
                    "board_statistics": {
                      "connections": { "maximum_count": 50, "incomplete_count": 0 },
                      "clearance_violations": { "total_count": 0, "total_violation_um": 0.0 },
                      "difficulty": { "difficulty_d": 100.0, "pin_count": 50, "signal_layer_count": 2 }
                    }
                  }
                }
              }
            },
            {
              "cache_key": "run-with-violations",
              "exit": { "code": 0, "state": "COMPLETED", "crashed": false, "timed_out": false },
              "phases": {
                "autorouter": {
                  "after": {
                    "board_statistics": {
                      "connections": { "maximum_count": 100, "incomplete_count": 10 },
                      "clearance_violations": { "total_count": 4, "total_violation_um": 200.0 },
                      "difficulty": { "difficulty_d": 200.0, "pin_count": 100, "signal_layer_count": 2 }
                    }
                  }
                }
              }
            },
            {
              "cache_key": "run-legacy-v19",
              "exit": { "code": 0, "state": "COMPLETED", "crashed": false, "timed_out": false },
              "phases": {
                "autorouter": {
                  "after": {
                    "board_statistics": {
                      "connections": { "maximum_count": 100, "incomplete_count": 0 },
                      "clearance_violations": { "total_count": 2, "total_violation_um": 50.0 },
                      "items": { "pin_count": 50 },
                      "layers": { "signal_count": 2 }
                    }
                  }
                }
              }
            },
            {
              "cache_key": "run-failed",
              "exit": { "code": 1, "state": "FAILED", "crashed": true, "timed_out": false },
              "phases": {
                "autorouter": {
                  "after": {
                    "board_statistics": {
                      "connections": { "maximum_count": 50, "incomplete_count": 0 },
                      "clearance_violations": { "total_count": 0, "total_violation_um": 0.0 }
                    }
                  }
                }
              }
            }
          ]
        }
        """;

    Path inputPath = tempDir.resolve("sample_benchmarks.json");
    Path outputPath = tempDir.resolve("normalized_scores.json");
    Files.writeString(inputPath, sampleJson, StandardCharsets.UTF_8);

    int exitCode = BenchmarkScoreCalculator.run(inputPath, outputPath);
    assertEquals(0, exitCode);
    assertTrue(Files.exists(outputPath));

    Map<String, BenchmarkScoreCalculator.BenchmarkScoreResult> scores =
        BenchmarkScoreCalculator.calculateScores(inputPath);
    assertEquals(4, scores.size());

    // Perfect run: 1000.0, not failed
    BenchmarkScoreCalculator.BenchmarkScoreResult perfect = scores.get("run-perfect");
    assertEquals(1000.0f, perfect.routerScore, 0.01f);
    assertFalse(perfect.isFailed);

    // With violations & incomplete connections:
    BenchmarkScoreCalculator.BenchmarkScoreResult withViolations =
        scores.get("run-with-violations");
    assertTrue(withViolations.routerScore > 0.0f && withViolations.routerScore < 1000.0f);
    assertFalse(withViolations.isFailed);

    // Legacy v1.9: difficulty correctly calculated from pins * layers = 100
    BenchmarkScoreCalculator.BenchmarkScoreResult legacy = scores.get("run-legacy-v19");
    assertTrue(legacy.routerScore > 900.0f && legacy.routerScore < 1000.0f);
    assertFalse(legacy.isFailed);

    // Failed run: 0.0, isFailed = true
    BenchmarkScoreCalculator.BenchmarkScoreResult failed = scores.get("run-failed");
    assertEquals(0.0f, failed.routerScore, 0.01f);
    assertTrue(failed.isFailed);
  }
}
