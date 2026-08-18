package app.freerouting.autoroute.pipeline;

import static app.freerouting.autoroute.pipeline.BatchAutorouter.BOARD_RANK_LIMIT;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.FANOUT_RECOVERY_STAGNATION_PASSES;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.MAXIMUM_TRIES_ON_THE_SAME_BOARD;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.STAGNATION_PASS_LIMIT;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.STAGNATION_SCORE_THRESHOLD;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.STOP_AT_PASS_MINIMUM;
import static app.freerouting.autoroute.pipeline.BatchAutorouter.STOP_AT_PASS_MODULO;

import app.freerouting.autoroute.BoardHistory;
import app.freerouting.autoroute.PerformanceProfiler;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.time.Instant;
import java.util.Set;

/** Owns fanout, autoroute-pass, stagnation, and final-board lifecycle decisions. */
final class AutorouteBatchLoop {

  private final BatchAutorouter router;

  AutorouteBatchLoop(BatchAutorouter router) {
    this.router = router;
  }

  boolean run() {
    RoutingBoard board = router.board;
    final RouterSettings settings = router.settings;
    final RoutingJob job = router.job;
    final StoppableThread thread = router.thread;
    final boolean isOptimizerAutorouter = router.isOptimizerAutorouter;

    boolean anyRoutable = false;
    for (int i = 0; i < router.settings.getLayerCount(); i++) {
      if (router.settings.getLayerActive(i) && router.board.layerStructure.layers[i].isSignal) {
        anyRoutable = true;
        break;
      }
    }
    if (!anyRoutable) {
      FRLogger.warn("Cannot start autorouter: all layers are disabled.");
      router.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(router, TaskState.CANCELLED, 0, router.board.getHash()));
      throw new IllegalArgumentException("Cannot start autorouter: all layers are disabled.");
    }

