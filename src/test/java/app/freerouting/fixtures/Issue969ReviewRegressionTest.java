package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.actions.ComponentItemTransform;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
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
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void viaShovePreservesOrthogonalRouting(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    board.insertTrace(
        new Polyline(new IntPoint(100000, -120000), new IntPoint(100000, -80000)),
        0,
        500,
        new int[] {2},
        1,
        FixedState.UNFIXED);
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 1, false, board.rules);
    assertTrue(
        ForcedViaInserter.insert(
            via, new IntPoint(100000, -100000), new int[] {1}, 1, new int[] {0, 0}, 20, 20, board));
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
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  static Stream<Arguments> clearanceCases() {
    var cases = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int[] classes : new int[][] {{1, 1}, {1, 2}, {2, 2}}) {
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
