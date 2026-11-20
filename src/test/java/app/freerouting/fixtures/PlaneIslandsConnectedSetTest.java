package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@code Plane-islands-joined-on-other-layer.dsn}: a GND plane on F.Cu cut in two islands by a
 * trace of another net, and a whole GND plane on B.Cu. GND pad J1 and a via sit on the left island,
 * J2 and another via on the right one: the two islands are joined through the vias and the B.Cu
 * plane. The connected set of any GND item must hold every other GND item, whatever item the walk
 * starts from.
 */
class PlaneIslandsConnectedSetTest extends RoutingFixtureTest {

  private static Pin pin(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void islandsJoinedOnAnotherLayerAreConnected() {
    TestingSettings loadOnly = new TestingSettings();
    loadOnly.setJobTimeoutString("00:01:00");
    loadOnly.setFanoutEnabled(false);
    loadOnly.setRouterEnabled(false);
    loadOnly.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob("Plane-islands-joined-on-other-layer.dsn", loadOnly);
    runRoutingJob(job);

    RoutingBoard board = job.board;
    Pin j1 = pin(board, "J1");
    int gnd = j1.getNetNumber(0);
    List<Item> gndItems = new ArrayList<>();
    gndItems.add(j1);
    gndItems.add(pin(board, "J2"));
    gndItems.addAll(board.getVias());
    boolean fractured = false;
    for (ConductionArea area : board.getConductionAreas()) {
      gndItems.add(area);
      fractured |= area.isFractured();
    }
    assertEquals(6, gndItems.size(), "J1, J2, two vias and two planes");
    assertTrue(fractured, "the F.Cu plane is expected to be cut in two islands");

    for (Item start : gndItems) {
      Set<Item> connected = start.getConnectedSet(gnd);
      for (Item other : gndItems) {
        assertTrue(connected.contains(other), "the walk from " + start + " misses " + other);
      }
    }
  }
}
