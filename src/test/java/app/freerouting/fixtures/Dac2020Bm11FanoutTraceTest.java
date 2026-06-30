package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Dac2020Bm11FanoutTraceTest extends RoutingFixtureTest {

  @Test
  void testDAC2020Bm11FanoutTrace() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);  // no limit
    ts.setJobTimeoutString("00:00:20");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);

    RoutingJob job = GetRoutingJob("Issue730-DAC2020_bm11.dsn", ts);
    RunRoutingJob(job);
  }

  @Test
  void testDAC2020Bm11FanoutEscapeRate() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);  // no limit
    ts.setJobTimeoutString("00:00:20");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);

    RoutingJob job = GetRoutingJob("Issue730-DAC2020_bm11.dsn", ts);
    RunRoutingJob(job);

    app.freerouting.core.scoring.BoardStatistics stats = new app.freerouting.core.scoring.BoardStatistics(job.board);

    System.out.println("Fanout Escape Statistics: escaped=" + stats.fanout.escapedCount + ", total=" + stats.fanout.totalSmdPins);

    org.junit.jupiter.api.Assertions.assertTrue(stats.fanout.escapedCount >= 154,
        "Expected at least 154 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }
}