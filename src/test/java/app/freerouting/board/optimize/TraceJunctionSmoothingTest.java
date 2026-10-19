package app.freerouting.board.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.GlobalSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TraceJunctionSmoothingTest {
  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    LayerStructure layers = new LayerStructure(new Layer[] {new Layer("Top", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    board =
        new RoutingBoard(
            new IntBox(0, 0, 100000, 100000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            new Communication());
    board.rules.nets.add("Net1", 1, false);
    board.rules.nets.add("Net2", 2, false);
  }

  @ParameterizedTest
  @CsvSource({
    "FORTYFIVE_DEGREE, false, false, 30000",
    "FORTYFIVE_DEGREE, false, false, 40000",
    "FORTYFIVE_DEGREE, false, true, 30000",
    "FORTYFIVE_DEGREE, false, true, 40000",
    "FORTYFIVE_DEGREE, true, false, 30000",
    "FORTYFIVE_DEGREE, true, false, 40000",
    "FORTYFIVE_DEGREE, true, true, 30000",
    "FORTYFIVE_DEGREE, true, true, 40000",
    "NONE, false, false, 30000",
    "NONE, false, false, 40000",
    "NONE, false, true, 30000",
    "NONE, false, true, 40000",
    "NONE, true, false, 30000",
    "NONE, true, false, 40000",
    "NONE, true, true, 30000",
    "NONE, true, true, 40000"
  })
  void unlockedInteriorJunctionRetainsTopologyAndClearance(
      AngleRestriction restriction, boolean reverseBranch, boolean reverseTrunk, int junctionX) {
    board.rules.setTraceAngleRestriction(restriction);
    final PolylineTrace trunk =
        insertTrace(
            List.of(
                new IntPoint(10000, 10000),
                new IntPoint(40000, 10000),
                new IntPoint(50000, 20000),
                new IntPoint(50000, 50000)),
            reverseTrunk,
            1,
            FixedState.USER_FIXED);
    Point junction = new IntPoint(junctionX, 10000);
    PolylineTrace branch =
        insertTrace(
            List.of(junction, new IntPoint(junctionX - 10000, 0)),
            reverseBranch,
            1,
            FixedState.UNFIXED);
    insertTrace(
        List.of(new IntPoint(45000, 8000), new IntPoint(45000, 11000)),
        false,
        2,
        FixedState.USER_FIXED);
    final List<Point> originalBranch = corners(branch);
    final List<Point> originalTrunk = corners(trunk);
    assertEquals(1, board.getConnectedSets(1).size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());

    trunk.setFixedState(FixedState.UNFIXED);
    assertFalse(branch.pullTight(true, 100, null));
    assertFalse(
        branch.smoothenEndCornersFork(true, 100, null),
        "Endpoint smoothing must decline an unsupported interior junction");

    assertEquals(originalBranch, corners(branch));
    assertEquals(originalTrunk, corners(trunk));
    assertTrue(branch.isOnTheBoard());
    assertTrue(branch.getNormalContacts().contains(trunk));
    assertTrue(trunk.getNormalContacts().contains(branch));
    assertEquals(1, board.getConnectedSets(1).size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @CsvSource({"FORTYFIVE_DEGREE, false", "FORTYFIVE_DEGREE, true", "NONE, false", "NONE, true"})
  void endpointJunctionStillAllowsSmoothing(AngleRestriction restriction, boolean reverseBranch) {
    board.rules.setTraceAngleRestriction(restriction);
    Point junction = new IntPoint(30000, 10000);
    insertTrace(
        List.of(junction, new IntPoint(30000, 0), new IntPoint(40000, 0)),
        false,
        1,
        FixedState.UNFIXED);
    PolylineTrace branch =
        insertTrace(
            List.of(junction, new IntPoint(20000, 0)), reverseBranch, 1, FixedState.UNFIXED);
    assertTrue(
        branch.smoothenEndCornersFork(true, 100, null),
        "Supported endpoint junctions should remain optimizable");
    assertEquals(1, board.getConnectedSets(1).size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @CsvSource({"FORTYFIVE_DEGREE, false", "FORTYFIVE_DEGREE, true", "NONE, false", "NONE, true"})
  void interiorContactVetoesSmoothingEvenWithAnEndpointContact(
      AngleRestriction restriction, boolean endpointInsertedFirst) {
    board.rules.setTraceAngleRestriction(restriction);
    Point junction = new IntPoint(30000, 10000);
    final List<Point> endpointPoints =
        List.of(junction, new IntPoint(30000, 0), new IntPoint(40000, 0));
    if (endpointInsertedFirst) {
      insertTrace(endpointPoints, false, 1, FixedState.UNFIXED);
    }
    final PolylineTrace trunk =
        insertTrace(
            List.of(
                new IntPoint(10000, 10000),
                new IntPoint(40000, 10000),
                new IntPoint(50000, 20000),
                new IntPoint(50000, 50000)),
            false,
            1,
            FixedState.UNFIXED);
    if (!endpointInsertedFirst) {
      insertTrace(endpointPoints, false, 1, FixedState.UNFIXED);
    }
    PolylineTrace branch =
        insertTrace(List.of(junction, new IntPoint(20000, 0)), false, 1, FixedState.UNFIXED);
    final List<Point> original = corners(branch);
    assertEquals(2, branch.getStartContacts().size());
    assertFalse(branch.smoothenEndCornersFork(true, 100, null));
    assertEquals(original, corners(branch));
    assertTrue(branch.getNormalContacts().contains(trunk));
    assertEquals(1, board.getConnectedSets(1).size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  private PolylineTrace insertTrace(
      List<Point> points, boolean reverse, int net, FixedState fixedState) {
    List<Point> ordered = new ArrayList<>(points);
    if (reverse) {
      Collections.reverse(ordered);
    }
    return board.insertTraceWithoutCleaning(
        new Polyline(ordered.toArray(Point[]::new)), 0, 100, new int[] {net}, 1, fixedState);
  }

  private List<Point> corners(PolylineTrace trace) {
    List<Point> result = new ArrayList<>();
    for (int i = 0; i < trace.cornerCount(); i++) {
      result.add(trace.polyline().corner(i));
    }
    return result;
  }
}
