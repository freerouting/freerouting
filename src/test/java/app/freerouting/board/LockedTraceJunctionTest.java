package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A DSN {@code fix} wire (so every locked KiCad track) is input-fixed: as fixed as the system-fixed
 * lock that some pipeline stages put on every trace for their run, but a preserved route, so a branch
 * that ends on its centre line is connected there, as on a {@code route} wire. A stage's lock is not.
 */
public class LockedTraceJunctionTest {

  private static final String DSN =
      """
      (pcb locked (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 100000 100000)) (via V) (rule (width 200) (clearance 100)))
        (library (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
        (network (net N))
        (wiring
          (wire (path top 200 2000 5000 18000 5000) (net N) (type fix))
          (wire (path top 200 10000 9000 14000 9000) (net N) (type route))
          (via V 30000 30000 (net N) (type fix))))
      """;

  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    board = load(DSN.getBytes(StandardCharsets.UTF_8));
  }

  private static RoutingBoard load(byte[] dsnBytes) {
    ByteArrayInputStream stream = new ByteArrayInputStream(dsnBytes);
    return (RoutingBoard) ((BoardReadResult.Success) DsnReader.readBoard(stream, null, null)).board();
  }

  private IntPoint p(double x, double y) {
    FloatPoint boardCoords = board.communication.coordinateTransform.dsnToBoard(new double[] {x, y});
    return boardCoords.round();
  }

  private int net(String name) {
    return board.rules.nets.get(name, 1).netNumber;
  }

  private PolylineTrace wireFrom(double x, double y) {
    IntPoint target = p(x, y);
    for (Item item : board.getItems()) {
      if (item instanceof PolylineTrace trace) {
        if (trace.firstCorner().equals(target) || trace.lastCorner().equals(target)) {
          return trace;
        }
      }
    }
    throw new AssertionError("Trace from " + x + ", " + y + " not found");
  }

  private PolylineTrace insert(int layer, FixedState fixedState, IntPoint... corners) {
    int halfWidth = (int) board.communication.coordinateTransform.dsnToBoard(100);
    return (PolylineTrace)
        board.insertTraceWithoutCleaning(
            new Polyline(corners), layer, halfWidth, new int[] {net("N")}, 1, fixedState);
  }

  @Test
  public void dsnFixWiresAndViasAreInputFixedAndKeptAsFixed() {
    PolylineTrace trunk = wireFrom(2000, 5000);
    Via via = null;
    for (Item item : board.getItems()) {
      if (item instanceof Via v) {
        via = v;
        break;
      }
    }
    assertNotNull(via);

    assertEquals(FixedState.INPUT_FIXED, trunk.getFixedState());
    assertEquals(FixedState.INPUT_FIXED, via.getFixedState());
    assertEquals(FixedState.USER_FIXED, wireFrom(10000, 9000).getFixedState());

    for (Item item : new Item[] {trunk, via}) {
      assertTrue(item.isShoveFixed());
      assertTrue(item.isUserFixed());
      assertTrue(item.isDeletionForbidden());
      assertFalse(item.isRoutable());
      item.unfix();
      assertEquals(FixedState.INPUT_FIXED, item.getFixedState());
    }
    assertTrue(trunk.isPreservedRoute());
    assertTrue(FixedState.INPUT_FIXED.isFix());
    assertTrue(FixedState.SYSTEM_FIXED.isFix());
    assertFalse(FixedState.USER_FIXED.isFix());
    assertFalse(FixedState.SYSTEM_FIXED.isPreservedRoute());
  }

  @Test
  public void branchEndingInsideAFixWireIsConnectedAndSurvivesTailRemoval() {
    PolylineTrace trunk = wireFrom(2000, 5000);
    PolylineTrace anchor = wireFrom(10000, 9000);
    PolylineTrace branch = insert(0, FixedState.UNFIXED, p(10000, 9000), p(10000, 5000));

    assertTrue(branch.getStartContacts().contains(anchor));
    assertTrue(branch.getEndContacts().contains(trunk));
    assertTrue(trunk.getNormalContacts().contains(branch));
    assertEquals(p(10000, 5000), branch.normalContactPoint(trunk));
    assertEquals(p(10000, 5000), trunk.normalContactPoint(branch));
    assertFalse(branch.isTail());

    assertFalse(board.removeTraceTails(-1, Item.StopConnectionOption.NONE));
    assertTrue(branch.isOnTheBoard());
    assertTrue(trunk.isOnTheBoard());
    assertEquals(p(2000, 5000), trunk.firstCorner());
    assertEquals(p(18000, 5000), trunk.lastCorner());
    Set<Item> connectedSet = branch.getConnectedSet(net("N"));
    assertTrue(connectedSet.contains(trunk));
    assertTrue(connectedSet.contains(anchor));
  }

  @Test
  public void viaOnAFixWireIsConnected() {
    PolylineTrace trunk = wireFrom(2000, 5000);
    Padstack padstack = board.library.padstacks.get("V");
    assertNotNull(padstack);
    Via via =
        board.insertVia(
            padstack, p(6000, 5000), new int[] {net("N")}, 1, FixedState.UNFIXED, true);

    assertTrue(via.getNormalContacts().contains(trunk));
    assertTrue(trunk.getNormalContacts().contains(via));
    assertEquals(p(6000, 5000), via.normalContactPoint(trunk));
  }
}
