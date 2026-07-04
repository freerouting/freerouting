package app.freerouting.core;

import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.core.scoring.RoutingEta;
import app.freerouting.core.scoring.RoutingEta.Confidence;
import app.freerouting.logger.FRLogger;

import java.util.ArrayDeque;

/**
 * Calculates and maintains an ETA for the routing job.
 *
 * <h2>Key design decisions</h2>
 * <ul>
 * <li><b>Phase-Aware Mathematical Models.</b> Different phases of routing behave differently.
 * <ul>
 * <li><b>Fanout:</b> Bypasses numeric ETA entirely (too fast/erratic).</li>
 * <li><b>Autoroute:</b> Exponential decay model (rip-up and reroute maze search).</li>
 * <li><b>Optimizer:</b> Aggressively weighted pass-velocity model to ignore the slow 1st pass.</li>
 * </ul>
 * </li>
 * <li><b>Terminal State Locking.</b> Prevents delayed thread updates from overwriting "Stopped" 
 * or "Completed" messages in the UI.</li>
 * <li><b>Anomaly Detection.</b> Auto-resets the mathematical models if the board is swapped 
 * or the timer unexpectedly jumps backwards.</li>
 * </ul>
 */
public class RoutingEtaCalculator {

  // Alpha weights for the Exponential Moving Averages (EMA) in Autoroute phase
  private static final double EMA_ALPHA_TIME = 0.20;
  private static final double EMA_ALPHA_DECAY = 0.15;
  
  // Guarantees we never predict an infinite ETA even if the router stalls temporarily
  // (units: per-pass, used only by the legacy pass-boundary decay estimator)
  private static final double MIN_DECAY_RATE = 0.005; 
  // Floor for the intra-pass regression decay rate (units: per-second)
  private static final double MIN_DECAY_RATE_PER_SECOND = 0.0005;

  private static final double HYSTERESIS_BYPASS_RATIO = 1.2;
  private static final int HYSTERESIS_CONFIRM_COUNT = 3;

  private String currentPhase = "";
  private String lastPhase = "";
  private RoutingEta lastEta = null;
  
  // API compatibility fields
  private Integer maxPasses;
  private Integer maxOptimizerPasses;
  private Integer threadCount;
  private int totalConnectableItems;

  // State for tracking progress
  private int lastPass = -1;
  private double lastPassElapsedSeconds = 0.0;
  private int lastKnownIncompleteCount = -1;

  // Global phase trackers (to account for all time passed in the current phase)
  private double phaseStartElapsedSeconds = 0.0;
  private int phaseStartIncompleteCount = -1;
  private int phaseStartPass = 0;

  // EMA Trackers for Autoroute
  private double emaPassDuration = -1.0;
  private double emaDecayRate = -1.0;

  // Rolling-regression decay trackers. BatchAutorouterThread and BatchFanout both fire
  // board-updated events roughly every second (ProgressThrottler(1000)) WITHIN a pass, with
  // real decreasing incompleteCount - not just at pass boundaries. Items are shuffled per pass,
  // so this intra-pass rate is not front-loaded with "easy" items/pins. Each tracker fits
  // ln(count) vs. wall-clock time by OLS to get a statistically grounded decay rate quickly,
  // with safeguards against small-sample overconfidence and confidence flapping (see the class).
  private final PhaseDecayTracker autorouteTracker = new PhaseDecayTracker();
  private final PhaseDecayTracker fanoutTracker = new PhaseDecayTracker();

  // Hysteresis state
  private double lastDisplayedEtaSeconds = -1;
  private int consecutiveWorseReadings = 0;

  // --- API Compatibility Methods ---

  public void startRouting() {
      resetForBoardSwap();
      currentPhase = "starting";
      lastEta = createFallbackEta(0, 0, currentPhase);
  }

  /**
   * Clears ETA state carried over from a previous board/session.
   *
   * <p>This is intentionally stronger than {@link #resetEstimators()} because it also clears the
   * last emitted phase and ETA payload, allowing a newly loaded board to start from a clean UI
   * state instead of inheriting the previous board's completion/stopped text.
   */
  public void resetForBoardSwap() {
      resetEstimators();
      currentPhase = "";
      lastPhase = "";
      lastEta = null;
  }

