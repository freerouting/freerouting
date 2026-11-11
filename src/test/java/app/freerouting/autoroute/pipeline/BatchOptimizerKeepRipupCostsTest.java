package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.Test;

/**
 * Verifies what the optimizer does with the increased ripup costs of the start after a pass without
 * gain: dropped by default, kept with {@code optimizer.keep_increased_ripup_costs}.
 */
class BatchOptimizerKeepRipupCostsTest {

  private static RouterSettings mergeCli(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  @Test
  void increasedRipupCostsAreDroppedByDefault() {
    RouterSettings merged = mergeCli();

    assertFalse(merged.optimizer.keepIncreasedRipupCosts);
    assertFalse(BatchOptimizer.increasedRipupCostsAfterPassWithoutGain(merged.optimizer));
  }

  @Test
  void cliFlagKeepsIncreasedRipupCosts() {
    RouterSettings merged = mergeCli("--router.optimizer.keep_increased_ripup_costs=true");

    assertTrue(merged.optimizer.keepIncreasedRipupCosts);
    assertTrue(BatchOptimizer.increasedRipupCostsAfterPassWithoutGain(merged.optimizer));
  }

  @Test
  void missingOptimizerSettingsDropIncreasedRipupCosts() {
    assertFalse(BatchOptimizer.increasedRipupCostsAfterPassWithoutGain(null));
  }

  @Test
  void defaultGoesOnOnceWithNormalRipupCosts() {
    RouterSettings merged = mergeCli();

    assertTrue(BatchOptimizer.continueAfterPassWithoutGain(merged.optimizer, 1));
  }

  @Test
  void keptRipupCostsStopAfterTwoPassesWithoutGainInARow() {
    RouterSettings merged = mergeCli("--router.optimizer.keep_increased_ripup_costs=true");

    assertTrue(BatchOptimizer.continueAfterPassWithoutGain(merged.optimizer, 1));
    assertFalse(BatchOptimizer.continueAfterPassWithoutGain(merged.optimizer, 2));
  }
}
