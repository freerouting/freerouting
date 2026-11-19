package app.freerouting.core.facade;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.autoroute.pipeline.RoutingPipeline;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.RoutingStage;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.DifferentialPairDetector;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.ApiSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.RulesFileSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Single-entry, in-memory headless routing facade.
 *
 * <p>Executes board parsing, routing pipeline sequencing (autoroute + optimizer + DRC), and SES
 * serialization synchronously on the calling thread without requiring disk I/O, background
 * schedulers, GUI managers, or cloud telemetry.
 *
 * <p>Designed for embedding in WebAssembly (TeaVM, CheerpJ), Web Workers, CLI pipelines,
 * microservices, and high-throughput unit tests.
 */
public final class FreeroutingHeadlessEngine {

  private FreeroutingHeadlessEngine() {}

  /**
   * Routes a board using the specified in-memory request.
   *
   * @param request configuration and input data for the routing job (must not be {@code null})
   * @return comprehensive routing outcome containing SES output, DRC violations, and statistics
   */
  public static HeadlessRoutingResult route(HeadlessRoutingRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }

    long startTime = System.currentTimeMillis();

    if (request.cancellationToken().isCancelled()) {
      return HeadlessRoutingResult.cancelled(null, null, System.currentTimeMillis() - startTime);
    }

    // 1. Parse DSN into RoutingBoard (purely in-memory, no temporary files)
    byte[] dsnBytes = request.dsnBytes();
    BoardReadResult readResult = DsnReader.readBoard(dsnBytes, null, null, request.designName());

    RoutingBoard board;
    if (readResult instanceof BoardReadResult.Success s) {
      board = (RoutingBoard) s.board();
    } else if (readResult instanceof BoardReadResult.OutlineMissing o) {
      board = (RoutingBoard) o.board();
    } else if (readResult instanceof BoardReadResult.ParseError p) {
      return HeadlessRoutingResult.failure(
          "DSN parse error at '" + p.location() + "': " + p.detail(),
          System.currentTimeMillis() - startTime);
    } else if (readResult instanceof BoardReadResult.IoError io) {
      return HeadlessRoutingResult.failure(
          "DSN IO error: " + io.cause().getMessage(), System.currentTimeMillis() - startTime);
    } else {
      return HeadlessRoutingResult.failure(
          "Unknown DSN board read result", System.currentTimeMillis() - startTime);
    }

    if (board == null) {
      return HeadlessRoutingResult.failure(
          "DSN did not contain a valid board", System.currentTimeMillis() - startTime);
    }

    if (request.cancellationToken().isCancelled()) {
      return HeadlessRoutingResult.cancelled(
          board, new BoardStatistics(board, null, false), System.currentTimeMillis() - startTime);
    }

    // 2. Resolve settings using SettingsMerger priority ladder and apply .rules to the board
    SettingsMerger settingsMerger =
        new SettingsMerger(
            new DefaultSettings(),
            new DsnFileSettings(new ByteArrayInputStream(dsnBytes), request.designName()));

    byte[] rulesBytes = request.rulesBytes();
    if (rulesBytes != null && rulesBytes.length > 0) {
      String rulesName = request.rulesName() != null ? request.rulesName() : "rules.rules";
      boolean rulesApplied =
          RulesReader.read(new ByteArrayInputStream(rulesBytes), rulesName, board);
      if (!rulesApplied) {
        return HeadlessRoutingResult.failure(
            "Failed to apply rules file '" + rulesName + "' to board",
            System.currentTimeMillis() - startTime);
      }
      settingsMerger.addOrReplaceSources(
          new RulesFileSettings(new ByteArrayInputStream(rulesBytes), rulesName));
    }

    if (request.settings() != null) {
      settingsMerger.addOrReplaceSources(new ApiSettings(request.settings()));
    }

    RouterSettings routerSettings = settingsMerger.merge();
    int boardLayerCount = board.getLayerCount();
    if (routerSettings.getLayerCount() != boardLayerCount) {
      routerSettings.setLayerCount(boardLayerCount);
    }
    routerSettings.applyBoardSpecificOptimizations(board);
    routerSettings.applyNetClassExclusions(board);
    applyBoardSettingsOverrides(board, routerSettings);
    prepareBoardConnectivity(board, routerSettings);
    captureInitialClearanceBaseline(board);

    // 3. Prepare RoutingJob
    RoutingJob job = new RoutingJob();
    job.board = board;
    job.routerSettings = routerSettings;
    job.name = request.designName();
    Long deadlineMillis = parseDeadlineMillis(startTime, routerSettings.jobTimeoutString);
    HeadlessStoppableThread thread =
        new HeadlessStoppableThread(request.cancellationToken(), deadlineMillis);
    job.thread = thread;
    job.state = RoutingJobState.RUNNING;

