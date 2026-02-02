package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

public class Issue508ReproductionTest extends TestBasedOnAnIssue {

    @Test
    public void testIssue508_BM01_first_2_nets() {
        // Set console logging level to TRACE for detailed output
        System.setProperty("freerouting.logging.console.level", "TRACE");

        // Enable detailed logging and filter to Net #98
        Freerouting.globalSettings.debugSettings.enableDetailedLogging = true;
        Freerouting.globalSettings.debugSettings.filterByNet.add("98");

        // Get the job
        RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn");

        // Configure settings
        RouterSettings testSettings = job.routerSettings;
        testSettings.maxPasses = 1;
        testSettings.maxItems = 2;
        testSettings.jobTimeoutString = "00:00:15"; // 15 seconds timeout

        RunRoutingJob(job, testSettings);

        assertTrue(job.board.get_statistics().connections.incompleteCount == 104,
                "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 104 incomplete connections with the target of 2 items to route.");
    }
}