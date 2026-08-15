package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.ShapeBoundingDirections;
import app.freerouting.geometry.planar.TileShape;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Regression tests for the lifetime of leaves returned by {@link MinAreaTree#overlaps}. */
class MinAreaTreeLeafLifecycleTest {

  private static final RegularTileShape QUERY_SHAPE = new IntBox(-100, -100, 100, 100);

  @Test
  void readerCanUseCapturedLeafAfterWriterRemovesIt() throws Exception {
    MinAreaTree tree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);
    TestStorable object = new TestStorable(1, new IntBox(-10, -10, 10, 10));
    tree.insert(object);

    AtomicReference<ShapeTree.Leaf> capturedLeaf = new AtomicReference<>();
    CountDownLatch readerCaptured = new CountDownLatch(1);
    CountDownLatch writerRemoved = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      final Future<?> reader =
          executor.submit(
              () -> {
                ShapeTree.Leaf localLeaf = tree.overlaps(QUERY_SHAPE).iterator().next();
                capturedLeaf.set(localLeaf);
                readerCaptured.countDown();
                await(writerRemoved);

                assertAll(
                    () -> assertSame(object, localLeaf.object),
                    () -> assertNotNull(localLeaf.boundingShape),
                    () -> assertTrue(localLeaf.boundingShape.intersects(QUERY_SHAPE)));
              });

      final Future<?> writer =
          executor.submit(
              () -> {
                await(readerCaptured);
                ShapeTree.Leaf localLeaf = capturedLeaf.get();
                assertNotNull(localLeaf);
                try {
                  tree.removeLeaf(localLeaf);
                } finally {
                  writerRemoved.countDown();
                }
              });

      reader.get(30, TimeUnit.SECONDS);
      writer.get(30, TimeUnit.SECONDS);
      assertEquals(0, tree.size());
      assertTrue(tree.overlaps(QUERY_SHAPE).isEmpty());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void removingSameLeafTwiceDoesNotCorruptTree() {
    MinAreaTree tree = new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE);
    TestStorable first = new TestStorable(1, new IntBox(-10, -10, 10, 10));
    tree.insert(first);

    tree.removeLeaf(first.entries[0]);
    assertEquals(0, tree.size());

    tree.removeLeaf(first.entries[0]);
    assertEquals(0, tree.size());

    TestStorable second = new TestStorable(2, new IntBox(-20, -20, 20, 20));
    tree.insert(second);
    assertEquals(1, tree.size());
    assertTrue(tree.overlaps(QUERY_SHAPE).contains(second.entries[0]));
  }

  @Test
  void deterministicRemovalCanMakeReaderMissSurvivingLeaf() throws Exception {
    BlockingGate gate = new BlockingGate();
    MinAreaTree tree = new GatedMinAreaTree(gate);
    TestStorable removed = new TestStorable(1, new BlockingIntBox(-20, -20, -5, -5, gate));
    TestStorable surviving = new TestStorable(2, new BlockingIntBox(5, 5, 20, 20, gate));
    tree.insert(removed);
    tree.insert(surviving);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      final Future<Set<ShapeTree.Leaf>> reader = executor.submit(() -> tree.overlaps(QUERY_SHAPE));

      await(gate.readerEntered);
      final Future<?> writer =
          executor.submit(
              () -> {
                try {
                  tree.removeLeaf(removed.entries[0]);
                } finally {
                  gate.writerFinished.countDown();
                }
              });
      await(gate.writerEntered);
      gate.allowWriterToContinue.countDown();
      assertFalse(
          gate.writerFinished.await(1, TimeUnit.SECONDS),
          "writer must remain blocked while the reader owns the logical read lock");
      gate.allowReaderToContinue.countDown();

      Set<ShapeTree.Leaf> result = reader.get(30, TimeUnit.SECONDS);
      assertTrue(
          result.stream().anyMatch(leaf -> leaf == surviving.entries[0]),
          "the reader must still see the surviving leaf");
      writer.get(30, TimeUnit.SECONDS);
      Set<ShapeTree.Leaf> afterRemoval = tree.overlaps(QUERY_SHAPE);
      assertFalse(afterRemoval.contains(removed.entries[0]));
      assertTrue(afterRemoval.contains(surviving.entries[0]));
    } finally {
      gate.allowWriterToContinue.countDown();
      gate.allowReaderToContinue.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(30, TimeUnit.SECONDS), "test coordination timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("test coordination was interrupted", exception);
    }
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

  private static final class BlockingGate {

    private final CountDownLatch readerEntered = new CountDownLatch(1);
    private final CountDownLatch allowReaderToContinue = new CountDownLatch(1);
    private final CountDownLatch writerEntered = new CountDownLatch(1);
    private final CountDownLatch allowWriterToContinue = new CountDownLatch(1);
    private final CountDownLatch writerFinished = new CountDownLatch(1);
    private final AtomicBoolean blockOnce = new AtomicBoolean(true);
  }

  private static final class GatedMinAreaTree extends MinAreaTree {

    private final BlockingGate gate;

    private GatedMinAreaTree(BlockingGate gate) {
      super(FortyfiveDegreeBoundingDirections.INSTANCE);
      this.gate = gate;
    }

    @Override
    public void removeLeaf(ShapeTree.Leaf leaf) {
      gate.writerEntered.countDown();
      await(gate.allowWriterToContinue);
      super.removeLeaf(leaf);
    }
  }

  private static final class BlockingIntBox extends IntBox {

    private final BlockingGate gate;

    private BlockingIntBox(int llX, int llY, int urX, int urY, BlockingGate gate) {
      super(new IntPoint(llX, llY), new IntPoint(urX, urY));
      this.gate = gate;
    }

    @Override
    public RegularTileShape boundingShape(ShapeBoundingDirections dirs) {
      return this;
    }

    @Override
    public RegularTileShape union(RegularTileShape other) {
      IntBox otherBox = other.boundingBox();
      return new BlockingIntBox(
          Math.min(ll.x, otherBox.ll.x),
          Math.min(ll.y, otherBox.ll.y),
          Math.max(ur.x, otherBox.ur.x),
          Math.max(ur.y, otherBox.ur.y),
          gate);
    }

    @Override
    public boolean intersects(Shape other) {
      if (gate.blockOnce.compareAndSet(true, false)) {
        gate.readerEntered.countDown();
        await(gate.allowReaderToContinue);
      }
      return super.intersects(other);
    }
  }
}
