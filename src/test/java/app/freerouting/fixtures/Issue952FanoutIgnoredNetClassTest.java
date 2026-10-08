package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #952: Net class setting "Ignored by autorouter" must be respected by
 * the fanout stage. When a net class is marked as ignored, BatchFanout must not create escape
 * traces or vias on SMD pins belonging to that net class.
 */
class Issue952FanoutIgnoredNetClassTest extends RoutingFixtureTest {

  @Test
  void fanoutOnlyStageSkipsIgnoredNetClass() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setEnabled(false);
    testSettingsSource.setOptimizerEnabled(false);
    testSettingsSource.setFanoutEnabled(true);
    testSettingsSource.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {"Power,Default"};

    runRoutingJob(job);
    assertNotNull(job.board);

    Collection<Net> nets3v3 = job.board.rules.nets.get("+3V3");
    Collection<Net> nets5v = job.board.rules.nets.get("+5V");
    assertNotNull(nets3v3);
    assertNotNull(nets5v);
    assertTrue(!nets3v3.isEmpty());
    assertTrue(!nets5v.isEmpty());

    for (Net net : nets3v3) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(0, traces, "Ignored net +3V3 must not have any fanout traces created");
      assertEquals(0, vias, "Ignored net +3V3 must not have any fanout vias created");
    }

    for (Net net : nets5v) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(0, traces, "Ignored net +5V must not have any fanout traces created");
      assertEquals(0, vias, "Ignored net +5V must not have any fanout vias created");
    }

    // Non-ignored net class (kicad_default) should still have fanout vias created
    long totalVias = job.board.getVias().size();
    assertTrue(totalVias > 0, "Non-ignored nets should have fanout vias created");
  }

  @Test
  void fullAutorouteWithFanoutSkipsIgnoredNetClass() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setEnabled(true);
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setOptimizerEnabled(false);
    testSettingsSource.setFanoutEnabled(true);
    testSettingsSource.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {"Power,Default"};

    runRoutingJob(job);
    assertNotNull(job.board);

    Collection<Net> nets3v3 = job.board.rules.nets.get("+3V3");
    Collection<Net> nets5v = job.board.rules.nets.get("+5V");
    assertNotNull(nets3v3);
    assertNotNull(nets5v);
    assertTrue(!nets3v3.isEmpty());
    assertTrue(!nets5v.isEmpty());

    for (Net net : nets3v3) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(0, traces, "Ignored net +3V3 must not have any traces after autoroute + fanout");
      assertEquals(0, vias, "Ignored net +3V3 must not have any vias after autoroute + fanout");
    }

    for (Net net : nets5v) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(0, traces, "Ignored net +5V must not have any traces after autoroute + fanout");
      assertEquals(0, vias, "Ignored net +5V must not have any vias after autoroute + fanout");
    }

    // Confirm that other nets were actually routed
    long totalTraces = job.board.getTraces().size();
    assertTrue(totalTraces > 0, "Non-ignored nets should have traces routed");
  }

  @Test
  void allNetClassesIgnoredResultsInZeroFanoutViasAndTraces() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setEnabled(false);
    testSettingsSource.setOptimizerEnabled(false);
    testSettingsSource.setFanoutEnabled(true);
    testSettingsSource.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
    job.routerSettings.autorouter.ignoreNetClasses =
        new String[] {"default", "kicad_default", "Power,Default"};

    runRoutingJob(job);
    assertNotNull(job.board);

    assertEquals(
        0,
        job.board.getVias().size(),
        "When all net classes are ignored, fanout must not create any vias");
    assertEquals(
        0,
        job.board.getTraces().size(),
        "When all net classes are ignored, fanout must not create any traces");
  }

  @Test
  void fullPipelineWithOptimizerSkipsIgnoredNetClass() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setEnabled(true);
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setOptimizerEnabled(true);
    testSettingsSource.setOptimizerMaxPasses(1);
    testSettingsSource.setFanoutEnabled(true);
    testSettingsSource.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {"Power,Default"};

    runRoutingJob(job);
    assertNotNull(job.board);

    Collection<Net> nets3v3 = job.board.rules.nets.get("+3V3");
    Collection<Net> nets5v = job.board.rules.nets.get("+5V");
    assertNotNull(nets3v3);
    assertNotNull(nets5v);

    for (Net net : nets3v3) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(
          0,
          traces,
          "Ignored net +3V3 must not have any traces after full pipeline with optimizer");
      assertEquals(
          0, vias, "Ignored net +3V3 must not have any vias after full pipeline with optimizer");
    }

    for (Net net : nets5v) {
      long traces =
          job.board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
      long vias = job.board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      assertEquals(
          0, traces, "Ignored net +5V must not have any traces after full pipeline with optimizer");
      assertEquals(
          0, vias, "Ignored net +5V must not have any vias after full pipeline with optimizer");
    }

    // Confirm that other nets were actually routed
    long totalTraces = job.board.getTraces().size();
    assertTrue(totalTraces > 0, "Non-ignored nets should have traces routed");
  }
}
