package app.freerouting.core;

import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.core.scoring.RoutingEta;
import app.freerouting.core.scoring.RoutingEta.Confidence;
import app.freerouting.logger.FRLogger;

/**
 * Calculates and maintains an ETA for the routing job.
 *
 * <h2>Key design decisions</h2>
 * <ul>
 *   <li><b>Continuous-time regression, not pass-endpoint regression.</b> Every update() call
 *       contributes a sample of (elapsedSeconds, ln(remaining)) to a rolling window. This gives
 *       real signal mid-pass, including during a long first pass.</li>
 *   <li><b>Normalized score as the progress signal.</b> Uses BoardStatistics.getNormalizedScore()
 *       which captures ALL quality factors: unrouted connections, clearance violations, trace
 *       bends, trace length, and via count. This naturally handles ripup/reroute non-monotonicity
 *       since the score only improves when the board genuinely gets better.</li>
 *   <li><b>R²-gated confidence, with a progress-only display mode below threshold.</b> A weak
 *       regression fit produces a progress string instead of a falsely confident time estimate.</li>
 *   <li><b>Statistical prediction band</b> from the regression's own residual standard error.</li>
 *   <li><b>Display-side hysteresis</b> decoupled from the model: increases require confirmation,
 *       decreases shown immediately.</li>
 *   <li><b>Fanout shows progress only</b> (no time estimate) since it's typically too fast to
 *       estimate reliably.</li>
 *   <li><b>Pass count hidden from progress text</b> to avoid misleading "Pass 1/100" displays.</li>
 * </ul>
 */
public class RoutingEtaCalculator {

  // ---------- Regression window ----------
  private static final int REGRESSION_WINDOW_SIZE = 24;
  private static final double MIN_SAMPLE_INTERVAL_SECONDS = 1.5;

  // ---------- Confidence / display gating ----------
  private static final double R_SQUARED_LOW_THRESHOLD = 0.5;
  private static final double R_SQUARED_HIGH_THRESHOLD = 0.85;
  private static final int MIN_SAMPLES_FOR_LOW_CONFIDENCE = 4;
  private static final int MIN_SAMPLES_FOR_MEDIUM_CONFIDENCE = 8;
  private static final int MIN_SAMPLES_FOR_HIGH_CONFIDENCE = 14;

  // ---------- Display hysteresis ----------
  private static final int HYSTERESIS_CONFIRM_COUNT = 3;
  private static final double HYSTERESIS_BYPASS_RATIO = 2.0;

  // ---------- EWMA smoothing constants (fanout/optimizer only; autoroute uses regression) ----------
  private static final double EWMA_PASS_DURATION = 0.3;
  private static final double EWMA_OPTIMIZER = 0.5;
  private static final double EWMA_FANOUT = 0.3;

  // Phase identifiers
  private static final String PHASE_IDLE = "idle";
  private static final String PHASE_FANOUT = "fanout";
  private static final String PHASE_AUTOROUTE = "autoroute";
  private static final String PHASE_OPTIMIZER = "optimizer";
  private static final String PHASE_COMPLETED = "completed";
  private static final String PHASE_STOPPED = "stopped";

  // ---------- Bound constants ----------
  private static final double MAX_ACCEPTABLE_ETA_SECONDS = 7200; // 2h cap
  private static final int DEFAULT_MAX_OPTIMIZER_PASSES = 10;
  private static final int STUCK_NET_FAILURE_THRESHOLD = 5;

  // "Effectively done" threshold: solving the regression for when the remaining
  // value reaches this (rather than exactly 0, which is -infinity in log space)
  // gives a finite, well-defined forecast time.
  private static final double COMPLETION_FRACTION_EPSILON_ITEMS = 0.5;

  // ---------- State ----------
  private String currentPhase = PHASE_IDLE;
  private int autoroutePassesCompleted = 0;
  private int optimizerPassesCompleted = 0;
  private double totalElapsedSeconds = 0;

