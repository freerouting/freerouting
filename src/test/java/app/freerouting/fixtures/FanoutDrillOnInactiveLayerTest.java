package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code Priority-net-classes-adc-pair-4layer.dsn}, fanout only: the analog inputs AIN0..AIN3 of
 * both ADCs are in the class ANALOG, which may only use F.Cu.
 *
 * <p>The fanout search stopped at the first drill it went through, whatever the layer the drill led
 * to. With cheap vias for the priority classes, the cheapest escape of an AIN pin is a drill that
 * leads to B.Cu, a layer ANALOG may not use: the search ended there, the result was dropped, and
 * the pin was left without fanout. The search now only stops at a drill leading to a layer the net
 * may use (or to a plane), and goes on otherwise.
 */
class FanoutDrillOnInactiveLayerTest extends RoutingFixtureTest {

  private static final String BOARD = "Priority-net-classes-adc-pair-4layer.dsn";

  private static final String[] ANALOG_PINS = {
    "U1-6", "U1-7", "U1-10", "U1-11", "U2-6", "U2-7", "U2-10", "U2-11"
  };

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

  @Test
  void analogPinsEscapeWithCheapPriorityVias() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(100);
    settings.setMaxItems(0);
    settings.setFanoutEnabled(true);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    job.routerSettings.autorouter.priorityNetClasses = new String[] {"ANALOG", "DIFF"};
    job.routerSettings.autorouter.priorityViaCosts = 1;
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    List<String> notEscaped = new ArrayList<>();
    for (String reference : ANALOG_PINS) {
      if (!BoardStatistics.isPinEscaped(pin(job.board, reference))) {
        notEscaped.add(reference);
      }
    }
    assertTrue(notEscaped.isEmpty(), "ANALOG pins left without fanout: " + notEscaped);
  }
}
