package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ComponentOutline;
import app.freerouting.board.model.items.Item;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #957: KiCad DSN with component outlines and copper planes completes
 * board snapshots, hashing, and auto-routing cleanly.
 */
class Issue957SmokeRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue957-smoke-ldo-led.dsn";

  @Test
  @DisplayName(
      "KiCad 10 board with component outlines serializes, hashes, clones, and routes cleanly")
  void boardWithComponentOutlinesRoutesAndSerializesCleanly() {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;

    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(2);
    testSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, testSettings);
    assertNotNull(job, "RoutingJob must load successfully from fixture");

    // Run the routing job (initializes board, takes snapshots, runs routing loop)
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state, "Routing job must complete successfully");
    assertNotNull(job.board, "Job board must be initialized after routing");

    // Verify board has ComponentOutline items
    boolean foundComponentOutline = false;
    for (Item item : job.board.getItems()) {
      if (item instanceof ComponentOutline) {
        foundComponentOutline = true;
        break;
      }
    }
    assertTrue(foundComponentOutline, "Fixture must contain ComponentOutline items");

    // Verify snapshot hashing and serialization (which failed in GraalVM native CLI under Issue
    // #957)
    String hash = job.board.getHash();
    assertNotNull(hash, "Board hash must be computed successfully");
    assertFalse(hash.isBlank(), "Board hash must not be blank");

    BasicBoard clonedBoard = job.board.clone();
    assertNotNull(clonedBoard, "Board must clone successfully via serialization");
    assertEquals(hash, clonedBoard.getHash(), "Cloned board must have identical hash");
  }
}
