package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class Issue508ReproductionTest extends TestBasedOnAnIssue {

    @Test
    public void testIssue508_BM01_first_2_nets() {
        // Set console logging level to TRACE for detailed output
        System.setProperty("freerouting.logging.console.level", "TRACE");

        // Enable detailed logging and filter to Net #98
        Freerouting.globalSettings.debugSettings.enableDetailedLogging = true;
        Freerouting.globalSettings.debugSettings.filterByNet.add("98");

        // Create testing settings
        TestingSettings testSettingsSource = new TestingSettings();
        testSettingsSource.setMaxPasses(1);
        testSettingsSource.setMaxItems(2);
        testSettingsSource.setJobTimeoutString("00:00:15"); // 15 seconds timeout

        // Get the job with injected settings
        RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

        RunRoutingJob(job);

        assertTrue(GetBoardStatistics(job).connections.incompleteCount == 193,
                "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 193 incomplete connections with the target of 2 items to route.");
    }
}