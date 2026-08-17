package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.logger.AllowErrorLogs;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntry;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Regression test for Issue #632's malformed multi-board DSN export. */
class Issue632RoutingFailureTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue632-MiniAutoPilot/Mini Auto Pilot.dsn";

  @Test
  @AllowErrorLogs("The malformed fixture is expected to produce a load-time parse error.")
  void issue632IsRejectedBeforeAutorouting() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(1);
    settings.setJobTimeoutString("00:00:10");

    RoutingJob job = getRoutingJob(FIXTURE, settings);
    RoutingJob completed = runRoutingJob(job);

    assertEquals(RoutingJobState.TERMINATED, completed.state);
    assertNull(completed.board);

    String jobMessages =
        Arrays.stream(FRLogger.getLogEntries().getEntries(null, completed.id))
            .map(LogEntry::getMessage)
            .collect(Collectors.joining("\n"));
    assertTrue(jobMessages.contains("348 of 378 placed pins"));
    assertFalse(jobMessages.contains("Failed to set up routing job"));
  }
}
