package app.freerouting.management.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.analytics.AnalyticsClient;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.GlobalSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingJobSchedulerActionThreadTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testRoutingJobSchedulerActionThreadEmitsAnalytics() throws Exception {
    List<String> trackedEvents = Collections.synchronizedList(new ArrayList<>());
    AnalyticsClient testClient =
        new AnalyticsClient() {
          @Override
          public void identify(String userId, String anonymousId, Traits traits) {}

          @Override
          public void track(
              String userId, String anonymousId, String event, Properties properties) {
            trackedEvents.add(event);
          }

          @Override
          public void setEnabled(boolean enabled) {}
        };

    // Initialize test client via reflection
    java.lang.reflect.Field clientField = FRAnalytics.class.getDeclaredField("analytics");
    clientField.setAccessible(true);
    clientField.set(null, testClient);
    FRAnalytics.setEnabled(true);

    String json =
        """
        {
          "designName": "ActionThreadTestBoard",
          "unit": "MM",
          "resolution": 1000.0,
          "layers": [
            {"index": 0, "name": "F.Cu", "type": "signal"},
            {"index": 1, "name": "B.Cu", "type": "signal"}
          ],
          "outline": {
            "corners": [
              {"x": 0.0, "y": 0.0},
              {"x": 20.0, "y": 0.0},
              {"x": 20.0, "y": 20.0},
              {"x": 0.0, "y": 20.0}
            ]
          }
        }
        """;

    RoutingJob job = new RoutingJob();
    app.freerouting.management.HeadlessBoardManager manager =
        new app.freerouting.management.HeadlessBoardManager(job);
    manager.loadFromKiCadJson(
        new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        null,
        null);
    job.board = manager.getRoutingBoard();

    job.routerSettings =
        new app.freerouting.settings.SettingsMerger(
                new app.freerouting.settings.sources.DefaultSettings())
            .merge();
    job.routerSettings.setLayerCount(job.board.getLayerCount());
    job.routerSettings.applyBoardSpecificOptimizations(job.board);
    job.routerSettings.autorouter.maxPasses = 0; // Quick 0-pass test
    job.state = RoutingJobState.RUNNING;

    RoutingJobSchedulerActionThread actionThread = new RoutingJobSchedulerActionThread(job);
    job.thread = actionThread;

    actionThread.threadAction();
    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertTrue(
        trackedEvents.contains("Auto-router Started"), "Should have tracked 'Auto-router Started'");
    assertTrue(
        trackedEvents.contains("Auto-router Finished"),
        "Should have tracked 'Auto-router Finished'");
  }

  @Test
  void jobClockStopBecomesTimedOut() {
    RoutingJobSchedulerActionThread action = stoppedJob(RoutingJobState.RUNNING, true);
    action.assignTerminalState();
    assertEquals(RoutingJobState.TIMED_OUT, action.job.state);
  }

  @Test
  void finishedJobStaysCompletedWhenTheMonitorAssignsTimeout() {
    RoutingJob job = new RoutingJob();
    job.state = RoutingJobState.COMPLETED;
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    action.markTimedOutIfStillActive();
    assertEquals(RoutingJobState.COMPLETED, job.state);
  }

  @Test
  void stopBeforeTheJobClockCompletes() {
    RoutingJobSchedulerActionThread action = stoppedJob(RoutingJobState.RUNNING, false);
    action.assignTerminalState();
    assertEquals(RoutingJobState.COMPLETED, action.job.state);
  }

  @Test
  void userCancelWinsOverTheExpiredJobClock() {
    RoutingJobSchedulerActionThread action = stoppedJob(RoutingJobState.STOPPING, true);
    action.job.setCancelledByUser(true);
    action.assignTerminalState();
    assertEquals(RoutingJobState.CANCELLED, action.job.state);
  }

  @Test
  void monitorTimeoutIsKeptWhenThePipelineFinishes() {
    RoutingJob job = new RoutingJob();
    job.state = RoutingJobState.RUNNING;
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    action.markTimedOutIfStillActive();
    action.assignTerminalState();
    assertEquals(RoutingJobState.TIMED_OUT, job.state);
  }

  @Test
  @app.freerouting.logger.AllowErrorLogs(
      "Verifies that fatal routing pipeline errors are caught and recorded as TERMINATED")
  void testFatalErrorSetsJobStateToTerminated() {
    RoutingJob job = new RoutingJob();
    job.state = RoutingJobState.RUNNING;
    // routerSettings is null, which causes threadAction() to throw NullPointerException
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    job.thread = action;

    action.threadAction();

    assertEquals(
        RoutingJobState.TERMINATED, job.state, "Fatal error must set job state to TERMINATED");
    assertNotNull(job.finishedAt, "Job finishedAt must be recorded on fatal error");
  }

  private static RoutingJobSchedulerActionThread stoppedJob(
      RoutingJobState state, boolean clockExpired) {
    RoutingJob job = new RoutingJob();
    job.state = state;
    job.timeoutAt = clockExpired ? Instant.now().minusSeconds(5) : Instant.now().plusSeconds(3600);
    RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
    job.thread = action;
    action.requestStop();
    return action;
  }
}