    router.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(router, TaskState.STARTED, 0, router.board.getHash()));

    // Capture initial state for session summary
    router.sessionStartTime = Instant.now();
    router.initialUnroutedCount = calculateIncompleteCount(router.board);

    final BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    // Record configuration for profiler
    if (router.settings.getLayerCount() > 0) {
      int layerCount = router.settings.getLayerCount();
      double[] prefCosts = new double[layerCount];
      double[] againstCosts = new double[layerCount];
      for (int i = 0; i < layerCount; i++) {
        prefCosts[i] = router.settings.getPreferredDirectionTraceCosts(i);
        againstCosts[i] = router.settings.getAgainstPreferredDirectionTraceCosts(i);
      }
      PerformanceProfiler.recordConfiguration(
          router.settings.getViaCosts(),
          router.settings.getPlaneViaCosts(),
          prefCosts,
          againstCosts);
    }

    job.logDebug(
        "Checking fanout pre-pass. settings.fanout.enabled="
            + router.settings.isFanoutEnabled()
            + ", smdPins="
            + router.board.getSmdPins().size());
    // Run SMD fanout pre-pass when the board has SMD pins and fanout is enabled
    if (router.settings.isFanoutEnabled()) {
      if (router.board.getSmdPins().isEmpty()) {
        job.logInfo("Fanout stage is enabled but skipped because the board has no SMD pins.");
      } else {
        final float fanoutCpuSecondsStart = AutorouteRuntimeMetrics.currentThreadCpuSeconds();
        final float fanoutAllocatedMbStart = AutorouteRuntimeMetrics.currentThreadAllocatedMb();
        float fanoutPeakHeapMbAtStart = AutorouteRuntimeMetrics.currentHeapUsageMb();
        final float[] fanoutPeakHeapMbObserved = new float[] {fanoutPeakHeapMbAtStart};
        // Count pins that actually need fanout. BatchFanout only processes SMD pins that
        // belong to a net, so exclude netless pins from the total. Among net-connected
        // pins, count those that are already fully connected (empty unconnected set).
        int netConnectedSmdPins = 0;
        int alreadyConnectedAtStart = 0;
        for (app.freerouting.board.model.items.Pin pin : router.board.getSmdPins()) {
          if (pin.netCount() > 0) {
            netConnectedSmdPins++;
            if (pin.getUnconnectedSet(pin.getNetNumber(0)).isEmpty()) {
              alreadyConnectedAtStart++;
            }
          }
        }
        int pinsToFanout = netConnectedSmdPins - alreadyConnectedAtStart;
        job.logInfo(
            "Fanout stage started on board '"
                + router.board.getHash()
                + "' with "
                + pinsToFanout
                + " of "
                + router.board.getSmdPins().size()
                + " SMD pins needing fanout ("
                + alreadyConnectedAtStart
                + " already connected, "
                + (router.board.getSmdPins().size() - netConnectedSmdPins)
                + " netless).");
        BatchFanout.FanoutRunSummary fanoutSummary =
            BatchFanout.fanoutBoard(
                router.board,
                router.settings,
                router.thread,
                status -> {
                  fanoutPeakHeapMbObserved[0] =
                      Math.max(
                          fanoutPeakHeapMbObserved[0],
                          AutorouteRuntimeMetrics.currentHeapUsageMb());
                  RouterCounters fanoutCounters = new RouterCounters();
                  fanoutCounters.phase = "fanout";
                  fanoutCounters.passCount = status.passNo();
                  fanoutCounters.queuedToBeRoutedCount = status.pinsToGo();
                  fanoutCounters.routedCount = status.routedCount();
                  fanoutCounters.skippedCount = 0;
                  fanoutCounters.rippedCount = 0;
                  fanoutCounters.failedToBeRoutedCount =
                      status.notRoutedCount() + status.insertErrorCount();
                  fanoutCounters.incompleteCount =
                      status.boardStatistics().connections.incompleteCount;
                  fanoutCounters.fanoutExtraViasCount = status.extraViasThisPass();
                  router.fireBoardUpdatedEvent(
                      status.boardStatistics(), fanoutCounters, router.board);

                  if (status.passCompleted()) {
                    String boardHash = router.board.getHash();
                    String fanoutMessage =
                        String.format(
                            java.util.Locale.US,
                            "Fanout pass #%d on board '%s' completed in %.2f seconds with "
                                + "%d SMD pin%s fanouted, %d not routed, %d insert error%s, "
                                + "+%d extra via%s (%d SMD pin%s still to check in pass, "
                                + "ripup costs=%d).",
                            status.passNo(),
                            boardHash,
                            status.passDurationMillis() / 1000.0,
                            status.routedCount(),
                            status.routedCount() == 1 ? "" : "s",
                            status.notRoutedCount(),
                            status.insertErrorCount(),
                            status.insertErrorCount() == 1 ? "" : "s",
                            status.extraViasThisPass(),
                            status.extraViasThisPass() == 1 ? "" : "s",
                            status.pinsToGo(),
                            status.pinsToGo() == 1 ? "" : "s",
                            status.ripupCosts());
                    job.logInfo(fanoutMessage);
                  }
                });
        router.fanoutTimedOut = fanoutSummary.isTimedOut();

        float fanoutCpuSecondsEnd = AutorouteRuntimeMetrics.currentThreadCpuSeconds();
        float fanoutAllocatedMbEnd = AutorouteRuntimeMetrics.currentThreadAllocatedMb();

        float fanoutCpuSecondsUsed;
        if (fanoutCpuSecondsStart >= 0f && fanoutCpuSecondsEnd >= fanoutCpuSecondsStart) {
          fanoutCpuSecondsUsed = fanoutCpuSecondsEnd - fanoutCpuSecondsStart;
        } else {
          fanoutCpuSecondsUsed = Math.max(0f, AutorouteRuntimeMetrics.cpuSecondsSnapshot(job));
        }

        float fanoutAllocatedMb;
        if (fanoutAllocatedMbStart >= 0f && fanoutAllocatedMbEnd >= fanoutAllocatedMbStart) {
          fanoutAllocatedMb = fanoutAllocatedMbEnd - fanoutAllocatedMbStart;
        } else {
          fanoutAllocatedMb = Math.max(0f, AutorouteRuntimeMetrics.allocatedMemoryMbSnapshot(job));
        }

        float fanoutPeakHeapMb =
            Math.max(fanoutPeakHeapMbObserved[0], AutorouteRuntimeMetrics.currentHeapUsageMb());
        fanoutPeakHeapMb =
            Math.max(fanoutPeakHeapMb, AutorouteRuntimeMetrics.peakHeapMbSnapshot(job));
        BatchFanout.EscapeStatistics finalEscape = fanoutSummary.escapeStatistics();
        String fanoutCompletionStatus =
            fanoutSummary.isTimedOut()
                ? "completed with timeout:"
                : (router.thread.isStopAutoRouterRequested() ? "interrupted:" : "completed:");
        String fanoutSummaryMessage =
            String.format(
                java.util.Locale.US,
                "Fanout stage %s started with %d total SMD pins, completed in %.2f seconds, "
                    + "escaped pins: %d/%d (%.1f%%), using %.2f total CPU seconds, "
                    + "%.2f GB total allocated, and %.1f MB peak heap usage.",
                fanoutCompletionStatus,
                finalEscape.totalSmdPins(),
                fanoutSummary.totalDurationMillis() / 1000.0,
                finalEscape.escapedCount(),
                finalEscape.totalSmdPins(),
                finalEscape.escapedPercentage(),
                fanoutCpuSecondsUsed,
                fanoutAllocatedMb / 1024.0f,
                fanoutPeakHeapMb);
        job.logInfo(fanoutSummaryMessage);
      }
    }

    int currentUnrouted = calculateIncompleteCount(router.board);
    boolean isRouterEnabled =
        router.settings.getRunRouter()
            && (router.settings.maxPasses == null || router.settings.maxPasses >= 0);
    if (isRouterEnabled) {
      job.logInfo(
          "Auto-routing stage started on board '"
              + router.board.getHash()
              + "' for "
              + currentUnrouted
              + " unrouted item"
              + (currentUnrouted == 1 ? "" : "s")
              + ".");
    }
    boolean continueAutorouting = isRouterEnabled;

    int currentPass = 1;
    int consecutiveNoImprovementPasses = 0;
    boolean fanoutRecoveryApplied = false;
    float lastBestScore = Float.NEGATIVE_INFINITY; // score at last board-restore or improvement
    float globalBestScore = Float.NEGATIVE_INFINITY; // best score seen across all passes
    int passOfBestScore = 0; // pass where globalBestScore was achieved
    int incompleteCountAtBestScore = 0; // incomplete count when globalBestScore was recorded
    // Track board hashes that have already been routed. If the board does not change between
    // two consecutive passes (same hash at pass start), the router is making no progress and
    // would produce identical decisions with identical ripup budgets — stop immediately rather
    // than waiting for the full stagnation window. This mirrors the v1.9 behaviour and catches
    // the degenerate case where plane-net items repeatedly fail or are inserted+removed each
    // pass without updating the board state.
    Set<String> alreadyRoutedBoardHashes = new java.util.HashSet<>();
    while (continueAutorouting && !router.thread.isStopAutoRouterRequested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        router.thread.requestStopAutoRouter();
      }

      String currentBoardHash = router.board.getHash();

      // Same-hash stop disabled because ripup budgets and random seeds change per-pass, making
      // progress possible in later passes.
      // if (alreadyRoutedBoardHashes.contains(currentBoardHash)) {
      //   job.logInfo("Board state has not changed since pass #" + (currentPass - 1)
      //       + " (hash " + currentBoardHash + "). The auto-router cannot make further progress;
      // stopping.");
      //   thread.request_stop_auto_router();
      //   break;
      // }
      // alreadyRoutedBoardHashes.add(currentBoardHash);

      if (router.settings.maxPasses != null
          && router.settings.maxPasses > 0
          && currentPass > router.settings.maxPasses) {
        thread.requestStopAutoRouter();
        break;
      }

      if (job != null) {
        job.setCurrentPass(currentPass);
      }

      router.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(router, TaskState.RUNNING, currentPass, currentBoardHash));

      float boardScoreBefore =
          new BoardStatistics(router.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(router.board);

      FRLogger.traceEntry(
          "BatchAutorouter.autoroute_pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "'");

      continueAutorouting = autoroutePass(currentPass);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(router.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (router.thread.isStopAutoRouterRequested())) {
        if (((currentPass % STOP_AT_PASS_MODULO == 0) && (currentPass >= STOP_AT_PASS_MINIMUM))
            || (router.thread.isStopAutoRouterRequested())) {
          // Check if the score improved compared to the previous passes, restore a
          // previous board if not. Use strict ">" so that equally-scored boards do NOT
          // trigger a restore — if every board has the same (possibly zero) score the old
          // ">=" test would restore on every check cycle, growing the history unboundedly
          // and never stopping.
          if (bh.getMaxScore() > boardScoreAfter) {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null) {
              job.logInfo(
                  "The router was not able to improve the board, stopping the auto-router.");
              thread.requestStopAutoRouter();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT) {
              thread.requestStopAutoRouter();
              break;
            }

            router.board = boardToRestore;
            board = router.board;
            var boardStatistics = router.board.getStatistics();
            // Reset pass-local stagnation counter when restoring a previous board state
            consecutiveNoImprovementPasses = 0;
            boardStatisticsAfter = boardStatistics;
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            currentBoardHash = router.board.getHash();
            // Reset the same-hash set after a board restore: the restored board will be
            // routed with a higher ripup budget on subsequent passes, so earlier routing
            // decisions from the same hash may no longer apply.
            alreadyRoutedBoardHashes.clear();
            job.logDebug(
                "Restoring an earlier board that has the score of "
                    + FRLogger.formatScore(
                        boardScoreAfter,
                        boardStatisticsAfter.connections.incompleteCount,
                        boardStatisticsAfter.clearanceViolations.totalCount)
                    + ".");
          }
        }
      }
      double autorouterPassDuration =
          FRLogger.traceExit(
              "BatchAutorouter.autoroute_pass #"
                  + currentPass
                  + " on board '"
                  + currentBoardHash
                  + "'");

      String passCompletedMessage =
          String.format(
              java.util.Locale.US,
              "Auto-routing pass #%d on board '%s' was completed in %.2f seconds with score %s",
              currentPass,
              currentBoardHash,
              autorouterPassDuration,
              FRLogger.formatScore(
                  boardScoreAfter,
                  boardStatisticsAfter.connections.incompleteCount,
                  boardStatisticsAfter.clearanceViolations.totalCount));
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage +=
            String.format(
                java.util.Locale.US,
                ", using %.2f CPU seconds and the job allocated %.2f GB of memory so far.",
                job.resourceUsage.cpuTimeUsed,
                job.resourceUsage.maxMemoryUsed / 1024.0f);
      } else {
        passCompletedMessage += ".";
      }
      if (!isOptimizerAutorouter) {
        job.logInfo(passCompletedMessage);
      }

      DesignRulesChecker tempDrc = new DesignRulesChecker(router.board, null);
      tempDrc.calculateAllIncompletes();
      StringBuilder perNetBreakdown = new StringBuilder();
      for (int netNumber = 1; netNumber <= router.board.rules.nets.maxNetNumber(); netNumber++) {
        int netIncomplete = tempDrc.getIncompleteCount(netNumber);
        if (netIncomplete > 0) {
          FRLogger.trace(
              "BatchAutorouter.autoroute_pass",
              "compare_unrouted_net",
              "pass=" + currentPass + ", net=" + netNumber + ", incomplete=" + netIncomplete,
              "Net #" + netNumber,
              new Point[0]);
          if (!perNetBreakdown.isEmpty()) {
            perNetBreakdown.append(',');
          }
          perNetBreakdown.append(netNumber).append('=').append(netIncomplete);
        }
      }
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_unrouted_breakdown",
          "pass="
              + currentPass
              + ", total="
              + tempDrc.getIncompleteCount()
              + ", breakdown="
              + perNetBreakdown,
          "",
          new Point[0]);

      if (Boolean.TRUE.equals(router.settings.saveIntermediateStages)) {
        fireBoardSnapshotEvent(router.board);
      }

      // Stagnation detection: abort when the normalized score hasn't improved by
      // at least STAGNATION_SCORE_THRESHOLD over STAGNATION_PASS_LIMIT consecutive
      // passes. This now fires whenever the router is still actively running
      // (continueAutorouting == true) after the mandatory minimum passes, regardless
      // of incompleteCount.  The old condition guarded on incompleteCount > 0, which
      // caused the check to be bypassed — and the counter to be silently reset — for
      // boards where DRC shows 0 incompletes but the router keeps cycling (e.g. when
      // plane-net false-work items kept autoroutePass() returning true).  If the
      // board is genuinely done (continueAutorouting == false) the while-loop exits
      // naturally and we never reach this block.
      if (currentPass >= STOP_AT_PASS_MINIMUM && continueAutorouting) {

        // --- Pass-local counter (resets after board restores) ---
        if (boardScoreAfter > lastBestScore + STAGNATION_SCORE_THRESHOLD) {
          consecutiveNoImprovementPasses = 0;
          lastBestScore = boardScoreAfter;
        } else {
          consecutiveNoImprovementPasses++;

          // One-time recovery for fanout-enabled jobs: aggressively remove tails, including
          // fanout vias, when score plateaus with remaining incompletes. This gives the
          // autorouter a chance to escape local dead-ends introduced by pre-fanout geometry
          // while keeping fanout enabled as the default behavior.
          if (router.settings.isFanoutEnabled()
              && !fanoutRecoveryApplied
              && boardStatisticsAfter.connections.incompleteCount > 0
              && consecutiveNoImprovementPasses >= FANOUT_RECOVERY_STAGNATION_PASSES) {
            final int incompletesBeforeRecovery = boardStatisticsAfter.connections.incompleteCount;
            removeTails(Item.StopConnectionOption.NONE);
            boardStatisticsAfter = new BoardStatistics(router.board);
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            consecutiveNoImprovementPasses = 0;
            fanoutRecoveryApplied = true;
            alreadyRoutedBoardHashes.clear();
            job.logDebug(
                "Applied one-time fanout recovery cleanup (removed fanout tails/vias). "
                    + "Incompletes: "
                    + incompletesBeforeRecovery
                    + " -> "
                    + boardStatisticsAfter.connections.incompleteCount
                    + ".");
          }

          if (consecutiveNoImprovementPasses >= STAGNATION_PASS_LIMIT) {
            String report = buildUnroutedConnectionsReport();
            job.logInfo(
                "The router's score ("
                    + FRLogger.defaultFloatFormat.format(boardScoreAfter)
                    + ") has not improved by more than "
                    + STAGNATION_SCORE_THRESHOLD
                    + " points in the last "
                    + STAGNATION_PASS_LIMIT
                    + " passes ("
                    + boardStatisticsAfter.connections.incompleteCount
                    + " item"
                    + (boardStatisticsAfter.connections.incompleteCount == 1 ? "" : "s")
                    + " still unconnected). Stopping the auto-router.\n"
                    + "The following connections could not be routed -- please review your design "
                    + "(e.g. check pad clearances, trace width rules, and available routing "
                    + "space):\n"
                    + report);
            thread.requestStopAutoRouter();
            break;
          }
        }

        // --- Global best tracker (not reset by board restores) ---
        // Stops the router if no pass anywhere has meaningfully improved the score
        // in the last STAGNATION_PASS_LIMIT passes, even across board-restore cycles.
        if (boardScoreAfter > globalBestScore + STAGNATION_SCORE_THRESHOLD) {
          globalBestScore = boardScoreAfter;
          passOfBestScore = currentPass;
          incompleteCountAtBestScore = boardStatisticsAfter.connections.incompleteCount;
        } else if ((currentPass - passOfBestScore) >= STAGNATION_PASS_LIMIT) {
          String report = buildUnroutedConnectionsReport();
          job.logInfo(
              "The router's best score ("
                  + FRLogger.defaultFloatFormat.format(globalBestScore)
                  + ") has not improved by more than "
                  + STAGNATION_SCORE_THRESHOLD
                  + " points since pass #"
                  + passOfBestScore
                  + ". Stopping the auto-router after "
                  + currentPass
                  + " passes ("
                  + incompleteCountAtBestScore
                  + " item"
                  + (incompleteCountAtBestScore == 1 ? "" : "s")
                  + " still unconnected).\n"
                  + "The following connections could not be routed -- please review your design "
                  + "(e.g. check pad clearances, trace width rules, and available routing space):\n"
                  + report);
          thread.requestStopAutoRouter();
          break;
        }

      } else if (boardStatisticsAfter.connections.incompleteCount == 0
          && boardScoreAfter > STAGNATION_SCORE_THRESHOLD) {
        // Board is fully routed AND has a positive score (genuine success).
        // A fully-routed board with score == 0 (e.g. caused by clearance violations
        // from plane routing) must NOT reset the stagnation counter; it should keep
        // accumulating until the global tracker fires.
        consecutiveNoImprovementPasses = 0;
        lastBestScore = boardScoreAfter;
      }

      // check if there are still unrouted items
      if (continueAutorouting && !router.thread.isStopAutoRouterRequested()) {
        currentPass++;
      }
    }

    // Ensure we finish with the best board ever seen during this routing session.
    // When stagnation or the max-pass limit fires, the loop exits with the board from the last
    // completed pass, which may be worse than an earlier pass that was recorded in the history.
    float currentFinalScore =
        new BoardStatistics(router.board).getNormalizedScore(job.routerSettings.scoring);
    float bestHistoryScore = bh.getMaxScore();
    if (bestHistoryScore > currentFinalScore) {
      RoutingBoard bestBoard = bh.restoreBestBoard();
      if (bestBoard != null) {
        BoardStatistics currentStats = new BoardStatistics(router.board);
        router.board = bestBoard;
        BoardStatistics bestStats = new BoardStatistics(router.board);
        job.logDebug(
            "The final board state (score "
                + FRLogger.formatScore(
                    currentFinalScore,
                    currentStats.connections.incompleteCount,
                    currentStats.clearanceViolations.totalCount)
                + ") is worse than the best board seen during routing (score "
                + FRLogger.formatScore(
                    bestStats.getNormalizedScore(job.routerSettings.scoring),
                    bestStats.connections.incompleteCount,
                    bestStats.clearanceViolations.totalCount)
                + "). Restoring the best board as the final result.");
      }
    }

    job.board = router.board;

    boolean wasRouterRun =
        router.settings.getRunRouter()
            && (router.settings.maxPasses == null || router.settings.maxPasses >= 0);
    if (wasRouterRun
        && !(router.removeUnconnectedVias
            || continueAutorouting
            || router.thread.isStopAutoRouterRequested())) {
      // clean up the route if the board is completed and if fanout is used.
      removeTails(Item.StopConnectionOption.NONE);
    }

    bh.clear();

    // Print all profiling results at the end of session
    PerformanceProfiler.printResults();
    PerformanceProfiler.reset();

    if (!router.thread.isStopAutoRouterRequested()) {
      router.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(
              router, TaskState.FINISHED, currentPass, router.board.getHash()));
    } else {
      // Distinguish between a user-requested cancellation and a job timeout so that
      // API consumers can tell the two apart via TaskStateChangedEvent.
      boolean isTimedOut = (job != null) && (job.state == RoutingJobState.TIMED_OUT);
      router.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(
              router,
              isTimedOut ? TaskState.TIMED_OUT : TaskState.CANCELLED,
              currentPass,
              router.board.getHash()));
    }

    return !router.thread.isStopAutoRouterRequested();
  }

  private int calculateIncompleteCount(RoutingBoard board) {
    return router.calculateIncompleteCount(board);
  }

  private void removeTails(Item.StopConnectionOption option) {
    router.removeTails(option);
  }

  private boolean autoroutePass(int passNo) {
    return router.autoroutePass(passNo);
  }

  private String buildUnroutedConnectionsReport() {
    return router.buildUnroutedConnectionsReport();
  }

  private void fireBoardSnapshotEvent(RoutingBoard board) {
    router.fireBoardSnapshotEvent(board);
  }
}