  public void setMaxPasses(Integer maxPasses) { this.maxPasses = maxPasses; }
  public void setMaxOptimizerPasses(Integer maxOptimizerPasses) { this.maxOptimizerPasses = maxOptimizerPasses; }
  public void setThreadCount(Integer threadCount) { this.threadCount = threadCount; }
  public void setTotalConnectableItems(int totalConnectableItems) { this.totalConnectableItems = totalConnectableItems; }

  public String getCurrentPhase() { return currentPhase; }
  public RoutingEta getCurrentEta() { return lastEta; }

  public void onFanoutStarted() { this.currentPhase = "fanout"; }
  public void onAutorouteStarted() { this.currentPhase = "autoroute"; }
  public void onOptimizerStarted() { this.currentPhase = "optimizer"; }
  
  public void onRoutingCompleted() { 
      this.currentPhase = "completed"; 
      double totalElapsedSeconds = lastEta != null ? lastEta.totalElapsedSeconds : 0.0;
      lastEta = new RoutingEta(0, Confidence.HIGH, 0, currentPhase, totalElapsedSeconds, 0, null);
      lastEta.progressText = "Completed.";
  }
  
  public void onRoutingStopped() { 
      this.currentPhase = "stopped"; 
      double totalElapsedSeconds = lastEta != null ? lastEta.totalElapsedSeconds : 0.0;
      int incompleteCount = lastEta != null ? lastEta.incompleteCount : 0;
      lastEta = new RoutingEta(0, Confidence.HIGH, 0, currentPhase, totalElapsedSeconds, incompleteCount, "Stopped");
      lastEta.progressText = "Stopped.";
  }

  /**
   * Adapter update method required by AutorouterAndRouteOptimizerThread.
   */
  public void update(double elapsedSeconds, RouterCounters counters, BoardStatistics stats) {
      // 1. Terminal State Lock: If we are stopped or completed, ignore all trailing thread updates.
      if ("stopped".equals(currentPhase) || "completed".equals(currentPhase)) {
          return; 
      }

      int pass = (counters != null && counters.passCount != null) ? counters.passCount : 0;
      int incomplete = (counters != null && counters.incompleteCount != null) ? counters.incompleteCount : 0;
      
      this.lastEta = calculateInternal(elapsedSeconds, pass, incomplete, this.currentPhase);
  }

  // --- Internal Phase-Aware ETA Math ---

  private RoutingEta calculateInternal(double totalElapsedSeconds, int currentPass, int incompleteCount, String currentPhase) {
    // 2. Anomaly Detection (Board Swap / Restart)
    // If time goes backward or passes drop unexpectedly, instantly reset our math models.
    if (lastPass != -1 && (totalElapsedSeconds < lastPassElapsedSeconds || currentPass < lastPass)) {
        resetEstimators();
    }

    if (!currentPhase.equals(lastPhase)) {
        resetEstimators();
        lastPhase = currentPhase;
    }

    // 3. FANOUT Phase
    if ("fanout".equals(currentPhase)) {
        // Fanout is fast/erratic pin escaping, so - unlike autoroute - we deliberately cap
        // confidence at MEDIUM even with a great fit; this phase can still churn (rip-up of
        // fanout vias) in ways a short regression window won't see coming.
        fanoutTracker.record(totalElapsedSeconds, incompleteCount);

        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
        }
        if (currentPass > lastPass) {
            lastPass = currentPass;
            lastPassElapsedSeconds = totalElapsedSeconds;
        }

        PhaseDecayTracker.Fit fit = fanoutTracker.fit();
        if (fit != null) {
            double decay = Math.max(fit.decayPerSecond, MIN_DECAY_RATE_PER_SECOND);
            double etaSeconds = Math.log(Math.max(incompleteCount, 1) / 0.5) / decay;
            Confidence confidence = minConfidence(fit.confidence, Confidence.MEDIUM);

            double marginRatio = confidence == Confidence.MEDIUM ? 0.20 : 0.35;
            double margin = Math.max(3.0, etaSeconds * marginRatio);

            RoutingEta fanoutEta = new RoutingEta(Math.max(0.0, etaSeconds), confidence, 0,
                currentPhase, totalElapsedSeconds, incompleteCount, null);
            fanoutEta.etaSecondsLow = Math.max(0.0, etaSeconds - margin);
            fanoutEta.etaSecondsHigh = etaSeconds + margin;
            if (confidence != Confidence.MEDIUM) {
                // NONE/LOW still render as text per RoutingEta.toDisplayString - keep it labeled.
                fanoutEta.progressText = "Escaping pins (Fanout)...";
            }
            return applyDisplayHysteresis(fanoutEta);
        }

        RoutingEta fallback = createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
        fallback.progressText = "Escaping pins (Fanout)...";
        return fallback;
    }

