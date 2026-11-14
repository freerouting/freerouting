package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * {@code Cross-class-clearance-garland.dsn}: the default clearance is 300 um, net classes CA and CB
 * are both 150 um. Between two net classes, KiCad applies the larger of the two clearances: 150 um
 * between CA and CB, 300 um between CA and the default class.
 *
 * <p>The DSN reader gave each new net class, against every class already read, the larger of its
 * own clearance and the value it copied from the default class. Between CA and CB that is the
 * default 300 um, not 150 um: the router kept a gap that KiCad does not ask for.
 *
 * <p>Each half of the board is barred by a wall of two pads with a 600 um opening, routed with a
 * 200 um trace. Net B (class CB) has to pass between two pads of class CA: 200 + 2 x 150 = 500 um
 * fits. Net D (class CA) has to pass between two pads of the default class: 200 + 2 x 300 = 800 um
 * does not, and D stays unrouted.
 */
class CrossClassClearanceTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Cross-class-clearance-garland.dsn";

  private static TestingSettings settings(boolean route) {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(5);
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(route);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static long tracesOf(RoutingBoard board, String netName) {
    long count = 0;
    for (Net net : board.rules.nets.get(netName)) {
      count += board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
    }
    return count;
  }

  @Test
  void twoNetClassesUseTheLargerOfTheirOwnClearances() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    ClearanceMatrix matrix = job.board.rules.clearanceMatrix;
    int ca = matrix.getNo("CA");
    int cb = matrix.getNo("CB");
    int defaultClass = job.board.rules.getDefaultNetClass().getTraceClearanceClass();
    assertTrue(ca > 0 && cb > 0, "classes CA and CB must have their own clearance class");

    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      int caOwn = matrix.getValue(ca, ca, layer, false);
      int defaultOwn = matrix.getValue(defaultClass, defaultClass, layer, false);
      assertTrue(caOwn < defaultOwn, "the fixture needs CA tighter than the default class");
      assertEquals(caOwn, matrix.getValue(cb, cb, layer, false), "CA and CB have the same value");
      assertEquals(caOwn, matrix.getValue(ca, cb, layer, false), "CA against CB");
      assertEquals(caOwn, matrix.getValue(cb, ca, layer, false), "CB against CA");
      assertEquals(defaultOwn, matrix.getValue(ca, defaultClass, layer, false), "CA vs default");
      assertEquals(defaultOwn, matrix.getValue(cb, defaultClass, layer, false), "CB vs default");
    }
  }

  @Test
  void aNetPassesBetweenPadsOfAnotherTighterClassButNotOfTheDefaultClass() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(true)));
    RoutingBoard board = job.board;

    assertTrue(tracesOf(board, "B") > 0, "net B (CB) must pass the 600 um opening between CA pads");
    assertEquals(
        0, tracesOf(board, "D"), "net D (CA) must not pass the opening between default pads");
  }
}