  private double ewmaPassDuration = -1;          // steady-state (pass 2+) only
  private double firstPassDurationSeconds = -1;  // tracked separately from steady-state EWMA
  private double ewmaOptimizerPassDuration = -1;
  private double ewmaFanoutItemsPerSecond = -1;

  private int lastKnownIncompleteCount = 0;
  private int lastKnownMaximumCount = 0;
  private int totalConnectableItems = 0; // fallback denominator if BoardStatistics unavailable
  private double phaseStartTimeSeconds;
  private int maxPasses = 100;
  private int maxOptimizerPasses = DEFAULT_MAX_OPTIMIZER_PASSES;
  private int threadCount = 1;

  // Continuous-time regression window: (elapsedSeconds, ln(remaining))
  private final double[] regressionX = new double[REGRESSION_WINDOW_SIZE];
  private final double[] regressionY = new double[REGRESSION_WINDOW_SIZE];
  private int regressionIndex = 0;
  private int regressionCount = 0;
  private double lastSampleElapsedSeconds = -1;

  // Cached regression fit, recomputed each time a sample is added
  private double fitSlope = Double.NaN;
  private double fitIntercept = Double.NaN;
  private double fitRSquared = 0;
  private double fitResidualStdError = 0;
  private double fitMeanX = 0;
  private double fitSumSqX = 0;

  // Stuck-net tracking, fed externally from failureLog aggregation
  private int stuckNetCount = 0;

  // Stagnation tracking, fed from BatchAutorouter's own counters
  private int consecutiveNoImprovementPasses = 0;
  private int stagnationPassLimit = 10;

  // Display-side hysteresis state (independent of the underlying model)
  private double lastDisplayedEtaSeconds = -1;
  private int consecutiveWorseReadings = 0;

  // Frozen state
  private boolean frozen = false;
  private RoutingEta frozenEta = null;
  private boolean started = false;

  public RoutingEtaCalculator() {
  }

  /** Must be called when routing begins. */
  public void startRouting() {
    reset();
    this.started = true;
    this.phaseStartTimeSeconds = currentTimeSeconds();
    this.frozen = false;
    this.frozenEta = null;
    FRLogger.debug("RoutingEtaCalculator: started");
  }

  /**
   * Call whenever a different board is loaded, including before routing starts on it.
   * Without this, a previous board's frozen "Completed"/"Stopped" state (or stale display
   * hysteresis) persists on screen since nothing else clears it until Route is pressed again.
   */
  public void notifyBoardChanged() {
    FRLogger.debug("RoutingEtaCalculator: board changed, clearing state");
    reset();
  }

  public void setTotalConnectableItems(int total) {
    this.totalConnectableItems = total > 0 ? total : 0;
  }

  public void setMaxPasses(int maxPasses) {
    this.maxPasses = maxPasses > 0 ? maxPasses : 100;
  }

  public void setMaxOptimizerPasses(int maxOptimizerPasses) {
    this.maxOptimizerPasses = maxOptimizerPasses > 0 ? maxOptimizerPasses : DEFAULT_MAX_OPTIMIZER_PASSES;
  }

  public void setThreadCount(int threadCount) {
    this.threadCount = threadCount > 0 ? threadCount : 1;
  }

  public void setStagnationPassLimit(int stagnationPassLimit) {
    this.stagnationPassLimit = stagnationPassLimit > 0 ? stagnationPassLimit : 10;
  }

  /** Call once per pass with BatchAutorouter's consecutiveNoImprovementPasses. */
  public void updateStagnation(int consecutiveNoImprovementPasses) {
    this.consecutiveNoImprovementPasses = Math.max(0, consecutiveNoImprovementPasses);
  }

