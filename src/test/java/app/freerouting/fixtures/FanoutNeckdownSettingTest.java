package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fanout must not narrow traces when automatic neckdown is disabled (issue #973).
 *
 * <p>The fanout pre-pass retried failed trace insertions with 75 %, 60 % and 50 % of the net-class
 * width even with automatic neckdown turned off. Users who disable neckdown rely on every trace
 * keeping its net-class width (their CAD rejects anything narrower on import).
 */
class FanoutNeckdownSettingTest extends RoutingFixtureTest {

  private static TestingSettings fanoutOnly(boolean automaticNeckdown) {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);
    ts.setJobTimeoutString("00:01:00");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);
    ts.setAutomaticNeckdown(automaticNeckdown);
    return ts;
  }

  private static List<String> narrowedTraces(RoutingJob job) {
    List<String> narrowed = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      int expected = job.board.rules.getTraceHalfWidth(trace.getNetNumber(0), trace.getLayer());
      if (trace.getHalfWidth() < expected) {
        narrowed.add(
            "net "
                + trace.getNetNumber(0)
                + " layer "
                + trace.getLayer()
                + ": half width "
                + trace.getHalfWidth()
                + " < "
                + expected);
      }
    }
    return narrowed;
  }

  @Test
  void fanoutKeepsNetClassWidthWhenNeckdownDisabled() {
    RoutingJob job = getRoutingJob("Issue730-DAC2020_bm11.dsn", fanoutOnly(false));
    assertFalse(job.routerSettings.getAutomaticNeckdown());
    assertTrue(job.routerSettings.isFanoutEnabled());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertFalse(job.board.getTraces().isEmpty(), "fanout should have inserted traces");
    List<String> narrowed = narrowedTraces(job);
    assertTrue(
        narrowed.isEmpty(),
        () ->
            narrowed.size() + " traces narrower than their net class, e.g. " + narrowed.getFirst());
  }
}
