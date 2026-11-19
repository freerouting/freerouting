package app.freerouting.core.facade;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.autoroute.pipeline.RoutingPipeline;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.RoutingStage;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
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

    // 1. Parse DSN into RoutingBoard
    byte[] dsnBytes = request.dsnBytes();
    BoardReadResult readResult =
        DsnReader.readBoard(new ByteArrayInputStream(dsnBytes), null, null, request.designName());

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
          board, board.getStatistics(), System.currentTimeMillis() - startTime);
    }

    // 2. Resolve settings using SettingsMerger priority ladder
    SettingsMerger settingsMerger =
        new SettingsMerger(
            new DefaultSettings(),
            new DsnFileSettings(new ByteArrayInputStream(dsnBytes), request.designName()));

    byte[] rulesBytes = request.rulesBytes();
    if (rulesBytes != null && rulesBytes.length > 0) {
      settingsMerger.addOrReplaceSources(
          new RulesFileSettings(
              new ByteArrayInputStream(rulesBytes),
              request.rulesName() != null ? request.rulesName() : "rules.rules"));
    }

    if (request.settings() != null) {
      settingsMerger.addOrReplaceSources(new ApiSettings(request.settings()));
    }

    RouterSettings routerSettings = settingsMerger.merge();
    routerSettings.applyBoardSpecificOptimizations(board);

    // 3. Prepare RoutingJob
    RoutingJob job = new RoutingJob();
    job.board = board;
    job.routerSettings = routerSettings;
    job.name = request.designName();
    HeadlessStoppableThread thread = new HeadlessStoppableThread(request.cancellationToken());
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

    boolean cancelled = request.cancellationToken().isCancelled();
    job.state = cancelled ? RoutingJobState.CANCELLED : RoutingJobState.COMPLETED;

    // 6. Gather results & statistics
    BoardStatistics stats = board.getStatistics();
    int unrouted =
        stats != null && stats.connections != null ? stats.connections.incompleteCount : 0;

    String errorMessage = cancelled ? "Routing was cancelled" : null;

    // 7. Perform DRC if requested
    Collection<ClearanceViolation> violations = Collections.emptyList();
    if (request.runDrc() && !cancelled) {
      try {
        violations = new DesignRulesChecker(board, null).getAllClearanceViolations();
      } catch (Exception e) {
        FRLogger.warn("DRC check encountered an error: " + e.getMessage());
        errorMessage = "DRC check failed: " + e.getMessage();
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

  /** Standalone DRC check for a board represented by DSN bytes. */
  public static Collection<ClearanceViolation> checkDrc(byte[] dsnBytes) {
    if (dsnBytes == null || dsnBytes.length == 0) {
      return Collections.emptyList();
    }
    BoardReadResult result =
        DsnReader.readBoard(new ByteArrayInputStream(dsnBytes), null, null, "drc_check.dsn");
    if (result instanceof BoardReadResult.Success s) {
      return checkDrc((RoutingBoard) s.board());
    } else if (result instanceof BoardReadResult.OutlineMissing o) {
      return checkDrc((RoutingBoard) o.board());
    }
    return Collections.emptyList();
  }
}
