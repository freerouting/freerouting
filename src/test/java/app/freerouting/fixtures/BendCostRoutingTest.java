package app.freerouting.fixtures;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
public class BendCostRoutingTest extends RoutingFixtureTest {

  @Test
  public void testRoutingWithBendCosts() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(2);
    testSettingsSource.setMaxItems(5);
    testSettingsSource.setJobTimeoutString("00:00:30");

    // Injects bend cost parameter for layer 0 (or default bend cost)
    testSettingsSource.setDefaultBendCost(5.0);

    RoutingJob job = GetRoutingJob("Issue026-J2_reference.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue026-J2_reference.dsn")
        .check();
  }
}
