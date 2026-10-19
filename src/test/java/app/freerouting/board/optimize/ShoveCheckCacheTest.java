package app.freerouting.board.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.ShapeEntrySide;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.api.Test;

class ShoveCheckCacheTest {

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer[] layers = new Layer[] {layer1};
    LayerStructure layerStructure = new LayerStructure(layers);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    Communication communication = new Communication();

    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
        0,
        boardRules,
        communication);
  }

  private ShoveCheckCache.Key makeKey(TileShape s, ShapeEntrySide f, Direction d, int[] nets) {
    return new ShoveCheckCache.Key(s, f, d, 0, nets, 1, 5, 3, 3, true);
  }

  @Test
  void testKeysCompareByValue() {
    IntOctagon a = new IntBox(0, 0, 100, 50).toIntOctagon();
    IntOctagon same = new IntBox(0, 0, 100, 50).toIntOctagon();
    IntOctagon other = new IntBox(0, 0, 100, 51).toIntOctagon();
    ShapeEntrySide side1 = new ShapeEntrySide(2, new FloatPoint(50, 0));
    ShapeEntrySide side2 = new ShapeEntrySide(2, new FloatPoint(50, 0));

    ShoveCheckCache.Key key1 = makeKey(a, side1, Direction.RIGHT, new int[] {4});
    ShoveCheckCache.Key key2 = makeKey(same, side2, Direction.RIGHT, new int[] {4});
    ShoveCheckCache.Key keyOther = makeKey(other, side1, Direction.RIGHT, new int[] {4});

    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, keyOther);

    // Direction difference
    assertNotEquals(key1, makeKey(a, side1, Direction.UP, new int[] {4}));
    assertNotEquals(key1, makeKey(a, side1, null, new int[] {4}));

    // Side difference
    assertNotEquals(key1, makeKey(a, null, Direction.RIGHT, new int[] {4}));

    // Nets difference
    assertNotEquals(key1, makeKey(a, side1, Direction.RIGHT, new int[] {4, 5}));

    // Simplex through integer points
    assertEquals(
        makeKey(a.toSimplex(), side1, null, new int[] {4}),
        makeKey(same.toSimplex(), side2, null, new int[] {4}));
    assertNotEquals(
        makeKey(a.toSimplex(), side1, null, new int[] {4}),
        makeKey(other.toSimplex(), side1, null, new int[] {4}));
    assertNotEquals(
        makeKey(a.toSimplex(), side1, null, new int[] {4}), makeKey(a, side1, null, new int[] {4}));
  }

  @Test
  void testSupportedChecks() {
    IntOctagon a = new IntBox(0, 0, 100, 50).toIntOctagon();
    assertTrue(ShoveCheckCache.supported(a, Direction.RIGHT, new int[] {4, 5}));
    assertTrue(ShoveCheckCache.supported(a.toSimplex(), null, new int[] {4}));
    // More than 2 nets not supported
    assertFalse(ShoveCheckCache.supported(a, Direction.RIGHT, new int[] {4, 5, 6}));
  }

  @Test
  void testCachePutGetAndResetOnContentChange() {
    RoutingBoard board = createTestBoard();
    ShoveCheckCache cache = ShoveCheckCache.forThread();
    IntOctagon shape = new IntBox(100, 100, 500, 500).toIntOctagon();
    ShoveCheckCache.Key key = makeKey(shape, null, Direction.RIGHT, new int[] {1});

    long stamp = board.itemList.getContentVersion();
    ShoveCheckCache.Entry entry = new ShoveCheckCache.Entry(true, 3, false, -1, false, -1);
    cache.put(board, stamp, key, entry);

    ShoveCheckCache.Entry retrieved = cache.tryGet(board, stamp, key);
    assertEquals(entry, retrieved);

    // Bumping content version invalidates the cache entries
    board.itemList.noteContentChange();
    long newStamp = board.itemList.getContentVersion();
    assertNotEquals(stamp, newStamp);

    ShoveCheckCache.Entry afterChange = cache.tryGet(board, newStamp, key);
    assertNull(afterChange);
  }
}
