package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecouplingRouteCheckpointTest {

  @Test
  @DisplayName("Rollback removes newly inserted items generated during the attempt")
  void testCaptureAndRollbackNewItems() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    IntBox searchBox = new IntBox(new IntPoint(10000, 10000), new IntPoint(20000, 20000));
    int netNo = 1;
    int layer = 0;

    DecouplingRouteCheckpoint checkpoint =
        DecouplingRouteCheckpoint.capture(board, searchBox, layer, netNo);
    int maxIdBefore = checkpoint.maxIdBefore;

    // Insert a new trace into the board
    Polyline line = new Polyline(new IntPoint(12000, 12000), new IntPoint(15000, 15000));
    PolylineTrace newTrace =
        board.insertTraceWithoutCleaning(
            line, layer, 150, new int[] {netNo}, 1, FixedState.UNFIXED);
    assertNotNull(newTrace);
    assertTrue(newTrace.getId() > maxIdBefore);
    assertNotNull(board.getItem(newTrace.getId()));

    // Rollback
    checkpoint.rollback(board);

    // Verify newly inserted item was removed
    assertNull(board.getItem(newTrace.getId()), "Newly inserted trace must be removed on rollback");
  }

  @Test
  @DisplayName("Rollback restores split pre-existing traces without data loss (Issue #1054)")
  void testRollbackRestoresSplitPreExistingTrace() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Insert an initial pre-existing trace to be snapshot
    Polyline origLine = new Polyline(new IntPoint(10000, 10000), new IntPoint(20000, 10000));
    int netNo = 1;
    int layer = 0;
    PolylineTrace origTrace =
        board.insertTraceWithoutCleaning(
            origLine, layer, 200, new int[] {netNo}, 1, FixedState.UNFIXED);
    assertNotNull(origTrace);
    origTrace.setDecouplingProtected(true);
    origTrace.assignComponentId(42);
    int origId = origTrace.getId();

    IntBox searchBox = origTrace.boundingBox().offset(1000);

    // Capture checkpoint
    DecouplingRouteCheckpoint checkpoint =
        DecouplingRouteCheckpoint.capture(board, searchBox, layer, netNo);
    assertEquals(1, checkpoint.snapshots.size());
    assertEquals(origId, checkpoint.snapshots.get(0).originalId());
    assertTrue(checkpoint.snapshots.get(0).decouplingFlags() != 0);

    // Simulate trace split (Issue #1054): parent trace removed, two new child traces inserted with
    // higher IDs
    origTrace.assignComponentId(0);
    board.removeItem(origTrace);
    assertNull(board.getItem(origId));

    Polyline childLine1 = new Polyline(new IntPoint(10000, 10000), new IntPoint(15000, 10000));
    Polyline childLine2 = new Polyline(new IntPoint(15000, 10000), new IntPoint(20000, 10000));

    PolylineTrace child1 =
        board.insertTraceWithoutCleaning(
            childLine1, layer, 200, new int[] {netNo}, 1, FixedState.UNFIXED);
    PolylineTrace child2 =
        board.insertTraceWithoutCleaning(
            childLine2, layer, 200, new int[] {netNo}, 1, FixedState.UNFIXED);
    assertNotNull(child1);
    assertNotNull(child2);
    assertTrue(child1.getId() > checkpoint.maxIdBefore);
    assertTrue(child2.getId() > checkpoint.maxIdBefore);

    // Rollback
    checkpoint.rollback(board);

    // Verify children removed
    assertNull(board.getItem(child1.getId()), "Split child 1 must be removed on rollback");
    assertNull(board.getItem(child2.getId()), "Split child 2 must be removed on rollback");

    // Verify original trace restored in the board with identical geometry
    boolean foundRestored = false;
    for (Item it : board.getItems()) {
      if (it instanceof PolylineTrace pt && pt.containsNet(netNo) && pt.getLayer() == layer) {
        if (pt.polyline().cornerCount() == origLine.cornerCount()
            && pt.firstCorner().equals(origTrace.firstCorner())
            && pt.lastCorner().equals(origTrace.lastCorner())) {
          foundRestored = true;
          assertTrue(pt.isDecouplingProtected(), "Restored trace must preserve decoupling flags");
          assertEquals(42, pt.getComponentId(), "Restored trace must preserve component ID");
          break;
        }
      }
    }
    assertTrue(foundRestored, "Original split trace must be restored after rollback");
  }

  @Test
  @DisplayName("Rollback does not duplicate pre-existing traces that were not split")
  void testNoDuplicateWhenOriginalTraceNotSplit() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    Polyline line = new Polyline(new IntPoint(5000, 5000), new IntPoint(8000, 5000));
    int netNo = 1;
    int layer = 0;
    PolylineTrace trace =
        board.insertTraceWithoutCleaning(
            line, layer, 150, new int[] {netNo}, 1, FixedState.UNFIXED);
    assertNotNull(trace);
    int traceId = trace.getId();

    IntBox box = trace.boundingBox().offset(500);
    DecouplingRouteCheckpoint checkpoint =
        DecouplingRouteCheckpoint.capture(board, box, layer, netNo);

    int countBefore = 0;
    for (Item it : board.getItems()) {
      if (it instanceof PolylineTrace) {
        countBefore++;
      }
    }

    // Rollback without modifying anything
    checkpoint.rollback(board);

    int countAfter = 0;
    for (Item it : board.getItems()) {
      if (it instanceof PolylineTrace) {
        countAfter++;
      }
    }

    assertEquals(countBefore, countAfter, "Trace count must not change when no items were split");
    assertNotNull(board.getItem(traceId), "Original unsplit trace must remain in board");
  }
}
