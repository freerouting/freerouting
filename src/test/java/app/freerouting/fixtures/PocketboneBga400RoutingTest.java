package app.freerouting.fixtures;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

class PocketboneBga400RoutingTest extends RoutingFixtureTest {

  @Test
  void testPocketboneBga400RoutingWithGpuAcceleration() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(10);
    testSettingsSource.setJobTimeoutString("00:01:30");

    RoutingJob job = getRoutingJob("PocketBone-BGA400.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "PocketBone-BGA400.dsn").exactClearanceViolations(0).check();
  }
}
