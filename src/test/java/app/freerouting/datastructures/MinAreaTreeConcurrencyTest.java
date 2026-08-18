package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Regression test for thread-safety of {@link MinAreaTree#overlaps}. The traversal stack must be
 * local to each call; a shared instance field corrupts under concurrent use (for example routing on
 * the main thread while deferred post-load runs {@link BoardStatistics} on a virtual thread).
 */
class MinAreaTreeConcurrencyTest {

  @Test
  void overlapsConcurrentQueriesDoNotCorruptStack() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    var tree = board.searchTreeManager.getDefaultTree();
    RegularTileShape queryShape =
        board.getBoundingBox().boundingShape(FortyfiveDegreeBoundingDirections.INSTANCE);

    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Future<?>> futures = new ArrayList<>();
    for (int thread = 0; thread < 8; thread++) {
      futures.add(
          pool.submit(
              () -> {
                for (int i = 0; i < 200; i++) {
                  tree.overlaps(queryShape);
                }
              }));
    }
    futures.add(pool.submit(() -> new BoardStatistics(board, null, false, false)));

    assertDoesNotThrow(
        () -> {
          for (Future<?> future : futures) {
            future.get(2, TimeUnit.MINUTES);
          }
        });

    pool.shutdown();
    assertDoesNotThrow(
        () -> {
          if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
            pool.shutdownNow();
          }
        });
  }
}
