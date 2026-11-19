package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.OptimizerPatience;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Discussion #1000: Verifies that configuring {@link
 * OptimizerPatience#EXHAUSTIVE} or {@link OptimizerPatience#PATIENT} prevents the post-routing
 * optimizer from aborting early after 12 consecutive candidate failures, allowing post-route
 * optimization sweeps without introducing DRC violations.
 */
class Issue1000OptimizerPatienceTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue1000-IMSAI_CP-A_Rev-5.dsn";

  @Test
  void testOptimizerPatienceExhaustiveEvaluatesBeyondCanaryLimit() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(1);
    testSettings.setMaxItems(30);
    testSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    assertNotNull(job, "RoutingJob must load successfully from fixture");

    job.routerSettings.setFanoutEnabled(false);
    job.routerSettings.autorouter.enabled = true;
    job.routerSettings.setRunOptimizer(true);

    // Apply Discussion #1000 user settings
    job.routerSettings.setViaCosts(1);
    job.routerSettings.setPreferredDirectionIsHorizontal(0, false);
    job.routerSettings.setPreferredDirectionIsHorizontal(1, true);

    // Set optimizer patience to EXHAUSTIVE (0 failure cap, 0% threshold)
    job.routerSettings.setOptimizerPatience(OptimizerPatience.EXHAUSTIVE);
    assertEquals(OptimizerPatience.EXHAUSTIVE, job.routerSettings.getOptimizerPatience());

    runRoutingJob(job);

    assertNotNull(job.board, "Job board must be initialized after routing and optimization");
    RoutingBoard board = job.board;

    // Full DRC verification: ensure router introduced zero new clearance violations
    // beyond the 36 pre-existing imported violations.
    var drc = new DesignRulesChecker(board, null);
    var violations = drc.getAllClearanceViolations();
    assertEquals(
        36,
        violations.size(),
        "Router and optimizer must not introduce any new clearance violations beyond the 36 pre-existing violations");
  }

  @Test
  void testBatchOptimizerWithPatienceExhaustiveEvaluatesPastCanaryLimit() {
    // Route a small bounded slice once so the board has >20 real candidate items.
    TestingSettings routeSettings = new TestingSettings();
    routeSettings.setMaxPasses(1);
    routeSettings.setMaxItems(30);
    routeSettings.setJobTimeoutString("00:01:00");

    RoutingJob routedJob = getRoutingJob(FIXTURE, routeSettings);
    assertNotNull(routedJob, "RoutingJob must load successfully from fixture");
    routedJob.routerSettings.setFanoutEnabled(false);
    routedJob.routerSettings.autorouter.enabled = true;
    routedJob.routerSettings.setRunOptimizer(false);
    routedJob.routerSettings.setViaCosts(1);
    routedJob.routerSettings.setPreferredDirectionIsHorizontal(0, false);
    routedJob.routerSettings.setPreferredDirectionIsHorizontal(1, true);
    runRoutingJob(routedJob);
    assertNotNull(routedJob.board, "Routed board must be initialized");

    int balancedItems =
        runControlledFailurePass(routedJob.board.deepCopy(), OptimizerPatience.BALANCED, 20);
    int patientItems =
        runControlledFailurePass(routedJob.board.deepCopy(), OptimizerPatience.PATIENT, 20);
    int exhaustiveItems =
        runControlledFailurePass(routedJob.board.deepCopy(), OptimizerPatience.EXHAUSTIVE, 20);

    assertEquals(12, balancedItems, "BALANCED must abort pass 1 at the 12-failure canary cap");
    assertEquals(
        20,
        patientItems,
        "PATIENT must continue evaluating candidates past the 12-failure canary cap");
    assertEquals(
        20,
        exhaustiveItems,
        "EXHAUSTIVE must continue evaluating candidates past the 12-failure canary cap");
  }

  private int runControlledFailurePass(
      RoutingBoard board, OptimizerPatience patience, int maxItems) {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setJobTimeoutString("00:00:30");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    assertNotNull(job, "RoutingJob must load successfully from fixture");
    job.board = board;
    job.thread = new NoOpStoppableThread();
    job.routerSettings.setOptimizerPatience(patience);
    assertEquals(patience, job.routerSettings.getOptimizerPatience());
    job.routerSettings.optimizer.enablePreflightGuards = false;
    job.routerSettings.optimizer.maxPasses = 1;
    job.routerSettings.optimizer.maxItems = maxItems;
    job.routerSettings.optimizer.maxThreads = 1;

    app.freerouting.autoroute.pipeline.BatchOptimizer optimizer =
        new app.freerouting.autoroute.pipeline.BatchOptimizer(job) {
          @Override
          protected java.util.concurrent.Callable<CandidateResult> createCandidateTask(
              int itemId,
              boolean withPreferredDirections,
              app.freerouting.core.scoring.BoardStatistics.OptimizerMetrics baselineMetrics) {
            return () -> unimprovedCandidate(itemId);
          }
        };
    optimizer.runBatchLoop();

    var phase = job.resultPhaseMetrics.optimizer;
    assertNotNull(phase, "Optimizer phase metrics must be recorded");
    assertEquals(1, phase.passesCompleted, "Optimizer must complete pass 1");
    return optimizer.getTotalItemsOptimized();
  }

  private static final class NoOpStoppableThread extends app.freerouting.core.StoppableThread {
    @Override
    protected void threadAction() {}
  }
}
