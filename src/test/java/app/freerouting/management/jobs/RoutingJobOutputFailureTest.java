package app.freerouting.management.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    RoutingJobOutputWriter.write(job);
    assertEquals(0, job.output.getData().available(), "Failed export must discard stale bytes");
  }

  @ParameterizedTest
  @EnumSource(
      value = FileFormat.class,
      names = {"SES", "SCR", "KICAD_SESSION_JSON"})
  @app.freerouting.logger.AllowErrorLogs("Exercises a transient output export failure")
  void failedExportPreservesDestinationAndRecovers(FileFormat format, @TempDir Path directory)
      throws Exception {
    RoutingJob job = new RoutingJob();
    job.name = "output-regression.dsn";
    job.state = RoutingJobState.RUNNING;
    job.output = new BoardFileDetails();
    job.output.format = format;
    String extension =
        format == FileFormat.KICAD_SESSION_JSON
            ? "json"
            : format.name().toLowerCase(java.util.Locale.ROOT);
    String destination = directory.resolve("requested." + extension).toString();
    job.output.setFilename(destination);
    job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));
    BoardFileDetails requestedOutput = job.output;
    AtomicInteger publishedSnapshots = new AtomicInteger();
    requestedOutput.addUpdatedEventListener(event -> publishedSnapshots.incrementAndGet());

    assertFalse(RoutingJobOutputWriter.write(job));
    assertSame(requestedOutput, job.output);
    assertEquals(destination, job.output.getAbsolutePath());
    assertEquals(format, job.output.format);
    assertEquals(0, job.output.getData().available());
    assertEquals(0, job.output.size);
    assertEquals(0, job.output.crc32);
    assertEquals(0, publishedSnapshots.get(), "Failure must not publish an empty output file");

    job.board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");
    assertTrue(RoutingJobOutputWriter.write(job));
    assertSame(requestedOutput, job.output);
    assertEquals(destination, job.output.getAbsolutePath());
    assertEquals(format, job.output.format);
    assertTrue(job.output.getData().available() > 0);
    assertEquals(1, publishedSnapshots.get());
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
    assertTrue(RoutingJobOutputWriter.write(job));
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
    assertFalse(RoutingJobOutputWriter.write(job));
    assertEquals(0, job.output.getData().available());
  }

  @Test
  void invalidatedOutputRemovesPersistedPreviewFile(@TempDir Path tempDir) throws Exception {
    GlobalSettings previousSettings = Freerouting.globalSettings;
    try {
      GlobalSettings.resetForTesting();
      GlobalSettings.setUserDataPath(tempDir);
      GlobalSettings settings = new GlobalSettings();
      settings.featureFlags.saveJobs = true;
      GlobalSettings.setCurrent(settings);
      Freerouting.globalSettings = settings;

      Session session = SessionManager.getInstance().createSession(UUID.randomUUID(), "Test/1.0");
      RoutingJob job = new RoutingJob();
      job.sessionId = session.id;
      job.output = new BoardFileDetails();
      job.output.setFilename("preview.ses");
      job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));

      RoutingJobScheduler scheduler = RoutingJobScheduler.getInstance();
      scheduler.saveJob(job);

      try (var paths = Files.walk(tempDir.resolve("data"))) {
        assertTrue(
            paths.anyMatch(p -> p.getFileName().toString().equals("preview.ses")),
            "Intermediate preview must be persisted when available");
      }

      job.output.invalidateData();
      scheduler.saveJob(job);

      try (var paths = Files.walk(tempDir.resolve("data"))) {
        assertFalse(
            paths.anyMatch(p -> p.getFileName().toString().equals("preview.ses")),
            "Invalidated output must remove previously persisted preview file");
      }
    } finally {
      GlobalSettings.resetForTesting();
      Freerouting.globalSettings = previousSettings;
      GlobalSettings.setCurrent(previousSettings != null ? previousSettings : new GlobalSettings());
    }
  }
}
