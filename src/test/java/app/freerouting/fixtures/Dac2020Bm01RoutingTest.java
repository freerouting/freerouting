package app.freerouting.fixtures;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Dac2020Bm01RoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  @Test
  public void testIssue508_BM01_first_2_nets_only() {
    // Set console logging level to INFO for quick test
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;

    // Disable detailed logging
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:30");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    // There are 195 connections in total on the board
    // If both net (99 and 98) are routed, there should be 193 incomplete connections left
    // If only net 99 is routed, there should be 194 incomplete connections left
    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(194)
        .check();
  }
}