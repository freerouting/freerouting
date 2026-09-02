package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.freerouting.util.gson.GsonProvider;
import org.junit.jupiter.api.Test;

class RuntimeEnvironmentTest {

  @Test
  void sanitizeCommandLineArgumentsRedactsSensitiveFlags() {
    String[] args = {
      "-mp",
      "10",
      "--google-api-key",
      "AIzaSySecretKey123",
      "--token=secret-token-xyz",
      "-de",
      "board.ses",
      "--db-password",
      "super-secret",
      "-is",
      "board.dsn"
    };

    String sanitized = RuntimeEnvironment.sanitizeCommandLineArguments(args);
    assertFalse(sanitized.contains("AIzaSySecretKey123"), "API key must be redacted");
    assertFalse(sanitized.contains("secret-token-xyz"), "Token must be redacted");
    assertFalse(sanitized.contains("super-secret"), "Password must be redacted");
    assertEquals(
        "-mp 10 --google-api-key [REDACTED] --token=[REDACTED] -de board.ses --db-password"
            + " [REDACTED] -is board.dsn",
        sanitized);
  }

  @Test
  void googleSheetsProviderSettingsDoesNotSerializeApiKey() {
    GoogleSheetsProviderSettings settings = new GoogleSheetsProviderSettings();
    settings.sheetUrl = "https://docs.google.com/spreadsheets/d/abc/edit";
    settings.googleApiKey = "AIzaSySecretKey123";

    String json = GsonProvider.GSON.toJson(settings);
    assertFalse(
        json.contains("AIzaSySecretKey123"), "googleApiKey must not be serialized into JSON");
  }

  @Test
  void measureCpuScoreReturnsPositiveScore() {
    long start = System.currentTimeMillis();
    int score = RuntimeEnvironment.measureCpuScore();
    long elapsed = System.currentTimeMillis() - start;

    org.junit.jupiter.api.Assertions.assertTrue(score > 0, "CPU score must be strictly positive");
    org.junit.jupiter.api.Assertions.assertTrue(
        elapsed < 1000, "CPU benchmark should complete well within a bounded startup window");
  }

  @Test
  void cpuScoreIsSerializedIntoJson() {
    RuntimeEnvironment env = new RuntimeEnvironment();
    env.cpuScore = 1234;
    String json = GsonProvider.GSON.toJson(env);
    org.junit.jupiter.api.Assertions.assertTrue(
        json.contains("\"cpu_score\": 1234"), "JSON must serialize cpu_score correctly");
  }
}
