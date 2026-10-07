package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEventListener;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingStage;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Sequences the shared fanout, autoroute, and optimization stages for a routing job. */
public final class RoutingPipeline {

  /** Receives lifecycle callbacks between pipeline stages. */
  public interface StageListener {
    /** Invoked after fanout and autorouting have completed. */
    default void afterRouting(BatchAutorouter autorouter) {}

    /** Invoked immediately before optimization starts. */
    default void beforeOptimization(BatchOptimizer optimizer) {}

    /** Invoked after optimization has completed. */
    default void afterOptimization(BatchOptimizer optimizer) {}
  }

  private final RoutingJob job;
  private BatchAutorouter autorouter;
  private final BatchOptimizer optimizer;
  private final List<StageListener> stageListeners = new ArrayList<>();
  private final List<BoardUpdatedEventListener> boardUpdatedListeners = new ArrayList<>();
  private final List<TaskStateChangedEventListener> taskStateListeners = new ArrayList<>();

  private RoutingPipeline(RoutingJob job, Function<RoutingJob, BatchOptimizer> optimizerFactory) {
    this.job = job;
    normalizeRouterAlgorithm(job);
    this.autorouter = new BatchAutorouter(job);
    this.optimizer = job.routerSettings.getRunOptimizer() ? optimizerFactory.apply(job) : null;
  }

  /** Creates the canonical routing pipeline for a routing job. */
  public static RoutingPipeline create(RoutingJob job) {
    return new RoutingPipeline(job, BatchOptimizer::create);
  }

  /** Creates a pipeline using the GUI optimizer policy. */
  public static RoutingPipeline createForGui(RoutingJob job) {
    return create(job);
  }

  /** Creates a pipeline using the headless optimizer policy. */
  public static RoutingPipeline createForHeadless(RoutingJob job) {
    return create(job);
  }

  /** Returns the shared autorouter stage. */
  public BatchAutorouter getAutorouter() {
    return this.autorouter;
  }

  /** Returns the configured optimizer stage, or {@code null} when optimization is disabled. */
  public BatchOptimizer getOptimizer() {
    return this.optimizer;
  }

  /** Registers a listener for stage transitions. */
  public void addStageListener(StageListener listener) {
    this.stageListeners.add(listener);
  }

  /** Forwards board updates from both algorithm stages to the supplied listener. */
  public void addBoardUpdatedEventListener(BoardUpdatedEventListener listener) {
    this.boardUpdatedListeners.add(listener);
    this.autorouter.addBoardUpdatedEventListener(listener);
    if (this.optimizer != null) {
      this.optimizer.addBoardUpdatedEventListener(listener);
    }
  }

  /** Forwards task-state updates from both algorithm stages to the supplied listener. */
  public void addTaskStateChangedEventListener(TaskStateChangedEventListener listener) {
    this.taskStateListeners.add(listener);
    this.autorouter.addTaskStateChangedEventListener(listener);
    if (this.optimizer != null) {
      this.optimizer.addTaskStateChangedEventListener(listener);
    }
  }

  /** Runs the configured stages in order. */
  public void run() {
    if (this.job != null && this.job.board != null) {
      this.job.board.awaitPostLoad();
    }
    runRoutingStage();
    runOptimizationStage();
    this.job.stage = RoutingStage.IDLE;
  }

