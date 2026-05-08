package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterScoringSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BoardScoreBreakdown} and {@link ScoringWeightComparison}.
 *
 * <p>These tests do not load any DSN file; they construct {@link BoardStatistics} through
 * the public fields directly so that every assertion is fully deterministic.
 */
class ScoringWeightComparisonTest {

  // A minimal synthetic board:  10 total connections, 2 unrouted, 0 DRC violations,
  // 50 bends, 250.0 mm total trace, 15 vias.
  private static final int MAX_CONNECTIONS = 10;
  private static final int INCOMPLETE_CONNECTIONS = 2;
  private static final int CLEARANCE_VIOLATIONS = 0;
  private static final int BEND_COUNT = 50;
  private static final float TOTAL_TRACE_MM = 250.0f;
  private static final int VIA_COUNT = 15;

  private BoardStatistics stats;
  private RouterScoringSettings defaultWeights;

  @BeforeEach
  void setUp() {
    // Build board statistics by hand (no DSN file needed).
    stats = new BoardStatistics();
    stats.connections = new BoardStatisticsConnections();
    stats.connections.maximumCount = MAX_CONNECTIONS;
    stats.connections.incompleteCount = INCOMPLETE_CONNECTIONS;
    stats.clearanceViolations = new BoardStatisticsClearanceViolations();
    stats.clearanceViolations.totalCount = CLEARANCE_VIOLATIONS;
    stats.bends = new BoardStatisticsBends();
    stats.bends.totalCount = BEND_COUNT;
    stats.bends.ninetyDegreeCount = 0;
    stats.bends.fortyFiveDegreeCount = BEND_COUNT;
    stats.bends.otherAngleCount = 0;
    stats.traces = new BoardStatisticsTraces();
    stats.traces.totalLength = TOTAL_TRACE_MM;
    stats.traces.totalWeightedLength = TOTAL_TRACE_MM;
    stats.traces.totalCount = 8;
    stats.traces.totalSegmentCount = 8;
    stats.traces.averageLength = TOTAL_TRACE_MM / 8;
    stats.traces.totalHorizontalLength = TOTAL_TRACE_MM / 2;
    stats.traces.totalVerticalLength = TOTAL_TRACE_MM / 2;
    stats.traces.totalAngledLength = 0f;
    stats.vias = new BoardStatisticsVias();
    stats.vias.totalCount = VIA_COUNT;
    stats.vias.throughHoleCount = VIA_COUNT;
    stats.vias.blindCount = 0;
    stats.vias.buriedCount = 0;

    // Populate defaultWeights from the authoritative DefaultSettings constants.
    defaultWeights = new RouterScoringSettings();
    defaultWeights.unroutedNetPenalty = DefaultSettings.DEFAULT_UNROUTED_NET_PENALTY;
    defaultWeights.clearanceViolationPenalty = DefaultSettings.DEFAULT_CLEARANCE_VIOLATION_PENALTY;
    defaultWeights.bendPenalty = DefaultSettings.DEFAULT_BEND_PENALTY;
    defaultWeights.defaultPreferredDirectionTraceCost = DefaultSettings.DEFAULT_PREFERRED_DIRECTION_TRACE_COST;
    defaultWeights.defaultUndesiredDirectionTraceCost = DefaultSettings.DEFAULT_UNDESIRED_DIRECTION_TRACE_COST;
    defaultWeights.viaCosts = DefaultSettings.DEFAULT_VIA_COSTS;
    defaultWeights.planeViaCosts = DefaultSettings.DEFAULT_PLANE_VIA_COSTS;
    defaultWeights.startRipupCosts = DefaultSettings.DEFAULT_START_RIPUP_COSTS;
  }

  // -------------------------------------------------------------------------
  // BoardScoreBreakdown tests
  // -------------------------------------------------------------------------

