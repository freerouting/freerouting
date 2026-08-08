package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

class Dac2020Bm11FanoutTraceTest extends RoutingFixtureTest {

  private static TestingSettings fanoutOnlySettings() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);
    ts.setJobTimeoutString("00:01:00");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);
    return ts;
  }

  private static void assertFanoutOnlyJobSettings(RoutingJob job) {
    assertFalse(
        job.routerSettings.getRunRouter(), "Fanout-only fixture must not run the autorouter");
    assertFalse(
        job.routerSettings.getRunOptimizer(),
        "Fanout-only fixture must not run the route optimizer");
    assertTrue(
        job.routerSettings.isFanoutEnabled(), "Fanout-only fixture must run the fanout pre-pass");
  }

  @Test
  void dac2020Bm11FanoutTrace() {
    RoutingJob job = getRoutingJob("Issue730-DAC2020_bm11.dsn", fanoutOnlySettings());
    assertFanoutOnlyJobSettings(job);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);

    BoardStatistics stats = new BoardStatistics(job.board);
    assertTrue(
        stats.fanout.escapedCount >= 154,
        "Expected at least 154 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }

  @Test
  void dac2020Bm11FanoutEscapeRate() {
    RoutingJob job = getRoutingJob("Issue730-DAC2020_bm11.dsn", fanoutOnlySettings());
    assertFanoutOnlyJobSettings(job);
    runRoutingJob(job);

    BoardStatistics stats = new BoardStatistics(job.board);

    IO.println(
        "Fanout Escape Statistics: escaped="
            + stats.fanout.escapedCount
            + ", total="
            + stats.fanout.totalSmdPins);

    assertTrue(
        stats.fanout.escapedCount >= 154,
        "Expected at least 154 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }
}