  private void runRoutingStage() {
    boolean routerEnabled =
        this.job.routerSettings.getRunRouter()
            && (this.job.routerSettings.autorouter.maxPasses == null
                || this.job.routerSettings.autorouter.maxPasses >= 0);

    if (routerEnabled || this.job.routerSettings.isFanoutEnabled()) {
      this.job.stage = RoutingStage.ROUTING;
    }

    List<RoutingFallback.Strategy> strategies =
        RoutingFallback.parse(
            this.job.routerSettings,
            name ->
                this.job.logWarning(
                    "Unknown routing fallback strategy '"
                        + name
                        + "' ignored; known strategies: "
                        + RoutingFallback.knownNames()
                        + "."));
    byte[] input =
        (!strategies.isEmpty() && this.job.board != null) ? this.job.board.serialize(false) : null;

    if (routerEnabled && !this.job.thread.isStopAutoRouterRequested()) {
      this.autorouter.runBatchLoop();
      if (input != null) {
        runFallback(strategies, input);
      }
    } else if (this.job.routerSettings.isFanoutEnabled()
        && !this.job.thread.isStopAutoRouterRequested()) {
      Integer originalMaxPasses = this.job.routerSettings.autorouter.maxPasses;
      try {
        this.job.routerSettings.autorouter.maxPasses = 0;
        this.autorouter.runBatchLoop();
      } finally {
        this.job.routerSettings.autorouter.maxPasses = originalMaxPasses;
      }
    }

    this.job.board.finishAutoroute();
    for (StageListener listener : this.stageListeners) {
      listener.afterRouting(this.autorouter);
    }
  }