  /**
   * Call whenever the set of DRC-blocked / currently-unroutable nets changes (driven by
   * board.failureLog: a net with a stable failure reason for STUCK_NET_FAILURE_THRESHOLD+
   * consecutive passes). Excluding these from the completion target stops the model from
   * chasing a 100% that is not currently achievable.
   */
  public void updateStuckNetCount(int stuckNetCount) {
    this.stuckNetCount = Math.max(0, stuckNetCount);
  }

  // ======================== Phase lifecycle ========================

  public void onFanoutStarted() {
    if (frozen || !started) return;
    FRLogger.debug("RoutingEtaCalculator: fanout started");
    this.currentPhase = PHASE_FANOUT;
    this.phaseStartTimeSeconds = currentTimeSeconds();
    this.ewmaFanoutItemsPerSecond = -1;
  }

  public void onAutorouteStarted() {
    if (frozen || !started) return;
    FRLogger.debug("RoutingEtaCalculator: autoroute started");
    this.currentPhase = PHASE_AUTOROUTE;
    this.phaseStartTimeSeconds = currentTimeSeconds();
    this.ewmaPassDuration = -1;
    this.firstPassDurationSeconds = -1;
    resetRegressionWindow();
    this.consecutiveNoImprovementPasses = 0;
    this.stuckNetCount = 0;
    this.lastDisplayedEtaSeconds = -1;
    this.consecutiveWorseReadings = 0;
  }

  public void onOptimizerStarted() {
    if (frozen || !started) return;
    FRLogger.debug("RoutingEtaCalculator: optimizer started");
    this.currentPhase = PHASE_OPTIMIZER;
    this.phaseStartTimeSeconds = currentTimeSeconds();
    this.optimizerPassesCompleted = 0;
  }

  public void onRoutingStopped() {
    onRoutingStopped(null);
  }

  public void onRoutingStopped(String reason) {
    if (frozen) return;
    FRLogger.debug("RoutingEtaCalculator: routing stopped, freezing" + (reason != null ? " (" + reason + ")" : ""));
    this.frozen = true;
    this.frozenEta = new RoutingEta(0, Confidence.HIGH, 0,
        PHASE_STOPPED, totalElapsedSeconds, lastKnownIncompleteCount, reason);
    this.currentPhase = PHASE_STOPPED;
  }

  public void onRoutingCompleted() {
    if (frozen) return;
    FRLogger.debug("RoutingEtaCalculator: routing completed, freezing");
    this.frozen = true;
    this.frozenEta = new RoutingEta(0, Confidence.HIGH, 0,
        PHASE_COMPLETED, totalElapsedSeconds, 0);
    this.currentPhase = PHASE_COMPLETED;
  }

  /**
   * Called when the board is restored to an earlier (better-scoring) version. This is a
   * discontinuity in the underlying signal, not noise on a continuous trend, so the regression
   * window is fully cleared rather than down-weighted — old samples are from a different curve
   * and would corrupt the new fit if left in the window.
   */
  public void onBoardRestored() {
    if (frozen) return;
    FRLogger.debug("RoutingEtaCalculator: board restored, resetting regression window");
    resetRegressionWindow();
  }

  // ======================== Periodic updates ========================

