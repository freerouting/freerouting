package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The tree walks test bounding shapes through {@link ShapeTree#boundsIntersect} and the octagon
 * coordinates inlined into the nodes; both must answer exactly like {@code
 * RegularTileShape.intersects}, or the walks would visit other leaves and the routing would change.
 */
class ShapeTreeBoundsIntersectTest {

  private static final int CASES = 20_000;

  @Test
  void boundsIntersectMatchesIntersectsForEveryShapeTypePair() {
    Random random = new Random(42);
    for (int i = 0; i < CASES; i++) {
      RegularTileShape a = randomShape(random);
      RegularTileShape b = randomShape(random);
      assertEquals(
          a.intersects(b), ShapeTree.boundsIntersect(a, b), () -> a + " vs " + b + " differ");
    }
  }

  @Test
  void inlinedNodeBoundsMatchIntersects() {
    Random random = new Random(7);
    for (int i = 0; i < CASES; i++) {
      RegularTileShape nodeShape = randomShape(random);
      IntOctagon query = randomOctagon(random);
      ShapeTree.InnerNode node = new ShapeTree.InnerNode(nodeShape, null);
      assertEquals(nodeShape.intersects(query), node.boundsIntersect(query));

      // Re-set through the setter: the inlined coordinates follow the new shape.
      RegularTileShape replaced = randomShape(random);
      node.setBoundingShape(replaced);
      assertEquals(replaced.intersects(query), node.boundsIntersect(query));

      // Assigned directly, bypassing the setter: the stale inlined copy must not be used.
      RegularTileShape assigned = randomOctagon(random);
      node.boundingShape = assigned;
      assertEquals(assigned.intersects(query), node.boundsIntersect(query));
    }
  }

  @Test
  void octagonQueriesFindTheSameLeavesAsABruteForceScan() {
    Random random = new Random(1234);
    MinAreaTree tree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);
    List<TestStorable> stored = new ArrayList<>();
    for (int i = 0; i < 400; i++) {
      TestStorable object = new TestStorable(i, randomOctagon(random));
      tree.insert(object);
      stored.add(object);
    }
    // Removals shrink the ancestors' bounds through the setter as well.
    for (int i = 0; i < 100; i++) {
      TestStorable removed = stored.remove(random.nextInt(stored.size()));
      for (ShapeTree.Leaf leaf : removed.leaves) {
        tree.removeLeaf(leaf);
      }
    }
    for (int q = 0; q < 500; q++) {
      IntOctagon query = randomOctagon(random);
      List<Integer> expected = new ArrayList<>();
      for (TestStorable object : stored) {
        if (object.shape.boundingOctagon().intersects(query)) {
          expected.add(object.id);
        }
      }
      List<Integer> found = new ArrayList<>();
      for (ShapeTree.Leaf leaf : tree.overlaps(query)) {
        found.add(((TestStorable) leaf.object).id);
      }
      expected.sort(Comparator.naturalOrder());
      found.sort(Comparator.naturalOrder());
      assertEquals(expected, found);
    }
  }

  private static RegularTileShape randomShape(Random random) {
    return random.nextBoolean() ? randomOctagon(random) : randomBox(random);
  }

  private static IntBox randomBox(Random random) {
    int x = random.nextInt(2000) - 1000;
    int y = random.nextInt(2000) - 1000;
    return new IntBox(x, y, x + random.nextInt(300), y + random.nextInt(300));
  }

  private static IntOctagon randomOctagon(Random random) {
    IntOctagon box = randomBox(random).toIntOctagon();
    // Cut the corners by random amounts; normalize() makes the octagon canonical.
    return new IntOctagon(
            box.leftX,
            box.bottomY,
            box.rightX,
            box.topY,
            box.upperLeftDiagonalX + random.nextInt(120),
            box.lowerRightDiagonalX - random.nextInt(120),
            box.lowerLeftDiagonalX + random.nextInt(120),
            box.upperRightDiagonalX - random.nextInt(120))
        .normalize();
  }

  private static final class TestStorable implements ShapeTree.Storable {
    private final int id;
    private final TileShape shape;
    private ShapeTree.Leaf[] leaves = new ShapeTree.Leaf[0];

    TestStorable(int id, TileShape shape) {
      this.id = id;
      this.shape = shape;
    }

    @Override
    public int treeShapeCount(ShapeTree shapeTree) {
      return 1;
    }

    @Override
    public TileShape getTreeShape(ShapeTree shapeTree, int shapeIndex) {
      return shape;
    }

    @Override
    public void setSearchTreeEntries(ShapeTree.Leaf[] entries, ShapeTree shapeTree) {
      this.leaves = entries;
    }

    @Override
    public int compareTo(Object other) {
      return Integer.compare(id, ((TestStorable) other).id);
    }
  }
}
