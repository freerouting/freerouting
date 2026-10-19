package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-routes an unfinished board under the configured fallback strategies and keeps the best result.
 * A residual board (a few open connections) is continued first. A fanout-only strategy whose fanout
 * leaves a board an earlier pass loop already started on stops after that fanout.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center).
 */
final class RoutingFallbackRunner {

  private final Map<String, RoutingFallback.Outcome> passLoopStarts = new LinkedHashMap<>();
  private final BatchAutorouter listenerSource;

  RoutingFallbackRunner(BatchAutorouter listenerSource) {
    this.listenerSource = listenerSource;
  }

  BatchAutorouter run(
      RoutingJob job,
      BatchAutorouter defaultRouter,
      List<RoutingFallback.Strategy> strategies,
      byte[] input) {
    ClearanceBaseline inputBaseline = job.board.clearanceBaseline;
    RoutingFallback.Outcome best =
        RoutingFallback.Outcome.of("default", job.board, job.routerSettings);
    if (best.isCompleteAndClean()) {
      return defaultRouter;
    }
    BatchAutorouter.StopReason reason = defaultRouter.lastStopReason;
    if (!RoutingFallback.isRetryable(reason)) {
      job.logInfo(
          "Routing fallback skipped: the pass loop stopped at its "
              + RoutingFallback.describe(reason)
              + " with "
              + best
              + ".");
      return defaultRouter;
    }
    boolean fanoutRuns = job.routerSettings.isFanoutEnabled() && !job.board.getSmdPins().isEmpty();
    List<RoutingFallback.Strategy> applicable =
        RoutingFallback.applicable(strategies, job.routerSettings, fanoutRuns);
    List<RoutingFallback.Strategy> continuations = new ArrayList<>();
    List<RoutingFallback.Strategy> alternatives = new ArrayList<>();
    for (RoutingFallback.Strategy strategy : applicable) {
      if (strategy.continueBest) {
        continuations.add(strategy);
      } else {
        alternatives.add(strategy);
      }
    }
    boolean residual = RoutingSupervisor.residual(job.routerSettings, best.unrouted);
    if (alternatives.isEmpty() && continuations.isEmpty() && !residual) {
      return defaultRouter;
    }
    job.logInfo(
        "Routing fallback: the default configuration stopped at "
            + RoutingFallback.describe(reason)
            + " with "
            + best
            + "; trying "
            + alternatives.size()
            + " alternative "
            + (alternatives.size() == 1 ? "strategy" : "strategies")
            + (residual ? ", then continuing the best board" : "")
            + ".");
    if (defaultRouter.passLoopStartHash != null) {
      this.passLoopStarts.put(defaultRouter.passLoopStartHash, best);
    }
    ClearanceBaseline defaultClearance =
        RoutingFallback.captureClearance(job.board, best.violations);
    Attempt bestAttempt = new Attempt(best, job.board, defaultRouter, defaultClearance);
    RoutingBoard continuedFrom = null;
    if (residual && !alternatives.isEmpty()) {
      bestAttempt = runContinuations(job, bestAttempt, automaticContinuations(job.routerSettings));
      continuedFrom = bestAttempt.board;
    }
    for (RoutingFallback.Strategy strategy : alternatives) {
      if (stopped(job) || bestAttempt.outcome.isCompleteAndClean()) {
        break;
      }
      Attempt attempt = runAttempt(job, input, strategy, false, inputBaseline);
      if (attempt == null) {
        continue;
      }
      if (attempt.router.lastStopReason == BatchAutorouter.StopReason.REPEATED) {
        RoutingFallback.Outcome repeated =
            this.passLoopStarts.get(attempt.router.passLoopStartHash);
        job.logInfo(
            "Routing fallback '"
                + strategy.name
                + "': its fanout left the board an earlier pass loop started on; stopped after the fanout ("
                + (repeated != null ? repeated : attempt.outcome)
                + ").");
        if (stopAlternatives(bestAttempt.outcome, attempt.router.lastStopReason)) {
          break;
        }
        continue;
      }
      if (strategy.fanoutOnly
          && attempt.router.passLoopStartHash != null
          && attempt.router.lastStopReason != BatchAutorouter.StopReason.TIMED_OUT) {
        this.passLoopStarts.putIfAbsent(attempt.router.passLoopStartHash, attempt.outcome);
      }
      job.logInfo(
          "Routing fallback '"
              + strategy.name
              + "': "
              + attempt.outcome
              + " after "
              + RoutingFallback.describe(attempt.router.lastStopReason)
              + ".");
      if (replaces(attempt, bestAttempt)) {
        bestAttempt = attempt;
      } else {
        job.board = bestAttempt.board;
      }
      if (stopAlternatives(bestAttempt.outcome, attempt.router.lastStopReason)) {
        break;
      }
    }
    List<RoutingFallback.Strategy> after = new ArrayList<>(continuations);
    if (continuedFrom != null && bestAttempt.board == continuedFrom) {
      if (!bestAttempt.outcome.isCompleteAndClean()) {
        job.logInfo(
            "Routing fallback: no alternative replaced the board the continuations ran on; not continuing it again.");
      }
    } else {
      after.addAll(automaticContinuations(job.routerSettings));
    }
    if (!after.isEmpty()) {
      bestAttempt = runContinuations(job, bestAttempt, after);
    }
    job.board = bestAttempt.board;
    job.logInfo(
        "Routing fallback chose '"
            + bestAttempt.outcome.strategy
            + "': "
            + bestAttempt.outcome
            + ".");
    return bestAttempt.router;
  }

