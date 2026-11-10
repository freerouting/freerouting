package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Dead copper of a plane net: {@code Plane-dead-islands.dsn} has a plane P on B.Cu with four small
 * zones of P left alone in its holes, between the thermal vias of another net, as a fill leaves
 * them. No via fits there and nothing touches them.
 */
class DeadCopperTest extends RoutingFixtureTest {

  @Test
  void onlyTheIslandsAreDeadCopper() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Plane-dead-islands.dsn");
    int p = board.rules.nets.get("P").iterator().next().netNumber;
    List<ConductionArea> areas =
        board.getConnectableItems(p).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .toList();
    assertEquals(5, areas.size());
    assertEquals(4, areas.stream().filter(ConductionArea::isDeadCopper).count());
  }

  @Test
  void deadCopperIsNoIncompleteConnection() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(20);
    ts.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Plane-dead-islands.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertNotNull(job.board);
    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "The islands are no incomplete connections");
  }
}
