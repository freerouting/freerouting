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
    // Verify that landing vias occupy exactly one layer, and trace/via diameters match settings
    double resolution = job.board.communication.get_resolution(app.freerouting.board.Unit.MM);

    boolean foundStartVia = false;
    boolean foundEndVia = false;
    boolean foundTrace = false;
    
    for (app.freerouting.board.Item item : job.board.get_items()) {
      if (item instanceof app.freerouting.board.Via via) {
        if (via.isEscapeVia) {
          foundStartVia = true;
          // Starting via should use startViaDiameterMm
          double smallestRadius = via.get_padstack().get_shape(via.first_layer()).min_width() / 2.0;
          double expectedRadius = (ts.getSettings().fanout.startViaDiameterMm != null
              ? ts.getSettings().fanout.startViaDiameterMm : 0.250) * resolution / 2.0;
          org.junit.jupiter.api.Assertions.assertEquals(expectedRadius, smallestRadius, 1.0);
        } else if (via.get_padstack().name.contains("fanout_end")) {
          foundEndVia = true;
          // Landing via (fanout end via) should occupy exactly one layer
          org.junit.jupiter.api.Assertions.assertEquals(via.first_layer(), via.last_layer(), "Landing via must be single-layer");
          // Landing via should use endViaDiameterMm
          double smallestRadius = via.get_padstack().get_shape(via.first_layer()).min_width() / 2.0;
          double expectedRadius = (ts.getSettings().fanout.endViaDiameterMm != null
              ? ts.getSettings().fanout.endViaDiameterMm : 0.250) * resolution / 2.0;
          org.junit.jupiter.api.Assertions.assertEquals(expectedRadius, smallestRadius, 1.0);
        }
      } else if (item instanceof app.freerouting.board.Trace trace) {
        foundTrace = true;
      }
    }
    
    org.junit.jupiter.api.Assertions.assertTrue(foundStartVia, "Must find at least one starting escape via");
    org.junit.jupiter.api.Assertions.assertTrue(foundEndVia, "Must find at least one landing end via");
    org.junit.jupiter.api.Assertions.assertTrue(foundTrace, "Must find at least one escape trace");
  }
}