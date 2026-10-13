package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.Point;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #955: Rotated pad centers and trace endpoints round differently, losing
 * existing connections on DSN import.
 *
 * <p>Verifies that on rotated and mirrored footprints (such as switch S21 rotated 60 degrees on the
 * back layer), high-precision continuous floating-point coordinates are preserved so that
 * single-stage rounding yields canonical integer coordinates matching the imported trace endpoints.
 */
public class Issue955RotatedPadContactTest extends RoutingFixtureTest {

  @Test
  void testRotatedPadContactsOnImport() {
    RoutingJob job = getRoutingJob("Issue955-rotated-pad-contacts.dsn");
    app.freerouting.management.BoardLoader.loadBoardIfNeeded(job);

    Pin pin2at1 = null;
    Pin pin1 = null;

    for (Pin pin : job.board.getPins()) {
      if ("S21".equals(pin.componentName())) {
        if ("2@1".equals(pin.name())) {
          pin2at1 = pin;
        } else if ("1".equals(pin.name())) {
          pin1 = pin;
        }
      }
    }

    assertNotNull(pin2at1, "S21 pin 2@1 must exist");
    assertNotNull(pin1, "S21 pin 1 must exist");

    final Point c2at1 = pin2at1.getCenter();
    final Point c1 = pin1.getCenter();
    final Set<Item> contacts2at1 = pin2at1.getNormalContacts();
    final Set<Item> contacts1 = pin1.getNormalContacts();

    assertEquals(
        new app.freerouting.geometry.planar.IntPoint(2088569, -1304305),
        c2at1,
        "Pin 2@1 center must match trace endpoint");
    assertEquals(
        new app.freerouting.geometry.planar.IntPoint(2165372, -1215279),
        c1,
        "Pin 1 center must match trace endpoint");

    assertEquals(1, contacts2at1.size(), "S21 pin 2@1 should have 1 normal contact");
    assertEquals(1, contacts1.size(), "S21 pin 1 should have 1 normal contact");
  }
}
