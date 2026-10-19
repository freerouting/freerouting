package app.freerouting.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.GlobalSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CliOutputLifecycleTest {
  @ParameterizedTest
  @EnumSource(RoutingJobState.class)
  void waitingEndsForEveryTerminalState(RoutingJobState state) {
    boolean terminal =
        switch (state) {
          case INVALID, COMPLETED, TIMED_OUT, CANCELLED, TERMINATED -> true;
          default -> false;
        };
    assertEquals(terminal, CliRunner.isCliTerminalState(state));
  }

  @Test
  void emptyOutputDoesNotOverwriteExistingFile(@TempDir Path directory) throws Exception {
    Path destination = directory.resolve("result.ses");
    Files.writeString(destination, "previous-user-file");
    GlobalSettings settings = new GlobalSettings();
    settings.initialOutputFile = destination.toString();
    RoutingJob job = new RoutingJob();
    job.state = RoutingJobState.TIMED_OUT;
    job.output = new BoardFileDetails();
    assertFalse(CliRunner.writeCliOutputIfAvailable(settings, job));
    assertEquals("previous-user-file", Files.readString(destination));
  }
}
