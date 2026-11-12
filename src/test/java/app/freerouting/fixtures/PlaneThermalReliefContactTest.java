package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A pin on a thermal relief of a filled plane: {@code Plane-thermal-relief-4layer.dsn} has its GND
 * plane on In1.Cu as KiCad fills it, with a thermal relief around the through-hole pin J1-2. The
 * centre of the pin lies in the window of the relief, and the four spokes overlap the edge of its
 * pad: the pin is connected to the plane.
 */
class PlaneThermalReliefContactTest extends RoutingFixtureTest {

  @Test
  void aPinOnAThermalReliefTouchesItsPlane() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Plane-thermal-relief-4layer.dsn");
    Pin pin = null;
    for (Pin current : board.getPins()) {
      if ("J1".equals(current.componentName()) && "2".equals(current.name())) {
        pin = current;
      }
    }
    assertNotNull(pin, "J1-2 must exist");
    int gnd = board.rules.nets.get("GND").iterator().next().netNumber;
    ConductionArea plane =
        board.getConnectableItems(gnd).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .findFirst()
            .orElseThrow();

    assertFalse(plane.getArea().contains(pin.getCenter()), "The centre of J1-2 lies in the relief");
    assertTrue(pin.getNormalContacts().contains(plane), "J1-2 touches the plane");
    assertTrue(plane.getNormalContacts().contains(pin), "The plane touches J1-2");
  }
}
