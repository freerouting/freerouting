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
    assertFalse(BatchOptimizer.shouldStopAfterConsecutivePassesWithoutGain(merged.optimizer, 1));
    assertTrue(BatchOptimizer.shouldStopAfterConsecutivePassesWithoutGain(merged.optimizer, 2));
  }

  @Test
  void zeroImprovementThresholdStopsExplicitlyAfterTwoNoGainPasses() {
    RouterSettings merged =
        mergeCli(
            "--router.optimizer.keep_increased_ripup_costs=true",
            "--router.optimizer.optimization_improvement_threshold=0.0");

    assertFalse(BatchOptimizer.shouldStopAfterConsecutivePassesWithoutGain(merged.optimizer, 1));
    assertTrue(BatchOptimizer.shouldStopAfterConsecutivePassesWithoutGain(merged.optimizer, 2));
  }

  @Test
  void candidateGainEvaluatesVetoAndScoreImprovement() {
    // Vetoed candidates count as no-gain passes even if candidate score was higher
    assertFalse(BatchOptimizer.hasPassGain("CONNECTIVITY_REGRESSION", 500.0f, 900.0f));
    assertFalse(BatchOptimizer.hasPassGain("DRC_COUNT_REGRESSION", 500.0f, 900.0f));

    // Accepted candidate with higher score counts as gain
    assertTrue(BatchOptimizer.hasPassGain(null, 500.0f, 600.0f));

    // Unchanged or lower score counts as no-gain
    assertFalse(BatchOptimizer.hasPassGain(null, 500.0f, 500.0f));
    assertFalse(BatchOptimizer.hasPassGain(null, 500.0f, 400.0f));
  }

  @Test
  void stopRuleScoresUsesIncumbentScoreOnVeto() {
    // When candidate is vetoed, incumbent score is retained (e.g. 500.0f before and after)
    float scoreBefore = 500.0f;
    float incumbentScore = 500.0f;
    BatchOptimizer.StopRuleView stopView =
        BatchOptimizer.stopRuleScores(scoreBefore, incumbentScore, false);

    assertTrue(stopView.percent() <= 0.0);
  }
}
