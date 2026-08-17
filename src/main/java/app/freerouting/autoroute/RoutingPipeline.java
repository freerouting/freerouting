package app.freerouting.autoroute;

import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEventListener;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingStage;
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
  private final BatchAutorouter autorouter;
  private final BatchOptimizer optimizer;
  private final List<StageListener> stageListeners = new ArrayList<>();

  private RoutingPipeline(RoutingJob job, Function<RoutingJob, BatchOptimizer> optimizerFactory) {
    this.job = job;
    normalizeRouterAlgorithm(job);
    this.autorouter = new BatchAutorouter(job);
    this.optimizer = job.routerSettings.getRunOptimizer() ? optimizerFactory.apply(job) : null;
  }

  /** Creates a pipeline using the GUI optimizer policy, including optional multithreading. */
  public static RoutingPipeline createForGui(RoutingJob job) {
    return new RoutingPipeline(job, BatchOptimizer::createForGui);
  }

  /** Creates a pipeline using the headless single-threaded optimizer policy. */
  public static RoutingPipeline createForHeadless(RoutingJob job) {
    return new RoutingPipeline(job, BatchOptimizer::createForHeadless);
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
    this.autorouter.addBoardUpdatedEventListener(listener);
    if (this.optimizer != null) {
      this.optimizer.addBoardUpdatedEventListener(listener);
    }
  }

  /** Forwards task-state updates from both algorithm stages to the supplied listener. */
  public void addTaskStateChangedEventListener(TaskStateChangedEventListener listener) {
    this.autorouter.addTaskStateChangedEventListener(listener);
    if (this.optimizer != null) {
      this.optimizer.addTaskStateChangedEventListener(listener);
    }
  }

  /** Runs the configured stages in order. */
  public void run() {
    runRoutingStage();
    runOptimizationStage();
    this.job.stage = RoutingStage.IDLE;
  }

  private void runRoutingStage() {
    boolean routerEnabled =
        this.job.routerSettings.getRunRouter()
            && (this.job.routerSettings.maxPasses == null
                || this.job.routerSettings.maxPasses >= 0);

    if (routerEnabled || this.job.routerSettings.isFanoutEnabled()) {
      this.job.stage = RoutingStage.ROUTING;
    }

    if (routerEnabled && !this.job.thread.isStopAutoRouterRequested()) {
      this.autorouter.runBatchLoop();
    } else if (this.job.routerSettings.isFanoutEnabled()
        && !this.job.thread.isStopAutoRouterRequested()) {
      Integer originalMaxPasses = this.job.routerSettings.maxPasses;
      try {
        this.job.routerSettings.maxPasses = 0;
        this.autorouter.runBatchLoop();
      } finally {
        this.job.routerSettings.maxPasses = originalMaxPasses;
      }
    }

    this.job.board.finishAutoroute();
    for (StageListener listener : this.stageListeners) {
      listener.afterRouting(this.autorouter);
    }
  }

  private void runOptimizationStage() {
    if (this.optimizer == null || this.job.thread.isStopRequested()) {
      return;
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
    String algorithm = job.routerSettings.algorithm;
    if (!RouterSettings.ALGORITHM_CURRENT.equals(algorithm)) {
      job.logWarning(
          "The algorithm '"
              + algorithm
              + "' is not supported. The default algorithm '"
              + RouterSettings.ALGORITHM_CURRENT
              + "' will be used instead.");
      job.routerSettings.algorithm = RouterSettings.ALGORITHM_CURRENT;
    }
  }
}