  /**
   * Exploration attempts after fallback. Each attempt is a full route of the best board with a
   * lighter fallback list. Later tries of a kind go further. Stops after {@code explore_patience}
   * attempts without an improvement, or when the board is complete.
   */
  BatchAutorouter explore(RoutingJob job, BatchAutorouter incumbent) {
    if (!RoutingSupervisor.explores(job.routerSettings) || stopped(job)) {
      return incumbent;
    }
    RoutingSupervisor supervisor = new RoutingSupervisor();
    RoutingFallback.Outcome best =
        RoutingFallback.Outcome.of("incumbent", job.board, job.routerSettings);
    Attempt bestAttempt = new Attempt(best, job.board, incumbent, null);
    RouterSettings jobSettings = job.routerSettings;
    int round = 0;
    while (!bestAttempt.outcome.isCompleteAndClean()
        && !supervisor.explorationExhausted(jobSettings)
        && !stopped(job)) {
      round++;
      RoutingSupervisor.Attempt planned =
          supervisor.nextAttempt(round, jobSettings, bestAttempt.board);
      RouterSettings settings = jobSettings.clone();
      RoutingSupervisor.apply(planned, jobSettings, settings);
      byte[] input;
      try {
        input = bestAttempt.board.serialize(false);
      } catch (RuntimeException e) {
        job.logWarning("Exploration stopped; the best board could not be saved: " + e.getMessage());
        break;
      }
      RoutingBoard copy = (RoutingBoard) BasicBoard.deserialize(input);
      if (copy.clearanceBaseline == null) {
        copy.clearanceBaseline = bestAttempt.board.clearanceBaseline;
      }
      if ("neighbourhood".equals(planned.kind())) {
        int ripped = RetryNeighbourhood.ripNearOpenPins(copy, planned.tryNumber());
        job.logInfo(
            "Exploration '"
                + planned.name()
                + "' ripped "
                + ripped
                + " item(s) within "
                + RetryNeighbourhood.Reach.of(planned.tryNumber()).radiusMm()
                + " mm.");
      }
      job.logInfo("Exploration attempt '" + planned.name() + "' from " + bestAttempt.outcome + ".");
      int openBefore = bestAttempt.outcome.unrouted;
      Attempt attempt = runPrepared(job, copy, settings, false, "explore-" + planned.kind());
      boolean kept = false;
      if (attempt != null && replaces(attempt, bestAttempt)) {
        bestAttempt = attempt;
        kept = true;
      }
      int openAfter = bestAttempt.outcome.unrouted;
      supervisor.attemptDone(planned, openBefore, openAfter, kept);
    }
    job.board = bestAttempt.board;
    job.routerSettings = jobSettings;
    return bestAttempt.router;
  }

  private Attempt runContinuations(
      RoutingJob job, Attempt bestAttempt, List<RoutingFallback.Strategy> continuations) {
    for (RoutingFallback.Strategy strategy : continuations) {
      if (bestAttempt.outcome.isCompleteAndClean() || stopped(job)) {
        break;
      }
      if (!strategy.continueBest
          && !RoutingSupervisor.residual(job.routerSettings, bestAttempt.outcome.unrouted)) {
        break;
      }
      RoutingBoard copy = bestAttempt.board.deepCopy();
      RouterSettings settings = job.routerSettings.clone();
      strategy.apply(settings);
      settings.setFanoutEnabled(false);
      Attempt attempt = runPrepared(job, copy, settings, true, strategy.name);
      if (attempt == null) {
        break;
      }
      job.logInfo(
          "Routing fallback continuation '"
              + strategy.name
              + "': "
              + attempt.outcome
              + " after "
              + RoutingFallback.describe(attempt.router.lastStopReason)
              + ".");
      if (replaces(attempt, bestAttempt)) {
        bestAttempt = attempt;
      } else if (!strategy.continueBest) {
        job.board = bestAttempt.board;
        job.logInfo(
            "Routing fallback: stopping residual continuations after '"
                + strategy.name
                + "' did not improve the incumbent.");
        break;
      } else {
        job.board = bestAttempt.board;
      }
    }
    return bestAttempt;
  }

