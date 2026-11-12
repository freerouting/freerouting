package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A window with a spike: {@code Plane-window-spike-4layer.dsn} is a small 4-layer board, GND on
 * In1.Cu and +3V3 on In2.Cu, with a round window of 1.5 mm radius in the GND plane around (30 mm,
 * -20 mm). At its lowest corner the border of the window steps back by 0.1 um, one unit of the DSN,
 * as a fill rounded to the resolution leaves it.
 */
class PlaneWindowSpikeTest extends RoutingFixtureTest {

  @Test
  void aWindowWithASpikeKeepsItsPlane() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Plane-window-spike-4layer.dsn");
    int gnd = board.rules.nets.get("GND").iterator().next().netNumber;
    ConductionArea plane =
        board.getConnectableItems(gnd).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .findFirst()
            .orElseThrow();

    TileShape[] pieces = plane.getArea().splitToConvex();
    assertNotNull(pieces, "The plane is split into convex pieces");
    assertTrue(covers(pieces, new IntPoint(320000, -200000)), "Beside the window: copper");
    assertTrue(covers(pieces, new IntPoint(100000, -50000)), "Far from the window: copper");
    assertFalse(covers(pieces, new IntPoint(300000, -200000)), "In the window: no copper");
  }

  private static boolean covers(TileShape[] pieces, IntPoint point) {
    for (TileShape piece : pieces) {
      if (piece.contains(point)) {
        return true;
      }
    }
    return false;
  }
}
