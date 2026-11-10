package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fanout search ends at the first drill it goes through. That via is an escape only if the net
 * can use it: a drill to a signal layer the net may not use, or into the plane of another net when
 * the net may only use the pin's layer, leads nowhere.
 */
class FanoutDrillOnInactiveLayerTest extends RoutingFixtureTest {

  private static TestingSettings fanoutOnly() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(100);
    settings.setMaxItems(0);
    settings.setFanoutEnabled(true);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static Pin pin(RoutingBoard board, String reference) {
    String[] parts = reference.split("-");
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && parts[0].equals(board.components.get(p.getComponentId()).name)
                    && parts[1].equals(p.name()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * {@code Fanout-layer-subset-garland-4layer.dsn}: In1 is the GND plane, In2 a signal layer. The
   * LED and PB nets are in the class INNER, which may use F.Cu and In2.Cu but not B.Cu. A drill to
   * B.Cu used to end the search, its result was dropped, and the pin was left without fanout,
   * although a drill to In2.Cu was a valid escape.
   */
  @Test
  void pinsOfALayerSubsetClassEscapeToTheirInnerLayer() {
    RoutingJob job = getRoutingJob("Fanout-layer-subset-garland-4layer.dsn", fanoutOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    String[] innerPins = {
      "R1-1", "R1-2", "R2-1", "R2-2", "R3-1", "R3-2", "R4-1", "R4-2",
      "D1-2", "D2-2", "D3-2", "D4-2", "U1-2", "U1-5", "U1-6", "U1-7"
    };
    List<String> notEscaped = new ArrayList<>();
    for (String reference : innerPins) {
      if (!BoardStatistics.isPinEscaped(pin(job.board, reference))) {
        notEscaped.add(reference);
      }
    }
    assertTrue(notEscaped.isEmpty(), "INNER pins left without fanout: " + notEscaped);
  }

  /**
   * {@code Priority-net-classes-adc-pair-4layer.dsn}: the analog inputs are in the class ANALOG,
   * which may only use F.Cu; In1 and In2 are the GND and 3.3 V planes. A via cannot take an ANALOG
   * net anywhere, so the fanout must not leave one, even with cheap vias for the priority classes.
   */
  @Test
  void netsRestrictedToThePinLayerGetNoFanoutVia() {
    RoutingJob job = getRoutingJob("Priority-net-classes-adc-pair-4layer.dsn", fanoutOnly());
    job.routerSettings.autorouter.priorityNetClasses = new String[] {"ANALOG", "DIFF"};
    job.routerSettings.autorouter.priorityViaCosts = 1;
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    List<String> analogVias = new ArrayList<>();
    for (Via via : job.board.getVias()) {
      Net net = job.board.rules.nets.get(via.getNetNumber(0));
      if (net != null && "ANALOG".equals(net.getNetClass().getName())) {
        analogVias.add(net.name + " at " + via.getCenter());
      }
    }
    assertTrue(analogVias.isEmpty(), "Fanout vias on ANALOG nets: " + analogVias);
  }
}
