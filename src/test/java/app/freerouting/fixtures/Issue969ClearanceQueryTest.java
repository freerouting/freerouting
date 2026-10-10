package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.board.actions.ForcedPadRouter;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Package;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ViaInfo;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class Issue969ClearanceQueryTest extends RoutingFixtureTest {
  static Stream<Arguments> queryCases() {
    var result = Stream.<Arguments>builder();
    for (boolean compensated : new boolean[] {false, true}) {
      for (int layer : new int[] {0, 1}) {
        for (int obstacleClass : new int[] {1, 2}) {
          for (int delta : new int[] {-500, 500, 2500}) {
            result.add(Arguments.of(compensated, layer, obstacleClass, delta));
          }
        }
      }
    }
    return result.build();
  }

  @ParameterizedTest
  @MethodSource("queryCases")
  void allWrappersAgree(boolean compensated, int layer, int obstacleClass, int delta)
      throws Exception {
    var board = loadBoard(compensated);
    var obstacle =
        board.insertObstacle(
            new IntBox(90000, -110000, 110000, -90000),
            layer,
            obstacleClass,
            FixedState.USER_FIXED);
    var tree = board.searchTreeManager.getDefaultTree();
    int clearance = board.rules.clearanceMatrix.getValue(1, obstacleClass, layer, false);
    var raw = new IntBox(110000 + clearance + delta, -100500, 111000 + clearance + delta, -99500);
    var query = (ConvexShape) raw.enlarge(tree.clearanceCompensationValue(1, layer));
    assertWrappers(board, query, layer, new int[] {1}, obstacle, delta < 0);
    assertWrappers(board, query, 1 - layer, new int[] {1}, obstacle, false);
  }

  private static void assertWrappers(
      RoutingBoard board,
      ConvexShape query,
      int layer,
      int[] ignoredNets,
      Item obstacle,
      boolean expected) {
    var tree = board.searchTreeManager.getDefaultTree();
    assertEquals(
        expected,
        tree.overlappingTreeEntriesWithClearance(query, layer, ignoredNets, 1).stream()
            .anyMatch(e -> e.object == obstacle),
        "entry result");
    Collection<TreeEntry> entries = new ArrayList<>();
    tree.overlappingTreeEntriesWithClearance(query, layer, ignoredNets, 1, entries);
    assertEquals(expected, entries.stream().anyMatch(e -> e.object == obstacle), "entry collector");
    assertEquals(
        expected,
        tree.overlappingItemsWithClearance(query, layer, ignoredNets, 1).contains(obstacle),
        "item result");
    Collection<Item> items = new ArrayList<>();
    tree.overlappingObjectsWithClearance(query, layer, ignoredNets, 1, items);
    assertEquals(expected, items.contains(obstacle), "item collector");
    Set<SearchTreeObject> objects = new TreeSet<>();
    tree.overlappingObjectsWithClearance(query, layer, ignoredNets, 1, objects);
    assertEquals(expected, objects.contains(obstacle), "object collector");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesSameNetFiltering(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    var trace =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {new IntPoint(90000, -100000), new IntPoint(110000, -100000)}),
            0,
            500,
            new int[] {1},
            1,
            FixedState.USER_FIXED);
    var query = new IntBox(95000, -100500, 105000, -99500);
    assertWrappers(board, query, 0, new int[] {1}, trace, false);
    assertWrappers(board, query, 0, new int[] {2}, trace, true);
    assertWrappers(board, query, 0, new int[] {2, 1}, trace, false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rawShapeCheckRejectsTrueViolation(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    assertFalse(board.checkShape(new IntBox(111500, -100500, 112500, -99500), 0, new int[] {1}, 1));
    assertTrue(board.checkShape(new IntBox(112500, -100500, 113500, -99500), 0, new int[] {1}, 1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void viaCreationRejectsTrueViolation(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 1, false, board.rules);
    assertFalse(
        ForcedViaInserter.check(
            via, new IntPoint(112000, -100000), new int[] {1}, 0, 0, board, new int[] {0, 0}, 1));
    assertTrue(
        ForcedViaInserter.check(
            via, new IntPoint(113000, -100000), new int[] {1}, 0, 0, board, new int[] {0, 0}, 1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void insertsLegalTraceAndPreservesItThroughCleanup(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var path =
        new Polyline(new Point[] {new IntPoint(113000, -105000), new IntPoint(113000, -95000)});
    var reached =
        board.insertForcedTracePolyline(
            path, 500, 0, new int[] {1}, 1, 0, 0, 0, 0, 100, true, null);
    assertEquals(path.lastCorner(), reached);
    assertFalse(board.getTraces().isEmpty());
    board.normalizeTraces(1);
    for (var trace : new ArrayList<>(board.getTraces())) {
      assertInstanceOf(PolylineTrace.class, trace).pullTight(true, 100, null);
      assertTrue(board.searchTreeManager.validateEntries(trace));
    }
    // Check physical geometry/DRC in the uncompensated default tree as well.
    board.searchTreeManager.setClearanceCompensationUsed(false);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
    assertTrue(
        board.getTraces().stream()
            .anyMatch(
                t ->
                    t.firstCorner().equals(path.firstCorner())
                        || t.lastCorner().equals(path.firstCorner())));
    assertTrue(
        board.getTraces().stream()
            .anyMatch(
                t ->
                    t.firstCorner().equals(path.lastCorner())
                        || t.lastCorner().equals(path.lastCorner())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidTraceInsertsNoCopper(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var path =
        new Polyline(new Point[] {new IntPoint(112000, -105000), new IntPoint(112000, -95000)});
    assertFalse(board.checkForcedTracePolyline(path, 500, 0, new int[] {1}, 1, 0, 0, 0));
    assertEquals(
        path.firstCorner(),
        board.insertForcedTracePolyline(
            path, 500, 0, new int[] {1}, 1, 0, 0, 0, 0, 100, true, null));
    assertTrue(board.getTraces().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void viaInsertionAndTraceLaunchKeepFullClearance(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var via = new ViaInfo("test", board.library.padstacks.get("Via100"), 1, false, board.rules);
    assertFalse(
        ForcedViaInserter.insert(
            via, new IntPoint(112000, -100000), new int[] {1}, 1, new int[] {0, 0}, 0, 0, board));
    assertTrue(board.getVias().isEmpty());
    // The via fits here, but the wider trace launching from it does not.
    assertFalse(
        ForcedViaInserter.check(
            via,
            new IntPoint(113000, -100000),
            new int[] {1},
            0,
            0,
            board,
            new int[] {1500, 0},
            1));
    assertFalse(
        ForcedViaInserter.insert(
            via,
            new IntPoint(113000, -100000),
            new int[] {1},
            1,
            new int[] {1500, 0},
            0,
            0,
            board));
    assertTrue(board.getVias().isEmpty());
    assertTrue(
        ForcedViaInserter.insert(
            via,
            new IntPoint(113000, -100000),
            new int[] {1},
            1,
            new int[] {500, 500},
            0,
            0,
            board));
    assertEquals(1, board.getVias().size());
    board.searchTreeManager.setClearanceCompensationUsed(false);
    assertEquals(0, new DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mazeViaLayerCheckUsesPreparedGeometry(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var room = new IntBox(110001, -150000, 150000, -50000);
    assertEquals(
        ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE,
        ForcedViaInserter.checkLayer(
            500,
            1,
            false,
            room,
            new IntPoint(112000, -100000),
            0,
            new int[] {1},
            0,
            0,
            board,
            0,
            1));
    assertEquals(
        ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE,
        ForcedViaInserter.checkLayer(
            500,
            1,
            false,
            room,
            new IntPoint(113000, -100000),
            0,
            new int[] {1},
            0,
            0,
            board,
            1500,
            1));
    assertEquals(
        ForcedPadRouter.CheckDrillResult.DRILLABLE,
        ForcedViaInserter.checkLayer(
            500,
            1,
            false,
            room,
            new IntPoint(113000, -100000),
            0,
            new int[] {1},
            0,
            0,
            board,
            500,
            1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void movingIndexedItemsDoesNotCompensateThemTwice(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    var via =
        board.insertVia(
            board.library.padstacks.get("Via100"),
            new IntPoint(120000, -100000),
            new int[] {1},
            1,
            FixedState.UNFIXED,
            false);
    assertFalse(board.checkMoveItem(via, new IntVector(-8000, 0), null));
    assertTrue(board.checkMoveItem(via, new IntVector(-7000, 0), null));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void traceEndPinSearchKeepsQueryCompensation(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    var padstack = board.library.padstacks.get("Via100");
    var pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", padstack.id, new IntVector(0, 0), 0)});
    var component = board.components.add(new IntPoint(110000, -100000), 0, true, pkg);
    var pin = board.insertPin(component.id, 0, new int[] {1}, 1, FixedState.USER_FIXED);
    var trace =
        board.insertTraceWithoutCleaning(
            new Polyline(
                new Point[] {new IntPoint(112500, -100000), new IntPoint(120000, -100000)}),
            0,
            500,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    assertTrue(trace.touchingPinsAtEndCorners().contains(pin));
  }

  @Test
  void retainsExplicitSafetyMargin() throws Exception {
    var board = loadBoard(false);
    var obstacle =
        board.insertObstacle(
            new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    assertWrappers(
        board, new IntBox(112008, -100500, 113008, -99500), 0, new int[] {1}, obstacle, true);
    assertWrappers(
        board, new IntBox(112020, -100500, 113020, -99500), 0, new int[] {1}, obstacle, false);
  }

  @Test
  void negativeLayerUsesEachLayersClearance() throws Exception {
    var board = loadBoard(false);
    var obstacle =
        board.insertObstacle(
            new IntBox(90000, -110000, 110000, -90000), 1, 2, FixedState.USER_FIXED);
    assertWrappers(
        board, new IntBox(115500, -100500, 116500, -99500), -1, new int[] {1}, obstacle, true);
  }

  @Test
  void incompatibleCompensationClassRemainsConservative() throws Exception {
    var board = loadBoard(true);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    var obstacle =
        board.insertObstacle(
            new IntBox(90000, -110000, 110000, -90000), 0, 2, FixedState.USER_FIXED);
    var tree = board.searchTreeManager.getDefaultTree();
    var query =
        (ConvexShape)
            new IntBox(115000, -100500, 116000, -99500)
                .enlarge(tree.clearanceCompensationValue(2, 0));
    // Class-1 compensation sums to only 4000 here; the class-2 pair needs 10000.
    assertTrue(
        tree.overlappingTreeEntriesWithClearance(query, 0, new int[] {1}, 2).stream()
            .anyMatch(e -> e.object == obstacle));
    assertTrue(tree.overlappingItemsWithClearance(query, 0, new int[] {1}, 2).contains(obstacle));
    // A tree for class 2 represents that query's actual rule without a conservative fallback.
    var matchingTree = board.searchTreeManager.getAutorouteTree(2);
    var legalQuery =
        (ConvexShape)
            new IntBox(120500, -100500, 121500, -99500)
                .enlarge(matchingTree.clearanceCompensationValue(2, 0));
    assertFalse(
        matchingTree
            .overlappingItemsWithClearance(legalQuery, 0, new int[] {1}, 2)
            .contains(obstacle));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void objectQueriesRetainRoomsButItemQueriesExcludeThem(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    var tree = board.searchTreeManager.getDefaultTree();
    var shape = new IntBox(90000, -110000, 110000, -90000);
    var room = new CompleteFreeSpaceExpansionRoom(shape, 0, 1234);
    tree.insert(room);
    for (int queryClass : new int[] {1, 2}) {
      Set<SearchTreeObject> objects = new TreeSet<>();
      tree.overlappingObjectsWithClearance(shape, 0, new int[0], queryClass, objects);
      assertTrue(objects.contains(room));
      assertTrue(tree.overlappingItemsWithClearance(shape, 0, new int[0], queryClass).isEmpty());
      // Collectors append; a null query must leave existing results intact.
      tree.overlappingObjectsWithClearance(null, 0, new int[0], queryClass, objects);
      assertTrue(objects.contains(room));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void partitionedNettedObstaclesPreserveIdentityAndNetFiltering(boolean compensated)
      throws Exception {
    var board = loadBoard(compensated);
    var shape = new IntBox(40000, -120000, 160000, -80000);
    var obstacle =
        board.insertObstacle(shape, 0, new int[] {1}, 1, 0, "pad_aux", FixedState.USER_FIXED);
    var tree = board.searchTreeManager.getDefaultTree();
    assertTrue(obstacle.treeShapeCount(tree) > 1);
    assertWrappers(board, shape, 0, new int[] {1}, obstacle, false);
    assertWrappers(board, shape, 0, new int[] {2}, obstacle, true);
    long entries =
        tree.overlappingTreeEntriesWithClearance(shape, 0, new int[] {2}, 1).stream()
            .filter(e -> e.object == obstacle)
            .count();
    assertTrue(entries > 1);
    assertEquals(
        1,
        tree.overlappingItemsWithClearance(shape, 0, new int[] {2}, 1).stream()
            .filter(i -> i == obstacle)
            .count());
    for (boolean mode : new boolean[] {!compensated, compensated}) {
      board.searchTreeManager.setClearanceCompensationUsed(mode);
      assertTrue(board.searchTreeManager.validateEntries(obstacle));
      assertWrappers(board, shape, 0, new int[] {2}, obstacle, true);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void refinedCircleQueriesAgreeAndStillRejectRealClearanceViolations(boolean compensated)
      throws Exception {
    var board = loadBoard(compensated);
    var circle = new app.freerouting.geometry.planar.Circle(new IntPoint(100000, -100000), 25000);
    var obstacle = board.insertObstacle(circle, 0, 1, FixedState.USER_FIXED);
    var tree = board.searchTreeManager.getDefaultTree();
    for (int gap : new int[] {1500, 2500, 4500}) {
      var query =
          (ConvexShape)
              new IntBox(125000 + gap, -100100, 126000 + gap, -99900)
                  .enlarge(tree.clearanceCompensationValue(1, 0));
      assertWrappers(board, query, 0, new int[] {1}, obstacle, gap < 2000);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rawSegmentWidthIsCompensatedBeforeOptimizerChecks(boolean compensated) throws Exception {
    var board = loadBoard(compensated);
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 1, FixedState.USER_FIXED);
    assertTrue(
        board.checkTraceSegment(
                new IntPoint(112000, -105000),
                new IntPoint(112000, -95000),
                0,
                new int[] {1},
                500,
                1,
                false)
            < Integer.MAX_VALUE);
    assertEquals(
        Integer.MAX_VALUE,
        board.checkTraceSegment(
            new IntPoint(113000, -105000),
            new IntPoint(113000, -95000),
            0,
            new int[] {1},
            500,
            1,
            false));
    // Check the returned stopping distance, not just whether some obstacle was found.
    board.insertObstacle(new IntBox(110000, -110000, 130000, -90000), 1, 1, FixedState.USER_FIXED);
    assertEquals(
        compensated ? 5499 : 5483,
        board.checkTraceSegment(
            new IntPoint(100000, -100000),
            new IntPoint(120000, -100000),
            1,
            new int[] {1},
            500,
            1,
            false));
  }

  @Test
  void segmentCheckerUsesPhysicalRulesForMismatchedQueryClass() throws Exception {
    var board = loadBoard(true);
    board.rules.clearanceMatrix.setValue(2, 2, 10000);
    board.searchTreeManager.clearanceValueChanged();
    board.insertObstacle(new IntBox(90000, -110000, 110000, -90000), 0, 2, FixedState.USER_FIXED);
    assertTrue(
        board.checkTraceSegment(
                new IntPoint(115500, -105000),
                new IntPoint(115500, -95000),
                0,
                new int[] {1},
                500,
                2,
                false)
            < Integer.MAX_VALUE);
    assertEquals(
        Integer.MAX_VALUE,
        board.checkTraceSegment(
            new IntPoint(121000, -105000),
            new IntPoint(121000, -95000),
            0,
            new int[] {1},
            500,
            2,
            false));
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
      matrix.setValue(1, 1, 0, 2000);
      matrix.setValue(1, 1, 1, 4000);
      matrix.setValue(1, 2, 0, 3000);
      matrix.setValue(2, 1, 0, 3000);
      matrix.setValue(1, 2, 1, 6000);
      matrix.setValue(2, 1, 1, 6000);
      board.searchTreeManager.clearanceValueChanged();
      board.searchTreeManager.setClearanceCompensationUsed(compensated);
      return board;
    }
  }
}
