package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code Priority-net-classes-adc-pair-4layer.dsn}: two TSSOP-16 ADCs with their decoupling, an SPI
 * bus to a header, analog inputs (class ANALOG, F.Cu only) to two other headers, and a differential
 * pair TXP/TXN (class DIFF) crossing the whole board through the bus and the decoupling. In1.Cu is
 * a GND plane and In2.Cu a +3V3 plane, both {@code (type power)}.
 *
 * <p>The autorouter routes nets in the order of their numbers, so the pair is laid around whatever
 * the nets numbered before it took: its two halves come out 1.3 mm apart. With {@code
 * priority_net_classes} the pair and the analog inputs are routed right after the plane pads, and
 * the other nets may not rip them up.
 */
@Tag("slow")
class PriorityNetClassesRoutingTest extends RoutingFixtureTest {

  private static final String BOARD = "Priority-net-classes-adc-pair-4layer.dsn";

  private RoutingJob route(String[] priorityClasses) {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:02:00");
    settings.setMaxPasses(20);
    settings.setOptimizerEnabled(false);
    settings.setViaCosts(100);
    settings.setPlaneAsPriority(true);
    RoutingJob job = getRoutingJob(BOARD, settings);
    job.routerSettings.autorouter.priorityNetClasses = priorityClasses;
    runRoutingJob(job);
    return job;
  }

  private static double length(RoutingBoard board, String netName) {
    Net net = board.rules.nets.get(netName, 1);
    return board.getTraces().stream()
        .filter(t -> t.containsNet(net.netNumber))
        .mapToDouble(Trace::getLength)
        .sum();
  }

  private static double pairSkew(RoutingBoard board) {
    double p = length(board, "TXP");
    double n = length(board, "TXN");
    return Math.abs(p - n) / Math.max(p, n);
  }

  private static long viasOfClass(RoutingBoard board, String className) {
    return board.getVias().stream()
        .map(v -> board.rules.nets.get(v.getNetNumber(0)))
        .filter(net -> net != null && net.getNetClass().getName().equals(className))
        .count();
  }

  @Test
  void priorityClassesRouteThePairAndTheAnalogInputsFirst() {
    RoutingJob plain = route(null);
    assertRoutingResult(plain, BOARD).maxIncompleteConnections(0).check();
    assertTrue(
        pairSkew(plain.board) > 0.02,
        "Without priority the pair is laid around the other nets: " + pairSkew(plain.board));

    RoutingJob job = route(new String[] {"ANALOG", "DIFF"});
    assertRoutingResult(job, BOARD).maxIncompleteConnections(0).exactClearanceViolations(0).check();
    assertTrue(pairSkew(job.board) < 0.01, "TXP and TXN must match: " + pairSkew(job.board));
    assertEquals(0L, viasOfClass(job.board, "DIFF"), "The pair needs no via");
    assertEquals(0L, viasOfClass(job.board, "ANALOG"), "ANALOG is routed on F.Cu only");
    assertTrue(
        job.board.rules.netClasses.get("DIFF").isRoutedFirst
            && job.board.rules.netClasses.get("ANALOG").isRoutedFirst,
        "Both classes are marked as routed first");
  }
}
