package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Discussion #1000: Verifies that on an oblong 2-layer PCB (such as IMSAI CP-A
 * Rev 5), user settings (via cost = 1 and inverted preferred directions: Layer 0 vertical, Layer 1
 * horizontal) allow the autorouter to place vias and route connections cleanly without introducing
 * clearance violations.
 */
class Issue1000ReproductionTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue1000-IMSAI_CP-A_Rev-5.dsn";

  @Test
  void testOblongTwoLayerBoardPlacesViasAndRoutesCleanly() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(1);
    testSettings.setMaxItems(50);
    testSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    assertNotNull(job, "RoutingJob must load successfully from fixture");

    // Turn off fanout to verify that autorouting itself places normal routing vias
    job.routerSettings.setFanoutEnabled(false);
    job.routerSettings.autorouter.enabled = true;

    // User settings: viaCosts = 1, Layer 0 vertical, Layer 1 horizontal
    job.routerSettings.setViaCosts(1);
    job.routerSettings.setPreferredDirectionIsHorizontal(0, false);
    job.routerSettings.setPreferredDirectionIsHorizontal(1, true);

    runRoutingJob(job);

    assertNotNull(job.board, "Job board must be initialized after routing");
    RoutingBoard board = job.board;

    // The raw DSN file has 36 pre-existing unfixable clearance violations in the imported design.
    // Full DRC verification: ensure router introduced zero new clearance violations.
    var drc = new DesignRulesChecker(board, null);
    var violations = drc.getAllClearanceViolations();
    assertEquals(
        36,
        violations.size(),
        "Router must not introduce any new clearance violations beyond the 36 pre-existing violations");

    // Verify the autorouter placed new routing vias (initial board had 13 vias)
    long totalVias = board.getItems().stream().filter(item -> item instanceof Via).count();
    assertTrue(
        totalVias > 13,
        "Autorouter must place routing vias on the board (expected > 13, found " + totalVias + ")");
  }
}
