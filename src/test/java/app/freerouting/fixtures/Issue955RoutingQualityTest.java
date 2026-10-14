package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.management.BoardLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Deterministic routing checkpoints for comparison with the unmodified upstream revision. */
@Tag("slow")
class Issue955RoutingQualityTest extends RoutingFixtureTest {
  @ParameterizedTest
  @CsvSource({
    "Issue955-rotated-pad-contacts.dsn,2",
    "Issue955-rotated-pad-contacts.dsn,10",
    "Issue508-DAC2020_bm01.dsn,2",
    "Issue508-DAC2020_bm01.dsn,10",
    "Issue026-J2_reference.dsn,2",
    "Issue026-J2_reference.dsn,10",
    "Issue955-rotated-pad-contacts.dsn,0",
    "Issue508-DAC2020_bm01.dsn,0",
    "Issue026-J2_reference.dsn,0"
  })
  void routingCheckpoint(String fixture, int maxItems) {
    var job = getRoutingJob(fixture);
    job.routerSettings.setMaxThreads(1);
    job.routerSettings.autorouter.maxPasses = 1;
    job.routerSettings.autorouter.maxItems = maxItems;
    job.routerSettings.fanout.enabled = false;
    job.routerSettings.optimizer.enabled = false;
    job.routerSettings.jobTimeoutString = "00:10:00";
    BoardLoader.loadBoardIfNeeded(job);
    var before = new BoardStatistics(job.board);
    int beforeDrc = new DesignRulesChecker(job.board, null).getAllClearanceViolations().size();
    long start = System.nanoTime();
    runRoutingJob(job);
    double seconds = (System.nanoTime() - start) / 1e9;
    assertEquals(RoutingJobState.COMPLETED, job.state);
    var after = new BoardStatistics(job.board);
    int afterDrc = new DesignRulesChecker(job.board, null).getAllClearanceViolations().size();
    System.out.printf(
        java.util.Locale.ROOT,
        "QUALITY,%s,%d,%d,%d,%d,%d,%d,%.6f,%.3f%n",
        fixture,
        maxItems,
        before.connections.incompleteCount,
        after.connections.incompleteCount,
        beforeDrc,
        afterDrc,
        after.vias.totalCount,
        after.traces.totalLengthMm,
        seconds);
    assertTrue(after.connections.incompleteCount <= before.connections.incompleteCount);
    assertTrue(afterDrc <= beforeDrc);
  }
}