  /** Called on every board update event during routing. */
  public void update(double elapsedSeconds, RouterCounters counters, BoardStatistics stats) {
    if (frozen || !started) return;
    this.totalElapsedSeconds = elapsedSeconds;

    int incompleteCount = (stats != null) ? stats.connections.incompleteCount : 0;
    int maximumCount = (stats != null) ? stats.connections.maximumCount : 0;
    this.lastKnownIncompleteCount = incompleteCount;
    this.lastKnownMaximumCount = maximumCount;

    // Cross-check pass count against RouterCounters, in case onAutoroutePassCompleted() was
    // missed on some exit path. Only ever advances, never regresses, to avoid instability if
    // counters arrive slightly out of order.
    if (counters != null && counters.passCount != null && counters.passCount > autoroutePassesCompleted
        && PHASE_AUTOROUTE.equals(currentPhase)) {
      autoroutePassesCompleted = counters.passCount;
    }

    if (PHASE_FANOUT.equals(currentPhase)) {
      updateFanout(counters);
    } else if (PHASE_OPTIMIZER.equals(currentPhase)) {
      // Optimizer: tracked via onOptimizerPassCompleted, no per-item progress
    } else if (PHASE_AUTOROUTE.equals(currentPhase)) {
      // Completion fraction, not full normalized score: calculateScore() includes
      // trace-length and via-count cost terms that are nonzero on any real routed
      // board, so the score asymptotes below its maximum even at 100% completion.
      // Regressing toward that ceiling means the model chases a target it can
      // never reach. Completion fraction reaches its target (near 0) for real.
      // getNormalizedScore(null) also NPEs: getMaximumScore() dereferences
      // scoringSettings.unroutedNetPenalty unconditionally.
      addRegressionSample(elapsedSeconds, effectiveIncompleteCount(incompleteCount), maximumCount);
    }
  }

  /** Called at the end of each autoroute pass. */
  public void onAutoroutePassCompleted(int passNumber, int incompleteCount, long passDurationMs,
                                       int rippedCount, int routedCount) {
    if (frozen || !started) return;
    this.autoroutePassesCompleted = Math.max(this.autoroutePassesCompleted, passNumber);
    double passDurationSeconds = passDurationMs / 1000.0;

    // First pass tracked separately: structurally different cost (larger search space, no
    // prior routing to guide ripup) and must not contaminate the steady-state duration used
    // for the hard budget ceiling.
    if (passNumber <= 1) {
      firstPassDurationSeconds = passDurationSeconds;
    } else {
      if (ewmaPassDuration < 0) {
        ewmaPassDuration = passDurationSeconds;
      } else {
        ewmaPassDuration = EWMA_PASS_DURATION * passDurationSeconds
            + (1 - EWMA_PASS_DURATION) * ewmaPassDuration;
      }
    }

    // Defensive freeze: if this pass exhausted the pass budget, freeze now rather than trust
    // that BatchAutorouter calls onRoutingStopped() on this specific exit path. Reaching
    // maxPasses is a distinct termination case from full completion, user-stop, and
    // stagnation-stop, and is easy to miss when those are wired at separate break sites.
    if (autoroutePassesCompleted >= maxPasses) {
      if (incompleteCount == 0) {
        onRoutingCompleted();
      } else {
        onRoutingStopped("max passes reached");
      }
    }
  }

  public void onOptimizerPassCompleted(int passNumber, long passDurationMs) {
    if (frozen || !started) return;
    this.optimizerPassesCompleted = passNumber;
    double seconds = passDurationMs / 1000.0;
    if (ewmaOptimizerPassDuration < 0) {
      ewmaOptimizerPassDuration = seconds;
    } else {
      ewmaOptimizerPassDuration = EWMA_OPTIMIZER * seconds + (1 - EWMA_OPTIMIZER) * ewmaOptimizerPassDuration;
    }
  }

  /** Returns the current (or frozen) ETA, with display hysteresis applied. */
  public RoutingEta getCurrentEta() {
    if (frozen && frozenEta != null) return frozenEta;
    if (!started) {
      return new RoutingEta(-1, Confidence.NONE, 0, currentPhase, totalElapsedSeconds, lastKnownIncompleteCount);
    }
    return applyDisplayHysteresis(computeLiveEta());
  }

  /** Returns the current phase string. */
  public String getCurrentPhase() {
    return this.currentPhase;
  }

