package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #954: "Circular keepouts produce false clearance violations due to
 * octagonal approximation".
 *
 * <p>A 5.0 mm circular keepout centered at (10, -10) mm with a 0.56 mm protected via 3.075 mm away
 * at 22.5 degrees has a true physical clearance of 3.075 - 2.500 - 0.280 = 0.295 mm, which
 * satisfies a 0.200 mm clearance rule.
 *
 * <p>With coarse octagonal approximation (boundingOctagon), corner vertices overshoot the circle
 * radius by ~8.24% (~0.206 mm on a 5.0 mm hole), causing false clearance violations. Approximating
 * circular keepouts with a 64-gon bounds the radial error to <= 0.12% (~3 um) and eliminates false
 * positives, while still detecting genuine clearance violations (e.g. 0.145 mm clearance in
 * invalid-polygon64.dsn).
 */
class Issue954CircularKeepoutClearanceTest extends RoutingFixtureTest {

  @Override
  @BeforeEach
  protected void setUp() {
    super.setUp();
    System.setProperty("freerouting.logging.console.level", "INFO");
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;
  }

  private RoutingJob createInspectionJob(String fixtureName) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    return getRoutingJob(fixtureName, settings);
  }

  @Test
  @DisplayName("Issue #954: circle keepout with valid 0.295 mm clearance has 0 violations")
  void testCircleKeepoutReportsNoClearanceViolations() {
    RoutingJob job = createInspectionJob("Issue954-circle.dsn");
    job = runRoutingJob(job);

    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    int violationCount = drc.getAllClearanceViolations().size();

    assertEquals(
        0,
        violationCount,
        "Physically valid circle clearance (0.295 mm vs 0.200 mm rule) must report 0 violations");
  }

  @Test
  @DisplayName("Issue #954: polygon64 keepout baseline has 0 clearance violations")
  void testPolygon64KeepoutReportsNoClearanceViolations() {
    RoutingJob job = createInspectionJob("Issue954-polygon64.dsn");
    job = runRoutingJob(job);

    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    int violationCount = drc.getAllClearanceViolations().size();

    assertEquals(0, violationCount, "polygon64.dsn baseline must report 0 violations");
  }

  @Test
  @DisplayName("Issue #954: invalid polygon64 keepout (0.145 mm clearance) preserves 2 violations")
  void testInvalidPolygon64KeepoutPreservesClearanceViolations() {
    RoutingJob job = createInspectionJob("Issue954-invalid-polygon64.dsn");
    job = runRoutingJob(job);

    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    int violationCount = drc.getAllClearanceViolations().size();

    assertEquals(
        2,
        violationCount,
        "invalid-polygon64.dsn (true clearance 0.145 mm vs 0.200 mm rule) must report 2 violations");
  }

  @Test
  @DisplayName("Autorouting on board with circular keepouts routes cleanly")
  void testCorneyIslandWirelessAutoroute() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    RoutingJob job = getRoutingJob("Issue368-CorneyIslandWireless_input_design.dsn", settings);
    job = runRoutingJob(job);
    assertRoutingResult(job, "Issue368-CorneyIslandWireless_input_design.dsn")
        .exactClearanceViolations(0)
        .check();
  }
}