  @Test
  void breakdownComputesMaximumScoreCorrectly() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);
    float expected = MAX_CONNECTIONS * DefaultSettings.DEFAULT_UNROUTED_NET_PENALTY;
    assertEquals(expected, bd.maximumScore, 0.01f,
        "maximumScore must equal maxConnections × unroutedNetPenalty");
  }

  @Test
  void breakdownComputesPenaltiesCorrectly() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);

    float expectedUnrouted = INCOMPLETE_CONNECTIONS * DefaultSettings.DEFAULT_UNROUTED_NET_PENALTY;
    assertEquals(expectedUnrouted, bd.unroutedConnectionsPenalty, 0.01f);

    float expectedViolations = CLEARANCE_VIOLATIONS * DefaultSettings.DEFAULT_CLEARANCE_VIOLATION_PENALTY;
    assertEquals(expectedViolations, bd.clearanceViolationsPenalty, 0.01f);

    float expectedBends = BEND_COUNT * DefaultSettings.DEFAULT_BEND_PENALTY;
    assertEquals(expectedBends, bd.bendsPenalty, 0.01f);
  }

  @Test
  void breakdownComputesCostsCorrectly() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);

    float expectedTraceCost = (float) (TOTAL_TRACE_MM * DefaultSettings.DEFAULT_PREFERRED_DIRECTION_TRACE_COST);
    assertEquals(expectedTraceCost, bd.traceLengthCost, 0.01f);

    float expectedViaCost = VIA_COUNT * DefaultSettings.DEFAULT_VIA_COSTS;
    assertEquals(expectedViaCost, bd.viasCost, 0.01f);
  }

  @Test
  void breakdownRawScoreEqualsFormulaResult() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);

    float expectedRaw = bd.maximumScore - bd.totalPenalties - bd.totalCosts;
    assertEquals(expectedRaw, bd.rawScore, 0.01f);
  }

  @Test
  void breakdownNormalizedScoreIsInRange() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);
    assertTrue(bd.normalizedScore >= 0f, "normalizedScore must be >= 0");
    assertTrue(bd.normalizedScore <= 1000f, "normalizedScore must be <= 1000");
  }

  @Test
  void breakdownNormalizedScoreForPerfectBoard() {
    // A board with no incomplete connections, no violations, no bends, no traces, no vias
    // should produce the maximum normalised score of 1000.
    stats.connections.incompleteCount = 0;
    stats.clearanceViolations.totalCount = 0;
    stats.bends.totalCount = 0;
    stats.traces.totalLength = 0f;
    stats.vias.totalCount = 0;

    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);
    assertEquals(1000f, bd.normalizedScore, 0.01f,
        "A board with no penalties and no costs must score 1000/1000");
  }

  @Test
  void breakdownThrowsOnNullStats() {
    assertThrows(NullPointerException.class,
        () -> BoardScoreBreakdown.of(null, defaultWeights));
  }

  @Test
  void breakdownThrowsOnNullWeights() {
    assertThrows(NullPointerException.class,
        () -> BoardScoreBreakdown.of(stats, null));
  }

  @Test
  void breakdownThrowsWhenRequiredWeightIsNull() {
    RouterScoringSettings incomplete = defaultWeights.clone();
    incomplete.unroutedNetPenalty = null;
    assertThrows(IllegalArgumentException.class,
        () -> BoardScoreBreakdown.of(stats, incomplete));
  }

  @Test
  void toSummaryStringContainsKeyFigures() {
    BoardScoreBreakdown bd = BoardScoreBreakdown.of(stats, defaultWeights);
    String summary = bd.toSummaryString();
    assertNotNull(summary);
    assertTrue(summary.contains("score="), "Summary must contain 'score='");
    assertTrue(summary.contains("unrouted="), "Summary must contain 'unrouted='");
    assertTrue(summary.contains("vias="), "Summary must contain 'vias='");
  }

  // -------------------------------------------------------------------------
  // ScoringWeightComparison tests
  // -------------------------------------------------------------------------

  @Test
  void comparisonIdenticalWeightsProducesZeroDeltas() {
    ScoringWeightComparison.Result result =
        ScoringWeightComparison.compare(stats, defaultWeights, defaultWeights);

    assertEquals(0f, result.rawScoreDelta, 0.01f,
        "Identical weights must produce zero raw-score delta");
    assertEquals(0f, result.normalizedScoreDelta, 0.01f,
        "Identical weights must produce zero normalised-score delta");
    assertFalse(result.isCandidateBetter(),
        "Neither configuration should be 'better' when they are equal");
  }

  @Test
  void higherUnroutedPenaltyMakesUnroutedBoardScoreWorse() {
    // Candidate doubles the unrouted penalty.
    RouterScoringSettings candidate = defaultWeights.clone();
    candidate.unroutedNetPenalty = defaultWeights.unroutedNetPenalty * 2;

    ScoringWeightComparison.Result result =
        ScoringWeightComparison.compare(stats, defaultWeights, candidate);

    // The maximum also scales with the penalty, so normalised score may or may not improve.
    // What must hold: candidate's raw unrouted-penalty contribution is exactly double.
    assertEquals(
        defaultWeights.unroutedNetPenalty * INCOMPLETE_CONNECTIONS,
        result.scoreA.unroutedConnectionsPenalty, 0.01f);
    assertEquals(
        candidate.unroutedNetPenalty * INCOMPLETE_CONNECTIONS,
        result.scoreB.unroutedConnectionsPenalty, 0.01f);
  }

  @Test
  void lowerViaCostMakesViaCostTermSmaller() {
    RouterScoringSettings candidate = defaultWeights.clone();
    candidate.viaCosts = defaultWeights.viaCosts / 2;

    ScoringWeightComparison.Result result =
        ScoringWeightComparison.compare(stats, defaultWeights, candidate);

    assertTrue(result.viasCostDelta < 0,
        "Halving viaCosts must reduce the via-cost contribution (negative delta)");
    assertEquals(
        -(float) (VIA_COUNT * (defaultWeights.viaCosts - candidate.viaCosts)),
        result.viasCostDelta, 0.01f);
  }

  @Test
  void lowerViaCostImprovesCandidateScore() {
    // With fewer unrouted connections the via cost dominates; lowering it should raise the score.
    RouterScoringSettings candidate = defaultWeights.clone();
    candidate.viaCosts = 1; // essentially free

    ScoringWeightComparison.Result result =
        ScoringWeightComparison.compare(stats, defaultWeights, candidate);

    assertTrue(result.isCandidateBetter(),
        "Near-zero via cost should produce a higher normalised score for this board");
  }

  @Test
  void routingOneMoreConnectionNotedInBreakdown() {
    // Simulate what happens when the router connects one more net: incompleteCount drops by 1.
    BoardStatistics statsAfterRouting = new BoardStatistics();
    statsAfterRouting.connections = new BoardStatisticsConnections();
    statsAfterRouting.connections.maximumCount = MAX_CONNECTIONS;
    statsAfterRouting.connections.incompleteCount = INCOMPLETE_CONNECTIONS - 1; // one more routed
    statsAfterRouting.clearanceViolations = new BoardStatisticsClearanceViolations();
    statsAfterRouting.clearanceViolations.totalCount = 0;
    statsAfterRouting.bends = new BoardStatisticsBends();
    statsAfterRouting.bends.totalCount = BEND_COUNT + 5;         // new wire adds bends
    statsAfterRouting.traces = new BoardStatisticsTraces();
    statsAfterRouting.traces.totalLength = TOTAL_TRACE_MM + 80f; // new wire is 80 mm
    statsAfterRouting.vias = new BoardStatisticsVias();
    statsAfterRouting.vias.totalCount = VIA_COUNT + 2;           // two more vias

    BoardScoreBreakdown before = BoardScoreBreakdown.of(stats, defaultWeights);
    BoardScoreBreakdown after  = BoardScoreBreakdown.of(statsAfterRouting, defaultWeights);

    // Connecting a net should always improve the normalised score when the penalty is dominant.
    assertTrue(after.normalizedScore > before.normalizedScore,
        "Successfully routing one more connection must improve the normalised score. "
            + "before=" + before.normalizedScore + " after=" + after.normalizedScore);
  }

  @Test
  void reportStringContainsVerdict() {
    RouterScoringSettings candidate = defaultWeights.clone();
    candidate.viaCosts = 1;

    ScoringWeightComparison.Result result =
        ScoringWeightComparison.compare(stats, defaultWeights, candidate);

    String report = result.toReportString();
    assertNotNull(report);
    assertTrue(report.contains("Verdict:"), "Report must contain a 'Verdict:' line");
    assertTrue(report.contains("Normalised score"), "Report must display normalised scores");
  }
}