package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * A DSN without any {@code (via ...)} declaration routes without vias.
 *
 * <p>No via rule was created for such a board, and every connection failed on a {@code
 * NullPointerException} in {@code AutorouteControl}, leaving the board unrouted.
 */
class RoutingWithoutViasTest extends RoutingFixtureTest {

  @Test
  void boardWithoutViaDeclarationRoutes() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(5);
    RoutingJob job = getRoutingJob("routing-without-vias.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    BoardStatistics stats = new BoardStatistics(job.board);
    assertEquals(0, stats.connections.incompleteCount);
    assertEquals(0, stats.vias.totalCount);
    assertEquals(0, stats.clearanceViolations.totalCount);
  }
}
