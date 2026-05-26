package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

public class RoutingFixtureTest {

  protected RoutingJobScheduler scheduler;

  @BeforeEach
  protected void setUp() {
    // Reset static logging flags so that a previous test (e.g. Dac2020Bm01RoutingTest) that sets
    // FRLogger.granularTraceEnabled = true does not contaminate subsequent tests.
    FRLogger.granularTraceEnabled = false;
    Freerouting.globalSettings = new GlobalSettings();
    scheduler = RoutingJobScheduler.getInstance();
    // Clear any leftover jobs from previous tests to avoid singleton state leaking between test runs.
    synchronized (scheduler.jobs) {
      scheduler.jobs.clear();
    }
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
        .of(testDirectory.toString(), "fixtures", filename)
        .toFile();
    while (!testFile.exists()) {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null) {
        break;
      }

      testFile = Path
          .of(testDirectory.toString(), "fixtures", filename)
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

      // Inject into global prototype so it survives Scheduler re-initialization
      Freerouting.globalSettings.settingsMergerProtype.addOrReplaceSources(testingSettings);

      job.routerSettings = merger.merge();

    } catch (IOException e) {
      throw new RuntimeException(testFile + " not found.", e);
    }

    return job;
  }

  protected RoutingJob RunRoutingJob(RoutingJob job) {
    if (job == null) {
      throw new IllegalArgumentException("The job cannot be null.");
    }

    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    long startTime = System.currentTimeMillis();
    long timeoutInMillis = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString) * 1000;

    while ((job.state != RoutingJobState.COMPLETED) && (job.state != RoutingJobState.CANCELLED)
        && (job.state != RoutingJobState.TERMINATED) && (job.state != RoutingJobState.TIMED_OUT)) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // Check for timeout every iteration
      if (System.currentTimeMillis() - startTime > timeoutInMillis) {
        // Request that the router stops cleanly before propagating the timeout.
        // Without this, the routing thread keeps running and starves subsequent tests.
        if (job.thread != null) {
          job.thread.requestStop();
        }
        if (job.state == RoutingJobState.RUNNING) {
          job.state = RoutingJobState.TIMED_OUT;
        }
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

  /**
   * Creates a fluent assertion builder for checking routing result properties.
   *
   * <p>Usage:
   * <pre>{@code
   * assertRoutingResult(job, "MyBoard.dsn")
   *     .maxDuration(Duration.ofMinutes(5))
   *     .passCount(1, 40)
   *     .maxIncompleteConnections(3)
   *     .exactClearanceViolations(0)
   *     .check();
   * }</pre>
   *
   * <p>Pass {@code null} (or simply omit a setter call) for any property you do not want to
   * check. A missing check is silently skipped.
   *
   * @param job       the completed routing job to inspect
   * @param boardName human-readable board file name for use in failure messages
   * @return a new {@link RoutingResultAssertions} builder bound to {@code job}
   */
  protected RoutingResultAssertions assertRoutingResult(RoutingJob job, String boardName) {
    return new RoutingResultAssertions(job, boardName);
  }

  /**
   * Fluent builder for asserting routing result properties.
   *
   * <p>All setter methods return {@code this} so calls can be chained. Call {@link #check()} at
   * the end to execute every configured assertion. Assertions where the expected value was never
   * set (i.e. remains {@code null}) are silently skipped, making it easy to add new checks
   * without breaking tests that do not need them.
   *
   * <p>Failure messages always include both the expected constraint and the actual value measured
   * from the job, so a CI failure immediately indicates what went wrong and by how much.
   */
  public static final class RoutingResultAssertions {

    private final RoutingJob job;
    private final String boardName;

    private Duration maxDuration;
    private Integer minPasses;
    private Integer maxPasses;
    private Integer maxIncompleteConnections;
    private Integer exactIncompleteConnections;
    private Integer maxClearanceViolations;
    private Integer exactClearanceViolations;

    RoutingResultAssertions(RoutingJob job, String boardName) {
      this.job = job;
      this.boardName = boardName;
    }

    /**
     * Asserts that the routing job completed within the given wall-clock duration.
     * Uses {@link RoutingJob#getDuration()} which returns the interval between
     * {@code startedAt} and {@code finishedAt}.
     */
    public RoutingResultAssertions maxDuration(Duration max) {
      this.maxDuration = max;
      return this;
    }

    /**
     * Convenience setter that configures both {@link #minPasses} and {@link #maxPasses}
     * in one call.
     */
    public RoutingResultAssertions passCount(int min, int max) {
      this.minPasses = min;
      this.maxPasses = max;
      return this;
    }

    /** Asserts that the router performed at least {@code min} routing passes. */
    public RoutingResultAssertions minPasses(int min) {
      this.minPasses = min;
      return this;
    }

    /** Asserts that the router stopped at or before {@code max} routing passes. */
    public RoutingResultAssertions maxPasses(int max) {
      this.maxPasses = max;
      return this;
    }

    /**
     * Asserts that the number of unrouted connections is at most {@code max}.
     * Mutually exclusive with {@link #exactIncompleteConnections(int)}; set only one.
     */
    public RoutingResultAssertions maxIncompleteConnections(int max) {
      this.maxIncompleteConnections = max;
      return this;
    }

    /**
     * Asserts that the number of unrouted connections is exactly {@code expected}.
     * Uses {@code assertEquals} internally, producing a clear diff in test output.
     * Mutually exclusive with {@link #maxIncompleteConnections(int)}; set only one.
     */
    public RoutingResultAssertions exactIncompleteConnections(int expected) {
      this.exactIncompleteConnections = expected;
      return this;
    }

    /**
     * Asserts that the number of clearance violations is at most {@code max}.
     * Mutually exclusive with {@link #exactClearanceViolations(int)}; set only one.
     */
    public RoutingResultAssertions maxClearanceViolations(int max) {
      this.maxClearanceViolations = max;
      return this;
    }

    /**
     * Asserts that the number of clearance violations is exactly {@code expected}.
     * Uses {@code assertEquals} internally, producing a clear diff in test output.
     * Mutually exclusive with {@link #maxClearanceViolations(int)}; set only one.
     */
    public RoutingResultAssertions exactClearanceViolations(int expected) {
      this.exactClearanceViolations = expected;
      return this;
    }

    /**
     * Executes all configured assertions against the job supplied at construction time.
     *
     * <p>Assertions are evaluated in the following order:
     * <ol>
     *   <li>Maximum wall-clock duration</li>
     *   <li>Minimum routing-pass count</li>
     *   <li>Maximum routing-pass count</li>
     *   <li>Maximum incomplete-connection count (or exact equality)</li>
     *   <li>Maximum clearance-violation count (or exact equality)</li>
     * </ol>
     *
     * <p>If both an exact and a maximum value are configured for the same property, both
     * checks run independently. In practice, set only one per property.
     */
    public void check() {
      Duration actualDuration = job.getDuration();
      int actualPasses = job.getCurrentPass();
      BoardStatistics stats = new BoardStatistics(job.board);
      int actualIncomplete = stats.connections.incompleteCount;
      int actualViolations = stats.clearanceViolations.totalCount;

      if (maxDuration != null) {
        assertTrue(
            actualDuration != null && actualDuration.compareTo(maxDuration) < 0,
            String.format(
                "'%s' should complete within %s, but took %s.",
                boardName,
                FRLogger.formatDuration(maxDuration.toSeconds()),
                actualDuration != null ? FRLogger.formatDuration(actualDuration.toSeconds()) : "N/A"));
      }

      if (minPasses != null) {
        assertTrue(
            actualPasses >= minPasses,
            String.format(
                "'%s' should have performed at least %d routing pass(es), but only had %d.",
                boardName, minPasses, actualPasses));
      }

      if (maxPasses != null) {
        assertTrue(
            actualPasses <= maxPasses,
            String.format(
                "'%s' should complete within at most %d routing pass(es), but required %d.",
                boardName, maxPasses, actualPasses));
      }

      if (maxIncompleteConnections != null) {
        assertTrue(
            actualIncomplete <= maxIncompleteConnections,
            String.format(
                "'%s' should have at most %d unrouted connection(s), but had %d.",
                boardName, maxIncompleteConnections, actualIncomplete));
      }

      if (exactIncompleteConnections != null) {
        assertEquals(
            exactIncompleteConnections,
            actualIncomplete,
            String.format(
                "'%s' should have exactly %d unrouted connection(s).",
                boardName, exactIncompleteConnections));
      }

      if (maxClearanceViolations != null) {
        assertTrue(
            actualViolations <= maxClearanceViolations,
            String.format(
                "'%s' should have at most %d clearance violation(s), but had %d.",
                boardName, maxClearanceViolations, actualViolations));
      }

      if (exactClearanceViolations != null) {
        assertEquals(
            exactClearanceViolations,
            actualViolations,
            String.format(
                "'%s' should have exactly %d clearance violation(s).",
                boardName, exactClearanceViolations));
      }
    }
  }
}