  /** Fully resets the calculator. */
  public void reset() {
    this.currentPhase = PHASE_IDLE;
    this.autoroutePassesCompleted = 0;
    this.optimizerPassesCompleted = 0;
    this.totalElapsedSeconds = 0;
    this.totalConnectableItems = 0;
    started = false;
    frozen = false;
    frozenEta = null;
    ewmaPassDuration = -1;
    firstPassDurationSeconds = -1;
    ewmaOptimizerPassDuration = -1;
    ewmaFanoutItemsPerSecond = -1;
    lastKnownIncompleteCount = 0;
    lastKnownMaximumCount = 0;
    phaseStartTimeSeconds = currentTimeSeconds();
    stuckNetCount = 0;
    consecutiveNoImprovementPasses = 0;
    lastDisplayedEtaSeconds = -1;
    consecutiveWorseReadings = 0;
    resetRegressionWindow();
  }

  // ======================== Private update helpers ========================

  private void resetRegressionWindow() {
    regressionIndex = 0;
    regressionCount = 0;
    lastSampleElapsedSeconds = -1;
    fitSlope = Double.NaN;
    fitIntercept = Double.NaN;
    fitRSquared = 0;
    fitResidualStdError = 0;
  }

  private void updateFanout(RouterCounters counters) {
    if (counters == null) return;
    int routed = counters.routedCount != null ? counters.routedCount : 0;
    int failed = counters.failedToBeRoutedCount != null ? counters.failedToBeRoutedCount : 0;
    int extraVias = counters.fanoutExtraViasCount != null ? counters.fanoutExtraViasCount : 0;
    int processed = routed + failed + extraVias;

    if (processed <= 0) return;
    double elapsed = currentTimeSeconds() - phaseStartTimeSeconds;
    if (elapsed <= 0) return;

    double rate = processed / elapsed;
    if (ewmaFanoutItemsPerSecond < 0) {
      ewmaFanoutItemsPerSecond = rate;
    } else {
      ewmaFanoutItemsPerSecond = EWMA_FANOUT * rate + (1 - EWMA_FANOUT) * ewmaFanoutItemsPerSecond;
    }
  }

  private int effectiveIncompleteCount(int rawIncompleteCount) {
    return Math.max(0, rawIncompleteCount - stuckNetCount);
  }

  /**
   * Adds a (elapsedSeconds, ln(completionFraction)) sample to the rolling regression window
   * and recomputes the fit. Uses completion fraction (effective incomplete / maximum), not
   * full normalized score — the score's quality terms (trace length, vias) make 100% score
   * unreachable even on a fully-routed board, which would make the regression target
   * unreachable too. Samples are throttled to MIN_SAMPLE_INTERVAL_SECONDS apart so a burst of
   * update() calls doesn't over-weight one short interval relative to others.
   */
  private void addRegressionSample(double elapsedSeconds, int effectiveIncomplete, int maximumCount) {
    int denominator = maximumCount > 0 ? maximumCount : totalConnectableItems;
    if (denominator <= 0) return;
    if (lastSampleElapsedSeconds >= 0 && (elapsedSeconds - lastSampleElapsedSeconds) < MIN_SAMPLE_INTERVAL_SECONDS) {
      return;
    }

    double fraction = Math.max(effectiveIncomplete, COMPLETION_FRACTION_EPSILON_ITEMS) / (double) denominator;
    double y = Math.log(fraction);

    regressionX[regressionIndex] = elapsedSeconds;
    regressionY[regressionIndex] = y;
    regressionIndex = (regressionIndex + 1) % REGRESSION_WINDOW_SIZE;
    if (regressionCount < REGRESSION_WINDOW_SIZE) regressionCount++;
    lastSampleElapsedSeconds = elapsedSeconds;

    recomputeFit();
  }

