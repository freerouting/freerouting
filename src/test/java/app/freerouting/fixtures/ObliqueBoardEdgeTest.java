package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Two pads near a board edge that is neither orthogonal nor diagonal connect with a straight trace.
 *
 * <p>The 45-degree search tree stored the outline line as its bounding octagon, which reached about
 * 3 mm into the board and covered both pad centres. The maze search found no start room ("no
 * accessible expansion doors found") and the connection stayed open in every pass. Seen on PCBench
 * boards with slanted or round outlines (PCB_small_halogen_replacement).
 */
class ObliqueBoardEdgeTest extends RoutingFixtureTest {

  @Test
  void padsNearAnObliqueBoardEdgeConnect() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(5);
    RoutingJob job = getRoutingJob("oblique-board-edge.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    BoardStatistics stats = new BoardStatistics(job.board);
    assertEquals(0, stats.connections.incompleteCount);
    assertEquals(0, stats.clearanceViolations.totalCount);
  }
}
