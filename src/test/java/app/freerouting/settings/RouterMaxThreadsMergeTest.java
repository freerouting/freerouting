package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The flat {@code router.max_threads} (and its {@code -mt} alias) is documented as the thread cap
 * for all stages. These tests check that it limits both {@code getOptimizerMaxThreads()} and {@code
 * getAutorouterMaxThreads()} through the settings merger.
 */
class RouterMaxThreadsMergeTest {

  private static RouterSettings mergeCli(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  @Test
  void flatCliFlagSetsOptimizerAndAutorouterThreads() {
    RouterSettings merged = mergeCli("--router.max_threads=1");

    assertEquals(1, merged.getOptimizerMaxThreads());
    assertEquals(1, merged.getAutorouterMaxThreads());
    assertEquals(1, merged.maxThreads);
  }

  @Test
  void legacyMtFlagSetsOptimizerAndAutorouterThreads() {
    RouterSettings merged = mergeCli("-mt", "1");

    assertEquals(1, merged.getOptimizerMaxThreads());
    assertEquals(1, merged.getAutorouterMaxThreads());
  }

  @Test
  void legacyMtZeroDisablesTheOptimizer() {
    RouterSettings merged = mergeCli("-mt", "0");

    assertFalse(merged.getRunOptimizer());
    assertNull(merged.maxThreads);
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    assertEquals(expected, merged.getOptimizerMaxThreads());
    assertEquals(expected, merged.getAutorouterMaxThreads());
  }

  @Test
  void optimizerDisabledFlagLeavesMaxThreadsUnset() {
    RouterSettings merged = mergeCli("--router.optimizer.enabled=false");

    assertFalse(merged.getRunOptimizer());
    assertNull(merged.maxThreads);
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    assertEquals(expected, merged.getOptimizerMaxThreads());
    assertEquals(expected, merged.getAutorouterMaxThreads());
  }

  @Test
  void flatMaxThreadsZeroStillMeansAllProcessors() {
    RouterSettings merged = mergeCli("--router.max_threads=0");

    assertTrue(merged.getRunOptimizer());
    assertEquals(Runtime.getRuntime().availableProcessors(), merged.getOptimizerMaxThreads());
    assertEquals(Runtime.getRuntime().availableProcessors(), merged.getAutorouterMaxThreads());
  }

  @Test
  void legacyMtNegativeDisablesTheOptimizerAndKeepsLaterFlags() {
    RouterSettings merged = mergeCli("-mt", "-1", "-mp", "5");

    assertFalse(merged.getRunOptimizer());
    assertEquals(5, merged.autorouter.maxPasses);
  }

  @Test
  void flatEnvironmentVariableSetsOptimizerAndAutorouterThreads() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(Map.of("FREEROUTING__ROUTER__MAX_THREADS", "1"));

    RouterSettings merged = new SettingsMerger(new DefaultSettings(), env).merge();

    assertEquals(1, merged.getOptimizerMaxThreads());
    assertEquals(1, merged.getAutorouterMaxThreads());
  }

  @Test
  void nestedFlagWinsOverFlatFlagWhenWithinGlobalCeiling() {
    assumeTrue(Runtime.getRuntime().availableProcessors() >= 2);

    RouterSettings merged = mergeCli("--router.max_threads=2", "--router.optimizer.max_threads=1");

    assertEquals(1, merged.getOptimizerMaxThreads());
    assertEquals(2, merged.getAutorouterMaxThreads());
  }

  @Test
  void flatFlagFromHigherPrioritySourceOverridesLowerNestedValue() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(Map.of("FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS", "2"));

    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(),
                env,
                new CliSettings(new String[] {"--router.max_threads=1"}))
            .merge();

    assertEquals(1, merged.getOptimizerMaxThreads());
  }

  @Test
  void stageSpecificAutorouterMaxThreadsDoesNotAffectOptimizer() {
    assumeTrue(Runtime.getRuntime().availableProcessors() >= 2);

    RouterSettings merged = mergeCli("--router.autorouter.max_threads=1");
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    assertEquals(1, merged.getAutorouterMaxThreads());
    assertEquals(expected, merged.getOptimizerMaxThreads());
  }

  @Test
  void withoutThreadFlagsTheDefaultsAreUnchanged() {
    RouterSettings merged = mergeCli("--router.max_passes=5");
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    assertEquals(expected, merged.getOptimizerMaxThreads());
    assertEquals(expected, merged.getAutorouterMaxThreads());
  }
}
