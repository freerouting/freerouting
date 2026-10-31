package app.freerouting.board.searchtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OutlineRoomGeometryTest {

  @ParameterizedTest
  @EnumSource(AngleRestriction.class)
  void slantedOutlineUsesGeneralRoomsWithoutChangingTraceAngles(AngleRestriction angle)
      throws Exception {
    var board = slantedBoard();
    board.rules.setTraceAngleRestriction(angle);
    var tree = board.searchTreeManager.getAutorouteTree(1);
    assertEquals(ShapeSearchTree.class, tree.getClass());
    assertEquals(angle, board.rules.getTraceAngleRestriction());
    assertSame(tree, board.searchTreeManager.getAutorouteTree(1));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void legalAnchorSurvivesOutlineClippingOnBothLayersAfterRotation(int rotation) throws Exception {
    var board = slantedBoard();
    var outline = board.getOutline();
    board.searchTreeManager.remove(outline);
    outline.turn90Degree(rotation, new IntPoint(100000, -100000));
    outline.clearDerivedData();
    board.searchTreeManager.insert(outline);
    Point anchor =
        new IntPoint(100000, -150000).turn90Degree(rotation, new IntPoint(100000, -100000));
    var tree = board.searchTreeManager.getAutorouteTree(1);
    for (int layer = 0; layer < 2; layer++) {
      assertFalse(roomsAt(board, tree, anchor, layer).isEmpty());
    }
    assertEquals(outline.lineCount() * 2, outline.tileShapeCount());
    for (int i = 0; i < outline.tileShapeCount(); i++) {
      assertEquals(i / outline.lineCount(), outline.shapeLayer(i));
    }
  }

  @Test
  void realEdgeRemainsBlockedAndClearanceChangesRebuildItsGeometry() throws Exception {
    var board = slantedBoard();
    var tree = board.searchTreeManager.getAutorouteTree(1);
    assertTrue(roomsAt(board, tree, new IntPoint(100000, -153000), 0).isEmpty());
    assertFalse(roomsAt(board, tree, new IntPoint(100000, -150000), 0).isEmpty());
    board.rules.clearanceMatrix.setValue(1, 1, 10000);
    board.searchTreeManager.reinsertTreeItems();
    assertTrue(roomsAt(board, tree, new IntPoint(100000, -150000), 0).isEmpty());
  }

  @Test
  void changingOutlineReplacesRetainedEngineAndClearsItsRooms() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue969-clearance-query.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    var fast = board.initAutoroute(1, 1, null, null, true);
    assertEquals(ShapeSearchTree45Degree.class, fast.autorouteSearchTree.getClass());
    assertSame(fast, board.initAutoroute(1, 1, null, null, true));
    var rooms =
        fast.completeExpansionRoom(
            fast.addIncompleteExpansionRoom(null, 0, new IntBox(90000, -100000, 90000, -100000)));
    assertFalse(rooms.isEmpty());
    replaceOutline(board, slantedShape());
    var general = board.initAutoroute(1, 1, null, null, true);
    assertNotSame(fast, general);
    assertEquals(ShapeSearchTree.class, general.autorouteSearchTree.getClass());
    for (var room : rooms) {
      assertTrue(
          fast.autorouteSearchTree.overlaps(room.getShape().boundingOctagon()).stream()
              .noneMatch(leaf -> leaf.object == room));
    }
    assertSame(general, board.initAutoroute(1, 1, null, null, true));
    replaceOutline(board, new IntBox(0, -200000, 200000, 0));
    assertSame(
        fast.autorouteSearchTree, board.initAutoroute(1, 1, null, null, true).autorouteSearchTree);
  }

  @Test
  void alignedOutlinesKeepSpecializedSolversAndDefaultTreeReuse() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue969-clearance-query.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    assertSame(
        board.searchTreeManager.getDefaultTree(), board.searchTreeManager.getAutorouteTree(0));
    var diagonal = board.searchTreeManager.getAutorouteTree(1);
    assertEquals(ShapeSearchTree45Degree.class, diagonal.getClass());
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    // Square caps at the outer corners cannot remove usable space inside a rectangle.
    assertEquals(
        ShapeSearchTree90Degree.class, board.searchTreeManager.getAutorouteTree(1).getClass());
    var outline = board.getOutline();
    outline.setFixedState(FixedState.UNFIXED);
    board.removeItem(outline);
    assertEquals(
        ShapeSearchTree90Degree.class, board.searchTreeManager.getAutorouteTree(1).getClass());
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    assertSame(diagonal, board.searchTreeManager.getAutorouteTree(1));
  }

  @Test
  void outsideAreaModeRebuildsShapesAndBlocksTheActualExterior() throws Exception {
    var board = slantedBoard();
    var outline = board.getOutline();
    var tree = board.searchTreeManager.getAutorouteTree(1);
    Point inside = new IntPoint(100000, -150000);
    Point outside = new IntPoint(100000, -170000);
    assertFalse(roomsAt(board, tree, outside, 0).isEmpty());
    var engine = board.initAutoroute(1, 1, null, null, true);
    var outsideRooms =
        engine.completeExpansionRoom(
            engine.addIncompleteExpansionRoom(
                new IntBox(90000, -175000, 110000, -165000),
                0,
                new IntBox(100000, -170000, 100000, -170000)));
    assertFalse(outsideRooms.isEmpty());
    outline.generateKeepoutOutside(true);
    for (var room : outsideRooms) {
      assertTrue(
          tree.overlaps(room.getShape().boundingOctagon()).stream()
              .noneMatch(leaf -> leaf.object == room),
          "new outside keepout must invalidate retained rooms");
    }
    assertFalse(roomsAt(board, tree, inside, 0).isEmpty());
    assertTrue(roomsAt(board, tree, outside, 0).isEmpty());
    outline.generateKeepoutOutside(false);
    assertFalse(roomsAt(board, tree, outside, 0).isEmpty());
    assertTrue(board.searchTreeManager.validateEntries(outline));
    board.finishAutoroute();
  }

  @Test
  void generalOutlineTreePreservesLegacyNonOutlineEnvelopesAndAnglePolicy() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    var general45 = board.searchTreeManager.getAutorouteTree(1);
    var legacy45 = new ShapeSearchTree45Degree(board, 1);
    for (var pin : board.getPins()) {
      var actual = general45.calculateTreeShapes(pin);
      var expected = legacy45.calculateTreeShapes(pin);
      assertEquals(expected.length, actual.length);
      for (int i = 0; i < actual.length; i++) {
        assertTrue(actual[i].contains(expected[i]));
        assertTrue(expected[i].contains(actual[i]));
      }
    }
    var obstacle =
        board.insertObstacle(
            new app.freerouting.geometry.planar.Circle(new IntPoint(40000, 40000), 1000),
            0,
            1,
            FixedState.USER_FIXED);
    var actual = general45.calculateTreeShapes(obstacle);
    var expected = legacy45.calculateTreeShapes(obstacle);
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < actual.length; i++) {
      assertTrue(actual[i].contains(expected[i]));
      assertTrue(expected[i].contains(actual[i]));
    }
    var oldEngine = board.initAutoroute(1, 1, null, null, true);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var nextEngine = board.initAutoroute(1, 1, null, null, true);
    assertNotSame(oldEngine, nextEngine);
    assertNotSame(general45, nextEngine.autorouteSearchTree);
    assertEquals(ShapeSearchTree.class, nextEngine.autorouteSearchTree.getClass());
  }

  @Test
  void roomQueriesSeeTraceEnvelopesWhileContactQueriesKeepExactShapes() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    var tree = board.searchTreeManager.getAutorouteTree(1);
    var trace =
        board.insertTraceWithoutCleaning(
            new app.freerouting.geometry.planar.Polyline(
                new IntPoint(30000, 30000), new IntPoint(40000, 33000)),
            0,
            100,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    var query = new IntBox(34000, 30050, 34000, 30050);
    assertFalse(trace.getTreeShape(tree, 0).intersects(query));
    assertTrue(tree.getRoomShape(trace, 0).intersects(query));
    var entries = new java.util.ArrayList<app.freerouting.datastructures.ShapeTree.TreeEntry>();
    tree.overlappingTreeEntries(query, 0, entries);
    assertTrue(entries.stream().noneMatch(e -> e.object == trace));
    tree.overlappingRoomEntries(query, 0, entries);
    assertTrue(entries.stream().anyMatch(e -> e.object == trace));
    var outline = board.getOutline();
    assertSame(outline.getTreeShape(tree, 0), tree.getRoomShape(outline, 0));
  }

  @Test
  void orthogonalRoomQueriesKeepBoxBoundsAfterExplicitTraceCutout() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var tree = board.searchTreeManager.getAutorouteTree(1);
    var trace =
        board.insertTraceWithoutCleaning(
            new app.freerouting.geometry.planar.Polyline(
                new IntPoint(30000, 30000), new IntPoint(40000, 30000)),
            0,
            1000,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    var cutout =
        new PolygonShape(
                new Point[] {
                  new IntPoint(30000, 29000), new IntPoint(40000, 29000), new IntPoint(40000, 31000)
                })
            .splitToConvex()[0];
    tree.changeItemShape(trace, 0, cutout);
    var query = new IntBox(30100, 30900, 30100, 30900);
    assertFalse(trace.getTreeShape(tree, 0).intersects(query));
    assertTrue(tree.getRoomShape(trace, 0).intersects(query));
    var entries = new java.util.ArrayList<app.freerouting.datastructures.ShapeTree.TreeEntry>();
    tree.overlappingTreeEntries(query, 0, entries);
    assertTrue(entries.stream().noneMatch(e -> e.object == trace));
    tree.overlappingRoomEntries(query, 0, entries);
    assertTrue(entries.stream().anyMatch(e -> e.object == trace));
  }

  @Test
  void removingTraceDropsRoomsThatMeetOnlyItsEnvelope() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    var engine = board.initAutoroute(1, 1, null, null, true);
    var tree = engine.autorouteSearchTree;
    var trace =
        board.insertTraceWithoutCleaning(
            new app.freerouting.geometry.planar.Polyline(
                new IntPoint(30000, 30000), new IntPoint(40000, 33000)),
            0,
            100,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    var gap = new IntBox(34000, 30050, 34001, 30051);
    assertFalse(trace.getTreeShape(tree, 0).intersects(gap));
    assertTrue(tree.getRoomShape(trace, 0).intersects(gap));
    var room = new CompleteFreeSpaceExpansionRoom(gap, 0, 99);
    tree.insert(room);
    board.removeItem(trace);
    assertTrue(
        tree.overlaps(gap).stream().noneMatch(leaf -> leaf.object == room),
        "envelope overlap must invalidate the retained room");
  }

  @Test
  void snapAngleChangeRebuildsDefaultTreeShapesAndDropsRetainedEngine() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue969-clearance-query.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    var via =
        board.insertVia(
            board.library.padstacks.get("Via100"),
            new IntPoint(10000, -10000),
            new int[] {1},
            0,
            FixedState.UNFIXED,
            false);
    var engine = board.initAutoroute(1, 0, null, null, true);
    assertSame(board.searchTreeManager.getDefaultTree(), engine.autorouteSearchTree);
    assertFalse(via.getTreeShape(engine.autorouteSearchTree, 0).isIntBox());
    var same = board.initAutoroute(1, 0, null, null, true);
    assertSame(engine, same);
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var rebuilt = board.initAutoroute(1, 0, null, null, true);
    assertNotSame(engine, rebuilt);
    assertSame(board.searchTreeManager.getDefaultTree(), rebuilt.autorouteSearchTree);
    assertTrue(via.getTreeShape(rebuilt.autorouteSearchTree, 0).isIntBox());
  }

  @Test
  void rawDrcTreeNeverReusesTheAutorouteEnvelopePolicy() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    board.searchTreeManager.setClearanceCompensationUsed(true);
    var envelope = board.searchTreeManager.getAutorouteTree(0);
    var raw = board.searchTreeManager.getUncompensatedTree();
    assertNotSame(envelope, raw);
    assertEquals(null, raw.obstacleShapeAngle());
    assertEquals(AngleRestriction.FORTYFIVE_DEGREE, envelope.obstacleShapeAngle());
    assertSame(raw, board.searchTreeManager.getUncompensatedTree());
  }

  @Test
  void concaveOrthogonalOutlineStillRequiresGeneralRooms() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue969-clearance-query.dsn");
    replaceOutline(
        board,
        new PolygonShape(
            new Point[] {
              new IntPoint(0, -200000),
              new IntPoint(200000, -200000),
              new IntPoint(200000, 0),
              new IntPoint(100000, 0),
              new IntPoint(100000, -100000),
              new IntPoint(0, -100000)
            }));
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    assertEquals(ShapeSearchTree.class, board.searchTreeManager.getAutorouteTree(1).getClass());
  }

  private static Collection<IncompleteFreeSpaceExpansionRoom> roomsAt(
      RoutingBoard board, ShapeSearchTree tree, Point anchor, int layer) {
    var point = anchor.toFloat().round();
    var rooms =
        tree.completeShape(
            new IncompleteFreeSpaceExpansionRoom(
                board.boundingBox, layer, new IntBox(point.x, point.y, point.x, point.y)),
            1,
            null,
            null);
    // Large rooms are subdivided, including sections away from the seed. Check usable rooms
    // containing the actual anchor rather than accepting any returned free-space section.
    return rooms.stream().filter(room -> room.getShape().contains(anchor)).toList();
  }

  private static RoutingBoard slantedBoard() throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue969-clearance-query.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    replaceOutline(board, slantedShape());
    return board;
  }

  private static PolygonShape slantedShape() {
    return new PolygonShape(
        new Point[] {
          new IntPoint(10000, -180000),
          new IntPoint(190000, -126000),
          new IntPoint(190000, -10000),
          new IntPoint(10000, -10000)
        });
  }

  private static void replaceOutline(RoutingBoard board, PolylineShape shape) {
    var old = board.getOutline();
    old.setFixedState(FixedState.UNFIXED);
    board.removeItem(old);
    board.insertOutline(new PolylineShape[] {shape}, 1);
  }
}
