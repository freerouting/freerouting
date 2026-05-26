package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.BoardHistory;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Regression test for GitHub Issue #684: Memory leak – BoardHistory grows unboundedly.
 *
 * <p><b>Problem Summary:</b><br>
 * {@code BatchAutorouter.runBatchLoop()} serialises the entire routing board on every pass and
 * stores it in a {@code BoardHistory} list that had no upper-bound on size. On a complex board with
 * many unrouted nets, running the auto-router for 4–6 days produced thousands of board snapshots,
 * each several megabytes, exhausting the 2 GB heap the user configured ({@code -Xmx2G}).
 *
 * <p>A secondary symptom was the log being flooded with the message
 * <em>"IdNoGenerator: danger of overflow, please regenerate id numbers from scratch!"</em>
 * — the ID generator emitted this on every single item insertion after the counter passed
 * {@code Integer.MAX_VALUE / 2}.
 *
 * <p><b>Fix applied:</b>
 * <ul>
 *   <li>{@code BoardHistory} is now capped at {@link BoardHistory#MAX_HISTORY_SIZE} entries.
 *       When the cap is reached the lowest-scoring entry is evicted before adding a new one.</li>
 *   <li>{@code ItemIdentificationNumberGenerator} wraps the counter back to 1 when it reaches
 *       the threshold and emits a single WARN per wrap instead of one per item.</li>
 * </ul>
 *
 * <p><b>Acceptance Criteria:</b>
 * <ul>
 *   <li>The routing job must reach a terminal state without {@link OutOfMemoryError}.</li>
 *   <li>The resulting board must be non-null.</li>
 *   <li>{@code BoardHistory.MAX_HISTORY_SIZE} must be a positive, bounded constant.</li>
 * </ul>
 *
 * @see <a href="https://github.com/freerouting/freerouting/issues/684">GitHub Issue #684</a>
 */
public class Issue684MemoryLeakRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE_FILE = "Issue684-Autorouter_PCB1_2026-5-8.dsn";

  /**
   * Verifies that the BoardHistory size cap constant is a positive, bounded value so that the
   * history can never grow to an unbounded size.
   */
  @Test
  void boardHistorySizeCapIsPositiveAndBounded() {
    assertTrue(BoardHistory.MAX_HISTORY_SIZE > 0,
        "BoardHistory.MAX_HISTORY_SIZE must be positive");
    assertTrue(BoardHistory.MAX_HISTORY_SIZE <= 100,
        "BoardHistory.MAX_HISTORY_SIZE should be a reasonable limit (≤ 100)");
  }

  /**
   * Smoke-test: verifies that the reported board can be loaded and routed for a small number
   * of items without throwing an {@link OutOfMemoryError} or any other unexpected exception.
   *
   * <p>Uses {@code maxItems=50} so the test finishes within ~30 seconds on CI hardware while
   * still exercising the routing code paths that led to the memory leak.
   */
  @Test
  void routing_completesWithoutOutOfMemoryError() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(3);
    testSettings.setMaxItems(50);
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

    Duration duration = completed.getDuration();
    var statsAfter = GetBoardStatistics(completed);
    IO.println("Issue684 routing test completed in "
        + FRLogger.formatDuration(duration.toSeconds())
        + " with " + statsAfter.connections.incompleteCount + " incomplete connections.");
  }
}