  private void runFallback(List<RoutingFallback.Strategy> strategies, byte[] input) {
    RoutingFallback.Outcome best =
        RoutingFallback.Outcome.of("default", this.job.board, this.job.routerSettings);
    if (best.isCompleteAndClean()) {
      return;
    }
    BatchAutorouter.StopReason reason = this.autorouter.lastStopReason;
    if (!RoutingFallback.isRetryable(reason)) {
      this.job.logInfo(
          "Routing fallback skipped: the pass loop stopped at its "
              + RoutingFallback.describe(reason)
              + " with "
              + best
              + ".");
      return;
    }
    List<RoutingFallback.Strategy> applicable =
        RoutingFallback.applicable(strategies, this.job.routerSettings, this.job.board);
    if (applicable.size() < strategies.size()) {
      int diff = strategies.size() - applicable.size();
      this.job.logInfo(
          "Routing fallback: "
              + diff
              + " strateg"
              + (diff == 1 ? "y does" : "ies do")
              + " not apply to this board's routing settings.");
    }
    if (applicable.isEmpty()) {
      return;
    }
    this.job.logInfo(
        "Routing fallback: the default configuration stopped at "
            + RoutingFallback.describe(reason)
            + " with "
            + best
            + "; trying "
            + applicable.size()
            + " alternative strateg"
            + (applicable.size() == 1 ? "y" : "ies")
            + ".");

    RoutingResultManifest.FallbackDetail detail = new RoutingResultManifest.FallbackDetail();
    detail.chosen = "default";
    RoutingResultManifest.FallbackAttempt defaultAttempt =
        new RoutingResultManifest.FallbackAttempt();
    defaultAttempt.strategy = best.strategy;
    defaultAttempt.unrouted = best.unrouted;
    defaultAttempt.newOrChangedViolations = best.newOrChanged;
    defaultAttempt.clearanceViolations = best.violations;
    defaultAttempt.routerScore = best.score;
    defaultAttempt.stopReason = RoutingFallback.manifestName(reason);
    float defaultSeconds =
        (this.job.resultPhaseMetrics.fanout.durationSeconds != null
                ? this.job.resultPhaseMetrics.fanout.durationSeconds
                : 0f)
            + (this.job.resultPhaseMetrics.autorouter.durationSeconds != null
                ? this.job.resultPhaseMetrics.autorouter.durationSeconds
                : 0f);
    defaultAttempt.durationSeconds = defaultSeconds;
    detail.attempts.add(defaultAttempt);
    this.job.resultPhaseMetrics.fallback = detail;

    Double fallbackMaxSeconds =
        this.job.routerSettings.autorouter != null
            ? this.job.routerSettings.autorouter.fallbackMaxSeconds
            : null;
    long startFallbackMillis = System.currentTimeMillis();
    RoutingBoard bestBoard = this.job.board;
    ClearanceBaseline bestClearance = RoutingFallback.captureClearance(bestBoard, best.violations);
    int attempts = 0;

    for (RoutingFallback.Strategy strategy : applicable) {
      if (this.job.thread != null) {
        this.job.thread.resetStopAutoRouterRequest();
        if (this.job.thread.isStopRequested()) {
          break;
        }
      }
      if (fallbackMaxSeconds != null && fallbackMaxSeconds > 0) {
        double elapsedSec = (System.currentTimeMillis() - startFallbackMillis) / 1000.0;
        if (elapsedSec >= fallbackMaxSeconds) {
          this.job.logInfo("Routing fallback: budget of " + fallbackMaxSeconds + "s expired.");
          break;
        }
      }
      attempts++;
      long attemptStartMillis = System.currentTimeMillis();
      byte[] sourceBytes = strategy.continueBest ? bestBoard.serialize(false) : input;
      RoutingBoard boardCopy = (RoutingBoard) BasicBoard.deserialize(sourceBytes);
      RouterSettings settingsCopy = this.job.routerSettings.clone();
      strategy.apply(settingsCopy);

      BatchAutorouter router = new BatchAutorouter(this.job, boardCopy, settingsCopy);
      for (BoardUpdatedEventListener listener : this.boardUpdatedListeners) {
        router.addBoardUpdatedEventListener(listener);
      }
      for (TaskStateChangedEventListener listener : this.taskStateListeners) {
        router.addTaskStateChangedEventListener(listener);
      }

      this.job.board = boardCopy;
      try {
        router.runBatchLoop();
      } catch (Exception e) {
        FRLogger.error(
            "Fallback attempt '" + strategy.name + "' failed with error: " + e.getMessage(), e);
        this.job.board = bestBoard;
        continue;
      }

      RoutingBoard routed = this.job.board;
      RoutingFallback.Outcome outcome =
          RoutingFallback.Outcome.of(strategy.name, routed, this.job.routerSettings);
      double attemptSec = (System.currentTimeMillis() - attemptStartMillis) / 1000.0;
      this.job.logInfo(
          String.format(
              java.util.Locale.US,
              "Routing fallback attempt %d/%d '%s': %s after %s in %.2f s.",
              attempts,
              applicable.size(),
              strategy.name,
              outcome,
              RoutingFallback.describe(router.lastStopReason),
              attemptSec));

      RoutingResultManifest.FallbackAttempt attempt = new RoutingResultManifest.FallbackAttempt();
      attempt.strategy = strategy.name;
      attempt.unrouted = outcome.unrouted;
      attempt.newOrChangedViolations = outcome.newOrChanged;
      attempt.clearanceViolations = outcome.violations;
      attempt.routerScore = outcome.score;
      attempt.stopReason = RoutingFallback.manifestName(router.lastStopReason);
      attempt.durationSeconds = (float) attemptSec;
      detail.attempts.add(attempt);

      int newOrChanged =
          RoutingFallback.newOrChangedClearance(routed, outcome.violations, bestClearance);
      boolean better = RoutingFallback.Outcome.canReplace(outcome, best, newOrChanged);
      if (better) {
        best = outcome;
        bestBoard = routed;
        bestClearance = RoutingFallback.captureClearance(bestBoard, best.violations);
        this.autorouter = router;
        detail.chosen = strategy.name;
      }
      if (outcome.isCompleteAndClean()) {
        break;
      }
    }
    this.job.board = bestBoard;
  }

  private void runOptimizationStage() {
    if (this.optimizer == null || this.job.thread.isStopRequested()) {
      return;
    }

    if (this.job.thread != null) {
      this.job.thread.resetStopAutoRouterRequest();
    }

    this.job.stage = RoutingStage.OPTIMIZATION;
    for (StageListener listener : this.stageListeners) {
      listener.beforeOptimization(this.optimizer);
    }
    this.optimizer.runBatchLoop();
    for (StageListener listener : this.stageListeners) {
      listener.afterOptimization(this.optimizer);
    }
  }

  private static void normalizeRouterAlgorithm(RoutingJob job) {
    String algorithm = job.routerSettings.autorouter.algorithm;
    if (!RouterSettings.ALGORITHM_CURRENT.equals(algorithm)) {
      job.logWarning(
          "The algorithm '"
              + algorithm
              + "' is not supported. The default algorithm '"
              + RouterSettings.ALGORITHM_CURRENT
              + "' will be used instead.");
      job.routerSettings.autorouter.algorithm = RouterSettings.ALGORITHM_CURRENT;
    }
  }
}
