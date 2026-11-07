package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Nets whose conduction areas do not connect them by themselves.
 *
 * <ul>
 *   <li>{@code Plane-teardrops-without-via.dsn}: each net is routed in two halves, one on each
 *       layer, that stop where a via was deleted; the two teardrops of that via are left as planes
 *       of the net. Each half touches its own teardrop.
 *   <li>{@code Plane-teardrops-same-layer.dsn}: the same with a piece of trace deleted between two
 *       teardrops on one layer.
 *   <li>{@code Plane-islands-in-plane.dsn}: small zones of a net drawn inside the plane of that net
 *       on the same layer; their copper is the plane's.
 *   <li>{@code Issue367-Charger.dsn}: the board these come from (KiCad teardrops exported as
 *       planes); six of its nets were left unrouted.
 *   <li>{@code Plane-dead-islands.dsn}: small zones of the plane net left alone in holes of its
 *       plane, between thermal vias of another net, as a fill leaves them; no via fits there.
 * </ul>
 */
class PlaneNetConnectionTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Plane-teardrops-without-via.dsn",
        "Plane-teardrops-same-layer.dsn",
        "Plane-islands-in-plane.dsn",
        "Plane-dead-islands.dsn",
        "Issue367-Charger.dsn"
      })
  void routesNetsWithDisjointPlanes(String fixture) {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(20);
    ts.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(fixture, ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertNotNull(job.board);
    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "All connections must be routed");
  }

  @Test
  void planesThatTouchNothingAreNoTarget() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Plane-dead-islands.dsn");
    int p = board.rules.nets.get("P").iterator().next().netNumber;
    List<ConductionArea> areas =
        board.getConnectableItems(p).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .toList();
    assertEquals(5, areas.size());
    for (ConductionArea area : areas) {
      // The plane and each island left alone in it: nothing to route from either.
      assertTrue(board.connectsAllPlanesOfNet(area.getConnectedSet(p), p));
    }
  }
}