  /** Ordinary least-squares fit of y = fitIntercept + fitSlope * x, plus R^2 and residual SE. */
  private void recomputeFit() {
    int n = regressionCount;
    if (n < 3) {
      fitSlope = Double.NaN;
      return;
    }
    double sumX = 0, sumY = 0;
    for (int i = 0; i < n; i++) {
      sumX += regressionX[i];
      sumY += regressionY[i];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;

    double sumXY = 0, sumXX = 0, sumYY = 0;
    for (int i = 0; i < n; i++) {
      double dx = regressionX[i] - meanX;
      double dy = regressionY[i] - meanY;
      sumXY += dx * dy;
      sumXX += dx * dx;
      sumYY += dy * dy;
    }

    if (sumXX < 1e-9) {
      fitSlope = Double.NaN;
      return;
    }

    double slope = sumXY / sumXX;
    double intercept = meanY - slope * meanX;

    double ssRes = 0;
    for (int i = 0; i < n; i++) {
      double predicted = intercept + slope * regressionX[i];
      double residual = regressionY[i] - predicted;
      ssRes += residual * residual;
    }
    double ssTot = sumYY;
    double rSquared = ssTot > 1e-9 ? Math.max(0, 1.0 - ssRes / ssTot) : 0;
    double residualStdError = n > 2 ? Math.sqrt(ssRes / (n - 2)) : 0;

    this.fitSlope = slope;
    this.fitIntercept = intercept;
    this.fitRSquared = rSquared;
    this.fitResidualStdError = residualStdError;
    this.fitMeanX = meanX;
    this.fitSumSqX = sumXX;
  }

  // ======================== ETA computation ========================

  private RoutingEta computeLiveEta() {
    if (PHASE_FANOUT.equals(currentPhase)) {
      // Fanout is typically very fast (seconds), so showing a time estimate is
      // misleading — the EWMA rate from a few seconds of data is unreliable.
      // Instead, show a simple progress message.
      return finalizeEta(-1, Confidence.NONE, 1, "Fanout in progress...", -1, -1);
    }
    if (PHASE_OPTIMIZER.equals(currentPhase)) {
      double eta = calcOptimizerEta();
      Confidence confidence = ewmaOptimizerPassDuration > 0 ? Confidence.MEDIUM : Confidence.NONE;
      int remaining = Math.max(0, maxOptimizerPasses - optimizerPassesCompleted);
      return finalizeEta(eta, confidence, remaining, null, -1, -1);
    }
    return computeAutorouteEta();
  }

  private double calcOptimizerEta() {
    if (ewmaOptimizerPassDuration <= 0) return -1;
    int remaining = Math.max(0, maxOptimizerPasses - optimizerPassesCompleted);
    return remaining <= 0 ? 0 : remaining * ewmaOptimizerPassDuration;
  }

  /**
   * Core of the redesign. Solves the regression for the time at which the normalized score
   * reaches the "effectively done" threshold, derives a statistical band from the regression's
   * own residual error, and gates confidence (and whether a time is shown at all) on R^2 and
   * sample count rather than on pass count alone.
   * <p>
   * Uses the normalized score (0-1000) as the progress signal, which captures all quality
   * factors: unrouted connections, clearance violations, trace bends, trace length, and via
   * count. This gives a much more complete picture of routing progress than incomplete count
   * alone, and naturally handles the ripup/reroute non-monotonicity since the score only
   * improves when the board genuinely gets better.
   */
  private RoutingEta computeAutorouteEta() {
    int effectiveIncomplete = effectiveIncompleteCount(lastKnownIncompleteCount);
    if (effectiveIncomplete <= 0) {
      return finalizeEta(0, Confidence.HIGH, 0, null, 0, 0);
    }

    int denominator = lastKnownMaximumCount > 0 ? lastKnownMaximumCount : totalConnectableItems;
    double steadyPassDuration = ewmaPassDuration > 0 ? ewmaPassDuration
        : (firstPassDurationSeconds > 0 ? firstPassDurationSeconds : -1);
    int passesRemainingBudget = Math.max(0, maxPasses - autoroutePassesCompleted);
    double budgetCeiling = steadyPassDuration > 0 ? passesRemainingBudget * steadyPassDuration : -1;

    double etaSeconds = -1;
    double etaLow = -1;
    double etaHigh = -1;
    Confidence confidence = Confidence.NONE;
    // Show pass number and remaining count. Don't show maxPasses since it's often
    // a large default (100+) that misleads users into thinking the router will run
    // that many passes, when in practice it stops much earlier via stagnation.
    String progressText = "Pass " + autoroutePassesCompleted
        + " - " + effectiveIncomplete + " remaining";

    boolean haveFit = !Double.isNaN(fitSlope) && fitSlope < 0 && denominator > 0;
    if (haveFit) {
      // Target: fraction = COMPLETION_FRACTION_EPSILON_ITEMS / denominator (effectively done).
      // Must match the fraction-based signal fed into addRegressionSample(), or the solved
      // target time is off by a factor of denominator (this was a bug in a prior revision:
      // the target used the raw epsilon while the signal was a fraction).
      double targetY = Math.log(COMPLETION_FRACTION_EPSILON_ITEMS / (double) denominator);
      double targetTime = (targetY - fitIntercept) / fitSlope; // absolute elapsedSeconds at target
      double rawEta = targetTime - totalElapsedSeconds;


      if (rawEta >= 0) {
        etaSeconds = rawEta;

        // Prediction standard error at the forecast point, propagated from y-space to
        // time-space via the delta method (dividing by |slope|). Approximate but grounded in
        // the regression's actual residuals rather than an arbitrary +/-X% heuristic.
        int n = regressionCount;
        double sePredY = fitResidualStdError * Math.sqrt(1.0 + 1.0 / n
            + Math.pow(targetTime - fitMeanX, 2) / Math.max(fitSumSqX, 1e-9));
        double seTime = Math.abs(fitSlope) > 1e-9 ? sePredY / Math.abs(fitSlope) : 0;
        etaLow = Math.max(0, etaSeconds - seTime);
        etaHigh = etaSeconds + seTime;

        confidence = confidenceFromFit(regressionCount, fitRSquared);
      }
    }

    // Hard budget ceiling always applies, regardless of what the regression predicts: not
    // everything routes in one pass, and the router will not run past maxPasses.
    if (budgetCeiling >= 0 && (etaSeconds < 0 || etaSeconds > budgetCeiling)) {
      etaSeconds = budgetCeiling;
      etaLow = Math.max(0, budgetCeiling - (etaHigh > etaSeconds ? (etaHigh - etaSeconds) : 0));
      etaHigh = budgetCeiling;
      if (confidence == Confidence.HIGH) confidence = Confidence.MEDIUM;
    }

    // Stagnation risk: the router may stop on its own (via its own stagnation check) before
    // the regression's projected convergence. Blend toward "stops soon" and cap confidence.
    if (stagnationPassLimit > 0 && steadyPassDuration > 0) {
      double stagnationRisk = Math.min(1.0, consecutiveNoImprovementPasses / (double) stagnationPassLimit);
      if (stagnationRisk > 0) {
        int passesUntilStagnationStop = Math.max(0, stagnationPassLimit - consecutiveNoImprovementPasses);
        double stagnationStopEta = passesUntilStagnationStop * steadyPassDuration;
        etaSeconds = etaSeconds < 0 ? stagnationStopEta
            : (1 - stagnationRisk) * etaSeconds + stagnationRisk * stagnationStopEta;
        if (stagnationRisk >= 0.5 && confidence == Confidence.HIGH) confidence = Confidence.MEDIUM;
      }
    }

    int remainingPasses = steadyPassDuration > 0 && etaSeconds >= 0
        ? Math.min(passesRemainingBudget, (int) Math.ceil(etaSeconds / steadyPassDuration))
        : passesRemainingBudget;

    return finalizeEta(etaSeconds, confidence, remainingPasses, progressText, etaLow, etaHigh);
  }

  private Confidence confidenceFromFit(int sampleCount, double rSquared) {
    if (sampleCount < MIN_SAMPLES_FOR_LOW_CONFIDENCE || rSquared < 0.15) {
      return Confidence.NONE;
    }
    if (sampleCount < MIN_SAMPLES_FOR_MEDIUM_CONFIDENCE || rSquared < R_SQUARED_LOW_THRESHOLD) {
      return Confidence.LOW;
    }
    if (sampleCount < MIN_SAMPLES_FOR_HIGH_CONFIDENCE || rSquared < R_SQUARED_HIGH_THRESHOLD) {
      return Confidence.MEDIUM;
    }
    return Confidence.HIGH;
  }

  /**
   * Applies the MAX_ACCEPTABLE_ETA_SECONDS cap and packages the result. When confidence is
   * NONE (or LOW with too little data), the caller-supplied progressText should be preferred
   * by the UI over a time string — RoutingEta carries both so the UI can make that call.
   */
  private RoutingEta finalizeEta(double etaSeconds, Confidence confidence, int remainingPasses,
                                  String progressText, double etaLow, double etaHigh) {
    if (etaSeconds > MAX_ACCEPTABLE_ETA_SECONDS) {
      etaSeconds = MAX_ACCEPTABLE_ETA_SECONDS;
      if (confidence == Confidence.HIGH) confidence = Confidence.MEDIUM;
    }
    RoutingEta eta = new RoutingEta(etaSeconds, confidence, remainingPasses,
        currentPhase, totalElapsedSeconds, lastKnownIncompleteCount, null);
    eta.progressText = progressText;
    eta.etaSecondsLow = etaLow;
    eta.etaSecondsHigh = etaHigh;
    return eta;
  }

  /**
   * Display-side hysteresis, fully decoupled from the estimation model above. The underlying
   * model is free to fluctuate every update (that's expected — the input signal is genuinely
   * noisy); what reaches the screen only increases after HYSTERESIS_CONFIRM_COUNT consecutive
   * worse readings in a row, unless the jump is large enough (HYSTERESIS_BYPASS_RATIO) to
   * indicate a real discontinuity such as a board restore or stagnation onset. Decreases
   * (good news) are always shown immediately — there's no reason to make the user wait for
   * confirmation that things are going well.
   */
  private RoutingEta applyDisplayHysteresis(RoutingEta raw) {
    if (raw.etaSeconds < 0 || raw.confidence == Confidence.NONE) {
      // Nothing trustworthy to hold back or confirm; pass through as-is (progress-only display).
      lastDisplayedEtaSeconds = -1;
      consecutiveWorseReadings = 0;
      return raw;
    }

    if (lastDisplayedEtaSeconds < 0 || raw.etaSeconds <= lastDisplayedEtaSeconds) {
      lastDisplayedEtaSeconds = raw.etaSeconds;
      consecutiveWorseReadings = 0;
      return raw;
    }

    // raw.etaSeconds > lastDisplayedEtaSeconds: a worse reading.
    boolean largeJump = raw.etaSeconds > lastDisplayedEtaSeconds * HYSTERESIS_BYPASS_RATIO;
    consecutiveWorseReadings++;
    if (largeJump || consecutiveWorseReadings >= HYSTERESIS_CONFIRM_COUNT) {
      lastDisplayedEtaSeconds = raw.etaSeconds;
      consecutiveWorseReadings = 0;
      return raw;
    }

    // Hold the previously displayed value instead of the noisier new one.
    RoutingEta held = new RoutingEta(lastDisplayedEtaSeconds, raw.confidence, raw.estimatedRemainingPasses,
        raw.phase, raw.totalElapsedSeconds, raw.incompleteCount, raw.stopReason);
    held.progressText = raw.progressText;
    held.etaSecondsLow = raw.etaSecondsLow;
    held.etaSecondsHigh = raw.etaSecondsHigh;
    return held;
  }

  private static double currentTimeSeconds() {
    return System.currentTimeMillis() / 1000.0;
  }
}