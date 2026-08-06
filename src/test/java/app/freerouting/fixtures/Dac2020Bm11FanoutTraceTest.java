package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Dac2020Bm11FanoutTraceTest extends RoutingFixtureTest {

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
    Assertions.assertFalse(
        job.routerSettings.getRunRouter(),
        "Fanout-only fixture must not run the autorouter");
    Assertions.assertFalse(
        job.routerSettings.getRunOptimizer(),
        "Fanout-only fixture must not run the route optimizer");
    Assertions.assertTrue(
        job.routerSettings.isFanoutEnabled(),
        "Fanout-only fixture must run the fanout pre-pass");
  }

  @Test
  void testDAC2020Bm11FanoutTrace() {
    RoutingJob job = GetRoutingJob("Issue730-DAC2020_bm11.dsn", fanoutOnlySettings());
    assertFanoutOnlyJobSettings(job);
    RunRoutingJob(job);

    Assertions.assertEquals(RoutingJobState.COMPLETED, job.state);

    BoardStatistics stats = new BoardStatistics(job.board);
    Assertions.assertTrue(
        stats.fanout.escapedCount >= 154,
        "Expected at least 154 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }

  @Test
  void testDAC2020Bm11FanoutEscapeRate() {
    RoutingJob job = GetRoutingJob("Issue730-DAC2020_bm11.dsn", fanoutOnlySettings());
    assertFanoutOnlyJobSettings(job);
    RunRoutingJob(job);

    BoardStatistics stats = new BoardStatistics(job.board);

    System.out.println(
        "Fanout Escape Statistics: escaped=" + stats.fanout.escapedCount + ", total=" + stats.fanout.totalSmdPins);

    Assertions.assertTrue(
        stats.fanout.escapedCount >= 154,
        "Expected at least 154 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }
}