    // 4. Configure pipeline and listeners
    RoutingPipeline pipeline = RoutingPipeline.createForHeadless(job);

    if (request.progressListener() != null) {
      pipeline.addTaskStateChangedEventListener(
          event -> {
            notifyProgress(
                request.progressListener(),
                new HeadlessRoutingProgress(
                    job.stage,
                    event.getPassNumber(),
                    -1,
                    null,
                    "Task state: " + event.getTaskState()));
          });

      pipeline.addBoardUpdatedEventListener(
          event -> {
            BoardStatistics stats = event.getBoardStatistics();
            int unrouted =
                stats != null && stats.connections != null ? stats.connections.incompleteCount : 0;
            notifyProgress(
                request.progressListener(),
                new HeadlessRoutingProgress(
                    job.stage, job.getCurrentPass(), unrouted, stats, "Board updated"));
          });

      pipeline.addStageListener(
          new RoutingPipeline.StageListener() {
            @Override
            public void afterRouting(BatchAutorouter autorouter) {
              BoardStatistics stats = job.board.getStatistics();
              notifyProgress(
                  request.progressListener(),
                  new HeadlessRoutingProgress(
                      RoutingStage.ROUTING,
                      job.getCurrentPass(),
                      stats.connections != null ? stats.connections.incompleteCount : 0,
                      stats,
                      "Routing stage finished"));
            }

            @Override
            public void beforeOptimization(BatchOptimizer optimizer) {
              BoardStatistics stats = job.board.getStatistics();
              int unrouted =
                  stats != null && stats.connections != null
                      ? stats.connections.incompleteCount
                      : 0;
              notifyProgress(
                  request.progressListener(),
                  new HeadlessRoutingProgress(
                      RoutingStage.OPTIMIZATION, 0, unrouted, stats, "Optimization stage started"));
            }

            @Override
            public void afterOptimization(BatchOptimizer optimizer) {
              BoardStatistics stats = job.board.getStatistics();
              int unrouted =
                  stats != null && stats.connections != null
                      ? stats.connections.incompleteCount
                      : 0;
              notifyProgress(
                  request.progressListener(),
                  new HeadlessRoutingProgress(
                      RoutingStage.OPTIMIZATION,
                      0,
                      unrouted,
                      stats,
                      "Optimization stage finished"));
            }
          });
    }

    // 5. Execute routing pipeline
    try {
      pipeline.run();
    } catch (Exception e) {
      FRLogger.error("Error during in-memory routing pipeline execution", e);
      return HeadlessRoutingResult.failure(
          "Routing pipeline execution failed: " + e.getMessage(),
          System.currentTimeMillis() - startTime);
    }

    // Refresh board reference in case the autorouter or optimizer installed a snapshot/candidate
    board = job.board;

    boolean cancelled = request.cancellationToken().isCancelled();
    boolean timedOut = !cancelled && thread.isTimedOut();
    if (cancelled) {
      job.state = RoutingJobState.CANCELLED;
    } else if (timedOut) {
      job.state = RoutingJobState.TIMED_OUT;
    } else {
      job.state = RoutingJobState.COMPLETED;
    }

    // 6. Gather results & statistics without duplicating the clearance check
    BoardStatistics stats = new BoardStatistics(board, null, false);
    int unrouted = stats.connections != null ? stats.connections.incompleteCount : 0;

    String errorMessage = null;
    if (cancelled) {
      errorMessage = "Routing was cancelled";
    } else if (timedOut) {
      errorMessage = "Routing timed out";
    }

    // 7. Perform authoritative DRC once if requested
    Collection<ClearanceViolation> violations = Collections.emptyList();
    if (request.runDrc() && !cancelled) {
      try {
        violations = new DesignRulesChecker(board, null).getAllClearanceViolations();
      } catch (Exception e) {
        FRLogger.warn("DRC check encountered an error: " + e.getMessage());
        if (errorMessage == null) {
          errorMessage = "DRC check failed: " + e.getMessage();
        }
      }
    }

