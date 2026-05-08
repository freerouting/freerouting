package app.freerouting.core.scoring;

import app.freerouting.settings.RouterScoringSettings;

/**
 * Evaluates and compares two {@link RouterScoringSettings} configurations against the same
 * {@link BoardStatistics}, producing a human-readable report and a structured
 * {@link Result}.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * RouterScoringSettings current = settingsMerger.merge().scoring;
 *
 * RouterScoringSettings candidate = current.clone();
 * candidate.unroutedNetPenalty = 10_000f;   // proposed change
 * candidate.viaCosts           = 30;         // proposed change
 *
 * ScoringWeightComparison.Result result =
 *     ScoringWeightComparison.compare(boardStats, current, candidate);
 *
 * FRLogger.info(result.toReportString());
 * }</pre>
 *
 * <p>This class is stateless and all methods are static; it is not intended to be instantiated.
 */
public final class ScoringWeightComparison {

  private ScoringWeightComparison() {
    // utility class
  }

  /**
   * Evaluates {@code stats} under both weight configurations and returns a {@link Result} that
   * holds both breakdowns and the delta between them.
   *
   * @param stats      board statistics to evaluate (lengths must be in mm)
   * @param weightsA   first weight configuration  (labelled "A" / "baseline" in reports)
   * @param weightsB   second weight configuration (labelled "B" / "candidate" in reports)
   * @return a populated {@link Result}
   */
  public static Result compare(
      BoardStatistics stats,
      RouterScoringSettings weightsA,
      RouterScoringSettings weightsB) {

    BoardScoreBreakdown breakdownA = BoardScoreBreakdown.of(stats, weightsA);
    BoardScoreBreakdown breakdownB = BoardScoreBreakdown.of(stats, weightsB);
    return new Result(breakdownA, breakdownB);
  }

  // ---------------------------------------------------------------------------
  // Result
  // ---------------------------------------------------------------------------

  /**
   * Holds the outcome of comparing two weight configurations against the same board.
   *
   * <p>Positive deltas mean configuration B produces a higher (better) score than A.
   */
  public static final class Result {

    /** Score breakdown under configuration A (baseline). */
    public final BoardScoreBreakdown scoreA;

    /** Score breakdown under configuration B (candidate). */
    public final BoardScoreBreakdown scoreB;

    /** {@code scoreB.rawScore - scoreA.rawScore}; positive means B is better. */
    public final float rawScoreDelta;

    /** {@code scoreB.normalizedScore - scoreA.normalizedScore}; positive means B is better. */
    public final float normalizedScoreDelta;

    /** {@code scoreB.unroutedConnectionsPenalty - scoreA.unroutedConnectionsPenalty}. */
    public final float unroutedPenaltyDelta;

    /** {@code scoreB.clearanceViolationsPenalty - scoreA.clearanceViolationsPenalty}. */
    public final float clearancePenaltyDelta;

    /** {@code scoreB.bendsPenalty - scoreA.bendsPenalty}. */
    public final float bendsPenaltyDelta;

    /** {@code scoreB.traceLengthCost - scoreA.traceLengthCost}. */
    public final float traceLengthCostDelta;

    /** {@code scoreB.viasCost - scoreA.viasCost}. */
    public final float viasCostDelta;

    private Result(BoardScoreBreakdown a, BoardScoreBreakdown b) {
      this.scoreA = a;
      this.scoreB = b;
      this.rawScoreDelta = b.rawScore - a.rawScore;
      this.normalizedScoreDelta = b.normalizedScore - a.normalizedScore;
      this.unroutedPenaltyDelta = b.unroutedConnectionsPenalty - a.unroutedConnectionsPenalty;
      this.clearancePenaltyDelta = b.clearanceViolationsPenalty - a.clearanceViolationsPenalty;
      this.bendsPenaltyDelta = b.bendsPenalty - a.bendsPenalty;
      this.traceLengthCostDelta = b.traceLengthCost - a.traceLengthCost;
      this.viasCostDelta = b.viasCost - a.viasCost;
    }

    /**
     * Returns whether configuration B scores strictly higher than A on the normalised scale.
     */
    public boolean isCandidateBetter() {
      return normalizedScoreDelta > 0;
    }