    // 4. OPTIMIZER Phase
    if ("optimizer".equals(currentPhase)) {
        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
            if (maxOptimizerPasses != null && maxOptimizerPasses > currentPass && totalElapsedSeconds > 0) {
                int roughPassesRemaining = Math.max(1, maxOptimizerPasses - currentPass);
                double etaSeconds = totalElapsedSeconds * roughPassesRemaining;
                RoutingEta roughEta = new RoutingEta(etaSeconds, Confidence.LOW, roughPassesRemaining,
                    currentPhase, totalElapsedSeconds, 0, null);
                roughEta.etaSecondsLow = Math.max(0.0, etaSeconds * 0.80);
                roughEta.etaSecondsHigh = Math.max(roughEta.etaSecondsLow + 1.0, etaSeconds * 1.20);
                roughEta.progressText = "Optimizing routes...";
                return applyDisplayHysteresis(roughEta);
            }
            RoutingEta fallback = createFallbackEta(totalElapsedSeconds, 0, currentPhase);
            fallback.progressText = "Optimizing routes...";
            return fallback;
        }

        if (currentPass > lastPass) {
            double timeElapsed = totalElapsedSeconds - lastPassElapsedSeconds;
            int passesElapsed = currentPass - lastPass;
            double passDuration = Math.max(0.001, timeElapsed / passesElapsed);
            
            // Pass 1 of the optimizer does heavy rip-up and is notoriously slow. 
            // Passes 2+ are very fast. We use an aggressive weight to quickly adapt to the fast passes.
            if (emaPassDuration < 0 || currentPass <= 2) {
                emaPassDuration = passDuration;
            } else {
                emaPassDuration = (0.60 * passDuration) + (0.40 * emaPassDuration);
            }
            
            lastPass = currentPass;
            lastPassElapsedSeconds = totalElapsedSeconds;
        }

        if (emaPassDuration > 0 && maxOptimizerPasses != null && maxOptimizerPasses > 0) {
            int passesRemaining = Math.max(0, maxOptimizerPasses - currentPass);
            double etaSeconds = passesRemaining * emaPassDuration;

            // Optimizer passes 3+ are fast and stable, so this converges quickly once we have a
            // real target pass count - but don't claim HIGH confidence off a single sample.
            Confidence confidence = currentPass <= 1 ? Confidence.LOW
                : currentPass <= 3 ? Confidence.MEDIUM : Confidence.HIGH;
            double marginRatio = confidence == Confidence.HIGH ? 0.05 : 0.15;

            RoutingEta optEta = new RoutingEta(etaSeconds, confidence, passesRemaining, currentPhase, totalElapsedSeconds, 0, null);
            optEta.etaSecondsLow = Math.max(0, etaSeconds - (etaSeconds * marginRatio));
            optEta.etaSecondsHigh = etaSeconds + (etaSeconds * marginRatio);
            return applyDisplayHysteresis(optEta);
        }

