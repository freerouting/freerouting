package app.freerouting.management.jobs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoutingJobOutputFailureTest {
  @BeforeEach
  void setUp() {
    GlobalSettings.setCurrent(new GlobalSettings());
    FRAnalytics.setEnabled(false);
  }

  @Test
  @app.freerouting.logger.AllowErrorLogs("Exercises intentional routing output export failure")
  void unavailableBoardInvalidatesEarlierPreview() throws Exception {
    RoutingJob job = new RoutingJob();
    job.state = RoutingJobState.RUNNING;
    job.output = new BoardFileDetails();
    job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));
    job.output.format = FileFormat.SES;
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    Method export =
        RoutingJobSchedulerActionThread.class.getDeclaredMethod("setJobOutput", RoutingJob.class);
    export.setAccessible(true);
    export.invoke(action, job);
    assertNull(job.output, "Failed export must not leave an earlier preview downloadable");
  }

  @ParameterizedTest
  @EnumSource(
      value = FileFormat.class,
      names = {"SES", "SCR", "KICAD_SESSION_JSON"})
  void successfulExportReplacesEarlierPreview(FileFormat format) throws Exception {
    RoutingJob job = new RoutingJob();
    job.name = "output-regression.dsn";
    job.board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");
    job.output = new BoardFileDetails();
    job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));
    job.output.format = format;
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    Method export =
        RoutingJobSchedulerActionThread.class.getDeclaredMethod("setJobOutput", RoutingJob.class);
    export.setAccessible(true);
    assertTrue((boolean) export.invoke(action, job));
    assertNotEquals(
        "earlier-preview", new String(job.output.getData().readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  @app.freerouting.logger.AllowErrorLogs("Exercises an unsupported output format")
  void unsupportedOutputInvalidatesEarlierPreview() throws Exception {
    RoutingJob job = new RoutingJob();
    job.name = "output-regression.dsn";
    job.board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");
    job.output = new BoardFileDetails();
    job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));
    job.output.format = FileFormat.DSN;
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    Method export =
        RoutingJobSchedulerActionThread.class.getDeclaredMethod("setJobOutput", RoutingJob.class);
    export.setAccessible(true);
    assertFalse((boolean) export.invoke(action, job));
    assertNull(job.output);
  }
}
