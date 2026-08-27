package app.freerouting.settings.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for EnvironmentVariablesSource. */
class EnvironmentVariablesSourceTest {

  @Test
  void priority() {
    EnvironmentVariablesSource source = new EnvironmentVariablesSource(new HashMap<>());
    assertEquals(55, source.getPriority());
  }

  @Test
  void sourceName() {
    EnvironmentVariablesSource source = new EnvironmentVariablesSource(new HashMap<>());
    assertEquals("Environment Variables", source.getSourceName());
  }

  @Test
  void emptyEnvironment() {
    Map<String, String> env = new HashMap<>();
    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);

    RouterSettings settings = source.getSettings();
    assertNotNull(settings);
    assertEquals(0, source.getParsedCount());
  }

  @Test
  void simpleRouterSetting() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(100, settings.maxPasses);
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void nestedRouterSetting() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "8");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(8, settings.optimizer.maxThreads);
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void booleanSetting() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__VIAS_ALLOWED", "false");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertFalse(settings.viasAllowed);
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void multipleSettings() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__ROUTER__MAX_PASSES", "50",
                "FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "4",
                "FREEROUTING__ROUTER__VIAS_ALLOWED", "true"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(50, settings.maxPasses);
    assertEquals(4, settings.optimizer.maxThreads);
    assertTrue(settings.viasAllowed);
    assertEquals(3, source.getParsedCount());
  }

  @Test
  void ignoresNonRouterVariables() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__GUI__INPUT_DIRECTORY", "/some/path",
                "FREEROUTING__ROUTER__MAX_PASSES", "100",
                "PATH", "/usr/bin",
                "HOME", "/home/user"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(100, settings.maxPasses);
    // Only router settings should be parsed
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void ignoresVariablesWithoutPrefix() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "MAX_PASSES", "100",
                "ROUTER__MAX_PASSES", "200"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    // Null value should remain since no valid env vars were found
    assertNull(settings.maxPasses);
    assertEquals(0, source.getParsedCount());
  }

  @Test
  void invalidPropertyName() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__ROUTER__INVALID_PROPERTY", "value",
                "FREEROUTING__ROUTER__MAX_PASSES", "100"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(100, settings.maxPasses);
    // Only valid property should be parsed
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void invalidValue() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__MAX_PASSES", "not_a_number");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    // Should keep null value since parsing failed
    assertNull(settings.maxPasses);
    assertEquals(0, source.getParsedCount());
  }

  @Test
  void getParsedVariables() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__ROUTER__MAX_PASSES", "100",
                "FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "4"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    source.getSettings(); // Trigger parsing

    Map<String, String> parsed = source.getParsedVariables();
    assertEquals(2, parsed.size());
    assertEquals("100", parsed.get("FREEROUTING__ROUTER__MAX_PASSES"));
    assertEquals("4", parsed.get("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS"));
  }

  @Test
  void caseInsensitivePropertyNames() {
    Map<String, String> env = new HashMap<>();
    // Environment variables are case-sensitive, but property names are converted to
    // lowercase
    env.put("FREEROUTING__ROUTER__MAX_PASSES", "100");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(100, settings.maxPasses);
  }

  @Test
  void stringSettings() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__ALGORITHM", "freerouting-router-v19");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals("freerouting-router-v19", settings.algorithm);
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void saveIntermediateStages() {
    Map<String, String> env = new HashMap<>();
    env.put("FREEROUTING__ROUTER__SAVE_INTERMEDIATE_STAGES", "true");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertTrue(settings.saveIntermediateStages);
    assertEquals(1, source.getParsedCount());
  }

  @Test
  void optimizerStrategies() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__ROUTER__OPTIMIZER__BOARD_UPDATE_STRATEGY", "GREEDY",
                "FREEROUTING__ROUTER__OPTIMIZER__HYBRID_RATIO", "1:1",
                "FREEROUTING__ROUTER__OPTIMIZER__ITEM_SELECTION_STRATEGY", "SEQUENTIAL"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(
        app.freerouting.autoroute.BoardUpdateStrategy.GREEDY,
        settings.optimizer.boardUpdateStrategy);
    assertEquals("1:1", settings.optimizer.hybridRatio);
    assertEquals(
        app.freerouting.autoroute.ItemSelectionStrategy.SEQUENTIAL,
        settings.optimizer.itemSelectionStrategy);
    assertEquals(3, source.getParsedCount());
  }

  @Test
  void lowercaseEnvironmentVariableNames() {
    Map<String, String> env = new HashMap<>();
    env.put("freerouting__router__max_passes", "100");
    env.put("freerouting__router__save_intermediate_stages", "true");

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertEquals(100, settings.maxPasses);
    assertTrue(settings.saveIntermediateStages);
    assertEquals(2, source.getParsedCount());
  }

  @Test
  void traceCostSettings() {
    Map<String, String> env =
        new HashMap<>(
            Map.of(
                "FREEROUTING__ROUTER__SCORING__PREFERRED_DIRECTION_TRACE_COST", "1.5,2.0",
                "FREEROUTING__ROUTER__SCORING__UNDESIRED_DIRECTION_TRACE_COST", "2.5,3.0"));

    EnvironmentVariablesSource source = new EnvironmentVariablesSource(env);
    RouterSettings settings = source.getSettings();

    assertNotNull(settings);
    assertNotNull(settings.scoring.preferredDirectionTraceCost);
    assertEquals(2, settings.scoring.preferredDirectionTraceCost.length);
    assertEquals(1.5, settings.scoring.preferredDirectionTraceCost[0]);
    assertEquals(2.0, settings.scoring.preferredDirectionTraceCost[1]);
    assertEquals(2.5, settings.scoring.undesiredDirectionTraceCost[0]);
    assertEquals(3.0, settings.scoring.undesiredDirectionTraceCost[1]);
    assertEquals(2, source.getParsedCount());
  }
}
