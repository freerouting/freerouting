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
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
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

  private PolylineTrace insertTrace(Polyline polyline) {
    return board.insertTraceWithoutCleaning(polyline, 0, 10, new int[] {1}, 1, FixedState.UNFIXED);
  }
}
