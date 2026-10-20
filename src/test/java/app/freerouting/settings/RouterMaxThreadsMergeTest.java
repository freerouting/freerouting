package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The flat {@code router.max_threads} (and its {@code -mt} alias) is documented as the thread cap
 * for both pools. These tests check that it reaches {@code optimizer.max_threads} through the
 * settings merger, not only through {@link RouterSettings#setMaxThreads}.
 */
class RouterMaxThreadsMergeTest {

  private static RouterSettings mergeCli(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  @Test
  void flatCliFlagSetsOptimizerThreads() {
    RouterSettings merged = mergeCli("--router.max_threads=1");

    assertEquals(1, merged.optimizer.maxThreads);
    assertEquals(1, merged.autorouter.maxThreads);
  }

  @Test
  void legacyMtFlagSetsOptimizerThreads() {
    RouterSettings merged = mergeCli("-mt", "1");

    assertEquals(1, merged.optimizer.maxThreads);
  }

  @Test
  void legacyMtZeroDisablesTheOptimizer() {
    RouterSettings merged = mergeCli("-mt", "0");

    assertFalse(merged.getRunOptimizer());
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    assertEquals(expected, merged.optimizer.maxThreads);
  }

  @Test
  void flatMaxThreadsZeroStillMeansAllProcessors() {
    RouterSettings merged = mergeCli("--router.max_threads=0");

    assertTrue(merged.getRunOptimizer());
    assertEquals(Runtime.getRuntime().availableProcessors(), merged.optimizer.maxThreads);
  }

  @Test
  void flatEnvironmentVariableSetsOptimizerThreads() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(Map.of("FREEROUTING__ROUTER__MAX_THREADS", "1"));

    RouterSettings merged = new SettingsMerger(new DefaultSettings(), env).merge();

    assertEquals(1, merged.optimizer.maxThreads);
  }

  @Test
  void nestedFlagWinsOverFlatFlagFromTheSameSource() {
    assumeTrue(Runtime.getRuntime().availableProcessors() >= 2);

    RouterSettings merged = mergeCli("--router.max_threads=2", "--router.optimizer.max_threads=1");

    assertEquals(1, merged.optimizer.maxThreads);
    assertEquals(2, merged.autorouter.maxThreads);
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

    assertEquals(1, merged.optimizer.maxThreads);
  }

  @Test
  void withoutThreadFlagsTheDefaultsAreUnchanged() {
    RouterSettings merged = mergeCli("--router.max_passes=5");
    int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    assertEquals(expected, merged.optimizer.maxThreads);
    assertEquals(expected, merged.autorouter.maxThreads);
  }
}
