package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * No trace may be narrower than {@code neck_width_um}.
 *
 * <p>The necked retry routes a failed connection at the neck width, and the micro-neckdown in trace
 * insertion then narrowed that trace further (75 %, 60 %, 50 %). On this two-layer ESP32-C3 board
 * with 0.25 mm signal traces and a 150 um neck width, that left 75, 90 and 112 um traces, below
 * what the board house can make.
 */
class NeckWidthMinimumTest extends RoutingFixtureTest {

  @Test
  void noTraceNarrowerThanNeckWidth() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(30);
    ts.setCopperToEdgeClearanceUm(500);
    ts.setNeckWidthUm(150);
    RoutingJob job = getRoutingJob("Issue978-esp32c3.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);

    // Issue978-esp32c3.dsn uses (resolution um 10), so 150 um full width = 750 board units half
    // width.
    int expectedNeckHalfWidth = 750;
    int neckHalfWidth = AutorouteControl.neckHalfWidth(job.board, job.routerSettings);
    assertEquals(expectedNeckHalfWidth, neckHalfWidth);
    List<String> narrow = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      int classHalfWidth =
          job.board.rules.getTraceHalfWidth(trace.getNetNumber(0), trace.getLayer());
      if (trace.getHalfWidth() < Math.min(classHalfWidth, neckHalfWidth)) {
        narrow.add("net " + trace.getNetNumber(0) + ": half width " + trace.getHalfWidth());
      }
    }
    assertTrue(
        narrow.isEmpty(),
        () ->
            narrow.size()
                + " traces narrower than the neck half width "
                + neckHalfWidth
                + ": "
                + narrow);
  }
}
