package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * A through-hole pad whose plated slot is exported as a keepout connects.
 *
 * <p>KiCad writes the hole of a plated slot as a keepout on every signal layer. It covers the pad
 * centre, where traces connect to the pin, so the maze search found no start room ("no accessible
 * expansion doors found") and the connection stayed open in every pass. Seen on PCBench boards with
 * USB shield tabs and barrel jacks (card_card, DSKY-alarm-panel-replica, RGBMatrixPanelCPLD).
 */
class PlatedSlotKeepoutTest extends RoutingFixtureTest {

  @Test
  void padWithAPlatedSlotConnects() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(5);
    RoutingJob job = getRoutingJob("plated-slot-keepout.dsn", ts);
    job.routerSettings.autorouter.maxPasses = 5;
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    BoardStatistics stats = new BoardStatistics(job.board);
    assertEquals(0, stats.connections.incompleteCount);
    assertEquals(0, stats.clearanceViolations.totalCount);
  }
}
