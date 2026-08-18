package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.board.state.BoardObservers;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Characterization tests for the responsibility-oriented board service façades. */
class BoardServiceCharacterizationTest {

  @Test
  void itemQueriesAndSerializationRemainStable() {
    CountingObserver observer = new CountingObserver();
    RoutingBoard board = createBoard(observer);
    PolylineTrace trace = insertTrace(board, 10, 100, 200);

    assertNotNull(board.getOutline());
    assertEquals(trace, board.getItem(trace.getId()));
    assertTrue(board.getItems().contains(trace));
    assertEquals(1, board.getTraces().size());
    assertEquals(1, observer.newItems);

    byte[] serialized = board.serialize(false);
    RoutingBoard restored = (RoutingBoard) BasicBoard.deserialize(serialized);
    assertNotNull(restored);
    assertEquals(board.getHash(), restored.getHash());
    assertEquals(board.getTraces().size(), restored.getTraces().size());
    assertNotNull(restored.searchTreeManager);
  }

  @Test
  void snapshotUndoRedoPreservesItemsAndObserverNotifications() {
    CountingObserver observer = new CountingObserver();
    RoutingBoard board = createBoard(observer);
    board.generateSnapshot();

    PolylineTrace trace = insertTrace(board, 20, 100, 200);
    Set<Integer> changedNets = new HashSet<>();
    assertTrue(board.undo(changedNets));
    assertFalse(board.getTraces().contains(trace));
    assertTrue(changedNets.contains(20));
    assertEquals(1, observer.deletedItems);

    assertTrue(board.redo(changedNets));
    assertTrue(board.getTraces().contains(trace));
    assertEquals(2, observer.newItems);
  }

  @Test
  void changedAreaFacadeRetainsLifecycleAndGraphicsTracking() {
    RoutingBoard board = createBoard(new CountingObserver());
    board.startMarkingChangedArea();
    assertNotNull(board.changedArea);
    board.joinChangedArea(new IntPoint(100, 100).toFloat(), 0);
    board.markAllChangedArea();
    board.optChangedArea(new int[0], IntOctagon.EMPTY, 1, null, null, 0);

    assertTrue(board.changedArea == null);
    assertFalse(board.getGraphicsUpdateBox().isEmpty());
  }

  private static PolylineTrace insertTrace(RoutingBoard board, int netNumber, int x1, int x2) {
    return board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(x1, 100), new IntPoint(x2, 100)),
        0,
        10,
        new int[] {netNumber},
        0,
        FixedState.UNFIXED);
  }

  private static RoutingBoard createBoard(BoardObservers observers) {
    LayerStructure layerStructure = new LayerStructure(new Layer[] {new Layer("Top", true)});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    Communication communication = new Communication();
    communication.observers = null;
    PolylineShape outline = TileShape.getInstance(0, 0, 1000, 1000);
    RoutingBoard board =
        new RoutingBoard(
            new IntBox(0, 0, 1000, 1000),
            layerStructure,
            new PolylineShape[] {outline},
            0,
            boardRules,
            communication);
    communication.observers = observers;
    return board;
  }

  private static final class CountingObserver extends BoardObserverAdaptor {

    private int newItems;
    private int deletedItems;

    @Override
    public void notifyDeleted(Item item) {
      deletedItems++;
    }

    @Override
    public void notifyNew(Item item) {
      newItems++;
    }
  }
}
