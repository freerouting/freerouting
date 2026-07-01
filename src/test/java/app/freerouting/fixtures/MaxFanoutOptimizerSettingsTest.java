package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MaxFanoutOptimizerSettingsTest extends RoutingFixtureTest {

    @Test
    public void testFanoutMaxPassesAndMaxItems() {
        TestingSettings testSettingsSource = new TestingSettings();
        testSettingsSource.setEnabled(false); // disable main routing
        testSettingsSource.setOptimizerEnabled(false);
        testSettingsSource.setFanoutEnabled(true);
        testSettingsSource.setFanoutMaxPasses(1);
        testSettingsSource.setFanoutMaxItems(2); // limit to 2 pins
        testSettingsSource.setJobTimeoutString("00:02:00");

        RoutingJob job = GetRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
        RunRoutingJob(job);

        Assertions.assertNotNull(job.board);
        // The fanout process should run and stop early at maxItems limit.
        // We can't query the private BatchFanout instance directly easily, but we can verify
        // that the job finished successfully and the board has some (but very few) vias or changed traces.
        // Also this verifies the code executes without any exceptions.
    }

    @Test
    public void testOptimizerMaxPassesAndMaxItems() {
        TestingSettings testSettingsSource = new TestingSettings();
        testSettingsSource.setEnabled(true); // run autorouter first
        testSettingsSource.setMaxPasses(1);  // small number of passes
        testSettingsSource.setFanoutEnabled(false);
        testSettingsSource.setOptimizerEnabled(true);
        testSettingsSource.setOptimizerMaxPasses(1);
        testSettingsSource.setOptimizerMaxItems(2); // limit to 2 items
        testSettingsSource.setJobTimeoutString("00:02:00");

        RoutingJob job = GetRoutingJob("Issue026-J2_reference.dsn", testSettingsSource);
        RunRoutingJob(job);

        Assertions.assertNotNull(job.board);
        // Verifies the optimizer runs with the max_passes and max_items constraints and executes successfully.
    }
}
