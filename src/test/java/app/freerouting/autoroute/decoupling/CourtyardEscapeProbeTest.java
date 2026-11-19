package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CourtyardEscapeProbeTest {

  @Test
  @DisplayName("computeComponentPadBounds encloses all component pins")
  void testComputeComponentPadBounds() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Test non-existent component ID
    assertNull(CourtyardEscapeProbe.computeComponentPadBounds(board, -1));
    assertNull(CourtyardEscapeProbe.computeComponentPadBounds(board, 999999));

    // Find a multi-pin component
    int testCompId = -1;
    for (int i = 1; i <= board.components.count(); i++) {
      Component comp = board.components.get(i);
      if (comp != null && board.getComponentPins(comp.id).size() >= 4) {
        testCompId = comp.id;
        break;
      }
    }
    assertTrue(testCompId > 0, "Expected at least one multi-pin IC on DAC2020_bm01");

    IntBox padBounds = CourtyardEscapeProbe.computeComponentPadBounds(board, testCompId);
    assertNotNull(padBounds);

    for (Pin p : board.getComponentPins(testCompId)) {
      assertTrue(
          padBounds.contains(p.boundingBox()),
          "Component pad bounds must contain pin " + p.getId());
    }
  }

  @Test
  @DisplayName("findNeighborSignalPins filters out power, ground, and already escaped pins")
  void testFindNeighborSignalPinsFiltering() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Pick a candidate IC
    int ownerId = -1;
    for (int i = 1; i <= board.components.count(); i++) {
      Component comp = board.components.get(i);
      if (comp != null && board.getComponentPins(comp.id).size() >= 8) {
        ownerId = comp.id;
        break;
      }
    }
    assertTrue(ownerId > 0);

    IntBox padBounds = CourtyardEscapeProbe.computeComponentPadBounds(board, ownerId);
    assertNotNull(padBounds);

    // Search box around pad bounds
    IntBox searchBox = padBounds.offset(2000);
    int railNetNo = 1;
    int groundNetNo = 2;
    int layer = 0;

    List<Pin> neighborPins =
        CourtyardEscapeProbe.findNeighborSignalPins(
            board, searchBox, layer, ownerId, -1, railNetNo, groundNetNo);

    for (Pin p : neighborPins) {
      assertEquals(ownerId, p.getComponentId());
      assertFalse(p.containsNet(railNetNo), "Neighbor signal pins must not include power rail");
      assertFalse(p.containsNet(groundNetNo), "Neighbor signal pins must not include ground net");
    }
  }

  @Test
  @DisplayName("isEscapePossible returns true when no neighbor pins are trapped")
  void testEscapePossibleOnCleanBoard() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    int ownerId = -1;
    for (int i = 1; i <= board.components.count(); i++) {
      Component comp = board.components.get(i);
      if (comp != null && board.getComponentPins(comp.id).size() >= 8) {
        ownerId = comp.id;
        break;
      }
    }
    assertTrue(ownerId > 0);

    IntBox padBounds = CourtyardEscapeProbe.computeComponentPadBounds(board, ownerId);
    assertNotNull(padBounds);

    IntBox searchBox = padBounds.offset(1000);
    boolean safe =
        CourtyardEscapeProbe.isEscapePossible(board, ownerId, -1, searchBox, 0, 1, 2, null);
    assertTrue(safe, "Clean unrouted board pins should be able to escape");
  }

  @Test
  @DisplayName("AutorouteEngine.probeLegalViaPlacement respects shape containment")
  void testLegalViaPlacementPossibleContainment() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    app.freerouting.autoroute.maze.AutorouteEngine engine =
        board.initAutoroute(1, 1, null, null, false);

    // Tiny 10x10 box where no via center exists must return false
    app.freerouting.geometry.planar.TileShape tinyBox =
        new IntBox(
            new app.freerouting.geometry.planar.IntPoint(0, 0),
            new app.freerouting.geometry.planar.IntPoint(10, 10));
    assertFalse(engine.probeLegalViaPlacement(tinyBox), "No via can be placed in a 10x10 unit box");

    // Board bounding box must contain legal vias
    assertTrue(
        engine.probeLegalViaPlacement(board.boundingBox),
        "Whole board must accommodate legal vias");
  }

  @Test
  @DisplayName("canPinEscape returns false when pin cannot escape pad bounds and has no via rule")
  void testTrappedPinWithoutEscapeOrVia() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Pick an unrouted signal pin
    Pin targetPin = null;
    for (Pin p : board.getSmdPins()) {
      if (p.netCount() > 0 && p.getNetNumber(0) > 2) {
        targetPin = p;
        break;
      }
    }
    assertNotNull(targetPin);

    // If padBounds encloses the whole board (so room can never extend outside)
    // and maxDepth = 0 (cannot expand any rooms), canPinEscape must return false
    IntBox padBounds = board.boundingBox.offset(1000000);
    boolean escaped =
        CourtyardEscapeProbe.canPinEscape(
            board, targetPin, padBounds, targetPin.firstLayer(), 0, null);
    assertFalse(escaped, "Pin unable to expand past pad bounds must be identified as trapped");
  }

  private void assertEquals(int expected, int actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
