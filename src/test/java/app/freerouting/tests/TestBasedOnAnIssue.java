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
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class TestBasedOnAnIssue
{
  protected RoutingJobScheduler scheduler;
  protected RouterSettings settings;

  @BeforeEach
  protected void setUp()
  {
    Freerouting.globalSettings = new GlobalSettings();
    settings = Freerouting.globalSettings.routerSettings;
    scheduler = RoutingJobScheduler.getInstance();
  }

  protected RoutingJob GetRoutingJob(String filename)
  {
    RoutingJob job = GetRoutingJob(filename, null);
    job.routerSettings.jobTimeoutString = "00:05:00";
    return job;
  }

  protected RoutingJob GetRoutingJob(String filename, Long seed)
  {
    // Create a new session
    UUID sessionId = UUID.randomUUID();
    Session session = SessionManager
        .getInstance()
        .createSession(sessionId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

    // Create a new job
    RoutingJob job = new RoutingJob(session.id);
    if (seed != null)
    {
      job.routerSettings.random_seed = seed;
    }

    // Look for the file in the current directory and its parent directories
    Path testDirectory = Path
        .of(".")
        .toAbsolutePath();
    File testFile = Path
        .of(testDirectory.toString(), "tests", filename)
        .toFile();
    while (!testFile.exists())
    {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null)
      {
        break;
      }

      testFile = Path
          .of(testDirectory.toString(), "tests", filename)
          .toFile();
      if (testFile == null)
      {
        break;
      }
    }

    // Load the file as input
    try
    {
      job.setInput(testFile);

      var statsBefore = new BoardStatistics(job.input
          .getData()
          .readAllBytes(), job.input.format);
      var jobSettings = new RouterSettings(statsBefore.layers.totalCount);
      job.routerSettings.applyNewValuesFrom(jobSettings);
    } catch (IOException e)
    {
      throw new RuntimeException(testFile + " not found.", e);
    }

    return job;
  }

  protected RoutingJob RunRoutingJob(RoutingJob job, RouterSettings settings)
  {
    if (job == null)
    {
      throw new IllegalArgumentException("The job cannot be null.");
    }

    job.routerSettings = settings;
    job.routerSettings.set_stop_pass_no(job.routerSettings.get_start_pass_no() + job.routerSettings.maxPasses - 1);
    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    long startTime = System.currentTimeMillis();
    long timeoutInMillis = TextManager.parseTimespanString(settings.jobTimeoutString) * 1000;

    while ((job.state != RoutingJobState.COMPLETED) && (job.state != RoutingJobState.CANCELLED) && (job.state != RoutingJobState.TERMINATED))
    {
      try
      {
        Thread.sleep(100);
      } catch (InterruptedException e)
      {
        throw new RuntimeException(e);
      }

      // Check for timeout every iteration
      if (System.currentTimeMillis() - startTime > timeoutInMillis)
      {
        float timeoutInMinutes = timeoutInMillis / 60000.0f;
        throw new RuntimeException("Routing job timed out after " + timeoutInMinutes + " minutes.");
      }
    }

    return job;
  }

  protected BoardStatistics GetBoardStatistics(RoutingJob job)
  {
    if ((job == null) || (job.board == null))
    {
      throw new IllegalArgumentException("The job or its board cannot be null.");
    }

    return new BoardStatistics(job.board);
  }
}