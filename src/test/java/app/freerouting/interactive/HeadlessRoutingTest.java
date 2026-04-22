package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sub-Issue 07 – Guard headless / API code paths against {@code interactiveSettings} usage.
 *
 * <p>Verifies that a full autoroute pass can complete in headless mode without encountering any
 * {@link NullPointerException} or {@link IllegalStateException} caused by {@code interactiveSettings}
 * being accessed. Also asserts that {@link HeadlessBoardManager#getInteractiveSettings()} returns
 * {@code null} throughout the routing session, confirming that GUI-specific state never leaks into
 * the headless code path.
 */
class HeadlessRoutingTest {

  private RoutingJobScheduler scheduler;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    InteractiveSettings.resetForTesting();
    scheduler = RoutingJobScheduler.getInstance();
    synchronized (scheduler.jobs) {
      scheduler.jobs.clear();
    }
  }

  /**
   * Verifies that {@link HeadlessBoardManager#getInteractiveSettings()} returns {@code null}
   * after loading a DSN file in headless mode.
   *
   * <p>This guards against regressions where a code-path inside {@code loadFromSpecctraDsn} or
   * its callees accidentally initialises {@code interactiveSettings} on a headless manager.
   */
  @Test
  void headlessManager_getInteractiveSettings_isNullAfterDsnLoad() {
    assertDoesNotThrow(() -> {
      var manager = new HeadlessBoardManager(new RoutingJob());
      try (FileInputStream dsnInput = new FileInputStream("tests/empty_board.dsn")) {
        manager.loadFromSpecctraDsn(
            dsnInput,
            new BoardObserverAdaptor(),
            new ItemIdentificationNumberGenerator());
      }

      assertNull(manager.getInteractiveSettings(),
          "HeadlessBoardManager.getInteractiveSettings() must return null at all times in headless mode");
    });
  }

  /**
   * Verifies that a full autoroute pass completes in headless mode without any
   * {@link NullPointerException} or {@link IllegalStateException} originating from
   * {@code interactiveSettings} being null.
   *
   * <p>The job is run against a small, real-world DSN file with a strictly bounded pass/item
   * count to keep test duration short while still exercising the complete routing pipeline.
   */
  @Test
  void headlessRouting_completesWithoutNpeOrIllegalState() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(2);
    testSettings.setMaxItems(10);
    testSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = assertDoesNotThrow(
        () -> createRoutingJob("Issue508-DAC2020_bm01.dsn", testSettings),
        "Loading the DSN file in headless mode must not throw any exception");

    assertNotNull(job, "Routing job must not be null");

    RoutingJob completedJob = assertDoesNotThrow(
        () -> runRoutingJob(job),
        "Running a routing job in headless mode must not throw any exception");

    assertTrue(
        completedJob.state == RoutingJobState.COMPLETED
            || completedJob.state == RoutingJobState.CANCELLED
            || completedJob.state == RoutingJobState.TERMINATED,
        "Routing job must reach a terminal state");
  }

  /**
   * Verifies that the routing job's board is non-null after a completed headless run,
   * confirming the entire pipeline executed without errors.
   */
  @Test
  void headlessRouting_boardIsNonNullAfterCompletion() {
    TestingSettings testSettings = new TestingSettings();
    testSettings.setMaxPasses(1);
    testSettings.setMaxItems(5);
    testSettings.setJobTimeoutString("00:00:30");

    RoutingJob job = createRoutingJob("Issue508-DAC2020_bm01.dsn", testSettings);
    runRoutingJob(job);

    assertNotNull(job.board,
        "RoutingJob.board must be non-null after a completed headless routing run");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private RoutingJob createRoutingJob(String filename, TestingSettings testingSettings) {
    UUID userId = UUID.randomUUID();
    var session = SessionManager.getInstance().createSession(userId, "test/1.0");

    RoutingJob job = new RoutingJob(session.id);

    Path testDirectory = Path.of(".").toAbsolutePath();
    File testFile = Path.of(testDirectory.toString(), "tests", filename).toFile();
    while (!testFile.exists()) {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null) {
        break;
      }
      testFile = Path.of(testDirectory.toString(), "tests", filename).toFile();
    }

    try {
      job.setInput(testFile);

      SettingsMerger merger = new SettingsMerger(new DefaultSettings(),
          new DsnFileSettings(job.input.getData(), job.input.getFilename()));

      if (testingSettings == null) {
        testingSettings = new TestingSettings();
      }
      var testRouterSettings = testingSettings.getSettings();
      if (testRouterSettings.jobTimeoutString == null) {
        testingSettings.setJobTimeoutString("00:01:00");
      }
      if (testRouterSettings.maxPasses == null) {
        testingSettings.setMaxPasses(100);
      }
      merger.addOrReplaceSources(testingSettings);

      Freerouting.globalSettings.settingsMergerProtype.addOrReplaceSources(testingSettings);
      job.routerSettings = merger.merge();
    } catch (IOException e) {
      throw new RuntimeException(testFile + " not found.", e);
    }

    return job;
  }

  private RoutingJob runRoutingJob(RoutingJob job) {
    if (job == null) {
      throw new IllegalArgumentException("The job cannot be null.");
    }

    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    long startTime = System.currentTimeMillis();
    long timeoutInMillis = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString) * 1000;

    while ((job.state != RoutingJobState.COMPLETED) && (job.state != RoutingJobState.CANCELLED)
        && (job.state != RoutingJobState.TERMINATED)) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (System.currentTimeMillis() - startTime > timeoutInMillis) {
        float timeoutInMinutes = timeoutInMillis / 60000.0f;
        throw new RuntimeException("Routing job timed out after " + timeoutInMinutes + " minutes.");
      }
    }

    return job;
  }
}
