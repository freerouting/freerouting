package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.TileShape;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that {@link MinAreaTree} scratch stack handling is re-entrant on the same thread,
 * resets cleanly on normal completion and exceptions, and does not leak tree instances via
 * thread-local maps (Issue #1123).
 */
class MinAreaTreeReentrancyAndMemoryTest {

  private static final RegularTileShape QUERY_SHAPE = new IntBox(-500, -500, 500, 500);

  @Test
  void reentrantTreeQueriesOnSameThreadDoNotCorruptStack() {
    MinAreaTree outerTree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);
    MinAreaTree innerTree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);

    for (int i = 0; i < 20; i++) {
      outerTree.insert(new TestStorable(i, new IntBox(i * 10, i * 10, i * 10 + 5, i * 10 + 5)));
      innerTree.insert(new TestStorable(100 + i, new IntBox(i * 5, i * 5, i * 5 + 3, i * 5 + 3)));
    }

    AtomicInteger reentrantCalls = new AtomicInteger();
    MinAreaTree reentrantTree =
        new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE) {
          @Override
          protected void onNodeVisited() {
            // Trigger an inner query on the same thread during outer query traversal
            if (reentrantCalls.incrementAndGet() == 1) {
              List<ShapeTree.Leaf> innerResult = innerTree.overlaps(QUERY_SHAPE);
              assertEquals(20, innerResult.size());
            }
          }
        };

    for (int i = 0; i < 15; i++) {
      reentrantTree.insert(
          new TestStorable(200 + i, new IntBox(i * 2, i * 2, i * 2 + 2, i * 2 + 2)));
    }

    List<ShapeTree.Leaf> outerResult = reentrantTree.overlaps(QUERY_SHAPE);
    assertEquals(15, outerResult.size());
  }

  @Test
  void traversalStackResetsCleanlyAfterException() {
    MinAreaTree tree =
        new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE) {
          private boolean shouldFail = true;

          @Override
          protected void onNodeVisited() {
            if (shouldFail) {
              shouldFail = false;
              throw new IllegalStateException("Simulated query failure");
            }
          }
        };

    for (int i = 0; i < 10; i++) {
      tree.insert(new TestStorable(i, new IntBox(i * 10, i * 10, i * 10 + 5, i * 10 + 5)));
    }

    assertThrows(IllegalStateException.class, () -> tree.overlaps(QUERY_SHAPE));

    // The subsequent query on the same thread must succeed cleanly with a reset stack
    assertDoesNotThrow(
        () -> {
          List<ShapeTree.Leaf> result = tree.overlaps(QUERY_SHAPE);
          assertEquals(10, result.size());
        });
  }

  @Test
  void discardedTreesAreGarbageCollectedWithoutThreadLocalRetention() {
    WeakReference<MinAreaTree> weakTree = createAndQueryTree();

    for (int i = 0; i < 5 && weakTree.get() != null; i++) {
      System.gc();
      try {
        Thread.sleep(50);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }

    assertNull(
        weakTree.get(),
        "Discarded MinAreaTree must be garbage collected and not retained in threadLocals");
  }

  private WeakReference<MinAreaTree> createAndQueryTree() {
    MinAreaTree tree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);
    for (int i = 0; i < 50; i++) {
      tree.insert(new TestStorable(i, new IntBox(i * 10, i * 10, i * 10 + 5, i * 10 + 5)));
    }
    List<ShapeTree.Leaf> results = tree.overlaps(QUERY_SHAPE);
    assertEquals(50, results.size());
    return new WeakReference<>(tree);
  }

  private static final class TestStorable implements ShapeTree.Storable {
    private final int id;
    private final TileShape shape;
    private ShapeTree.Leaf[] entries;

    private TestStorable(int id, TileShape shape) {
      this.id = id;
      this.shape = shape;
    }

    @Override
    public int compareTo(Object other) {
      return Integer.compare(id, ((TestStorable) other).id);
    }

    @Override
    public int treeShapeCount(ShapeTree shapeTree) {
      return 1;
    }

    @Override
    public TileShape getTreeShape(ShapeTree shapeTree, int index) {
      return shape;
    }

    @Override
    public void setSearchTreeEntries(ShapeTree.Leaf[] entries, ShapeTree tree) {
      this.entries = entries;
    }
  }
}