        // NOTE: BatchOptimizer's real stop condition is score-improvement vs.
        // optimizationImprovementThreshold, not a fixed pass count - job.routerSettings.optimizer
        // .maxPasses is explicitly optional there. When no pass cap is configured (the common
        // case), maxOptimizerPasses is never populated here, so a real numeric ETA isn't possible
        // without also being fed the actual improvement-vs-threshold ratio. Rather than fabricate
        // a fake countdown, surface honest live progress instead of a static string.
        RoutingEta fallback = createFallbackEta(totalElapsedSeconds, 0, currentPhase);
        if (emaPassDuration > 0) {
            fallback.progressText = String.format("Optimizing routes (pass %d, ~%.0fs/pass)...",
                currentPass, emaPassDuration);
        } else {
            fallback.progressText = "Optimizing routes...";
        }
        return fallback;
    }

    // 5. AUTOROUTE Phase: Exponential Decay (Rip-up and reroute maze search)
    if ("autoroute".equals(currentPhase)) {
        // Handle immediate autoroute completion edge case
        if (incompleteCount <= 0) {
            RoutingEta eta = new RoutingEta(0, Confidence.HIGH, 0, currentPhase, totalElapsedSeconds, 0, null);
            eta.progressText = "Completing...";
            return eta;
        }

        // Record this sample immediately (even before any pass boundary) so the intra-pass
        // regression has data from the very first throttled update in pass 1.
        autorouteTracker.record(totalElapsedSeconds, incompleteCount);

        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
        }

        double safeIncomplete = Math.max(1.0, incompleteCount);

        // --- Legacy pass-boundary decay estimator (per-pass units) ---
        // Still needed for: (a) emaPassDuration, used to translate a decay-per-second ETA into
        // an approximate "remaining passes" figure for display, and (b) as a fallback/blend
        // input once real pass boundaries exist, since it captures rip-up/reroute churn across
        // full passes that a single pass's intra-pass regression cannot see.
        if (currentPass > lastPass) {
            int passesElapsed = currentPass - lastPass;
            double timeElapsed = totalElapsedSeconds - lastPassElapsedSeconds;

            double passDuration = Math.max(0.001, timeElapsed / passesElapsed);
            double safeLastIncomplete = Math.max(1.0, lastKnownIncompleteCount);

            double k;
            if (safeIncomplete >= safeLastIncomplete) {
                k = -0.01; // Rip-up stall penalty
            } else {
                k = -Math.log(safeIncomplete / safeLastIncomplete) / passesElapsed;
            }

            if (emaPassDuration < 0) {
                emaPassDuration = passDuration;
                emaDecayRate = Math.max(k, MIN_DECAY_RATE);
            } else {
                emaPassDuration = (EMA_ALPHA_TIME * passDuration) + ((1 - EMA_ALPHA_TIME) * emaPassDuration);
                emaDecayRate = (EMA_ALPHA_DECAY * k) + ((1 - EMA_ALPHA_DECAY) * emaDecayRate);
            }

            lastPass = currentPass;
            lastPassElapsedSeconds = totalElapsedSeconds;
            lastKnownIncompleteCount = incompleteCount;
        }

        // --- Intra-pass regression estimator (per-second units) ---
        PhaseDecayTracker.Fit fit = autorouteTracker.fit();
        boolean regressionUsable = fit != null;

        int totalPhasePasses = currentPass - phaseStartPass;
        boolean legacyUsable = emaPassDuration > 0 && emaDecayRate > 0;

        if (!regressionUsable && !legacyUsable) {
            // Not enough data from either estimator yet (e.g. very first update of the phase).
            RoutingEta fallback = createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
            fallback.progressText = "Analyzing search space...";
            return applyDisplayHysteresis(fallback);
        }

        double effectiveDecayPerSecond;
        Confidence confidence;

        if (regressionUsable) {
            effectiveDecayPerSecond = fit.decayPerSecond;
            confidence = fit.confidence;

            if (legacyUsable && totalPhasePasses >= 2) {
                // Blend in the pass-boundary decay (converted to per-second) once we have at
                // least two completed passes worth of macro rip-up/reroute behavior. Weight
                // shifts toward the pass-based signal as more full passes accumulate, since it
                // sees dynamics a single pass's regression can't (temporary incomplete spikes
                // from rip-up between passes, layer/via churn, etc).
                double legacyDecayPerSecond = emaDecayRate / emaPassDuration;
                double passWeight = Math.min(0.5, totalPhasePasses * 0.08);
                effectiveDecayPerSecond = (1 - passWeight) * fit.decayPerSecond
                    + passWeight * legacyDecayPerSecond;
                // totalPhasePasses >= 2 here already implies currentPass >= 2, so
                // passCountConfidence is never NONE in this branch - taking the min is safe
                // and just guards against a young phase overclaiming despite a lucky-looking fit.
                confidence = minConfidence(confidence, passCountConfidence(currentPass));
            }
        } else {
            // Regression not yet trustworthy (too few/short samples, or a bad/rising fit,
            // e.g. single-threaded runs where board-updated events are sparse) - fall back
            // to the original pass-boundary-only estimator.
            double globalDecayRate = -1.0;
            if (totalPhasePasses > 0 && phaseStartIncompleteCount > 0) {
                double safeStartInc = Math.max(1.0, phaseStartIncompleteCount);
                if (safeIncomplete < safeStartInc) {
                    globalDecayRate = -Math.log(safeIncomplete / safeStartInc) / totalPhasePasses;
                }
            }
            double legacyDecay = Math.max(emaDecayRate, MIN_DECAY_RATE);
            if (globalDecayRate > 0) {
                legacyDecay = (legacyDecay * 0.70) + (globalDecayRate * 0.30);
            }
            effectiveDecayPerSecond = legacyDecay / emaPassDuration;
            confidence = passCountConfidence(currentPass);
        }

        effectiveDecayPerSecond = Math.max(effectiveDecayPerSecond, MIN_DECAY_RATE_PER_SECOND);

        double etaSeconds = Math.log(Math.max(incompleteCount, 1) / 0.5) / effectiveDecayPerSecond;

        // Approximate remaining-pass count, for display/telemetry only - the real ETA above is
        // computed directly in seconds and does not depend on this being exact.
        int remainingPasses = emaPassDuration > 0 ? (int) Math.ceil(etaSeconds / emaPassDuration) : -1;

        if (maxPasses != null && maxPasses > 0 && emaPassDuration > 0) {
            int hardPassLimit = Math.max(0, maxPasses - currentPass);
            double hardCapSeconds = hardPassLimit * emaPassDuration;
            if (etaSeconds > hardCapSeconds) {
                etaSeconds = hardCapSeconds;
            }
            if (remainingPasses >= 0) {
                remainingPasses = Math.min(remainingPasses, hardPassLimit);
            }
        }

        double marginRatio;
        if (confidence == Confidence.LOW) marginRatio = 0.25;
        else if (confidence == Confidence.MEDIUM) marginRatio = 0.10;
        else if (confidence == Confidence.HIGH) marginRatio = 0.05;
        else marginRatio = 0.35;

        double margin = Math.max(5.0, etaSeconds * marginRatio);
        double etaLow = Math.max(0, etaSeconds - margin);
        double etaHigh = etaSeconds + margin;

        RoutingEta rawEta = new RoutingEta(etaSeconds, confidence, remainingPasses, currentPhase, totalElapsedSeconds, incompleteCount, null);
        rawEta.etaSecondsLow = etaLow;
        rawEta.etaSecondsHigh = etaHigh;

        if (confidence == Confidence.NONE) {
            rawEta.progressText = "Analyzing search space...";
        }

        return applyDisplayHysteresis(rawEta);
    }

    // Fallback for unknown phases
    return createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
  }

  private void initPhaseTrackers(double totalElapsedSeconds, int currentPass, int incompleteCount) {
    lastPass = currentPass;
    lastPassElapsedSeconds = totalElapsedSeconds;
    lastKnownIncompleteCount = incompleteCount;
    
    phaseStartElapsedSeconds = totalElapsedSeconds;
    phaseStartIncompleteCount = incompleteCount;
    phaseStartPass = currentPass;
  }

  private void resetEstimators() {
    lastPass = -1;
    lastPassElapsedSeconds = 0.0;
    emaPassDuration = -1.0;
    emaDecayRate = -1.0;
    lastDisplayedEtaSeconds = -1;
    consecutiveWorseReadings = 0;
    autorouteTracker.reset();
    fanoutTracker.reset();
    
    phaseStartElapsedSeconds = 0.0;
    phaseStartIncompleteCount = -1;
    phaseStartPass = 0;
  }

  /** Confidence purely from how many autoroute passes have completed (legacy heuristic). */
  private static Confidence passCountConfidence(int currentPass) {
    if (currentPass <= 1) return Confidence.NONE;
    if (currentPass <= 3) return Confidence.LOW;
    if (currentPass <= 8) return Confidence.MEDIUM;
    return Confidence.HIGH;
  }

  // Explicit rank map rather than a.ordinal() - safer than assuming Confidence's declared
  // enum order without having sight of RoutingEta.java.
  private static int confidenceRank(Confidence c) {
    switch (c) {
      case NONE: return 0;
      case LOW: return 1;
      case MEDIUM: return 2;
      case HIGH: return 3;
      default: return 0;
    }
  }

  private static Confidence minConfidence(Confidence a, Confidence b) {
    return confidenceRank(a) <= confidenceRank(b) ? a : b;
  }

  private RoutingEta createFallbackEta(double totalElapsedSeconds, int incompleteCount, String currentPhase) {
    RoutingEta eta = new RoutingEta(-1, Confidence.NONE, -1, currentPhase, totalElapsedSeconds, incompleteCount, null);
    eta.progressText = "Calculating ETA...";
    return eta;
  }

  private RoutingEta applyDisplayHysteresis(RoutingEta raw) {
    if (raw.etaSeconds < 0 || raw.confidence == Confidence.NONE) {
      lastDisplayedEtaSeconds = -1;
      consecutiveWorseReadings = 0;
      return raw;
    }

    if (lastDisplayedEtaSeconds < 0 || raw.etaSeconds <= lastDisplayedEtaSeconds) {
      lastDisplayedEtaSeconds = raw.etaSeconds;
      consecutiveWorseReadings = 0;
      return raw;
    }

    boolean largeJump = raw.etaSeconds > lastDisplayedEtaSeconds * HYSTERESIS_BYPASS_RATIO;
    consecutiveWorseReadings++;
    if (largeJump || consecutiveWorseReadings >= HYSTERESIS_CONFIRM_COUNT) {
      lastDisplayedEtaSeconds = raw.etaSeconds;
      consecutiveWorseReadings = 0;
      return raw;
    }

    RoutingEta held = new RoutingEta(lastDisplayedEtaSeconds, raw.confidence, raw.estimatedRemainingPasses,
        raw.phase, raw.totalElapsedSeconds, raw.incompleteCount, raw.stopReason);
    held.progressText = raw.progressText;
    held.etaSecondsLow = raw.etaSecondsLow;
    held.etaSecondsHigh = raw.etaSecondsHigh;
    return held;
  }

  /**
   * Tracks (time, ln(count)) samples for a monotonically-decaying quantity (incomplete
   * connections, pins-to-go) and fits an OLS regression to estimate its decay rate.
   *
   * <p>Three safeguards beyond a plain regression, added after observing on real boards:
   * <ul>
   * <li><b>Adjusted R^2</b> instead of raw R^2. Raw R^2 is inflated with few points (a 5-point
   * fit can look "perfect" by chance), which was causing premature high-confidence ETAs on
   * large boards during the first ~30-40s that then had to visibly climb once more data came
   * in. Adjusted R^2 penalizes small sample counts and removes most of that bias.</li>
   * <li><b>Split-half slope-stability check.</b> Large boards often show a fast burst in the
   * first few seconds of a pass (whatever routes easily) before settling into a slower,
   * representative rate as the board fills in. Comparing the decay rate in the first half of
   * the sample window against the second half catches this directly - if the rate has slowed by
   * more than {@code MAX_SLOWDOWN_RATIO}, the fit is capped at LOW confidence regardless of its
   * raw fit quality, since the full-window average is still contaminated by the early burst.</li>
   * <li><b>Confidence-downgrade hysteresis.</b> A single noisy update can transiently drop R^2
   * below a threshold. Without damping, that flashes the UI back to "Calculating ETA..." for one
   * cycle before recovering - this requires several consecutive weaker readings before actually
   * downgrading the displayed confidence (upgrades are still immediate).</li>
   * </ul>
   */
  private static final class PhaseDecayTracker {
    private static final int MAX_SAMPLES = 60;
    private static final int MIN_SAMPLES = 5;
    private static final double MIN_SPAN_SECONDS = 2.0;
    private static final double MIN_ADJUSTED_R2 = 0.4;
    private static final double MAX_SLOWDOWN_RATIO = 1.6;
    private static final int CONFIDENCE_DOWNGRADE_CONFIRM_COUNT = 3;

    private final ArrayDeque<double[]> samples = new ArrayDeque<>();
    private Confidence displayedConfidence = Confidence.NONE;
    private int consecutiveWeakerReadings = 0;

    void reset() {
      samples.clear();
      displayedConfidence = Confidence.NONE;
      consecutiveWeakerReadings = 0;
    }

    void record(double elapsedSeconds, int count) {
      if (count <= 0) {
        return;
      }
      double[] last = samples.peekLast();
      if (last != null && elapsedSeconds <= last[0] + 1e-6) {
        return; // no new information (duplicate/throttle-collision timestamp)
      }
      samples.addLast(new double[] {elapsedSeconds, Math.log(count)});
      while (samples.size() > MAX_SAMPLES) {
        samples.pollFirst();
      }
    }

    static final class Fit {
      final double decayPerSecond;
      final Confidence confidence;
      final int sampleCount;

      Fit(double decayPerSecond, Confidence confidence, int sampleCount) {
        this.decayPerSecond = decayPerSecond;
        this.confidence = confidence;
        this.sampleCount = sampleCount;
      }
    }

    /** Returns null if there isn't enough data yet, or the trend isn't actually decaying. */
    Fit fit() {
      int n = samples.size();
      if (n < MIN_SAMPLES) {
        return null;
      }
      double span = samples.peekLast()[0] - samples.peekFirst()[0];
      if (span < MIN_SPAN_SECONDS) {
        return null;
      }

      double[] full = fitLinearRegression(samples, n);
      if (full == null || full[0] >= 0) {
        return null; // not decaying (flat or rising) - nothing usable to report
      }
      double decayPerSecond = -full[0];
      double adjustedR2 = adjustedR2(full[2], n);

      Confidence rawConfidence;
      if (adjustedR2 >= 0.85 && n >= 10) rawConfidence = Confidence.HIGH;
      else if (adjustedR2 >= 0.6 && n >= 6) rawConfidence = Confidence.MEDIUM;
      else if (adjustedR2 >= MIN_ADJUSTED_R2) rawConfidence = Confidence.LOW;
      else rawConfidence = Confidence.NONE;

      if (rawConfidence != Confidence.NONE && n >= 8) {
        double[][] arr = samples.toArray(new double[0][]);
        int mid = n / 2;
        double[] firstHalf = fitLinearRegression(java.util.Arrays.asList(arr).subList(0, mid), mid);
        double[] secondHalf = fitLinearRegression(java.util.Arrays.asList(arr).subList(mid, n), n - mid);
        if (firstHalf != null && secondHalf != null && firstHalf[0] < 0 && secondHalf[0] < 0) {
          double firstDecay = -firstHalf[0];
          double secondDecay = -secondHalf[0];
          if (firstDecay / Math.max(secondDecay, 1e-9) > MAX_SLOWDOWN_RATIO) {
            // Rate has genuinely slowed within this window - the early burst is skewing the
            // full-window average, so don't trust it beyond LOW confidence yet.
            rawConfidence = minConfidence(rawConfidence, Confidence.LOW);
          }
        }
      }

      // Confidence-downgrade hysteresis: allow immediate upgrades, but require several
      // consecutive weaker readings before actually downgrading what's displayed.
      if (confidenceRank(rawConfidence) >= confidenceRank(displayedConfidence)) {
        displayedConfidence = rawConfidence;
        consecutiveWeakerReadings = 0;
      } else {
        consecutiveWeakerReadings++;
        if (consecutiveWeakerReadings >= CONFIDENCE_DOWNGRADE_CONFIRM_COUNT) {
          displayedConfidence = rawConfidence;
          consecutiveWeakerReadings = 0;
        }
      }

      if (displayedConfidence == Confidence.NONE) {
        return null;
      }
      return new Fit(decayPerSecond, displayedConfidence, n);
    }

    private static double adjustedR2(double r2, int n) {
      if (n <= 2) {
        return r2;
      }
      return 1.0 - (1.0 - r2) * (n - 1.0) / (n - 2.0);
    }

    /**
     * Ordinary least squares fit of y = intercept + slope * x.
     *
     * @return {slope, intercept, r2}, or null if the samples are degenerate (e.g. all same x).
     */
    private static double[] fitLinearRegression(Iterable<double[]> samples, int n) {
      double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
      for (double[] s : samples) {
        sumX += s[0];
        sumY += s[1];
        sumXY += s[0] * s[1];
        sumXX += s[0] * s[0];
      }
      double denom = n * sumXX - sumX * sumX;
      if (Math.abs(denom) < 1e-9) {
        return null;
      }
      double slope = (n * sumXY - sumX * sumY) / denom;
      double intercept = (sumY - slope * sumX) / n;

      double meanY = sumY / n;
      double ssTot = 0, ssRes = 0;
      for (double[] s : samples) {
        double predicted = intercept + slope * s[0];
        double residual = s[1] - predicted;
        ssRes += residual * residual;
        ssTot += (s[1] - meanY) * (s[1] - meanY);
      }
      double r2 = ssTot > 1e-9 ? 1.0 - (ssRes / ssTot) : (ssRes < 1e-9 ? 1.0 : 0.0);
      return new double[] {slope, intercept, r2};
    }
  }
}