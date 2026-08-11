package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class Issue690RoutingTest extends RoutingFixtureTest {

  @Test
  void issue690Ecc83() {
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
  void issue690KitDevColdfireXilinx() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(1);
    ts.setMaxItems(50);
    ts.setFanoutEnabled(false);
    ts.setJobTimeoutString("00:02:00");
    RoutingJob job = getRoutingJob("Issue690-kit-dev-coldfire-xilinx_5213.dsn", ts);
    runRoutingJob(job);
  }

  @Test
  void issue690SondeXilinx() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(1);
    ts.setMaxItems(50);
    ts.setFanoutEnabled(false);
    ts.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Issue690-sonde_xilinx.dsn", ts);
    runRoutingJob(job);
  }
}