  private Attempt runAttempt(
      RoutingJob job,
      byte[] input,
      RoutingFallback.Strategy strategy,
      boolean keepFanoutVias,
      ClearanceBaseline inputBaseline) {
    RoutingBoard board = (RoutingBoard) BasicBoard.deserialize(input);
    if (board.clearanceBaseline == null) {
      board.clearanceBaseline = inputBaseline;
    }
    RouterSettings settings = job.routerSettings.clone();
    strategy.apply(settings);
    Attempt attempt = runPrepared(job, board, settings, keepFanoutVias, strategy.name);
    if (attempt != null
        && strategy.fanoutOnly
        && attempt.router.lastStopReason == BatchAutorouter.StopReason.REPEATED) {
      RoutingFallback.Outcome repeated = this.passLoopStarts.get(attempt.router.passLoopStartHash);
      if (repeated != null) {
        return new Attempt(
            new RoutingFallback.Outcome(
                strategy.name,
                repeated.unrouted,
                repeated.newOrChanged,
                repeated.violations,
                repeated.score,
                repeated.designBlocked),
            job.board,
            attempt.router,
            attempt.baseline);
      }
    }
    return attempt;
  }

  private Attempt runPrepared(
      RoutingJob job,
      RoutingBoard board,
      RouterSettings settings,
      boolean keepFanoutVias,
      String strategyName) {
    RouterSettings savedSettings = job.routerSettings;
    RoutingBoard savedBoard = job.board;
    job.thread.resetAutoRouterStopRequest();
    job.board = board;
    job.routerSettings = settings;
    try {
      boolean removeUnconnectedVias = !keepFanoutVias && !settings.isFanoutEnabled();
      BatchAutorouter router =
          BatchAutorouter.createAttempt(job, board, settings, removeUnconnectedVias);
      router.copyListenersFrom(this.listenerSource);
      RoutingFallback.Strategy strategy = RoutingFallback.find(strategyName);
      if (strategy != null && strategy.fanoutOnly) {
        router.setRepeatsPassLoop(hash -> hash != null && this.passLoopStarts.containsKey(hash));
      }
      router.runBatchLoop();
      RoutingFallback.Outcome outcome =
          RoutingFallback.Outcome.of(strategyName, job.board, savedSettings);
      ClearanceBaseline baseline = RoutingFallback.captureClearance(job.board, outcome.violations);
      return new Attempt(outcome, job.board, router, baseline);
    } catch (RuntimeException e) {
      job.board = savedBoard;
      job.logError("Routing fallback attempt '" + strategyName + "' failed and was discarded.", e);
      return null;
    } finally {
      job.routerSettings = savedSettings;
    }
  }

  private static boolean replaces(Attempt candidate, Attempt incumbent) {
    int changed =
        RoutingFallback.newOrChangedClearance(
            candidate.board, candidate.outcome.violations, incumbent.baseline);
    return RoutingFallback.Outcome.canReplace(candidate.outcome, incumbent.outcome, changed);
  }

  private static boolean stopAlternatives(
      RoutingFallback.Outcome best, BatchAutorouter.StopReason reason) {
    return best.isCompleteAndClean()
        || reason == BatchAutorouter.StopReason.TIMED_OUT
        || reason == BatchAutorouter.StopReason.CANCELLED;
  }

  private static boolean stopped(RoutingJob job) {
    return job.thread.isStopRequested() || job.state == RoutingJobState.TIMED_OUT;
  }

  private static List<RoutingFallback.Strategy> automaticContinuations(RouterSettings settings) {
    List<RoutingFallback.Strategy> result = new ArrayList<>();
    RoutingFallback.Strategy continueStrategy = RoutingFallback.find("continue");
    if (continueStrategy != null) {
      result.add(continueStrategy);
    }
    if (settings.getNeckWidthUm() > 0) {
      RoutingFallback.Strategy noNeck = RoutingFallback.find("continue-no-neck");
      if (noNeck != null) {
        result.add(noNeck);
      }
    }
    return result;
  }

  private record Attempt(
      RoutingFallback.Outcome outcome,
      RoutingBoard board,
      BatchAutorouter router,
      ClearanceBaseline baseline) {}
}
