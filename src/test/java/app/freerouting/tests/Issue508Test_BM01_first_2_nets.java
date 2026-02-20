package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Issue508Test_BM01_first_2_nets extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void testIssue508_BM01_first_2_nets_only() {
    // Set console logging level to TRACE for detailed output
    System.setProperty("freerouting.logging.console.level", "TRACE");
    FRLogger.granularTraceEnabled = true;

    // Enable detailed logging and filter to Net #98 and #99
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = true;
//    Freerouting.globalSettings.debugSettings.filterByNet.add("98");
//    Freerouting.globalSettings.debugSettings.filterByNet.add("99");

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
    assertTrue(GetBoardStatistics(job).connections.incompleteCount == 193,
        "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 193 incomplete connections with the target of 2 items to route.");
  }
}