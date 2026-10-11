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
import app.freerouting.board.model.structure.ShapeEntrySide;
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
    // Measure physical circular pads and segment widths independently of the DRC search tree.
    for (var via : board.getVias()) {
      var center = via.getCenter().toFloat();
      for (var trace : board.getTraces()) {
        if (trace.sharesNet(via)) {
          continue;
        }
        var pad = via.getPadstack().getShape(trace.getLayer());
        if (pad == null) {
          continue;
        }
        int radius = assertInstanceOf(Circle.class, pad).radius;
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
                  - radius
                  - trace.getHalfWidth();
          int required =
              board.rules.clearanceMatrix.getValue(
                  via.clearanceClassIndex(), trace.clearanceClassIndex(), trace.getLayer(), false);
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

  static Stream<Arguments> layeredShoveCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int turn = 0; turn < 4; turn++) {
        for (int incomingClass : new int[] {1, 2}) {
          for (int strictLayer : new int[] {-1, 0, 1}) {
            cases.add(Arguments.of(compensated, turn, incomingClass, strictLayer));
          }
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("layeredShoveCases")
  void recursiveViaShoveUsesEachPairwiseClass(
      boolean compensated, int turn, int incomingClass, int strictLayer) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    // Both vias span both layers; either layer can impose the limiting separation.
    board.rules.clearanceMatrix.setValue(2, 2, strictLayer < 0 ? 10000 : 3000);
    if (strictLayer >= 0) {
      board.rules.clearanceMatrix.setValue(2, 2, strictLayer, 10000);
      board.rules.clearanceMatrix.setValue(1, 2, 1 - strictLayer, 2000);
      board.rules.clearanceMatrix.setValue(2, 1, 1 - strictLayer, 2000);
    }
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
        for (int layer = 0; layer < 2; layer++) {
          assertTrue(
              gap
                  >= board.rules.clearanceMatrix.getValue(
                      a.clearanceClassIndex(), b.clearanceClassIndex(), layer, false));
        }
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

  static Stream<Arguments> recursiveStopCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int turn = 0; turn < 4; turn++) {
        for (int displacedClass : new int[] {2, 3}) {
          cases.add(Arguments.of(compensated, turn, displacedClass));
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("recursiveStopCases")
  void recursiveTraceStopRetainsLegalCopper(boolean compensated, int turn, int displacedClass)
      throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var matrix = board.rules.clearanceMatrix;
    matrix.appendClass("third");
    matrix.setValue(2, 2, 10000);
    matrix.setValue(1, 3, 4000);
    matrix.setValue(3, 1, 4000);
    matrix.setValue(2, 3, 10000);
    matrix.setValue(3, 2, 10000);
    matrix.setValue(3, 3, 10000);
    board.rules.nets.add("third", 1, false);
    board.searchTreeManager.clearanceValueChanged();
    var originalStart = rotatedPoint(100000, -140000, turn);
    var originalEnd = rotatedPoint(100000, -60000, turn);
    board.insertTraceWithoutCleaning(
        new Polyline(originalStart, originalEnd),
        0,
        500,
        new int[] {2},
        displacedClass,
        FixedState.UNFIXED);
    var blockerCenter = rotatedPoint(125000, -88999, turn);
    final var blocker =
        board.insertVia(
            board.library.padstacks.get("Via100"),
            blockerCenter,
            new int[] {3},
            displacedClass,
            FixedState.UNFIXED,
            false);
    var path =
        new Polyline(rotatedPoint(70000, -100000, turn), rotatedPoint(130000, -100000, turn));
    var segment = new LineSegment(path, 1);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertEquals(0, TraceShover.check(board, segment, true, 0, new int[] {1}, 500, 2, 0, 0));
    // The blocker limits the substituted trace, then the incoming trace must stop another
    // pairwise clearance plus both trace half-widths behind it. Both clearances are 10000.
    double distance = TraceShover.check(board, segment, true, 0, new int[] {1}, 500, 2, 20, 0);
    assertEquals(compensated ? 33000 : 32968, distance, 0.01);
    // Removing only the blocker proves that this is a recursive, finite stopping result.
    board.removeItem(blocker);
    assertEquals(
        Integer.MAX_VALUE,
        TraceShover.check(board, segment, true, 0, new int[] {1}, 500, 2, 20, 0));
    board.insertItem(blocker);
    var stop = rotatedPoint(70000 + (int) distance - 1, -100000, turn);
    var retainedPath = new Polyline(path.firstCorner(), stop);
    int width = 500 + board.searchTreeManager.getDefaultTree().clearanceCompensationValue(2, 0);
    // Match the orthogonal insertion contract used by ShapeAndEntrySide.
    var shape = retainedPath.offsetShapes(width)[0].boundingBox();
    var side = new ShapeEntrySide(new LineSegment(retainedPath, 1), shape, true);
    var shover = new TraceShover(board);
    assertTrue(shover.check(shape, side, null, 0, new int[] {1}, 2, 20, 0, 0, null));
    board.startMarkingChangedArea();
    assertTrue(shover.insert(shape, side, 0, new int[] {1}, 2, null, 20, 0, 0));
    board.insertTraceWithoutCleaning(retainedPath, 0, 500, new int[] {1}, 2, FixedState.UNFIXED);
    assertConnectedEndpoints(board, path.firstCorner(), stop, 1);
    assertConnectedEndpoints(board, originalStart, originalEnd, 2);
    assertEquals(1, board.getVias().size());
    assertTrue(blocker.isOnTheBoard());
    assertEquals(blockerCenter, blocker.getCenter());
    assertOrthogonalCopper(board);
    assertViaTraceGaps(board);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  static Stream<Arguments> attachedViaCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int turn = 0; turn < 4; turn++) {
        for (int viaClass : new int[] {1, 2}) {
          for (int traceClass : new int[] {1, 2}) {
            cases.add(Arguments.of(compensated, turn, viaClass, traceClass));
          }
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("attachedViaCases")
  void movingAttachedViaRetainsBothNets(boolean compensated, int turn, int viaClass, int traceClass)
      throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    // A 9000-unit radius covers the attached trace's envelope even when its class needs
    // 7000 units more clearance than the via. The undersized case is tested separately.
    var pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 9000), new Circle(Point.ZERO, 9000)});
    var center = rotatedPoint(60000, -100000, turn);
    var anchor = rotatedPoint(30000, -100000, turn);
    final var via =
        board.insertVia(pad, center, new int[] {1}, viaClass, FixedState.UNFIXED, false);
    board.insertTraceWithoutCleaning(
        new Polyline(anchor, center), 0, 500, new int[] {1}, traceClass, FixedState.UNFIXED);
    var otherStart = rotatedPoint(100000, -140000, turn);
    var otherEnd = rotatedPoint(100000, -60000, turn);
    board.insertTraceWithoutCleaning(
        new Polyline(otherStart, otherEnd), 0, 500, new int[] {2}, 2, FixedState.UNFIXED);
    assertConnectedEndpoints(board, anchor, center, 1);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var destination = new IntPoint(100000, -100000);
    var delta = destination.differenceBy(center);
    assertTrue(DrillItemMover.check(via, delta, 20, 20, null, board, null));
    assertTrue(DrillItemMover.insert(via, delta, 20, 20, null, board));
    assertTrue(via.isOnTheBoard());
    assertEquals(1, board.getVias().size());
    assertEquals(destination, via.getCenter());
    assertConnectedEndpoints(board, anchor, destination, 1);
    assertTrue(
        via.getConnectedSet(1).stream()
            .anyMatch(
                item ->
                    item instanceof PolylineTrace trace
                        && (trace.firstCorner().equals(anchor)
                            || trace.lastCorner().equals(anchor))));
    assertConnectedEndpoints(board, otherStart, otherEnd, 2);
    assertOrthogonalCopper(board);
    assertViaTraceGaps(board);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @MethodSource("attachedViaCases")
  void sufficientAttachedViaEnvelopeAllowsShoving(
      boolean compensated, int turn, int viaClass, int traceClass) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    var pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 9000), new Circle(Point.ZERO, 9000)});
    var center = rotatedPoint(108000, -100000, turn);
    var anchor = rotatedPoint(150000, -100000, turn);
    final var attachedVia =
        board.insertVia(pad, center, new int[] {2}, viaClass, FixedState.UNFIXED, false);
    board.insertTraceWithoutCleaning(
        new Polyline(center, anchor), 0, 500, new int[] {2}, traceClass, FixedState.UNFIXED);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var incoming =
        new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    var destination = new IntPoint(100000, -100000);
    assertTrue(
        ForcedViaInserter.check(
            incoming, destination, new int[] {1}, 20, 20, board, new int[] {0, 0}, 2));
    assertTrue(
        ForcedViaInserter.insert(
            incoming, destination, new int[] {1}, 2, new int[] {0, 0}, 20, 20, board));
    assertEquals(2, board.getVias().size());
    assertTrue(attachedVia.isOnTheBoard());
    assertFalse(center.equals(attachedVia.getCenter()), "Attached via must actually be shoved");
    var insertedVia =
        board.getVias().stream().filter(v -> v.containsNet(1)).findFirst().orElseThrow();
    assertEquals(destination, insertedVia.getCenter());
    double gap = destination.toFloat().distance(attachedVia.getCenter().toFloat()) - 9500;
    assertTrue(gap >= board.rules.clearanceMatrix.getValue(2, viaClass, 0, false));
    assertConnectedEndpoints(board, attachedVia.getCenter(), anchor, 2);
    assertTrue(
        attachedVia.getConnectedSet(2).stream()
            .anyMatch(
                item ->
                    item instanceof PolylineTrace trace
                        && (trace.firstCorner().equals(anchor)
                            || trace.lastCorner().equals(anchor))));
    assertOrthogonalCopper(board);
    assertViaTraceGaps(board);
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
