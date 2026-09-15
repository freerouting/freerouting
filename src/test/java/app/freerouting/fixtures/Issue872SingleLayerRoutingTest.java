package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #872: Single-sided board routes 100% and optimizer starts from the
 * autorouter's best board rather than a stale pre-routing board instance.
 */
class Issue872SingleLayerRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue872-single-layer-repro.dsn";

  @Test
  void singleLayerBoardRoutesCleanlyWithOptimizer() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(5);
    testSettings.setJobTimeoutString("00:02:00");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    assertNotNull(job, "RoutingJob must load successfully from fixture");

    runRoutingJob(job);

    assertNotNull(job.board, "Job board must be initialized after routing");
    assertEquals(1, job.board.getLayerCount(), "Board must be single-sided (1 layer)");

    // Verify the routed board has zero incomplete connections and zero clearance violations
    assertRoutingResult(job, FIXTURE).maxIncompleteConnections(0).maxClearanceViolations(0).check();

    BoardStatistics finalStats = job.board.getStatistics();
    assertEquals(
        0, finalStats.connections.incompleteCount, "Final board must have 0 unrouted nets");
    assertEquals(
        0,
        finalStats.clearanceViolations.totalCount,
        "Final board must have 0 clearance violations");
    assertTrue(finalStats.traces.totalLength > 0, "Traces must have non-zero length");
  }

  @Test
  void optimizerStartsFromAutorouterBestBoard() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    TestingSettings testSettings = new TestingSettings();
    // Bounded to 1 pass so autorouter leaves an incomplete board, ensuring optimizer doesn't
    // regress
    testSettings.setMaxPasses(1);
    testSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    runRoutingJob(job);

    // Verify optimizer phase metrics captured the correct baseline from autorouter's result
    assertNotNull(job.resultPhaseMetrics, "Phase metrics must be recorded");
    assertNotNull(job.resultPhaseMetrics.autorouter, "Autorouter phase metrics must exist");
    assertNotNull(job.resultPhaseMetrics.optimizer, "Optimizer phase metrics must exist");

    if (job.resultPhaseMetrics.optimizer.before != null
        && job.resultPhaseMetrics.autorouter.after != null
        && job.resultPhaseMetrics.optimizer.before.boardStatistics != null
        && job.resultPhaseMetrics.autorouter.after.boardStatistics != null) {
      assertEquals(
          job.resultPhaseMetrics.autorouter.after.boardStatistics.connections.incompleteCount,
          job.resultPhaseMetrics.optimizer.before.boardStatistics.connections.incompleteCount,
          "Optimizer must start from the exact incomplete count produced by the autorouter");
    }
  }
}
