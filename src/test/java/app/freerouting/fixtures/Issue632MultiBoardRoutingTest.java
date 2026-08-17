package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.logger.AllowErrorLogs;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

class Issue632MultiBoardRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue632-MiniAutoPilot/Mini Auto Pilot.dsn";

  @Test
  void rejectsPinsOutsideAllPcbBoundaryPaths() {
    BoardReadResult result = DsnReader.readBoard(DsnTestFixtures.openResource(FIXTURE), null, null);

    BoardReadResult.InvalidGeometry invalidGeometry =
        assertInstanceOf(BoardReadResult.InvalidGeometry.class, result);
    assertEquals("(placement)", invalidGeometry.location());
    assertTrue(invalidGeometry.detail().contains("348 pin(s)"));
    assertTrue(invalidGeometry.detail().contains("outside all PCB boundary paths"));
  }

  @Test
  @AllowErrorLogs(
      "The test intentionally loads the invalid Issue #632 fixture to verify rejection.")
  void schedulerDoesNotStartRoutingForInvalidBoardGeometry() throws InterruptedException {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, testingSettings);
    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;
    for (int i = 0; i < 100 && job.state != RoutingJobState.INVALID; i++) {
      Thread.sleep(50);
    }

    assertEquals(RoutingJobState.INVALID, job.state);
    assertNull(job.board);
    assertNull(job.thread);
    assertEquals(0, job.getCurrentPass());
  }
}
