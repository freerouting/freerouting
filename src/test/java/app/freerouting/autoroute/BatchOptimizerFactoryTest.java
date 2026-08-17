package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import org.junit.jupiter.api.Test;

/** Verifies that adapter-specific optimizer construction preserves the headless policy. */
class BatchOptimizerFactoryTest {

  @Test
  void headlessFactoryAlwaysCreatesSingleThreadedOptimizer() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 8;

    BatchOptimizer optimizer = BatchOptimizer.createForHeadless(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }
}
