package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * {@code Plane-island-pad-without-via.dsn}: a GND plane on F.Cu cut in two islands by a trace of
 * another net, and a whole GND plane on B.Cu. GND pad J1 is alone on the left island; J2 and a via
 * to the B.Cu plane are on the right one. The connected set of J1 holds the F.Cu plane, and so does
 * the connected set of the via: the plane belongs to the group of whichever item {@link
 * NetIncompletes} starts from first. When the start followed the iteration order of a {@code
 * HashSet}, that is of the identity hash codes, the airline of the same board went from J1 to a
 * plane corner on one load and between the corners of the two planes on another, and the router,
 * which rips up around these airlines, routed the same board differently from one run to the next.
 */
class NetIncompletesOrderTest extends RoutingFixtureTest {

  /**
   * Each load gives new items and so new identity hash codes. The plane goes to the J1 group in
   * about one load out of four or five: the chance that a hash-ordered start gives the same airline
   * on all loads is below one in ten thousand.
   */
  private static final int LOADS = 60;

  private static Pin pin(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow();
  }

  private static String describe(Item item, FloatPoint corner) {
    return item.getClass().getSimpleName()
        + "("
        + Math.round(corner.x)
        + ","
        + Math.round(corner.y)
        + ")";
  }

  private static String airlines(NetIncompletes incompletes) {
    List<String> lines = new ArrayList<>();
    for (AirLine line : incompletes.getIncompletes()) {
      String from = describe(line.fromItem, line.fromCorner);
      String to = describe(line.toItem, line.toCorner);
      lines.add(from.compareTo(to) < 0 ? from + "-" + to : to + "-" + from);
    }
    lines.sort(null);
    return String.join(" ", lines);
  }

  @Test
  void airlinesOnFracturedPlaneAreSameOnEveryLoad() {
    Set<String> seen = new TreeSet<>();
    for (int i = 0; i < LOADS; i++) {
      TestingSettings loadOnly = new TestingSettings();
      loadOnly.setJobTimeoutString("00:01:00");
      loadOnly.setFanoutEnabled(false);
      loadOnly.setRouterEnabled(false);
      loadOnly.setOptimizerEnabled(false);
      RoutingJob job = getRoutingJob("Plane-island-pad-without-via.dsn", loadOnly);
      runRoutingJob(job);
      RoutingBoard board = job.board;

      Pin j1 = pin(board, "J1");
      int gnd = j1.getNetNumber(0);
      Via via = board.getVias().iterator().next();
      ConductionArea front =
          board.getConductionAreas().stream()
              .filter(a -> a.getLayer() == j1.firstLayer())
              .findFirst()
              .orElseThrow();
      assertTrue(front.isFractured(), "the SIG trace is expected to cut the F.Cu plane in two");
      Set<Item> fromJ1 = j1.getConnectedSet(gnd);
      Set<Item> fromVia = via.getConnectedSet(gnd);
      assertTrue(
          fromJ1.contains(front) && fromVia.contains(front) && !fromJ1.contains(via),
          "J1 and the via are expected on two islands of the F.Cu plane");

      NetIncompletes incompletes = new NetIncompletes(gnd, board.getConnectableItems(gnd), board);
      assertEquals(1, incompletes.count(), "J1 is not connected to the rest of GND");
      seen.add(airlines(incompletes));
    }
    assertFalse(seen.isEmpty());
    assertEquals(1, seen.size(), "GND airlines over " + LOADS + " loads: " + seen);
  }
}
