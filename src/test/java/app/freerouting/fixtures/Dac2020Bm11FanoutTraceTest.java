package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Dac2020Bm11FanoutTraceTest extends RoutingFixtureTest {

  @Test
  void testDAC2020Bm11FanoutTrace() {
    // Enable INFO-level console output to speed up test execution
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

    java.util.Collection<app.freerouting.rules.Net> foundNets = job.board.rules.nets.get("/RX0");
    int netNo = -1;
    if (!foundNets.isEmpty()) {
      netNo = foundNets.iterator().next().net_number;
    }
    System.out.println("--- Items for net /RX0 (net #" + netNo + "): ---");
    for (app.freerouting.board.Item item : job.board.get_items()) {
      if (item.contains_net(netNo)) {
        if (item instanceof app.freerouting.board.Pin pin) {
          System.out.println("Pin: " + pin.name() + ", layers: " + pin.first_layer() + "-" + pin.last_layer()
              + ", center: " + pin.get_center());
        } else if (item instanceof app.freerouting.board.Via via) {
          System.out.println("Via: " + via.get_padstack().name + ", layers: " + via.first_layer() + "-" + via.last_layer()
              + ", center: " + via.get_center());
        } else if (item instanceof app.freerouting.board.Trace trace) {
          System.out.println("Trace, layers: " + trace.first_layer() + "-" + trace.last_layer()
              + ", corners: " + trace.first_corner() + " -> " + trace.last_corner());
        } else {
          System.out.println("Item: " + item.getClass().getSimpleName() 
              + ", layers: " + item.first_layer() + "-" + item.last_layer());
        }
      }
    }
  }
}