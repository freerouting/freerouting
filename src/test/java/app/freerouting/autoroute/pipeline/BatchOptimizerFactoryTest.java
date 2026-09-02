package app.freerouting.autoroute.pipeline;

import static app.freerouting.Freerouting.globalSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that adapter-specific optimizer construction preserves the expected threading policies.
 */
class BatchOptimizerFactoryTest {

  private GlobalSettings originalGlobalSettings;

  @BeforeEach
  void setUp() {
    originalGlobalSettings = globalSettings;
    Freerouting.globalSettings = new GlobalSettings();
  }

  @AfterEach
  void tearDown() {
    Freerouting.globalSettings = originalGlobalSettings;
  }

  @Test
  void headlessFactoryAlwaysCreatesSingleThreadedOptimizer() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 8;

    BatchOptimizer optimizer = BatchOptimizer.createForHeadless(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }

  @Test
  void guiFactoryDefaultsToSingleThreadedOptimizerWhenMultiThreadingDisabled() {
    globalSettings.featureFlags.multiThreading = false;
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 8;

    BatchOptimizer optimizer = BatchOptimizer.createForGui(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }

  @Test
  void guiFactoryCreatesMultiThreadedOptimizerWhenMultiThreadingEnabledAndThreadsGreaterThanOne() {
    globalSettings.featureFlags.multiThreading = true;
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 4;

    BatchOptimizer optimizer = BatchOptimizer.createForGui(job);

    assertEquals(BatchOptimizerMultiThreaded.class, optimizer.getClass());
  }

  @Test
  void guiFactoryCreatesSingleThreadedOptimizerWhenMultiThreadingEnabledButMaxThreadsIsOne() {
    globalSettings.featureFlags.multiThreading = true;
    RoutingJob job = new RoutingJob();
    job.routerSettings.optimizer.maxThreads = 1;

    BatchOptimizer optimizer = BatchOptimizer.createForGui(job);

    assertEquals(BatchOptimizer.class, optimizer.getClass());
  }
}
