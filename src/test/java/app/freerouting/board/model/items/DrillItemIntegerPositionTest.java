package app.freerouting.board.model.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Padstacks;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.RationalVector;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.math.BigInteger;
import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DrillItemIntegerPositionTest {
  @ParameterizedTest
  @ValueSource(strings = {"circle", "box", "octagon"})
  void unsupportedFractionalPositionsAreRejectedBeforeChangingBoard(String kind) {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("F.Cu", true), new Layer("B.Cu", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    RoutingBoard board =
        new RoutingBoard(
            new IntBox(-10000, -10000, 10000, 10000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            new Communication());
    rules.nets.add("TEST", 1, false);
    board.library.padstacks = new Padstacks(layers);
    ConvexShape shape =
        switch (kind) {
          case "circle" -> new Circle(Point.ZERO, 100);
          case "box" -> new IntBox(-100, -100, 100, 100);
          default -> new IntOctagon(-100, -100, 100, 100, -150, 150, -150, 150);
        };
    var padstack =
        board.library.padstacks.add("test", new ConvexShape[] {shape, shape}, true, false);
    var initial = new ArrayList<>(board.getItems());
    Vector fractional = new RationalVector(BigInteger.ONE, BigInteger.ZERO, BigInteger.TWO);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            board.insertVia(
                padstack,
                Point.ZERO.translateBy(fractional),
                new int[] {1},
                1,
                FixedState.UNFIXED,
                true));
    assertEquals(initial, new ArrayList<>(board.getItems()));
    Vector integral = new RationalVector(BigInteger.TWO, BigInteger.ZERO, BigInteger.TWO);
    Via via =
        board.insertVia(
            padstack, Point.ZERO.translateBy(integral), new int[] {1}, 1, FixedState.UNFIXED, true);
    assertInstanceOf(IntPoint.class, via.getCenter());
    var before = new ArrayList<>(board.getItems());
    Point center = via.getCenter();
    assertThrows(IllegalArgumentException.class, () -> via.moveBy(fractional));
    assertThrows(IllegalArgumentException.class, () -> via.translateBy(fractional));
    assertEquals(center, via.getCenter());
    assertEquals(before, new ArrayList<>(board.getItems()));
    assertTrue(board.overlappingObjects(TileShape.getInstance(center), 0).contains(via));
    // Move beyond the old pad envelope so a stale tree entry cannot satisfy the lookup.
    Vector largeIntegral =
        new RationalVector(BigInteger.valueOf(2000), BigInteger.ZERO, BigInteger.TWO);
    via.moveBy(largeIntegral);
    assertEquals(new IntPoint(1001, 0), via.getCenter());
    for (int layer = 0; layer < 2; layer++) {
      assertTrue(
          board.overlappingObjects(TileShape.getInstance(via.getCenter()), layer).contains(via));
      assertFalse(board.overlappingObjects(TileShape.getInstance(center), layer).contains(via));
    }
    assertEquals(
        new IntPoint(1, 0), shape.translateBy(integral).boundingBox().centreOfGravity().round());
    assertThrows(IllegalArgumentException.class, () -> shape.translateBy(fractional));
  }
}
