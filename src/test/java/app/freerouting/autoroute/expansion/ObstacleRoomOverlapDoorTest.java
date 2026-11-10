package app.freerouting.autoroute.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Doors between the obstacle rooms of two traces of a foreign net. The autorouter may rip both up
 * and pass from one room to the other through their overlap; when the traces meet on a pin, that
 * overlap lies on the pin, which is not ripped.
 */
class ObstacleRoomOverlapDoorTest {

  private static final String DSN =
      """
      (pcb doors (resolution um 1) (unit um)
        (structure (layer top (type signal))
          (boundary (rect pcb 0 0 10000 6000)) (rule (width 150) (clearance 150)))
        (placement
          (component SMD (place P 3000 3000 front 0))
          (component TH (place B1 3000 5500 front 0) (place B2 3000 500 front 0)
            (place C1 7000 5500 front 0) (place C2 7000 500 front 0) (place C3 9500 3000 front 0)))
        (library
          (image SMD (pin PS 1 0 0)) (image TH (pin PT 1 0 0))
          (padstack PS (shape (rect top -400 -140 400 140)) (attach off))
          (padstack PT (shape (circle top 600)) (attach off)))
        (network (net B (pins P-1 B1-1 B2-1)) (net C (pins C1-1 C2-1 C3-1)))
        (wiring
          (wire (path top 150 3000 3000 3000 5500) (net B))
          (wire (path top 150 3000 3000 3000 500) (net B))
          (wire (path top 150 7000 3000 7000 5500) (net C))
          (wire (path top 150 7000 3000 7000 500) (net C))
          (wire (path top 150 7000 3000 9500 3000) (net C))))
      """;

  private static RoutingBoard readBoard() {
    ByteArrayInputStream stream = new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static List<PolylineTrace> traces(RoutingBoard board, String net) {
    int netNo = board.rules.nets.get(net).iterator().next().netNumber;
    return board.getConnectableItems(netNo).stream()
        .filter(PolylineTrace.class::isInstance)
        .map(PolylineTrace.class::cast)
        .toList();
  }

  @Test
  void noDoorBetweenTracesMeetingOnAPin() {
    RoutingBoard board = readBoard();
    ShapeSearchTree tree = board.searchTreeManager.getDefaultTree();
    List<PolylineTrace> b = traces(board, "B");
    assertEquals(2, b.size());
    ObstacleExpansionRoom first = new ObstacleExpansionRoom(b.get(0), 0, tree);
    ObstacleExpansionRoom second = new ObstacleExpansionRoom(b.get(1), 0, tree);

    assertFalse(first.createOverlapDoor(second), "the overlap of the two rooms is on pin P-1");
    assertTrue(first.getDoors().isEmpty());
  }

  @Test
  void doorBetweenTracesMeetingOffPins() {
    RoutingBoard board = readBoard();
    ShapeSearchTree tree = board.searchTreeManager.getDefaultTree();
    List<PolylineTrace> c = traces(board, "C");
    assertEquals(3, c.size());
    ObstacleExpansionRoom first = new ObstacleExpansionRoom(c.get(0), 0, tree);
    ObstacleExpansionRoom second = new ObstacleExpansionRoom(c.get(1), 0, tree);

    assertTrue(first.createOverlapDoor(second));
  }
}
