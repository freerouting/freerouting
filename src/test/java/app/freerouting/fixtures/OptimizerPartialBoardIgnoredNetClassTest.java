package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Routing and optimizing a board that stays partially routed must leave the nets of an ignored net
 * class unrouted. Net SKIP (class IGNORED) is easy to route; net WALLED cannot reach one of its
 * pins, and the optimizer runs with its pre-flight guards off so that it runs on master as well.
 */
class OptimizerPartialBoardIgnoredNetClassTest extends RoutingFixtureTest {

  private static final String DSN = "Optimizer-partial-board-ignored-net-class.dsn";

  @Test
  void pipelineLeavesIgnoredNetsUnroutedOnPartiallyRoutedBoard() {
    TestingSettings settings = new TestingSettings();
    settings.setOptimizerEnabled(true);
    settings.setOptimizerEnablePreflightGuards(false);
    RoutingJob job = getRoutingJob(DSN, settings);
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {"IGNORED"};

    runRoutingJob(job);

    Net skip = job.board.rules.nets.get("SKIP").iterator().next();
    assertEquals(
        0,
        job.board.getTraces().stream().filter(t -> t.containsNet(skip.netNumber)).count(),
        "the ignored net SKIP must not get traces");
    assertEquals(
        0,
        job.board.getVias().stream().filter(v -> v.containsNet(skip.netNumber)).count(),
        "the ignored net SKIP must not get vias");
    assertEquals(
        2, job.board.getStatistics().connections.incompleteCount, "SKIP and W2 must stay unrouted");
  }
}
