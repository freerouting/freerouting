package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.api.Test;

class DecouplingItemFlagsTest {

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer[] layers = new Layer[] {layer1};
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication = new Communication();

    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
        0,
        boardRules,
        communication);
  }

  @Test
  void testItemFlagCopy() {
    RoutingBoard board = createTestBoard();
    Polyline polyline = new Polyline(new Point[] {new IntPoint(0, 0), new IntPoint(1000, 0)});
    PolylineTrace trace =
        new PolylineTrace(polyline, 0, 100, new int[] {1}, 0, 1, 0, FixedState.UNFIXED, board);
    board.insertItem(trace);

    assertFalse(trace.isDecouplingProtected());
    trace.setDecouplingProtected(true);
    assertTrue(trace.isDecouplingProtected());

    Item copy = trace.copy(board.communication.idGenerator.newId());
    assertTrue(copy.isDecouplingProtected());
  }

  @Test
  void testPolylineTraceSplitPropagatesFlags() {
    RoutingBoard board = createTestBoard();
    Polyline polyline = new Polyline(new Point[] {new IntPoint(0, 0), new IntPoint(2000, 0)});
    PolylineTrace trace =
        new PolylineTrace(polyline, 0, 100, new int[] {1}, 0, 1, 0, FixedState.UNFIXED, board);
    board.insertItem(trace);
    trace.setDecouplingProtected(true);

    Point splitPoint = new IntPoint(1000, 0);
    var pieces = trace.split(splitPoint);

    assertNotNull(pieces);
    assertTrue(pieces.length == 2);
    assertTrue(pieces[0].isDecouplingProtected(), "First child trace must inherit decoupling flag");
    assertTrue(
        pieces[1].isDecouplingProtected(), "Second child trace must inherit decoupling flag");
  }

  @Test
  void testPolylineTraceCombineFlags() {
    RoutingBoard board = createTestBoard();
    Point p1 = new IntPoint(0, 0);
    Point p2 = new IntPoint(1000, 0);
    Point p3 = new IntPoint(2000, 0);

    PolylineTrace t1 =
        new PolylineTrace(
            new Polyline(new Point[] {p1, p2}),
            0,
            100,
            new int[] {1},
            0,
            1,
            0,
            FixedState.UNFIXED,
            board);
    PolylineTrace t2 =
        new PolylineTrace(
            new Polyline(new Point[] {p2, p3}),
            0,
            100,
            new int[] {1},
            0,
            2,
            0,
            FixedState.UNFIXED,
            board);

    board.insertItem(t1);
    board.insertItem(t2);

    t1.setDecouplingProtected(true);
    t2.setDecouplingProtected(true);

    boolean combined = t2.combine();
    assertTrue(combined, "Traces should combine");
    assertTrue(
        t2.isDecouplingProtected(),
        "Combined trace must remain protected when both inputs were protected");

    // Case B: One protected, one unprotected -> result must NOT be protected
    Point p4 = new IntPoint(3000, 0);
    PolylineTrace t3 =
        new PolylineTrace(
            new Polyline(new Point[] {p3, p4}),
            0,
            100,
            new int[] {1},
            0,
            3,
            0,
            FixedState.UNFIXED,
            board);
    board.insertItem(t3);
    assertFalse(t3.isDecouplingProtected());

    boolean combined2 = t3.combine();
    assertTrue(combined2, "Traces should combine");
    assertFalse(
        t3.isDecouplingProtected(),
        "Combined trace must lose protection if one merged piece is unprotected");
  }
}
