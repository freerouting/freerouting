package app.freerouting.tests;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

public class TestBasedOnAnIssue {

  protected RoutingJobScheduler scheduler;

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    scheduler = RoutingJobScheduler.getInstance();
  }

  protected RoutingJob GetRoutingJob(String filename) {
    return GetRoutingJob(filename, null);
  }

  protected RoutingJob GetRoutingJob(String filename, TestingSettings testingSettings) {
    // Create a new session
    UUID sessionId = UUID.randomUUID();
    Session session = SessionManager
        .getInstance()
        .createSession(sessionId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

    // Create a new job
    RoutingJob job = new RoutingJob(session.id);

    // Look for the file in the current directory and its parent directories
    Path testDirectory = Path
        .of(".")
        .toAbsolutePath();
    File testFile = Path
        .of(testDirectory.toString(), "tests", filename)
        .toFile();
    while (!testFile.exists()) {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null) {
        break;
      }

      testFile = Path
          .of(testDirectory.toString(), "tests", filename)
          .toFile();
      if (testFile == null) {
        break;
      }
    }

    // Load the file as input
    try {
      job.setInput(testFile);

      var statsBefore = new BoardStatistics(job.input
          .getData()
          .readAllBytes(), job.input.format);

      SettingsMerger merger = new SettingsMerger(new DefaultSettings(),
          new DsnFileSettings(job.input.getData(), job.input.getFilename()));

      if (testingSettings == null) {
        testingSettings = new TestingSettings();
      }

      testingSettings.setJobTimeoutString("00:01:00");
      testingSettings.setMaxPasses(100);
      merger.addOrReplaceSources(testingSettings);

      job.routerSettings = merger.merge();

    } catch (IOException e) {
      throw new RuntimeException(testFile + " not found.", e);
    }

    return job;
  }

  protected RoutingJob RunRoutingJob(RoutingJob job, RouterSettings settings) {
    if (job == null) {
      throw new IllegalArgumentException("The job cannot be null.");
    }

    job.routerSettings = settings;
    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    long startTime = System.currentTimeMillis();
    long timeoutInMillis = TextManager.parseTimespanString(settings.jobTimeoutString) * 1000;

    while ((job.state != RoutingJobState.COMPLETED) && (job.state != RoutingJobState.CANCELLED)
        && (job.state != RoutingJobState.TERMINATED)) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // Check for timeout every iteration
      if (System.currentTimeMillis() - startTime > timeoutInMillis) {
        float timeoutInMinutes = timeoutInMillis / 60000.0f;
        throw new RuntimeException("Routing job timed out after " + timeoutInMinutes + " minutes.");
      }
    }

    return job;
  }

  protected BoardStatistics GetBoardStatistics(RoutingJob job) {
    if ((job == null) || (job.board == null)) {
      throw new IllegalArgumentException("The job or its board cannot be null.");
    }

    return new BoardStatistics(job.board);
  }
}