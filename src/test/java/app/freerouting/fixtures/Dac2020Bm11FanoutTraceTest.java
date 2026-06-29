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
    // Verify that landing vias occupy exactly one layer, and trace lengths and via diameters are correct
    double resolution = job.board.communication.get_resolution(app.freerouting.board.Unit.UM);
    double minLen = 500.0 * resolution;
    double maxLen = 5000.0 * resolution;
    
    java.util.List<app.freerouting.board.Pin> pins = new java.util.ArrayList<>();
    for (app.freerouting.board.Item item : job.board.get_items()) {
      if (item instanceof app.freerouting.board.Pin pin) {
        pins.add(pin);
      }
    }

    boolean foundStartVia = false;
    boolean foundEndVia = false;
    boolean foundTrace = false;
    
    for (app.freerouting.board.Item item : job.board.get_items()) {
      if (item instanceof app.freerouting.board.Via via) {
        if (via.isEscapeVia) {
          foundStartVia = true;
          // Starting via should use startViaDiameterUm (200.0 um)
          double smallestRadius = via.get_padstack().get_shape(via.first_layer()).min_width() / 2.0;
          double expectedRadius = (200.0 * resolution) / 2.0;
          org.junit.jupiter.api.Assertions.assertEquals(expectedRadius, smallestRadius, 1.0);
        } else if (via.get_padstack().name.contains("fanout_end")) {
          foundEndVia = true;
          // Landing via (fanout end via) should occupy exactly one layer
          org.junit.jupiter.api.Assertions.assertEquals(via.first_layer(), via.last_layer(), "Landing via must be single-layer");
          // Landing via should use endViaDiameterUm (250.0 um)
          double smallestRadius = via.get_padstack().get_shape(via.first_layer()).min_width() / 2.0;
          double expectedRadius = (250.0 * resolution) / 2.0;
          org.junit.jupiter.api.Assertions.assertEquals(expectedRadius, smallestRadius, 1.0);

          // Verify distance to nearest pin on same net is bounded by [minLen, maxLen]
          double minDist = Double.MAX_VALUE;
          for (app.freerouting.board.Pin pin : pins) {
            if (pin.contains_net(via.get_net_no(0))) {
              double dist = via.get_center().to_float().distance(pin.get_center().to_float());
              if (dist < minDist) {
                minDist = dist;
              }
            }
          }
          if (minDist != Double.MAX_VALUE) {
            org.junit.jupiter.api.Assertions.assertTrue(minDist >= minLen - 1.0, "Landing via distance to pin " + minDist + " must be >= minLen " + minLen);
            org.junit.jupiter.api.Assertions.assertTrue(minDist <= maxLen + 1.0, "Landing via distance to pin " + minDist + " must be <= maxLen " + maxLen);
          }
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