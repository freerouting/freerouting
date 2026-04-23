package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests related to GitHub Issue #555: Routing Timeouts on BBD_Mars-64.dsn
 *
 * <p><b>Problem Summary:</b><br>
 * A significant performance regression was identified during overnight batch testing on a
 * medium-sized reference board ({@code BBD_Mars-64.dsn}). In a series of 100 iterations run with
 * different random seeds (AMD Ryzen 5 3600, 32 GB RAM, 15-minute timeout per run), every single
 * run failed by hitting the 15-minute timeout with approximately 30 unrouted connections remaining.
 * Historically, this board had been routed successfully in under 4 minutes.
 *
 * <p><b>Test Configuration Used to Reproduce:</b>
 * <pre>
 *   java -jar freerouting-executable.jar \
 *     -de BBD_Mars-64.dsn -do BBD_Mars-64.ses \
 *     --gui.enabled=false \
 *     --router.max_threads=11 \
 *     --router.max_passes=120 \
 *     --router.optimizer.enabled=false \
 *     --router.job_timeout="00:15:00"
 * </pre>
 *
 * <p><b>Expected Behavior:</b> The board should route fully (or near-fully) within 4–5 minutes
 * across a wide variety of random seeds.
 *
 * <p><b>Actual Behavior:</b> All 100 iterations timed out at 15 minutes, each leaving ~30
 * unrouted connections, indicating a systematic regression rather than a seed-specific edge case.
 *
 * <p><b>Root-Cause Hypothesis:</b> The regression is suspected to be linked to a recent change in
 * the core routing algorithm (possibly the maze-routing or expansion logic). A broader review of
 * the auto-router's performance characteristics — clearance handling, expansion ordering, and
 * tie-breaking — may be required to restore the original routing quality.
 *
 * <p><b>Benchmark Reference Points:</b>
 * <ul>
 *   <li>Freerouting v1.8: ~29.5 minutes (with failure)</li>
 *   <li>Freerouting v1.9: ~27 minutes (with failure)</li>
 *   <li>Freerouting v2.0: ~9 minutes (with partial failure)</li>
 *   <li>Freerouting v2.1: score 976.35, completed in ~3.6 minutes</li>
 * </ul>
 *
 * <p><b>Secondary Board – CNH_Functional_Tester_1.dsn:</b><br>
 * A second, smaller reference board ({@code Issue555-CNH_Functional_Tester_1.dsn}) is also
 * included in the test suite to track routing completion rate under a strict 40-pass limit.
 * v1.8 / v1.9 / v2.0 each completed this board in ~10–12 seconds with 4 unrouted connections
 * remaining; v2.1 achieves score 962.18 with 6 unrouted in ~54 seconds when capped at 40 passes.
 *
 * @see <a href="https://github.com/freerouting/freerouting/issues/555">GitHub Issue #555</a>
 */
public class BbdMars64PerformanceRoutingTest extends RoutingFixtureTest {

  @Test
  @Disabled("Temporary disabled: Routing_performance_with_BBD_Mars_64")
  void test_Issue_555_Routing_performance_with_BBD_Mars_64() {
    IO.println("Testing performance by routing reference board 'Issue555-BBD_Mars-64.dsn' with default settings.");
    IO.println(
        "The benchmark times for Freerouting v1.8, v1.9 and v2.0 were 29.5 minutes, 27 minutes with failure and 9 minutes with partial failure.");
    IO.println("The benchmark score for Freerouting v2.1 is 976.35, completed in 3.6 minutes.");

    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:15:00");

    var job = GetRoutingJob("Issue555-BBD_Mars-64.dsn", testingSettings);
    job = RunRoutingJob(job);

    if (job.output == null) {
      fail("Routing job failed.");
    } else {
      var bs = job.board.get_statistics();
      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);

      IO.println(
          "Routing was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of "
              + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount,
                  bs.clearanceViolations.totalCount)
              + ".");
    }

    assertTrue(Duration
        .between(job.startedAt, job.finishedAt)
        .compareTo(Duration.ofMinutes(5)) < 0,
        "Routing of the reference board 'BBD_Mars-64.dsn' should complete within 5 minutes.");
  }

  @Test
  void test_Issue_555_Routing_performance_with_CNH_Functional_Tester_1() {
    IO.println(
        "Testing performance by routing reference board 'Issue555-CNH_Functional_Tester_1.dsn' with default settings.");
    IO.println(
        "The benchmark times for Freerouting v1.8, v1.9 and v2.0 were 12 seconds (4 unrouted), 10 seconds (4 unrouted) and 11 seconds (4 unrouted) in 6 passes.");
    IO.println(
        "The benchmark score for Freerouting v2.1 is 962.18 (6 unrouted), completed in 54 seconds, hitting the 40 pass limit.");
    IO.println(
        "The benchmark score for Freerouting v2.2 is 955.73 (5 unrouted), completed in 42 seconds, with 18 passes.");

    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:03:00");
    testingSettings.setMaxPasses(40);

    var job = GetRoutingJob("Issue555-CNH_Functional_Tester_1.dsn", testingSettings);
    job = RunRoutingJob(job);

    if (job.output == null) {
      fail("Routing job failed.");
    } else {
      var bs = job.board.get_statistics();
      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);

      IO.println(
          "Routing was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of "
              + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount,
                  bs.clearanceViolations.totalCount)
              + ".");
    }

    // Check if we could finish within 1 minute
    assertTrue(job.getDuration().compareTo(Duration.ofMinutes(1)) < 0,
        "Routing of the reference board 'Issue555-CNH_Functional_Tester_1.dsn' should complete within 1 minute.");

    // Check if we could finish within 40 passes
    assertTrue(job.getCurrentPass() >= 1, "The routing job should have at least 1 pass.");
    assertTrue(job.getCurrentPass() <= 40, "The routing job should stop after at most 40 passes.");

    // Check if we have at most 6 unrouted connections
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 6,
        "Routing of the reference board 'Issue555-CNH_Functional_Tester_1.dsn' should leave at most 6 unrouted connections.");
  }
}