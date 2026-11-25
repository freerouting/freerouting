package app.freerouting.board.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstacks;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.GlobalSettings;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolylineTraceContactTest {
  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    LayerStructure layers = new LayerStructure(new Layer[] {new Layer("Top", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    board =
        new RoutingBoard(
            new IntBox(-10000, -10000, 10000, 10000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            new Communication());
    board.rules.nets.add("Net1", 1, false);
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void rationalEndpointRemainsTraversable(boolean reverseFirst, boolean reverseSecond) {
    // Integer-defined lines y=x and y=1-x meet at the rational point (0.5, 0.5).
    Line diagonal = new Line(new IntPoint(0, 0), new IntPoint(2, 2));
    Line cut = new Line(new IntPoint(0, 1), new IntPoint(1, 0));
    Line left = new Line(new IntPoint(-1000, 0), new IntPoint(-1000, 1));
    Line right = new Line(new IntPoint(1000, 0), new IntPoint(1000, 1));
    Polyline firstLine = new Polyline(new Line[] {left, diagonal, cut});
    Polyline secondLine = new Polyline(new Line[] {cut, diagonal, right});
    Point junction = firstLine.lastCorner();
    assertFalse(junction instanceof IntPoint);
    PolylineTrace first = insertTrace(reverseFirst ? firstLine.reverse() : firstLine);
    PolylineTrace second = insertTrace(reverseSecond ? secondLine.reverse() : secondLine);

    assertTrue(first.contains(junction));
    assertTrue(second.contains(junction));
    assertFalse(first.contains(null));
    assertTrue(first.getNormalContacts().contains(second));
    assertTrue(second.getNormalContacts().contains(first));
    assertEquals(junction, first.normalContactPoint((Item) second));
    assertEquals(junction, second.normalContactPoint((Item) first));
    assertTrue(first.getConnectionItems().contains(second));
    assertTrue(second.getConnectionItems().contains(first));
  }

  @Test
  void parallelDiagonalTracesReportExactContactsWithOctagonalBounds() {
    PolylineTrace[] diagonals = new PolylineTrace[16];
    for (int i = 0; i < diagonals.length; i++) {
      int offset = i * 200;
      diagonals[i] =
          insertTrace(
              new Polyline(new IntPoint(-4000, -4000 + offset), new IntPoint(4000, 4000 + offset)));
    }
    for (PolylineTrace diagonal : diagonals) {
      assertTrue(diagonal.getNormalContacts().isEmpty());
    }

    // Connect a branch to the interior midpoint (0, 0) of the first diagonal
    PolylineTrace branch = insertTrace(new Polyline(new IntPoint(0, 0), new IntPoint(2000, -2000)));
    assertEquals(Set.of(branch), diagonals[0].getNormalContacts());
    assertEquals(Set.of(diagonals[0]), branch.getNormalContacts());
    for (int i = 1; i < diagonals.length; i++) {
      assertTrue(diagonals[i].getNormalContacts().isEmpty());
    }
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,true", "true,false", "false,false"})
  void pinExitSwapRequiresStubEndpoint(boolean atStart, boolean endpoint) {
    board.rules.setPinEdgeToTurnDist(100);
    board.library.padstacks = new Padstacks(board.layerStructure);
    board.library.packages = new Packages(board.library.padstacks);
    var pad =
        board.library.padstacks.add(
            "pad", new ConvexShape[] {new IntBox(-500, -100, 500, 100)}, true, false);
    var pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", pad.id, Vector.ZERO, 0)});
    IntPoint center = new IntPoint(0, 0);
    var component = board.components.add(center, 0, true, pkg);
    board.insertPin(component.id, 0, new int[] {1}, 1, FixedState.UNFIXED);
    Point tip = new IntPoint(800, 0);
    PolylineTrace stub =
        board.insertTraceWithoutCleaning(
            new Polyline(center, tip), 0, 10, new int[] {1}, 1, FixedState.SHOVE_FIXED);
    Point junction = endpoint ? tip : new IntPoint(700, 0);
    Point end = new IntPoint(-2000, 80);
    Polyline path = new Polyline(junction, end);
    PolylineTrace trace = insertTrace(atStart ? path : path.reverse());
    assertEquals(Set.of(stub), atStart ? trace.getStartContacts() : trace.getEndContacts());
    assertEquals(endpoint, trace.swapConnectionToPin(atStart));
    assertEquals(endpoint ? FixedState.UNFIXED : FixedState.SHOVE_FIXED, stub.getFixedState());
    if (endpoint) {
      assertEquals(1, board.getItems().stream().filter(PolylineTrace.class::isInstance).count());
      assertTrue(trace.contains(center) && trace.contains(tip) && trace.contains(end));
    } else {
      assertTrue(stub.isOnTheBoard());
      assertEquals(2, stub.cornerCount());
      assertEquals(center, stub.firstCorner());
      assertEquals(tip, stub.lastCorner());
    }
    assertEquals(1, board.getConnectedSets(1).size());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  private PolylineTrace insertTrace(Polyline polyline) {
    return board.insertTraceWithoutCleaning(polyline, 0, 10, new int[] {1}, 1, FixedState.UNFIXED);
  }
}
