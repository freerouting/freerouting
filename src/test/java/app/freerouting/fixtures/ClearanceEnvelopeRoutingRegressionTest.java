package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * End-to-end regression tests verifying that the unified search/locator clearance envelopes and
 * width-safe target validations prevent clearance violations and route cleanly.
 */
class ClearanceEnvelopeRoutingRegressionTest extends RoutingFixtureTest {

  @Test
  void clearanceEnvelopeBm01Routing() {
    var testingSettings = new TestingSettings();
    testingSettings.setMaxItems(2);
    testingSettings.setMaxPasses(1);
    testingSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);
    assertNotNull(job, "Routing job must be loaded");

    job = runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state, "Job must complete successfully");
    int drcViolations = new DesignRulesChecker(job.board, null).getAllClearanceViolations().size();
    assertEquals(0, drcViolations, "Routing must produce 0 clearance violations");
  }

  @Test
  void clearanceEnvelopeBm01NeckdownRouting() {
    var testingSettings = new TestingSettings();
    testingSettings.setMaxItems(2);
    testingSettings.setMaxPasses(1);
    testingSettings.setJobTimeoutString("00:01:00");
    testingSettings.setNeckWidthUm(100.0);

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);
    assertNotNull(job, "Routing job must be loaded");

    job = runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state, "Job must complete successfully");
    int drcViolations = new DesignRulesChecker(job.board, null).getAllClearanceViolations().size();
    assertEquals(0, drcViolations, "Neckdown routing must produce 0 clearance violations");
  }

  @Test
  void clearanceEnvelopeBm01ExtendedRouting() {
    var testingSettings = new TestingSettings();
    testingSettings.setMaxItems(6);
    testingSettings.setMaxPasses(1);
    testingSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);
    assertNotNull(job, "Routing job must be loaded");

    job = runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state, "Job must complete successfully");
    int drcViolations = new DesignRulesChecker(job.board, null).getAllClearanceViolations().size();
    assertEquals(0, drcViolations, "Extended routing must produce 0 clearance violations");
  }
}
