package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.ShapeEntrySide;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ViaInfo;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Issue969ContainedTraceTest extends RoutingFixtureTest {
  private static final IntPoint CENTER = new IntPoint(100000, -100000);

  static Stream<Arguments> containmentCases() {
    var cases = Stream.<Arguments>builder();
    for (var angle : AngleRestriction.values()) {
      for (boolean compensated : new boolean[] {false, true}) {
        for (int endDistance : new int[] {30000, 50000}) {
          for (boolean insertDirectly : new boolean[] {false, true}) {
            cases.add(Arguments.of(angle, compensated, endDistance, insertDirectly));
          }
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("containmentCases")
  void containedForeignEndpointBlocksViaWithoutLosingCopper(
      AngleRestriction angle, boolean compensated, int endDistance, boolean insertDirectly)
      throws Exception {
    var board = loadBoard(angle, compensated);
    var start = new IntPoint(100000, -130000);
    var end = new IntPoint(100000, -100000 + endDistance);
    var original = insertTrace(board, start, end);
    final var originalPolyline = original.polyline();
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    // Test check and insertion independently: a failed check must not hide an unsafe insert.
    boolean accepted =
        insertDirectly
            ? ForcedViaInserter.insert(
                via, CENTER, new int[] {1}, 2, new int[] {0, 0}, 20, 20, board)
            : ForcedViaInserter.check(
                via, CENTER, new int[] {1}, 20, 20, board, new int[] {0, 0}, 2);
    // Compensation contributes 19000 per object. The resulting 39001-unit envelope
    // encloses both endpoints at distance 30000, or just the first when the other is 50000 away.
    assertEquals(!compensated, accepted);
    if (compensated || !insertDirectly) {
      assertTrue(board.getVias().isEmpty());
      assertEquals(1, board.getTraces().size());
      assertTrue(original.isOnTheBoard());
      assertSame(originalPolyline, original.polyline());
    } else {
      assertEquals(1, board.getVias().size());
      assertPhysicalGap(board, CENTER, 1000);
    }
    assertConnected(board, start, end);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  static Stream<Arguments> angleModes() {
    return Stream.of(AngleRestriction.values()).map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("angleModes")
  void crossingTraceCanStillBeShovedWithExcessCompensation(AngleRestriction angle)
      throws Exception {
    var board = loadBoard(angle, true);
    var start = new IntPoint(100000, -150000);
    var end = new IntPoint(100000, -50000);
    insertTrace(board, start, end);
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    assertTrue(
        ForcedViaInserter.check(via, CENTER, new int[] {1}, 20, 20, board, new int[] {0, 0}, 2));
    assertTrue(
        ForcedViaInserter.insert(via, CENTER, new int[] {1}, 2, new int[] {0, 0}, 20, 20, board));
    assertEquals(1, board.getVias().size());
    assertConnected(board, start, end);
    assertPhysicalGap(board, CENTER, 1000);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @MethodSource("angleModes")
  void enclosedTraceCannotHideBehindAnotherTracesReplacementPieces(AngleRestriction angle)
      throws Exception {
    var board = loadBoard(angle, true);
    var shortTrace =
        insertTrace(board, new IntPoint(100000, -130000), new IntPoint(100000, -70000));
    var longTrace = insertTrace(board, new IntPoint(104000, -150000), new IntPoint(104000, -50000));
    final var shortPolyline = shortTrace.polyline();
    final var longPolyline = longTrace.polyline();
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    assertFalse(
        ForcedViaInserter.check(via, CENTER, new int[] {1}, 20, 20, board, new int[] {0, 0}, 2));
    assertFalse(
        ForcedViaInserter.insert(via, CENTER, new int[] {1}, 2, new int[] {0, 0}, 20, 20, board));
    assertEquals(2, board.getTraces().size());
    assertTrue(board.getVias().isEmpty());
    assertTrue(shortTrace.isOnTheBoard());
    assertTrue(longTrace.isOnTheBoard());
    assertSame(shortPolyline, shortTrace.polyline());
    assertSame(longPolyline, longTrace.polyline());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @MethodSource("angleModes")
  void traceShovingAlsoRejectsContainedForeignEndpoints(AngleRestriction angle) throws Exception {
    var board = loadBoard(angle, true);
    var original = insertTrace(board, new IntPoint(100000, -130000), new IntPoint(100000, -70000));
    final var originalPolyline = original.polyline();
    var incoming = new Polyline(new IntPoint(99000, -100000), new IntPoint(101000, -100000));
    int halfWidth = 500 + board.searchTreeManager.getDefaultTree().clearanceCompensationValue(2, 0);
    TileShape shape = incoming.offsetShapes(halfWidth)[0];
    if (angle == AngleRestriction.NINETY_DEGREE) {
      shape = shape.boundingBox();
    }
    var side = new ShapeEntrySide(incoming, 1, shape);
    var shover = new TraceShover(board);
    assertFalse(shover.check(shape, side, null, 0, new int[] {1}, 2, 20, 20, 0, null));
    board.startMarkingChangedArea();
    assertFalse(shover.insert(shape, side, 0, new int[] {1}, 2, null, 20, 20, 0));
    assertEquals(1, board.getTraces().size());
    assertTrue(original.isOnTheBoard());
    assertSame(originalPolyline, original.polyline());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  static Stream<Arguments> junctionCases() {
    var cases = Stream.<Arguments>builder();
    for (var angle : AngleRestriction.values()) {
      for (int endDistance : new int[] {30000, 50000}) {
        for (boolean insertDirectly : new boolean[] {false, true}) {
          cases.add(Arguments.of(angle, endDistance, insertDirectly));
        }
      }
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("junctionCases")
  void containedBranchingJunctionIsRejectedWithoutChangingConnectivity(
      AngleRestriction angle, int endDistance, boolean insertDirectly) throws Exception {
    var board = loadBoard(angle, true);
    var start = new IntPoint(100000, -130000);
    var end = new IntPoint(100000, -100000 + endDistance);
    var originals = new ArrayList<PolylineTrace>();
    originals.add(insertTrace(board, start, end));
    for (int x : new int[] {95000, 105000}) {
      originals.add(
          board.insertTraceWithoutCleaning(
              new Polyline(
                  new Point[] {start, new IntPoint(x, start.y), new IntPoint(x, end.y), end}),
              0,
              500,
              new int[] {2},
              3,
              FixedState.UNFIXED));
    }
    final var originalPolylines = originals.stream().map(PolylineTrace::polyline).toList();
    assertEquals(2, originals.getFirst().getStartContacts().size());
    assertEquals(2, originals.getFirst().getEndContacts().size());
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    boolean accepted =
        insertDirectly
            ? ForcedViaInserter.insert(
                via, CENTER, new int[] {1}, 2, new int[] {0, 0}, 20, 20, board)
            : ForcedViaInserter.check(
                via, CENTER, new int[] {1}, 20, 20, board, new int[] {0, 0}, 2);
    assertFalse(accepted);
    assertTrue(board.getVias().isEmpty());
    assertEquals(originals.size(), board.getTraces().size());
    for (int i = 0; i < originals.size(); i++) {
      assertTrue(originals.get(i).isOnTheBoard());
      assertSame(originalPolylines.get(i), originals.get(i).polyline());
      assertEquals(2, originals.get(i).getStartContacts().size());
      assertEquals(2, originals.get(i).getEndContacts().size());
    }
    assertTrue(originals.getFirst().getConnectedSet(2).containsAll(originals));
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @MethodSource("angleModes")
  void containedSameNetCopperRemainsAttachable(AngleRestriction angle) throws Exception {
    var board = loadBoard(angle, true);
    var start = new IntPoint(100000, -130000);
    var end = new IntPoint(100000, -70000);
    insertTrace(board, start, end);
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 2, false, board.rules);
    assertTrue(
        ForcedViaInserter.check(via, CENTER, new int[] {2}, 20, 20, board, new int[] {0, 0}, 2));
    assertTrue(
        ForcedViaInserter.insert(via, CENTER, new int[] {2}, 2, new int[] {0, 0}, 20, 20, board));
    assertEquals(1, board.getVias().size());
    // Attaching a via legitimately splits the trace; retain its copper and connectivity.
    assertEquals(2, board.getTraces().size());
    assertConnected(board, start, end);
    assertTrue(board.getVias().iterator().next().getConnectedSet(2).containsAll(board.getTraces()));
    double retainedLength = 0;
    for (var trace : board.getTraces()) {
      assertEquals(start.x, trace.firstCorner().toFloat().x);
      assertEquals(start.x, trace.lastCorner().toFloat().x);
      retainedLength += trace.getLength();
    }
    assertEquals(60000, retainedLength, 0.01);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  private static PolylineTrace insertTrace(RoutingBoard board, IntPoint start, IntPoint end) {
    return board.insertTraceWithoutCleaning(
        new Polyline(start, end), 0, 500, new int[] {2}, 3, FixedState.UNFIXED);
  }

  private static void assertConnected(RoutingBoard board, IntPoint start, IntPoint end) {
    var first =
        board.getTraces().stream()
            .filter(t -> t.firstCorner().equals(start) || t.lastCorner().equals(start))
            .findFirst()
            .orElseThrow();
    var last =
        board.getTraces().stream()
            .filter(t -> t.firstCorner().equals(end) || t.lastCorner().equals(end))
            .findFirst()
            .orElseThrow();
    assertTrue(first.getConnectedSet(2).contains(last));
  }

  private static void assertPhysicalGap(RoutingBoard board, IntPoint viaCenter, int clearance) {
    var center = viaCenter.toFloat();
    for (var trace : board.getTraces()) {
      var polyline = ((PolylineTrace) trace).polyline();
      for (int i = 0; i < polyline.cornerCount() - 1; i++) {
        var a = polyline.cornerApprox(i);
        var b = polyline.cornerApprox(i + 1);
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double t =
            Math.max(
                0,
                Math.min(1, ((center.x - a.x) * dx + (center.y - a.y) * dy) / (dx * dx + dy * dy)));
        double gap =
            Math.hypot(center.x - a.x - t * dx, center.y - a.y - t * dy)
                - 500
                - trace.getHalfWidth();
        assertTrue(gap >= clearance, () -> "Physical gap " + gap + " is below " + clearance);
      }
    }
  }

  private static RoutingBoard loadBoard(AngleRestriction angle, boolean compensated)
      throws Exception {
    try (var input =
        Files.newInputStream(TestFixtures.resolvePath("Issue969-clearance-query.dsn"))) {
      var result =
          assertInstanceOf(
              BoardReadResult.Success.class,
              DsnReader.readBoard(input, null, null, "Issue969-clearance-query.dsn"));
      var board = (RoutingBoard) result.board();
      board.rules.setTraceAngleRestriction(angle);
      board.rules.clearanceMatrix.appendClass("two");
      board.rules.clearanceMatrix.appendClass("three");
      int[][] values = {{2000, 20000, 20000}, {20000, 10000, 1000}, {20000, 1000, 10000}};
      for (int i = 1; i <= 3; i++) {
        for (int j = 1; j <= 3; j++) {
          board.rules.clearanceMatrix.setValue(i, j, values[i - 1][j - 1]);
        }
      }
      board.searchTreeManager.clearanceValueChanged();
      board.searchTreeManager.setClearanceCompensationUsed(compensated);
      return board;
    }
  }
}
