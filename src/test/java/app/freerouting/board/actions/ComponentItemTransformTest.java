package app.freerouting.board.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ComponentItemTransformTest {
  private RoutingBoard board() {
    var layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    var rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    var board =
        new RoutingBoard(
            new IntBox(-100000, -100000, 100000, 100000),
            layers,
            new PolylineShape[] {new IntBox(-100000, -100000, 100000, 100000)},
            0,
            rules,
            new Communication());
    board.library.padstacks = new app.freerouting.core.library.Padstacks(layers);
    board.library.packages = new app.freerouting.core.library.Packages(board.library.padstacks);
    rules.nets.add("one", 1, false);
    rules.nets.add("two", 1, false);
    return board;
  }

  private Pin pin(RoutingBoard board, double x, double y, int net) {
    var pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 20), new Circle(Point.ZERO, 20)});
    var pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", pad.id, Vector.ZERO, 0)});
    var location = new FloatPoint(x, y);
    var component =
        board.components.addPrecise(
            "C" + board.components.count(), location, 0, true, pkg, pkg, false, null);
    return board.insertPin(component.id, 0, new int[] {net}, 1, FixedState.UNFIXED);
  }

  private Item trace(RoutingBoard board, Point from, Point to) {
    return board.insertTraceWithoutCleaning(
        new Polyline(new Point[] {from, to}), 0, 2, new int[] {1}, 1, FixedState.UNFIXED);
  }

  private List<Item> detach(RoutingBoard board, Item... items) {
    return java.util.Arrays.stream(items)
        .map(
            item -> {
              board.detachItemForMove(item);
              return item.copy(0);
            })
        .toList();
  }

  @ParameterizedTest
  @CsvSource({"false,true,0.5", "true,true,0.5", "false,false,-0.5", "true,false,-0.5"})
  void sharedJunctionViaAndUndoRemainConsistent(boolean rotateFirst, boolean front, double half) {
    var board = board();
    board.components.setFlipStyleRotateFirst(rotateFirst);
    var pin = pin(board, 2000 + half, 3000 + half, 1);
    if (!front) {
      board.searchTreeManager.remove(pin);
      board.components.changeSide(pin.getComponentId(), new IntPoint(2000, 0));
      pin.changePlacementSide(new IntPoint(2000, 0));
      board.searchTreeManager.insert(pin);
    }
    var original = pin.getCenter();
    var first = trace(board, original, original.translateBy(new IntVector(1000, 0)));
    var second = trace(board, original, original.translateBy(new IntVector(0, 1000)));
    var via =
        board.insertVia(pin.getPadstack(), original, new int[] {1}, 1, FixedState.UNFIXED, true);
    assertEquals(3, pin.getNormalContacts().size());
    board.generateSnapshot();
    var detached = detach(board, pin, first, second, via);
    var moved = ComponentItemTransform.apply(board, detached, 90, false, new IntPoint(100, 200));
    assertNotNull(moved);
    moved.forEach(board::insertItem);
    var movedPin = (Pin) moved.getFirst();
    assertEquals(3, movedPin.getNormalContacts().size());
    var newCenter = movedPin.getCenter();
    assertEquals(newCenter, ((app.freerouting.board.model.items.Via) moved.get(3)).getCenter());
    assertTrue(board.undo(null));
    assertEquals(original, board.getPins().iterator().next().getCenter());
    assertEquals(3, board.getPins().iterator().next().getNormalContacts().size());
    assertTrue(board.redo(null));
    assertEquals(newCenter, board.getPins().iterator().next().getCenter());
    assertEquals(3, board.getPins().iterator().next().getNormalContacts().size());
  }

  @Test
  void componentRotationApiUsesExactQuarterTurns() {
    var board = board();
    var pin = pin(board, 2000.5, -3000.5, 1);
    var component = board.components.get(pin.getComponentId());
    var start = component.getExactLocation();
    for (int i = 0; i < 4; i++) {
      component.rotate(90, new IntPoint(500, 600), false);
    }
    assertEquals(start.x, component.getExactLocation().x);
    assertEquals(start.y, component.getExactLocation().y);
  }

  @Test
  void conflictingAnchorsRejectWithoutChangingPoseOrSearchTree() {
    var board = board();
    var a = pin(board, 2000.5, 3000.5, 1);
    var b = pin(board, 4000, 3001, 1);
    var trace = trace(board, a.getCenter(), b.getCenter());
    var location = board.components.get(a.getComponentId()).getExactLocation();
    var detached = detach(board, a, b, trace);
    assertNull(ComponentItemTransform.apply(board, detached, 90, false, Point.ZERO));
    assertEquals(location, board.components.get(a.getComponentId()).getExactLocation());
    detached.forEach(board::insertItem);
    assertEquals(1, detached.getFirst().getNormalContacts().size());
    assertEquals(1, detached.get(1).getNormalContacts().size());
    assertEquals(2, detached.get(2).getNormalContacts().size());
  }

  @Test
  void newForeignNetClearanceRejectsAndLeavesStationaryPinUntouched() {
    var board = board();
    var a = pin(board, 2000.5, 3000.5, 1);
    var obstacle = pin(board, -3000, 2001, 2);
    var before = obstacle.getCenter();
    var detached = detach(board, a);
    assertNull(ComponentItemTransform.apply(board, detached, 90, false, Point.ZERO));
    assertEquals(before, obstacle.getCenter());
    assertTrue(obstacle.getNormalContacts().isEmpty());
    detached.forEach(board::insertItem);
    assertEquals(
        0,
        new app.freerouting.drc.DesignRulesChecker(board, null).getAllClearanceViolations().size());
  }

  @Test
  void existingNonGridPinEscapeCanStillRotateRigidly() {
    var board = board();
    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    var a = pin(board, 2000.5, 3000.5, 1);
    var wire = trace(board, a.getCenter(), a.getCenter().translateBy(new IntVector(1000, 600)));
    var detached = detach(board, a, wire);
    var moved = ComponentItemTransform.apply(board, detached, 90, false, Point.ZERO);
    assertNotNull(moved);
    moved.forEach(board::insertItem);
    assertEquals(1, moved.getFirst().getNormalContacts().size());
  }

  @Test
  void illegalTraceAngleRejectsRatherThanDistortingCopper() {
    var board = board();
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var a = pin(board, 2000.5, 3000.5, 1);
    var wire = trace(board, a.getCenter(), a.getCenter().translateBy(new IntVector(1000, 0)));
    var detached = detach(board, a, wire);
    assertNull(ComponentItemTransform.apply(board, detached, 45, false, Point.ZERO));
    detached.forEach(board::insertItem);
    assertEquals(1, detached.getFirst().getNormalContacts().size());
  }
}
