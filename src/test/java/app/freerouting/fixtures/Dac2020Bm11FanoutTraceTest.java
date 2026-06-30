package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Dac2020Bm11FanoutTraceTest extends RoutingFixtureTest {

  @Test
  void testDAC2020Bm11FanoutTrace() {
    // Enable TRACE-level console output and granular trace logging
    System.setProperty("freerouting.logging.console.level", "TRACE");
    FRLogger.granularTraceEnabled = true;

    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);  // no limit
    ts.setJobTimeoutString("00:10:00");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);

    RoutingJob job = GetRoutingJob("Issue508-DAC2020/DAC2020_bm11/DAC2020_bm11.unrouted.dsn", ts);
    RunRoutingJob(job);
  }

  @Test
  void testDAC2020Bm11FanoutEscapeRate() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;

    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(100);
    ts.setMaxItems(0);  // no limit
    ts.setJobTimeoutString("00:10:00");
    ts.setFanoutEnabled(true);
    ts.setRouterEnabled(false);
    ts.setOptimizerEnabled(false);

    RoutingJob job = GetRoutingJob("Issue508-DAC2020/DAC2020_bm11/DAC2020_bm11.unrouted.dsn", ts);
    RunRoutingJob(job);

    app.freerouting.core.scoring.BoardStatistics stats = new app.freerouting.core.scoring.BoardStatistics(job.board);

    System.out.println("Fanout Escape Statistics: escaped=" + stats.fanout.escapedCount + ", total=" + stats.fanout.totalSmdPins);

    org.junit.jupiter.api.Assertions.assertTrue(stats.fanout.escapedCount >= 156,
        "Expected at least 156 escaped pins, but had " + stats.fanout.escapedCount + ".");
  }
}