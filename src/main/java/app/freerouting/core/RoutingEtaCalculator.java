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
 * <li><b>Phase-Aware Mathematical Models.</b> Different phases of routing behave differently.
 * <ul>
 * <li><b>Fanout:</b> Linear item-velocity model (sequential pin breakouts).</li>
 * <li><b>Autoroute:</b> Exponential decay model (rip-up and reroute maze search).</li>
 * <li><b>Optimizer:</b> Linear pass-velocity model (sequential trace reduction).</li>
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
  private static final double MIN_DECAY_RATE = 0.005; 

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

  // Hysteresis state
  private double lastDisplayedEtaSeconds = -1;
  private int consecutiveWorseReadings = 0;

  // --- API Compatibility Methods ---

  public void startRouting() {
      resetEstimators();
      currentPhase = "starting";
      lastEta = createFallbackEta(0, 0, currentPhase);
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
      if (lastEta != null) {
          lastEta = new RoutingEta(0, Confidence.HIGH, 0, currentPhase, lastEta.totalElapsedSeconds, 0, null);
          lastEta.progressText = "Completed.";
      }
  }
  
  public void onRoutingStopped() { 
      this.currentPhase = "stopped"; 
      if (lastEta != null) {
           lastEta = new RoutingEta(0, Confidence.HIGH, 0, currentPhase, lastEta.totalElapsedSeconds, lastEta.incompleteCount, "Stopped");
           lastEta.progressText = "Stopped.";
      }
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

    // 3. FANOUT Phase: Linear Item Velocity (Pins breakout linearly, not exponentially)
    if ("fanout".equals(currentPhase)) {
        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
            RoutingEta fallback = createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
            fallback.progressText = "Escaping pins (Fanout)...";
            return fallback;
        }

        double timeInPhase = totalElapsedSeconds - phaseStartElapsedSeconds;
        int itemsProcessed = phaseStartIncompleteCount - incompleteCount;

        if (timeInPhase > 0 && itemsProcessed > 0) {
            double secondsPerItem = timeInPhase / itemsProcessed;
            double etaSeconds = incompleteCount * secondsPerItem;

            RoutingEta fanoutEta = new RoutingEta(etaSeconds, Confidence.MEDIUM, 0, currentPhase, totalElapsedSeconds, incompleteCount, null);
            fanoutEta.etaSecondsLow = Math.max(0, etaSeconds - (etaSeconds * 0.15));
            fanoutEta.etaSecondsHigh = etaSeconds + (etaSeconds * 0.15);
            fanoutEta.progressText = "Escaping pins (Fanout)...";
            
            lastPass = currentPass;
            lastPassElapsedSeconds = totalElapsedSeconds;
            return applyDisplayHysteresis(fanoutEta);
        }
        
        lastPass = currentPass;
        lastPassElapsedSeconds = totalElapsedSeconds;
        RoutingEta fallback = createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
        fallback.progressText = "Escaping pins (Fanout)...";
        return fallback;
    }

    // 4. OPTIMIZER Phase: Linear Pass Velocity (Trace smoothing is strictly pass-based)
    if ("optimizer".equals(currentPhase)) {
        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
            RoutingEta fallback = createFallbackEta(totalElapsedSeconds, 0, currentPhase);
            fallback.progressText = "Optimizing routes...";
            return fallback;
        }

        double timeInPhase = totalElapsedSeconds - phaseStartElapsedSeconds;
        int passesElapsed = currentPass - phaseStartPass;

        if (timeInPhase > 0 && passesElapsed > 0 && maxOptimizerPasses != null && maxOptimizerPasses > 0) {
            double secondsPerPass = timeInPhase / passesElapsed;
            int passesRemaining = Math.max(0, maxOptimizerPasses - currentPass);
            double etaSeconds = passesRemaining * secondsPerPass;

            RoutingEta optEta = new RoutingEta(etaSeconds, Confidence.HIGH, passesRemaining, currentPhase, totalElapsedSeconds, 0, null);
            optEta.etaSecondsLow = Math.max(0, etaSeconds - (etaSeconds * 0.05));
            optEta.etaSecondsHigh = etaSeconds + (etaSeconds * 0.05);
            
            lastPass = currentPass;
            lastPassElapsedSeconds = totalElapsedSeconds;
            return applyDisplayHysteresis(optEta);
        }
        
        lastPass = currentPass;
        lastPassElapsedSeconds = totalElapsedSeconds;
        RoutingEta fallback = createFallbackEta(totalElapsedSeconds, 0, currentPhase);
        fallback.progressText = "Optimizing routes...";
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

        if (lastPass == -1) {
            initPhaseTrackers(totalElapsedSeconds, currentPass, incompleteCount);
            return createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
        }

        double safeIncomplete = Math.max(1.0, incompleteCount);
        
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

        if (emaPassDuration < 0 || emaDecayRate < 0) {
            return createFallbackEta(totalElapsedSeconds, incompleteCount, currentPhase);
        }

        // Blend Local EMA (70%) with Global History (30%)
        int totalPhasePasses = currentPass - phaseStartPass;
        double globalDecayRate = -1.0;
        if (totalPhasePasses > 0 && phaseStartIncompleteCount > 0) {
            double safeStartInc = Math.max(1.0, phaseStartIncompleteCount);
            if (safeIncomplete < safeStartInc) {
                globalDecayRate = -Math.log(safeIncomplete / safeStartInc) / totalPhasePasses;
            }
        }

        double effectiveDecay = Math.max(emaDecayRate, MIN_DECAY_RATE);
        if (globalDecayRate > 0) {
            effectiveDecay = (effectiveDecay * 0.70) + (globalDecayRate * 0.30);
        }
        
        double remainingPassesDouble = Math.log(Math.max(incompleteCount, 1) / 0.5) / effectiveDecay;
        int remainingPasses = (int) Math.ceil(remainingPassesDouble);
        
        if (maxPasses != null && maxPasses > 0) {
            int hardPassLimit = Math.max(0, maxPasses - currentPass);
            remainingPasses = Math.min(remainingPasses, hardPassLimit);
        }
        
        double etaSeconds = remainingPasses * emaPassDuration;

        // Confidence and Bounds
        Confidence confidence;
        if (currentPass <= 1) confidence = Confidence.NONE;
        else if (currentPass <= 3) confidence = Confidence.LOW;
        else if (currentPass <= 8) confidence = Confidence.MEDIUM;
        else confidence = Confidence.HIGH;

        double marginRatio;
        if (confidence == Confidence.LOW) marginRatio = 0.25;
        else if (confidence == Confidence.MEDIUM) marginRatio = 0.10;
        else marginRatio = 0.05;

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
    
    phaseStartElapsedSeconds = 0.0;
    phaseStartIncompleteCount = -1;
    phaseStartPass = 0;
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
}