package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Regression test for GitHub Issue #420: OutOfMemoryError during post-route optimization.
 *
 * <p><b>Problem Summary:</b><br>
 * On boards with ~979 pins (and larger), enabling the post-route optimizer caused the JVM heap to
 * grow unboundedly, eventually triggering {@code java.lang.OutOfMemoryError: Java heap space} even
 * when large heaps (e.g., {@code -Xmx20g}) were configured. A secondary symptom was 100% CPU
 * utilisation with the GUI becoming unresponsive, indicating the optimizer was stuck in an
 * infinite (or near-infinite) allocation loop. MAT analysis confirmed that almost all heap was
 * unreachable (GC roots not releasing references), suggesting an object-retention leak inside the
 * optimizer rather than a simple allocation burst.
 *
 * <p><b>Board characteristics:</b> ~979 pins / 1125 unrouted nets. One full routing pass on this
 * board takes approximately 3–4 minutes, so tests must use {@code maxItems} to limit scope to a
 * small slice of the board that can be routed in well under a minute, while still exercising the
 * optimizer code path responsible for the reported leak.
 *
 * <p><b>Acceptance Criteria:</b>
 * <ul>
 *   <li>The routing + optimization job must complete (or be cancelled/terminated by the timeout)
 *       without throwing {@link OutOfMemoryError}.</li>
 *   <li>The job must reach a terminal state within the allotted time.</li>
 *   <li>The resulting board must be non-null.</li>
 *   <li>The optimizer must not introduce new clearance violations.</li>
 * </ul>
 *
 * @see <a href="https://github.com/freerouting/freerouting/issues/420">GitHub Issue #420</a>
 */
public class Issue420ContributionBoardRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE_FILE = "Issue420-contribution-board.dsn";

  /**
   * Smoke-test: verifies that the contribution board can be loaded and routed for a small number
   * of items without throwing an {@link OutOfMemoryError} or any other unexpected exception.
   *
   * <p>Uses {@code maxItems=100} so the test finishes within approximately 30–60 seconds even on
   * slow CI hardware, while still exercising the router on the large-board code path.
   */
  @Test
  void routing_completesWithoutOutOfMemoryError() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(1);
    testSettings.setMaxItems(100);
    testSettings.setJobTimeoutString("00:02:00");

    RoutingJob job = GetRoutingJob(FIXTURE_FILE, testSettings);
    assertNotNull(job, "RoutingJob must not be null");

    RoutingJob completed = RunRoutingJob(job);

    assertTrue(
        completed.state == RoutingJobState.COMPLETED
            || completed.state == RoutingJobState.CANCELLED
            || completed.state == RoutingJobState.TERMINATED,
        "Routing job must reach a terminal state; actual state: " + completed.state);

    assertNotNull(completed.board,
        "RoutingJob.board must be non-null after a completed routing run");
  }

  /**
   * Regression test for the OOM scenario: routes a small slice of the board and then runs the
   * post-route optimizer, exercising the exact code path that caused heap exhaustion.
   *
   * <p>The optimizer is capped at 1 pass with a single thread to keep the test deterministic and
   * within the 5-minute budget. If the memory-retention bug is still present the JVM will throw
   * {@link OutOfMemoryError} (recorded as a test error) or the job will hit the timeout.
   */
  @Test
  void optimizer_completesWithoutOutOfMemoryError() {
    // Route a limited number of items first so the optimizer has something to work with,
    // but the initial routing phase stays well under 1 minute.
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(1);
    testSettings.setMaxItems(150);
    testSettings.setJobTimeoutString("00:05:00");

    RoutingJob job = GetRoutingJob(FIXTURE_FILE, testSettings);
    assertNotNull(job, "RoutingJob must not be null");

    // Enable the optimizer with a minimal pass count.
    // The original reporter observed the crash after several optimizer passes, so even a
    // single optimizer pass is sufficient to exercise the leak path.
    job.routerSettings.optimizer.enabled = true;
    job.routerSettings.optimizer.maxPasses = 1;
    job.routerSettings.optimizer.maxThreads = 1;

    RoutingJob completed = RunRoutingJob(job);

    assertTrue(
        completed.state == RoutingJobState.COMPLETED
            || completed.state == RoutingJobState.CANCELLED
            || completed.state == RoutingJobState.TERMINATED,
        "Routing+optimizer job must reach a terminal state; actual state: " + completed.state);

    assertNotNull(completed.board,
        "RoutingJob.board must be non-null after routing+optimization run");

    // The optimizer must not introduce clearance violations.
    BoardStatistics statsAfter = GetBoardStatistics(completed);
    assertTrue(statsAfter.clearanceViolations.totalCount == 0,
        "Optimizer must not introduce clearance violations; found: "
            + statsAfter.clearanceViolations.totalCount);

    Duration duration = completed.getDuration();
    IO.println("Issue420 optimizer test completed in "
        + FRLogger.formatDuration(duration.toSeconds())
        + " with " + statsAfter.connections.incompleteCount + " incomplete connections"
        + " and " + statsAfter.clearanceViolations.totalCount + " clearance violations.");
  }
}

