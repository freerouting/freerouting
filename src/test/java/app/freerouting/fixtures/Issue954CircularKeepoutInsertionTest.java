package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class Issue954CircularKeepoutInsertionTest extends RoutingFixtureTest {
  static Stream<Arguments> insertionCases() {
    var cases = Stream.<Arguments>builder();
    int[][] profiles = {{750, 0}, {250, 0}, {1250, 0}, {750, -1}, {750, 1}};
    for (var angle : AngleRestriction.values()) {
      for (int diameter : new int[] {5000, 6000}) {
        for (boolean reverse : new boolean[] {false, true}) {
          for (int[] profile : profiles) {
            cases.add(Arguments.of(angle, diameter, reverse, profile[0], profile[1]));
          }
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("insertionCases")
  void insertsDetour(
      AngleRestriction restriction,
      int diameter,
      boolean reverse,
      int halfWidth,
      int verticalOffset)
      throws Exception {
    RoutingBoard board = loadBoard(diameter);
    board.rules.setTraceAngleRestriction(restriction);
    var obstacle =
        board.getItems().stream()
            .filter(i -> i instanceof ObstacleArea && i.isOnLayer(0))
            .map(i -> (ObstacleArea) i)
            .findFirst()
            .orElseThrow();
    var circle = assertInstanceOf(Circle.class, obstacle.getArea());
    int r = circle.radius;
    var center = circle.center;
    var start = new IntPoint(center.x + (reverse ? 2 : -2) * r, center.y + verticalOffset * r / 2);
    var end = new IntPoint(center.x - (reverse ? 2 : -2) * r, center.y + verticalOffset * r / 2);
    int clearanceClass = 1;
    var path = new Polyline(new Point[] {start, end});
    var tree = board.searchTreeManager.getDefaultTree();
    if (diameter > 5000) {
      assertTrue(obstacle.treeShapeCount(tree) > 1, "Exercise a partitioned convex obstacle");
    }
    var detour =
        new TraceShover(board)
            .springOverObstacles(path, halfWidth, 0, new int[] {1}, clearanceClass, null);
    assertNotNull(
        detour, "A convex keepout must support spring-over even when its index is partitioned");
    assertTrue(detour.cornerCount() > 2, "The route must actually detour around the keepout");
    for (var shape : detour.offsetShapes(halfWidth)) {
      assertTrue(
          tree.overlappingItemsWithClearance(shape, 0, new int[] {1}, clearanceClass).isEmpty(),
          "The generated detour must satisfy the same clearance query used during insertion");
    }
    insertAndClean(
        board,
        path,
        halfWidth,
        () -> assertSafeTraces(board, circle, clearanceClass, obstacle.clearanceClassIndex()));
  }

  private static void insertAndClean(
      RoutingBoard board, Polyline path, int halfWidth, Runnable checkGeometry) {
    var reached =
        board.insertForcedTracePolyline(
            path, halfWidth, 0, new int[] {1}, 1, 10, 10, 20, 0, 100, true, null);
    assertEquals(path.lastCorner(), reached, "Insertion must reach the requested destination");
    assertConnectedAndClear(board, path);
    checkGeometry.run();
    board.normalizeTraces(1);
    for (var trace : new ArrayList<>(board.getTraces())) {
      assertInstanceOf(PolylineTrace.class, trace).pullTight(true, 100, null);
    }
    assertConnectedAndClear(board, path);
    checkGeometry.run();
  }

  private static void assertConnectedAndClear(RoutingBoard board, Polyline path) {
    assertFalse(board.getTraces().isEmpty());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var startTrace =
        board.getTraces().stream()
            .filter(
                t ->
                    t.firstCorner().equals(path.firstCorner())
                        || t.lastCorner().equals(path.firstCorner()))
            .findFirst()
            .orElseThrow();
    var endTrace =
        board.getTraces().stream()
            .filter(
                t ->
                    t.firstCorner().equals(path.lastCorner())
                        || t.lastCorner().equals(path.lastCorner()))
            .findFirst()
            .orElseThrow();
    assertTrue(
        startTrace.getConnectedSet(1).contains(endTrace),
        "Insertion and optimization must preserve end-to-end connectivity");
  }

  private static void assertSafeTraces(
      RoutingBoard board, Circle circle, int clearanceClass, int obstacleClass) {
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    double clearance =
        board.rules.clearanceMatrix.getValue(clearanceClass, obstacleClass, 0, false);
    for (var trace : board.getTraces()) {
      var routed = assertInstanceOf(PolylineTrace.class, trace).polyline();
      for (int i = 0; i < routed.cornerCount() - 1; i++) {
        if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          assertTrue(routed.lines[i + 1].isOrthogonal());
        } else if (board.rules.getTraceAngleRestriction() == AngleRestriction.FORTYFIVE_DEGREE) {
          assertTrue(routed.lines[i + 1].isMultipleOf45Degree());
        }
        assertTrue(
            segmentDistance(
                        circle.center.toFloat(), routed.cornerApprox(i), routed.cornerApprox(i + 1))
                    - circle.radius
                    - trace.getHalfWidth()
                >= clearance - 0.01,
            "Independently measured physical clearance must satisfy the rule");
      }
    }
  }

  @Test
  void invalidCircularKeepoutPlacementStillFailsDrc() throws Exception {
    String dsn =
        Files.readString(TestFixtures.resolveFile("Issue954-circle.dsn").toPath())
            .replace("11176.751555 -12840.929562", "11119.349040 -12702.347633");
    try (var input = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8))) {
      var result =
          assertInstanceOf(
              BoardReadResult.Success.class,
              DsnReader.readBoard(input, null, null, "Issue954-invalid-circle.dsn"));
      assertEquals(
          2,
          new DesignRulesChecker(result.board(), null).getAllClearanceViolations().size(),
          "True 0.145 mm circle clearance must fail the 0.200 mm rule on both copper layers");
    }
  }

  @Test
  void endpointInsideKeepoutDoesNotInsertCopper() throws Exception {
    RoutingBoard board = loadBoard(5000);
    board.rules.setTraceAngleRestriction(AngleRestriction.NONE);
    var start = new IntPoint(50000, -100000);
    var end = new IntPoint(100000, -100000);
    var reached =
        board.insertForcedTracePolyline(
            new Polyline(start, end), 750, 0, new int[] {1}, 1, 10, 10, 20, 0, 100, true, null);
    assertEquals(start, reached);
    assertTrue(board.getTraces().isEmpty());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @Test
  void nonConvexKeepoutIsNotReplacedWithOneOfItsSections() throws Exception {
    RoutingBoard board = loadBoard(5000, false);
    var concave =
        new PolygonShape(
            new Point[] {
              new IntPoint(80000, -120000), new IntPoint(120000, -120000),
              new IntPoint(120000, -110000), new IntPoint(100000, -110000),
              new IntPoint(100000, -80000), new IntPoint(80000, -80000)
            });
    assertRejectedArea(board, concave);
  }

  @Test
  void holedKeepoutIsNotReplacedWithItsOuterBoundary() throws Exception {
    var area =
        new PolylineArea(
            new IntBox(70000, -130000, 130000, -70000),
            new PolylineShape[] {new IntBox(90000, -110000, 110000, -90000)});
    assertRejectedArea(loadBoard(5000, false), area);
  }

  private static void assertRejectedArea(RoutingBoard board, Area area) {
    assertTrue(area.splitToConvex().length > 1);
    var obstacle = board.insertObstacle(area, 0, 1, FixedState.USER_FIXED);
    assertEquals(1, board.getItems().stream().filter(ObstacleArea.class::isInstance).count());
    assertTrue(obstacle.treeShapeCount(board.searchTreeManager.getDefaultTree()) > 1);
    var path = new Polyline(new IntPoint(50000, -100000), new IntPoint(150000, -100000));
    assertNull(new TraceShover(board).springOverObstacles(path, 750, 0, new int[] {1}, 1, null));
    assertSame(obstacle, board.getShoveFailingObstacle());
    assertTrue(board.getTraces().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"5000,17,1,7", "5000,2001,751,22", "6000,3017,250,73"})
  void obliqueCircularDetourSurvivesCleanup(int diameter, int clearance, int width, int angle)
      throws Exception {
    var board = loadBoard(diameter);
    setClearance(board, clearance);
    var obstacle =
        (ObstacleArea)
            board.getItems().stream()
                .filter(i -> i instanceof ObstacleArea && i.isOnLayer(0))
                .findFirst()
                .orElseThrow();
    var circle = assertInstanceOf(Circle.class, obstacle.getArea());
    insertAndClean(
        board,
        obliquePath(circle.radius, angle),
        width,
        () -> assertSafeTraces(board, circle, 1, obstacle.clearanceClassIndex()));
  }

  @ParameterizedTest
  @CsvSource({"24000,17,1,7", "36000,2001,751,22", "36000,3017,250,73"})
  void asymmetricPolygonDetourSurvivesCleanup(int radius, int clearance, int width, int angle)
      throws Exception {
    var board = loadBoard(5000, false);
    setClearance(board, clearance);
    Point[] corners = new Point[5];
    for (int k = 0; k < corners.length; k++) {
      double direction = 2 * Math.PI * k / corners.length + 0.13;
      corners[k] =
          new IntPoint(
              100000 + (int) Math.round(radius * Math.cos(direction)),
              -100000 + (int) Math.round(0.6 * radius * Math.sin(direction)));
    }
    var polygon = new PolygonShape(corners);
    assertEquals(1, polygon.splitToConvex().length);
    var obstacle = board.insertObstacle(polygon, 0, 1, FixedState.USER_FIXED);
    if (radius > 25000) {
      assertTrue(obstacle.treeShapeCount(board.searchTreeManager.getDefaultTree()) > 1);
    }
    insertAndClean(board, obliquePath(radius, angle), width, () -> {});
  }

  @Test
  void partitionedRectangleSupportsDetour() throws Exception {
    var board = loadBoard(5000, false);
    var obstacle =
        board.insertObstacle(
            new IntBox(70000, -115000, 130000, -85000), 0, 1, FixedState.USER_FIXED);
    assertTrue(obstacle.treeShapeCount(board.searchTreeManager.getDefaultTree()) > 1);
    insertAndClean(board, obliquePath(30000, 22), 751, () -> {});
  }

  @Test
  void successiveCircularObstaclesRemainClearAfterCleanup() throws Exception {
    var board = loadBoard(5000, false);
    var first = new Circle(new IntPoint(80000, -100000), 12000);
    var second = new Circle(new IntPoint(120000, -105000), 12000);
    board.insertObstacle(first, 0, 1, FixedState.USER_FIXED);
    board.insertObstacle(second, 0, 1, FixedState.USER_FIXED);
    var path = new Polyline(new IntPoint(40000, -100000), new IntPoint(160000, -100000));
    insertAndClean(
        board,
        path,
        751,
        () -> {
          assertSafeTraces(board, first, 1, 1);
          assertSafeTraces(board, second, 1, 1);
        });
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,true", "false,false"})
  void circularCopperAreaRespectsNetAndObstacleFlag(boolean sameNet, boolean blocksForeignNets)
      throws Exception {
    var board = loadBoard(5000, false);
    int net = sameNet ? 1 : board.rules.nets.add("FOREIGN", 1, false).netNumber;
    var circle = new Circle(new IntPoint(100000, -100000), 30000);
    var area =
        board.insertConductionArea(
            circle, 0, new int[] {net}, 1, blocksForeignNets, FixedState.USER_FIXED);
    assertTrue(area.treeShapeCount(board.searchTreeManager.getDefaultTree()) > 1);
    var path = obliquePath(circle.radius, 22);
    var detour = new TraceShover(board).springOverObstacles(path, 751, 0, new int[] {1}, 1, null);
    assertNotNull(detour);
    boolean needsDetour = !sameNet && blocksForeignNets;
    if (needsDetour) {
      assertTrue(detour.cornerCount() > 2);
    } else {
      assertSame(path, detour, "Traversable copper must not force a detour");
    }
    insertAndClean(
        board,
        path,
        751,
        () -> {
          if (needsDetour) {
            assertSafeTraces(board, circle, 1, area.clearanceClassIndex());
          }
        });
  }

  private static Polyline obliquePath(int radius, int angle) {
    double direction = Math.toRadians(angle);
    int dx = (int) Math.round(2.1 * radius * Math.cos(direction));
    int dy = (int) Math.round(2.1 * radius * Math.sin(direction));
    return new Polyline(
        new IntPoint(100000 - dx, -100000 - dy), new IntPoint(100000 + dx, -100000 + dy));
  }

  private static void setClearance(RoutingBoard board, int clearance) {
    board.rules.clearanceMatrix.setValue(1, 1, clearance);
    board.searchTreeManager.clearanceValueChanged();
  }

  private static RoutingBoard loadBoard(int diameter) throws Exception {
    return loadBoard(diameter, true);
  }

  private static RoutingBoard loadBoard(int diameter, boolean keepCircularObstacles)
      throws Exception {
    String dsn =
        Files.readString(TestFixtures.resolveFile("Issue954-circle.dsn").toPath())
            .replace("5000)", diameter + ")");
    if (!keepCircularObstacles) {
      dsn =
          dsn.replace("(keepout \"\" (circle F.Cu " + diameter + "))", "")
              .replace("(keepout \"\" (circle B.Cu " + diameter + "))", "");
    }
    RoutingBoard board;
    try (var input = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8))) {
      var result =
          assertInstanceOf(
              BoardReadResult.Success.class,
              DsnReader.readBoard(input, null, null, "Issue954-circle.dsn"));
      board = (RoutingBoard) result.board();
    }
    board.rules.setTraceAngleRestriction(AngleRestriction.NONE);
    if (!keepCircularObstacles) {
      assertEquals(0, board.getItems().stream().filter(ObstacleArea.class::isInstance).count());
    }
    for (var via : new ArrayList<>(board.getVias())) {
      via.setFixedState(FixedState.UNFIXED);
      board.removeItem(via);
    }
    assertTrue(board.getVias().isEmpty());
    assertTrue(board.getTraces().isEmpty());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    return board;
  }

  private static double segmentDistance(FloatPoint p, FloatPoint a, FloatPoint b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double lengthSquared = dx * dx + dy * dy;
    double t =
        lengthSquared == 0
            ? 0
            : Math.clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared, 0, 1);
    return Math.hypot(p.x - a.x - t * dx, p.y - a.y - t * dy);
  }
}
