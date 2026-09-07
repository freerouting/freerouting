package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import org.junit.jupiter.api.Test;

/** Verifies that the unified optimizer factory constructs the canonical BatchOptimizer instance. */
class BatchOptimizerFactoryTest {

  @Test
  void canonicalFactoryCreatesBatchOptimizer() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 8;

    BatchOptimizer optimizer = BatchOptimizer.create(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }

  @Test
  void headlessFactoryCreatesBatchOptimizer() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 8;

    BatchOptimizer optimizer = BatchOptimizer.createForHeadless(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }

  @Test
  void guiFactoryCreatesBatchOptimizer() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 4;

    BatchOptimizer optimizer = BatchOptimizer.createForGui(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }
}
