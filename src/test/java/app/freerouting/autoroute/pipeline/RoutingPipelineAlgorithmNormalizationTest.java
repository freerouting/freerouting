package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

/**
 * Verifies that unrecognized or legacy algorithm identifiers normalize to the canonical algorithm.
 */
class RoutingPipelineAlgorithmNormalizationTest {

  @Test
  void unknownAlgorithmNormalizesToCurrent() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.autorouter.algorithm = "custom-unsupported-router";

    RoutingPipeline.create(job);

    assertEquals(RouterSettings.ALGORITHM_CURRENT, job.routerSettings.autorouter.algorithm);
  }

  @Test
  void legacyAlgorithmTokenNormalizesToCurrent() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.autorouter.algorithm = "freerouting-router-v19";

    RoutingPipeline.create(job);

    assertEquals(RouterSettings.ALGORITHM_CURRENT, job.routerSettings.autorouter.algorithm);
  }

  @Test
  void canonicalAlgorithmRemainsUnchanged() {
    RoutingJob job = new RoutingJob();
    job.routerSettings.autorouter.algorithm = RouterSettings.ALGORITHM_CURRENT;

    RoutingPipeline.create(job);

    assertEquals(RouterSettings.ALGORITHM_CURRENT, job.routerSettings.autorouter.algorithm);
  }
}
