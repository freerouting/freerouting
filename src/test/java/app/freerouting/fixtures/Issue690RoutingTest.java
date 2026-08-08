package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class Issue690RoutingTest extends RoutingFixtureTest {

  @Test
  void testIssue690Ecc83() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(1);
    ts.setMaxItems(50);
    ts.setFanoutEnabled(false);
    ts.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Issue690-ecc83.dsn", ts);
    runRoutingJob(job);
  }

  @Test
  @Tag("slow")
  void testIssue690KitDevColdfireXilinx() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(1);
    ts.setMaxItems(50);
    ts.setFanoutEnabled(false);
    ts.setJobTimeoutString("00:02:00");
    RoutingJob job = getRoutingJob("Issue690-kit-dev-coldfire-xilinx_5213.dsn", ts);
    runRoutingJob(job);
  }

  @Test
  void testIssue690SondeXilinx() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(1);
    ts.setMaxItems(50);
    ts.setFanoutEnabled(false);
    ts.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Issue690-sonde_xilinx.dsn", ts);
    runRoutingJob(job);
  }
}