    // 8. Serialize to SES if requested
    byte[] sesBytes = new byte[0];
    if (request.computeSes() && !cancelled) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SesWriter.write(board, baos, request.designName());
        sesBytes = baos.toByteArray();
      } catch (Exception e) {
        FRLogger.error("Failed to serialize SES output", e);
        if (errorMessage == null) {
          errorMessage = "SES serialization failed: " + e.getMessage();
        }
      }
    }

    long executionTimeMillis = System.currentTimeMillis() - startTime;
    boolean completed = !cancelled && errorMessage == null && unrouted == 0 && violations.isEmpty();

    return new HeadlessRoutingResult(
        completed,
        cancelled,
        unrouted,
        violations,
        stats,
        sesBytes,
        executionTimeMillis,
        errorMessage,
        board);
  }

  /** Convenience method: routes a board given raw DSN bytes with default settings. */
  public static HeadlessRoutingResult route(byte[] dsnBytes) {
    return route(HeadlessRoutingRequest.of(dsnBytes));
  }

  /** Convenience method: routes a board given DSN string content with default settings. */
  public static HeadlessRoutingResult route(String dsnContent) {
    return route(HeadlessRoutingRequest.of(dsnContent));
  }

  /** Convenience method: routes a board given a DSN input stream with default settings. */
  public static HeadlessRoutingResult route(InputStream dsnStream) throws IOException {
    return route(HeadlessRoutingRequest.of(dsnStream));
  }

  private static void notifyProgress(
      HeadlessRoutingProgressListener listener, HeadlessRoutingProgress progress) {
    if (listener != null) {
      try {
        listener.onProgress(progress);
      } catch (RuntimeException e) {
        FRLogger.warn("Progress listener threw exception: " + e.getMessage());
      }
    }
  }

  /** Standalone DRC check for an in-memory routing board. */
  public static Collection<ClearanceViolation> checkDrc(RoutingBoard board) {
    if (board == null) {
      return Collections.emptyList();
    }
    return new DesignRulesChecker(board, null).getAllClearanceViolations();
  }

  /**
   * Standalone DRC check for a board represented by DSN bytes.
   *
   * @throws IllegalArgumentException if {@code dsnBytes} is null, empty, or fails to parse
   * @throws UncheckedIOException if an I/O error occurs while reading the DSN bytes
   */
  public static Collection<ClearanceViolation> checkDrc(byte[] dsnBytes) {
    if (dsnBytes == null || dsnBytes.length == 0) {
      throw new IllegalArgumentException("dsnBytes must not be null or empty");
    }
    BoardReadResult result = DsnReader.readBoard(dsnBytes, null, null, "drc_check.dsn");
    if (result instanceof BoardReadResult.Success s) {
      return checkDrc((RoutingBoard) s.board());
    } else if (result instanceof BoardReadResult.OutlineMissing o) {
      return checkDrc((RoutingBoard) o.board());
    } else if (result instanceof BoardReadResult.ParseError p) {
      throw new IllegalArgumentException(
          "DSN parse error at '" + p.location() + "': " + p.detail());
    } else if (result instanceof BoardReadResult.IoError io) {
      throw new UncheckedIOException("DSN IO error", io.cause());
    }
    throw new IllegalArgumentException("Unknown DSN board read result");
  }

  private static void applyBoardSettingsOverrides(
      RoutingBoard board, RouterSettings routerSettings) {
    if (board == null || routerSettings == null) {
      return;
    }
    if (routerSettings.planeAsObstacle != null) {
      board.changePlaneAsObstacle(routerSettings.planeAsObstacle);
    }
  }

  private static void prepareBoardConnectivity(RoutingBoard board, RouterSettings routerSettings) {
    if (board == null) {
      return;
    }
    board.expandBoundingBoxToIncludeAllItems();
    board.reduceNetsOfRouteItems();
    boolean fixKicadFootprints =
        routerSettings != null && Boolean.TRUE.equals(routerSettings.fixKicadFootprints);
    board.connectNetlessPads(false, fixKicadFootprints, new int[1]);
    board.clusterNetTies();
    DifferentialPairDetector.detectAndRegister(board.rules);
  }

  private static void captureInitialClearanceBaseline(RoutingBoard board) {
    if (board == null) {
      return;
    }
    Collection<ClearanceViolation> initialViolations =
        new DesignRulesChecker(board, null).getAllClearanceViolations();
    board.preExistingClearanceViolationsCount = initialViolations.size();
    int unfixable = 0;
    for (ClearanceViolation v : initialViolations) {
      if (v.isUnfixable()) {
        unfixable++;
      }
    }
    board.unfixableClearanceViolationsCount = unfixable;
    board.clearanceBaseline = new ClearanceBaseline(initialViolations);
  }

  private static Long parseDeadlineMillis(long startTimeMillis, String timeoutString) {
    if (timeoutString == null || timeoutString.isBlank()) {
      return null;
    }
    String[] parts = timeoutString.trim().split(":");
    if (parts.length != 3) {
      return null;
    }
    try {
      long hours = Long.parseLong(parts[0]);
      long minutes = Long.parseLong(parts[1]);
      long seconds = Long.parseLong(parts[2]);
      long totalMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;
      return totalMillis > 0 ? startTimeMillis + totalMillis : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
