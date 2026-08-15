package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the read lock spanning ShapeSearchTree Leaf dereference and result creation.
 */
class ShapeSearchTreeLeafLifecycleTest {

  private static final IntBox QUERY_SHAPE = new IntBox(-100, -100, 100, 100);

  @Test
  void writerWaitsUntilTreeEntryResultIsConstructed() throws Exception {
    Gate gate = new Gate();
    GatedShapeSearchTree tree = new GatedShapeSearchTree(gate);
    BlockingRoom room = new BlockingRoom(gate);
    tree.insert(room);
    ShapeTree.Leaf leaf = tree.overlaps(QUERY_SHAPE).iterator().next();

    Collection<ShapeTree.TreeEntry> entries = new LinkedList<>();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      final Future<?> reader =
          executor.submit(() -> tree.overlappingTreeEntries(QUERY_SHAPE, 0, entries));

      await(gate.shapeLayerEntered);
      final Future<?> writer =
          executor.submit(
              () -> {
                try {
                  tree.removeLeaf(leaf);
                } finally {
                  gate.writerFinished.countDown();
                }
              });
      await(gate.writerEntered);
      gate.allowWriterToContinue.countDown();
      assertFalse(
          gate.writerFinished.await(1, TimeUnit.SECONDS),
          "writer must remain blocked while Leaf dereference is in progress");

      gate.allowShapeLayerToContinue.countDown();
      reader.get(30, TimeUnit.SECONDS);
      writer.get(30, TimeUnit.SECONDS);

      assertEquals(1, entries.size());
      assertSame(room, entries.iterator().next().object);
      assertTrue(tree.overlaps(QUERY_SHAPE).isEmpty());
    } finally {
      gate.allowWriterToContinue.countDown();
      gate.allowShapeLayerToContinue.countDown();
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

  private static final class Gate {

    private final CountDownLatch shapeLayerEntered = new CountDownLatch(1);
    private final CountDownLatch allowShapeLayerToContinue = new CountDownLatch(1);
    private final CountDownLatch writerEntered = new CountDownLatch(1);
    private final CountDownLatch allowWriterToContinue = new CountDownLatch(1);
    private final CountDownLatch writerFinished = new CountDownLatch(1);
  }

  private static final class GatedShapeSearchTree extends ShapeSearchTree {

    private final Gate gate;

    private GatedShapeSearchTree(Gate gate) {
      super(FortyfiveDegreeBoundingDirections.INSTANCE, null, 0);
      this.gate = gate;
    }

    @Override
    public void removeLeaf(ShapeTree.Leaf leaf) {
      gate.writerEntered.countDown();
      await(gate.allowWriterToContinue);
      super.removeLeaf(leaf);
    }
  }

  private static final class BlockingRoom extends CompleteFreeSpaceExpansionRoom {

    private final Gate gate;

    private BlockingRoom(Gate gate) {
      super(new IntBox(-20, -20, 20, 20), 0, 1);
      this.gate = gate;
    }

    @Override
    public int shapeLayer(int index) {
      gate.shapeLayerEntered.countDown();
      await(gate.allowShapeLayerToContinue);
      return super.shapeLayer(index);
    }
  }
}