    /**
     * Builds a multi-line, tabular comparison report suitable for logging.
     *
     * <p>Example output:
     * <pre>
     * ╔══ Scoring Weight Comparison ══════════════════════════════════════╗
     * ║ Board:  12 connections, 2 unrouted, 0 violations, 340 bends
     * ║         total trace 187.4 mm, 23 vias
     * ╠══ Component breakdown ═════════════════════════════════════════════╣
     * ║                           A (baseline)   B (candidate)      Delta
     * ║ Maximum score               48000.0        80000.0         +32000.0
     * ║ Unrouted penalty             8000.0        20000.0         +12000.0
     * ║ Clearance penalty               0.0            0.0              0.0
     * ║ Bend penalty                 3400.0         3400.0              0.0
     * ║ Trace length cost             187.4          150.0             -37.4
     * ║ Via cost                     1150.0          690.0            -460.0
     * ╠════════════════════════════════════════════════════════════════════╣
     * ║ Raw score                   35262.6        56760.0         +21497.4
     * ║ Normalised score (0-1000)     734.6          709.5             -25.1
     * ╚═══════════════════════════════════════════════════════════════════╝
     * Verdict: A is better (by 25.1 normalised points)
     * </pre>
     */
    public String toReportString() {
      BoardScoreBreakdown a = scoreA;
      BoardScoreBreakdown b = scoreB;

      String header = String.format(
          "%n╔══ Scoring Weight Comparison ══════════════════════════════════════╗"
              + "%n║ Board:  %d connections, %d unrouted, %d violations, %d bends"
              + "%n║         total trace %.1f mm, %d vias",
          a.maxConnections, a.incompleteConnections,
          a.clearanceViolations, a.bendCount,
          a.totalTraceLengthMm, a.viaCount);

      String tableHeader = String.format(
          "%n╠══ Component breakdown ═════════════════════════════════════════════╣"
              + "%n║ %-34s %12s  %12s  %10s",
          "", "A (baseline)", "B (candidate)", "Delta");

      String rows = String.format(
          "%n║ %-34s %12.1f  %12.1f  %+10.1f"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.1f → %.1f)"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.2f → %.2f)"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)",
          "Maximum score",
          a.maximumScore, b.maximumScore, b.maximumScore - a.maximumScore,
          "Unrouted penalty",
          a.unroutedConnectionsPenalty, b.unroutedConnectionsPenalty, unroutedPenaltyDelta,
          a.weights.unroutedNetPenalty, b.weights.unroutedNetPenalty,
          "Clearance penalty",
          a.clearanceViolationsPenalty, b.clearanceViolationsPenalty, clearancePenaltyDelta,
          a.weights.clearanceViolationPenalty, b.weights.clearanceViolationPenalty,
          "Bend penalty",
          a.bendsPenalty, b.bendsPenalty, bendsPenaltyDelta,
          a.weights.bendPenalty, b.weights.bendPenalty,
          "Trace length cost",
          a.traceLengthCost, b.traceLengthCost, traceLengthCostDelta,
          a.weights.defaultPreferredDirectionTraceCost, b.weights.defaultPreferredDirectionTraceCost,
          "Via cost",
          a.viasCost, b.viasCost, viasCostDelta,
          (double) a.weights.viaCosts, (double) b.weights.viaCosts);

      String totalsAndScores = String.format(
          "%n╠════════════════════════════════════════════════════════════════════╣"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f"
              + "%n║ %-34s %12.1f  %12.1f  %+10.1f",
          "Raw score", a.rawScore, b.rawScore, rawScoreDelta,
          "Normalised score (0–1000)", a.normalizedScore, b.normalizedScore, normalizedScoreDelta);

      String footer = "%n╚═══════════════════════════════════════════════════════════════════╝";

      String verdict;
      if (Math.abs(normalizedScoreDelta) < 0.01f) {
        verdict = "Verdict: A and B produce identical scores for this board.";
      } else if (isCandidateBetter()) {
        verdict = String.format("Verdict: B (candidate) is better by %.1f normalised points.", normalizedScoreDelta);
      } else {
        verdict = String.format("Verdict: A (baseline) is better by %.1f normalised points.", -normalizedScoreDelta);
      }

      return header + tableHeader + rows + totalsAndScores + String.format(footer) + "\n" + verdict;
    }
  }
}