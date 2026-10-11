package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.actions.ComponentItemTransform;
import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ViaInfo;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class Issue969ReviewRegressionTest extends RoutingFixtureTest {
  static Stream<Arguments> shoveCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int[] classes : new int[][] {{1, 1}, {1, 2}, {2, 1}, {2, 2}}) {
        for (int wideClearance : new int[] {3000, 10000}) {
          cases.add(Arguments.of(compensated, classes[0], classes[1], wideClearance));
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("shoveCases")
  void viaShovePreservesOrthogonalRouting(
      boolean compensated, int viaClass, int traceClass, int wideClearance) throws Exception {
    var board = loadBoard(compensated);
    board.rules.clearanceMatrix.setValue(2, 2, wideClearance);
    board.searchTreeManager.clearanceValueChanged();
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.insertTrace(
        new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
        0,
        500,
        new int[] {2},
        traceClass,
        FixedState.UNFIXED);
    var via =
        new ViaInfo("test", board.library.padstacks.get("Via100"), viaClass, false, board.rules);
    assertTrue(
        ForcedViaInserter.check(
            via,
            new IntPoint(100000, -100000),
            new int[] {1},
            20,
            20,
            board,
            new int[] {0, 0},
            viaClass));
    assertTrue(
        ForcedViaInserter.insert(
            via,
            new IntPoint(100000, -100000),
            new int[] {1},
            viaClass,
            new int[] {0, 0},
            20,
            20,
            board));
    assertEquals(1, board.getVias().size());
    assertEquals(new IntPoint(100000, -100000), board.getVias().iterator().next().getCenter());
    assertConnectedEndpoints(board, new IntPoint(100000, -120000), new IntPoint(100000, -80000), 2);
    assertOrthogonalCopper(board);
    assertViaTraceGaps(board);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertEquals(compensated, board.searchTreeManager.isClearanceCompensationUsed());
  }

  private static void assertConnectedEndpoints(
      RoutingBoard board, Point start, Point end, int net) {
    var first =
        board.getTraces().stream()
            .filter(
                t ->
                    t.containsNet(net)
                        && (t.firstCorner().equals(start) || t.lastCorner().equals(start)))
            .findFirst()
            .orElseThrow();
    var last =
        board.getTraces().stream()
            .filter(
                t ->
                    t.containsNet(net)
                        && (t.firstCorner().equals(end) || t.lastCorner().equals(end)))
            .findFirst()
            .orElseThrow();
    assertTrue(
        first.getConnectedSet(net).contains(last), "Original endpoints must remain connected");
  }

  private static void assertOrthogonalCopper(RoutingBoard board) {
    assertFalse(board.getTraces().isEmpty());
    for (var trace : board.getTraces()) {
      var polyline = assertInstanceOf(PolylineTrace.class, trace).polyline();
      for (int i = 0; i < polyline.cornerCount() - 1; i++) {
        var a = polyline.cornerApprox(i);
        var b = polyline.cornerApprox(i + 1);
        assertTrue(
            Math.abs(a.x - b.x) < 0.1 || Math.abs(a.y - b.y) < 0.1,
            () -> "Orthogonal shove produced a diagonal: " + a + " -> " + b);
      }
    }
  }

  private static void assertViaTraceGaps(RoutingBoard board) {
    // This fixture's Via100 is a 1000-unit diameter circle. Measure copper independently of DRC.
    for (var via : board.getVias()) {
      var center = via.getCenter().toFloat();
      for (var trace : board.getTraces()) {
        if (trace.sharesNet(via)) {
          continue;
        }
        var polyline = assertInstanceOf(PolylineTrace.class, trace).polyline();
        for (int i = 0; i < polyline.cornerCount() - 1; i++) {
          var a = polyline.cornerApprox(i);
          var b = polyline.cornerApprox(i + 1);
          double dx = b.x - a.x;
          double dy = b.y - a.y;
          double t =
              Math.max(
                  0,
                  Math.min(
                      1, ((center.x - a.x) * dx + (center.y - a.y) * dy) / (dx * dx + dy * dy)));
          double gap =
              Math.hypot(center.x - a.x - t * dx, center.y - a.y - t * dy)
                  - 500
                  - trace.getHalfWidth();
          int required =
              board.rules.clearanceMatrix.getValue(
                  via.clearanceClassIndex(), trace.clearanceClassIndex(), 0, false);
          assertTrue(gap >= required, () -> "Physical gap " + gap + " is below " + required);
        }
      }
    }
  }

  static Stream<Arguments> directionalShoveCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int turn = 0; turn < 4; turn++) {
        for (int viaClass : new int[] {1, 2}) {
          cases.add(Arguments.of(compensated, turn, viaClass));
        }
      }
    }
    return cases.build();
  }

  private static IntPoint rotatedPoint(int x, int y, int turn) {
    return (IntPoint) new IntPoint(x, y).turn90Degree(turn, new IntPoint(100000, -100000));
  }

  @ParameterizedTest
  @MethodSource("directionalShoveCases")
  void movingViaPreservesConnectedOrthogonalCopper(boolean compensated, int turn, int viaClass)
      throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    var start = rotatedPoint(100000, -120000, turn);
    var end = rotatedPoint(100000, -80000, turn);
    board.insertTrace(new Polyline(start, end), 0, 500, new int[] {2}, 2, FixedState.UNFIXED);
    var via =
        board.insertVia(
            board.library.padstacks.get("Via100"),
            rotatedPoint(70000, -100000, turn),
            new int[] {1},
            viaClass,
            FixedState.UNFIXED,
            false);
    var destination = new IntPoint(100000, -100000);
    var delta = destination.differenceBy(via.getCenter());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertTrue(DrillItemMover.check(via, delta, 20, 20, null, board, null));
    assertTrue(DrillItemMover.insert(via, delta, 20, 20, null, board));
    assertTrue(via.isOnTheBoard());
    assertEquals(destination, via.getCenter());
    assertConnectedEndpoints(board, start, end, 2);
    assertOrthogonalCopper(board);
    assertViaTraceGaps(board);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @MethodSource("directionalShoveCases")
  void recursiveViaShoveUsesEachPairwiseClass(boolean compensated, int turn, int incomingClass)
      throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.rules.nets.add("third", 1, false);
    board.searchTreeManager.clearanceValueChanged();
    var firstCenter = rotatedPoint(100300, -100000, turn);
    var secondCenter = rotatedPoint(111500, -100000, turn);
    var pad = board.library.padstacks.get("Via100");
    var first = board.insertVia(pad, firstCenter, new int[] {2}, 2, FixedState.UNFIXED, false);
    var second = board.insertVia(pad, secondCenter, new int[] {3}, 2, FixedState.UNFIXED, false);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var incoming = new ViaInfo("test", pad, incomingClass, false, board.rules);
    var center = new IntPoint(100000, -100000);
    assertTrue(
        ForcedViaInserter.check(
            incoming, center, new int[] {1}, 20, 20, board, new int[] {0, 0}, incomingClass));
    assertTrue(
        ForcedViaInserter.insert(
            incoming, center, new int[] {1}, incomingClass, new int[] {0, 0}, 20, 20, board));
    assertEquals(3, board.getVias().size());
    for (var a : board.getVias()) {
      for (var b : board.getVias()) {
        if (a == b) {
          continue;
        }
        double gap = a.getCenter().toFloat().distance(b.getCenter().toFloat()) - 1000;
        assertTrue(
            gap
                >= board.rules.clearanceMatrix.getValue(
                    a.clearanceClassIndex(), b.clearanceClassIndex(), 0, false));
      }
    }
    assertTrue(first.isOnTheBoard());
    assertTrue(second.isOnTheBoard());
    assertFalse(firstCenter.equals(first.getCenter()), "First obstacle must be shoved");
    assertFalse(
        secondCenter.equals(second.getCenter()), "Second obstacle must be shoved recursively");
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertEquals(compensated, board.searchTreeManager.isClearanceCompensationUsed());
  }

  @ParameterizedTest
  @MethodSource("directionalShoveCases")
  void stoppedTraceRespectsPairwiseViaClearance(boolean compensated, int turn, int traceClass)
      throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    board.insertVia(
        board.library.padstacks.get("Via100"),
        new IntPoint(100000, -100000),
        new int[] {2},
        2,
        FixedState.UNFIXED,
        false);
    var path =
        new Polyline(rotatedPoint(70000, -100000, turn), rotatedPoint(130000, -100000, turn));
    double distance =
        TraceShover.check(
            board, new LineSegment(path, 1), true, 0, new int[] {1}, 500, traceClass, 0, 0);
    int clearance = board.rules.clearanceMatrix.getValue(traceClass, 2, 0, !compensated);
    assertEquals(30000 - 1000 - clearance, distance, 0.01);
    var stop = rotatedPoint(70000 + (int) distance - 1, -100000, turn);
    board.insertTraceWithoutCleaning(
        new Polyline(path.firstCorner(), stop),
        0,
        500,
        new int[] {1},
        traceClass,
        FixedState.UNFIXED);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void physicalDrcTracksRuleChangesAndUndoRedo(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
        0,
        500,
        new int[] {1},
        2,
        FixedState.UNFIXED);
    var second =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(104500, -120000), new IntPoint(104500, -80000)),
            0,
            500,
            new int[] {2},
            2,
            FixedState.UNFIXED);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    assertEquals(1, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    board.generateSnapshot();
    second.moveBy(new IntVector(20000, 0));
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertTrue(board.undo(null));
    assertEquals(1, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertTrue(board.redo(null));
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    for (var trace : board.getTraces()) {
      assertTrue(board.searchTreeManager.validateEntries(trace));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectedTransformRemovesAllTemporaryEntries(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    var obstacle =
        board.insertObstacle(
            new IntBox(99500, -110500, 100500, -109500), 0, 1, FixedState.USER_FIXED);
    var original =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(90000, -120000), new IntPoint(90000, -80000)),
            0,
            500,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    board.detachItemForMove(original);
    assertNull(
        ComponentItemTransform.apply(
            board, List.of(original), 90, false, new IntPoint(100000, -100000)));
    var area = new IntBox(50000, -150000, 150000, -50000);
    for (var tree :
        List.of(
            board.searchTreeManager.getDefaultTree(),
            board.searchTreeManager.getUncompensatedTree())) {
      var indexed = tree.overlappingItemsWithClearance(area, 0, new int[0], 1);
      assertEquals(1, indexed.size());
      assertTrue(indexed.contains(obstacle));
    }
    assertFalse(original.isOnTheBoard());
    assertEquals(new IntPoint(90000, -120000), original.firstCorner());
    assertEquals(new IntPoint(90000, -80000), original.lastCorner());
    board.insertItem(original);
    assertTrue(board.searchTreeManager.validateEntries(original));
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedClassDetourRetainsLegalCopper(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    board.insertObstacle(new IntBox(98000, -102000, 102000, -98000), 0, 2, FixedState.USER_FIXED);
    var start = new IntPoint(70000, -100000);
    var end = new IntPoint(130000, -100000);
    var path = new Polyline(start, end);
    int width = 500 + board.searchTreeManager.getDefaultTree().clearanceCompensationValue(2, 0);
    var detour = new TraceShover(board).springOverObstacles(path, width, 0, new int[] {1}, 2, null);
    assertNotNull(detour);
    assertEquals(start, detour.firstCorner());
    assertEquals(end, detour.lastCorner());
    board.insertTraceWithoutCleaning(detour, 0, 500, new int[] {1}, 2, FixedState.UNFIXED);
    assertConnectedEndpoints(board, start, end, 1);
    assertOrthogonalCopper(board);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void optimizerAdmissionUsesMixedClassClearance(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
        0,
        500,
        new int[] {2},
        2,
        FixedState.UNFIXED);
    int width = 500 + board.searchTreeManager.getDefaultTree().clearanceCompensationValue(2, 0);
    assertFalse(
        board.checkTraceShape(
            new IntBox(106000 - width, -120000, 106000 + width, -80000),
            0,
            new int[] {1},
            2,
            null));
    assertTrue(
        board.checkTraceShape(
            new IntBox(112000 - width, -120000, 112000 + width, -80000),
            0,
            new int[] {1},
            2,
            null));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void attachedViaMustCoverTheTraceClearanceEnvelope(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    var pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 2500), new Circle(Point.ZERO, 2500)});
    var center = new IntPoint(108000, -100000);
    var via = board.insertVia(pad, center, new int[] {2}, 1, FixedState.UNFIXED, false);
    var end = new IntPoint(130000, -100000);
    var trace =
        board.insertTraceWithoutCleaning(
            new Polyline(center, end), 0, 500, new int[] {2}, 2, FixedState.UNFIXED);
    assertTrue(trace.getConnectedSet(2).contains(via));
    var incoming =
        new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    assertFalse(
        ForcedViaInserter.check(
            incoming,
            new IntPoint(100000, -100000),
            new int[] {1},
            20,
            20,
            board,
            new int[] {0, 0},
            2));
    assertTrue(trace.isOnTheBoard());
    assertEquals(center, trace.firstCorner());
    assertEquals(end, trace.lastCorner());
    assertTrue(trace.getConnectedSet(2).contains(via));
    assertEquals(1, board.getVias().size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  static Stream<Arguments> clearanceCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int[] classes : new int[][] {{1, 1}, {1, 2}, {2, 1}, {2, 2}}) {
        for (int delta : new int[] {-500, 500, 2000}) {
          cases.add(Arguments.of(compensated, classes[0], classes[1], delta));
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("clearanceCases")
  void drcUsesPhysicalGapWithCompensationStillEnabled(
      boolean compensated, int firstClass, int secondClass, int delta) throws Exception {
    var board = loadBoard(compensated);
    int clearance = board.rules.clearanceMatrix.getValue(firstClass, secondClass, 0, false);
    int secondX = 101000 + clearance + delta;
    var first =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
            0,
            500,
            new int[] {1},
            firstClass,
            FixedState.USER_FIXED);
    var second =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(secondX, -120000), new IntPoint(secondX, -80000)),
            0,
            500,
            new int[] {2},
            secondClass,
            FixedState.USER_FIXED);
    assertEquals(delta < 0, !first.clearanceViolations().isEmpty());
    assertEquals(delta < 0, !second.clearanceViolations().isEmpty());
    assertEquals(
        delta < 0 ? 1 : 0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertEquals(compensated, board.searchTreeManager.isClearanceCompensationUsed());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedClassSegmentCheckPreservesNonrectangularKeepout(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    // An existing angle-restricted tree must not be mistaken for physical DRC geometry.
    board.searchTreeManager.getAutorouteTree(0);
    board.insertObstacle(
        new PolygonShape(
            new IntPoint[] {
              new IntPoint(90000, -110000),
              new IntPoint(110000, -110000),
              new IntPoint(90000, -90000)
            }),
        0,
        1,
        FixedState.USER_FIXED);
    assertEquals(
        Integer.MAX_VALUE,
        board.checkTraceSegment(
            new IntPoint(107000, -96000),
            new IntPoint(107000, -94000),
            0,
            new int[] {1},
            500,
            2,
            false));
  }

  @Test
  void physicalTreeTracksEditsAndReindexing() throws Exception {
    var board = loadBoard(true);
    var manager = board.searchTreeManager;
    var tree = manager.getUncompensatedTree();
    assertSame(tree, manager.getUncompensatedTree());
    var area = new IntBox(90000, -110000, 110000, -90000);
    var obstacle = board.insertObstacle(area, 0, 1, FixedState.UNFIXED);
    assertTrue(tree.overlappingItemsWithClearance(area, 0, new int[0], 1).contains(obstacle));
    obstacle.moveBy(new IntVector(40000, 0));
    assertFalse(tree.overlappingItemsWithClearance(area, 0, new int[0], 1).contains(obstacle));
    assertTrue(manager.validateEntries(obstacle));
    final var movedArea = (IntBox) area.translateBy(new IntVector(40000, 0));
    board.rules.clearanceMatrix.setValue(1, 1, 2500);
    manager.clearanceValueChanged();
    tree = manager.getUncompensatedTree();
    assertTrue(tree.overlappingItemsWithClearance(movedArea, 0, new int[0], 1).contains(obstacle));
    manager.setClearanceCompensationUsed(false);
    assertSame(manager.getDefaultTree(), manager.getUncompensatedTree());
    manager.setClearanceCompensationUsed(true);
    tree = manager.getUncompensatedTree();
    assertTrue(manager.validateEntries(obstacle));
    assertTrue(tree.overlappingItemsWithClearance(movedArea, 0, new int[0], 1).contains(obstacle));
    board.removeItem(obstacle);
    assertFalse(tree.overlappingItemsWithClearance(movedArea, 0, new int[0], 1).contains(obstacle));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void identityTransformPreservesPreexistingDetachedPeerViolations(boolean compensated)
      throws Exception {
    var board = loadBoard(compensated);
    var first =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
            0,
            500,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    var second =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(102000, -120000), new IntPoint(102000, -80000)),
            0,
            500,
            new int[] {2},
            1,
            FixedState.UNFIXED);
    board.detachItemForMove(first);
    board.detachItemForMove(second);
    // Do not query DRC before apply: that would initialize the tree and hide the regression.
    var originals = List.of(first, second);
    var moved = ComponentItemTransform.apply(board, List.of(first, second), 0, false, Point.ZERO);
    assertNotNull(moved);
    assertEquals(originals.size(), moved.size());
    for (int i = 0; i < moved.size(); i++) {
      var trace = assertInstanceOf(PolylineTrace.class, moved.get(i));
      assertEquals(originals.get(i).firstCorner(), trace.firstCorner());
      assertEquals(originals.get(i).lastCorner(), trace.lastCorner());
      assertTrue(originals.get(i).sharesNet(trace));
      board.insertItem(trace);
    }
    assertEquals(1, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertEquals(compensated, board.searchTreeManager.isClearanceCompensationUsed());
  }

  private static RoutingBoard loadBoard(boolean compensated) throws Exception {
    try (var input =
        Files.newInputStream(TestFixtures.resolvePath("Issue969-clearance-query.dsn"))) {
      var result =
          assertInstanceOf(
              BoardReadResult.Success.class,
              DsnReader.readBoard(input, null, null, "Issue969-clearance-query.dsn"));
      var board = (RoutingBoard) result.board();
      var matrix = board.rules.clearanceMatrix;
      matrix.appendClass("wide");
      matrix.setValue(1, 1, 2000);
      matrix.setValue(1, 2, 3000);
      matrix.setValue(2, 1, 3000);
      matrix.setValue(2, 2, 3000);
      board.searchTreeManager.clearanceValueChanged();
      board.searchTreeManager.setClearanceCompensationUsed(compensated);
      return board;
    }
  }
}
