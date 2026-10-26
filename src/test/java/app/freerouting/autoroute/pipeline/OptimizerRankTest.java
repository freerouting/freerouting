package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.OptimizerScoreSettings;
import app.freerouting.settings.OptimizerScoringVersion;
import app.freerouting.settings.OptimizerSettings;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

/**
 * Tests that the optimizer ranks its passes by the V2 optimizer score without its floor at zero
 * ({@link BoardStatistics#getOptimizerRank}). On a dense board the score read 0.00 before and after
 * every pass, so a better board could not beat it.
 */
public class OptimizerRankTest {

  private static RouterSettings v2Settings() {
    RouterSettings settings = new RouterSettings();
    settings.optimizer = new OptimizerSettings();
    settings.optimizerScoring = new OptimizerScoreSettings();
    settings.optimizerScoring.version = OptimizerScoringVersion.V2_LOWER_BOUND;
    return settings;
  }

  /** DerKnopf_led-ring's statistics at kicad-ad33ee4, before and after its optimizer stage. */
  private static BoardStatistics derKnopf(float lengthMm, int vias, int bends) {
    BoardStatistics stats = new BoardStatistics();
    stats.bounds.minTraceLengthMm = 489.61f;
    stats.bounds.minViaCount = 9;
    stats.bounds.minBendCount = 62;
    stats.difficulty.difficultyD = 156.0f;
    stats.traces.totalLengthMm = lengthMm;
    stats.vias.totalCount = vias;
    stats.bends.totalCount = bends;
    return stats;
  }

  @Test
  public void rankGoesBelowZeroWhereTheScoreStops() {
    RouterSettings settings = v2Settings();
    BoardStatistics before = derKnopf(538.23f, 48, 246);
    BoardStatistics after = derKnopf(508.98f, 40, 271);

    assertEquals(0f, before.getOptimizerScore(settings), 0.001f);
    assertEquals(0f, after.getOptimizerScore(settings), 0.001f);

    // 1000 - 1000 * 48.62 / 489.61 - 2000 * 39 / 156 - 500 * 184 / 156
    assertTrue(
        before.getOptimizerRank(settings) >= -189.5f
            && before.getOptimizerRank(settings) <= -188.5f);
    // 1000 - 1000 * 19.37 / 489.61 - 2000 * 31 / 156 - 500 * 209 / 156
    assertTrue(
        after.getOptimizerRank(settings) >= -107.5f && after.getOptimizerRank(settings) <= -106.5f);
    assertTrue(after.getOptimizerRank(settings) > before.getOptimizerRank(settings));
  }

  @Test
  public void rankIsTheScoreAboveZero() {
    RouterSettings settings = v2Settings();
    BoardStatistics stats = new BoardStatistics();
    stats.bounds.minTraceLengthMm = 10.0f;
    stats.bounds.minViaCount = 1;
    stats.bounds.minBendCount = 1;
    stats.traces.totalLengthMm = 15.0f;
    stats.vias.totalCount = 2;
    stats.bends.totalCount = 3;
    stats.difficulty.difficultyD = 10.0f;

    assertEquals(stats.getOptimizerScore(settings), stats.getOptimizerRank(settings), 0.001f);
    assertTrue(
        stats.getOptimizerRank(settings) >= 199.999f
            && stats.getOptimizerRank(settings) <= 200.001f);
  }

  @Test
  public void aBetterBoardBelowZeroIsAccepted() {
    RouterSettings settings = v2Settings();
    BoardStatistics before = derKnopf(538.23f, 48, 246);
    // Same vias, shorter traces: the pass the 0.00 score threw away.
    BoardStatistics shorter = derKnopf(520.0f, 48, 246);
    before.connections.incompleteCount = 1;
    before.clearanceViolations.totalCount = 0;
    shorter.connections.incompleteCount = 1;
    shorter.clearanceViolations.totalCount = 0;

    assertEquals(
        "OPTIMIZER_SCORE_NOT_IMPROVED",
        BatchOptimizer.optimizerCandidateRejectionReason(
            1,
            0,
            before.getOptimizerScore(settings),
            shorter,
            shorter.getOptimizerScore(settings)));
    assertNull(
        BatchOptimizer.optimizerCandidateRejectionReason(
            1, 0, before.getOptimizerRank(settings), shorter, shorter.getOptimizerRank(settings)));
    assertEquals(
        "OPTIMIZER_SCORE_NOT_IMPROVED",
        BatchOptimizer.optimizerCandidateRejectionReason(
            1, 0, shorter.getOptimizerRank(settings), before, before.getOptimizerRank(settings)));
  }

  @Test
  public void improvementAboveZeroIsAShareOfTheScore() {
    assertEquals(
        (double) (330f - 300f) / 300f * 100.0,
        BatchOptimizer.scoreImprovementPercent(300f, 330f),
        0.0001);
    assertEquals(
        (double) (290f - 300f) / 300f * 100.0,
        BatchOptimizer.scoreImprovementPercent(300f, 290f),
        0.0001);
  }

  @Test
  public void stopRuleReadsTheRankOnAFinishedBoard() {
    BatchOptimizer.StopRuleView stop = BatchOptimizer.stopRuleScores(-434.02f, -359.60f, false);
    assertEquals(-434.02f, stop.before(), 0.01f);
    assertEquals(-359.60f, stop.after(), 0.01f);
    assertTrue(stop.percent() >= 5.18 && stop.percent() <= 5.20);
  }

  @Test
  public void stopRuleReadsTheFlooredScoreWithConnectionsOpenAtOrBelowZero() {
    // DerKnopf_led-ring, 1 open: a polish gain below zero does not keep the optimizer from the
    // rounds.
    BatchOptimizer.StopRuleView stop = BatchOptimizer.stopRuleScores(-189.06f, -181.62f, true);
    assertEquals(0f, stop.before(), 0.001f);
    assertEquals(0f, stop.after(), 0.001f);
    assertEquals(0.0, stop.percent(), 0.001);

    // Rising above zero is still a rise, as it was from a 0.00 score.
    stop = BatchOptimizer.stopRuleScores(-48.49f, 8.77f, true);
    assertEquals(0f, stop.before(), 0.001f);
    assertEquals(8.77f, stop.after(), 0.001f);
    assertEquals(0.0, stop.percent(), 0.001);
  }

  @Test
  public void stopRuleAboveZeroIsUnchangedWithConnectionsOpen() {
    BatchOptimizer.StopRuleView stop = BatchOptimizer.stopRuleScores(130.85f, 177.87f, true);
    assertEquals(130.85f, stop.before(), 0.01f);
    assertEquals(177.87f, stop.after(), 0.01f);
    assertEquals((double) (177.87f - 130.85f) / 130.85f * 100.0, stop.percent(), 0.0001);
  }

  @Test
  public void improvementFromZeroOrBelowIsAShareOfTheExcess() {
    // -189 -> -107 removes 82 of the 1,189 points the score charges: 6.9%.
    assertTrue(
        BatchOptimizer.scoreImprovementPercent(-189f, -107f) >= 6.89
            && BatchOptimizer.scoreImprovementPercent(-189f, -107f) <= 6.91);
    assertTrue(
        BatchOptimizer.scoreImprovementPercent(0f, 10f) >= 0.999
            && BatchOptimizer.scoreImprovementPercent(0f, 10f) <= 1.001);
    assertTrue(BatchOptimizer.scoreImprovementPercent(-189f, -190f) < 0);
    assertEquals(0.0, BatchOptimizer.scoreImprovementPercent(-189f, -189f), 0.0001);
  }
}
