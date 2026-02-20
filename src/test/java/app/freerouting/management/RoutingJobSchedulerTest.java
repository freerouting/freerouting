package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.settings.GlobalSettings;
import java.util.LinkedList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoutingJobSchedulerTest {

  private RoutingJobScheduler scheduler;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    scheduler = RoutingJobScheduler.getInstance();
  }

  @AfterEach
  void tearDown() {
    // Clear the job queue after each test
    scheduler.jobs.clear();
  }

  @Test
  void testGetInstance() {
    RoutingJobScheduler instance1 = RoutingJobScheduler.getInstance();
    RoutingJobScheduler instance2 = RoutingJobScheduler.getInstance();
    assertSame(instance1, instance2, "RoutingJobScheduler should be a singleton.");
  }

  @Test
  void testEnqueueJob() {
    // Create a test session
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session = sessionManager.createSession(userId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

    // Create a test job
    RoutingJob job = new RoutingJob();
    job.sessionId = session.id;

    // Enqueue the job
    scheduler.enqueueJob(job);

    // Assertions
    assertEquals(1, scheduler.jobs.size(), "Job queue should contain one job.");
    assertEquals(job, scheduler.jobs.getFirst(), "Enqueued job should be in the queue.");
    assertEquals(RoutingJobState.QUEUED, job.state, "Job state should be QUEUED.");
  }

  @Test
  void testGetQueuePosition() {
    // Create test jobs
    RoutingJob job1 = createTestJob();
    RoutingJob job2 = createTestJob();
    RoutingJob job3 = createTestJob();

    // Enqueue the jobs
    scheduler.enqueueJob(job1);
    scheduler.enqueueJob(job2);
    scheduler.enqueueJob(job3);

    // Assertions
    assertEquals(0, scheduler.getQueuePosition(job1), "First job should be at position 0.");
    assertEquals(1, scheduler.getQueuePosition(job2), "Second job should be at position 1.");
    assertEquals(2, scheduler.getQueuePosition(job3), "Third job should be at position 2.");
  }

  @Test
  void testListJobs() {
    // Create test jobs
    RoutingJob job1 = createTestJob();
    RoutingJob job2 = createTestJob();

    // Enqueue the jobs
    scheduler.enqueueJob(job1);
    scheduler.enqueueJob(job2);

    // Assertions
    RoutingJob[] listedJobs = scheduler.listJobs();
    assertEquals(2, listedJobs.length, "Listed jobs array should contain two jobs.");
    assertTrue(containsJob(listedJobs, job1), "Listed jobs should contain job1.");
    assertTrue(containsJob(listedJobs, job2), "Listed jobs should contain job2.");
  }

  @Test
  void testGetJob() {
    // Create a test job
    RoutingJob job = createTestJob();

    // Enqueue the job
    scheduler.enqueueJob(job);

    // Assertions
    assertEquals(job, scheduler.getJob(job.id.toString()), "Retrieved job should match the enqueued job.");
    assertNull(scheduler.getJob(UUID.randomUUID().toString()), "Retrieving a non-existent job should return null.");
  }

  @Test
  void testClearJobs() {
    // Create a test session
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session1 = sessionManager.createSession(userId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);
    Session session2 = sessionManager.createSession(userId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

    // Create test jobs
    RoutingJob job1 = createTestJob(session1.id);
    RoutingJob job2 = createTestJob(session1.id);
    RoutingJob job3 = createTestJob(session2.id); // Different session

    // Enqueue the jobs
    scheduler.enqueueJob(job1);
    scheduler.enqueueJob(job2);
    scheduler.enqueueJob(job3);

    // Clear jobs for the test session
    scheduler.clearJobs(session1.id.toString());

    // Assertions
    assertEquals(1, scheduler.jobs.size(), "Job queue should contain one job (from a different session).");
    assertFalse(containsJob(scheduler.jobs, job1), "Job1 should be removed.");
    assertFalse(containsJob(scheduler.jobs, job2), "Job2 should be removed.");
    assertTrue(containsJob(scheduler.jobs, job3), "Job3 (from a different session) should not be removed.");
  }

  // Helper method to create a test job with a random session ID
  private RoutingJob createTestJob() {
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session = sessionManager.createSession(userId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);
    return createTestJob(session.id);
  }

  // Helper method to create a test job with a specific session ID
  private RoutingJob createTestJob(UUID sessionId) {
    RoutingJob job = new RoutingJob();
    job.sessionId = sessionId;
    return job;
  }

  // Helper method to check if a job array contains a specific job
  private boolean containsJob(RoutingJob[] jobs, RoutingJob targetJob) {
    for (RoutingJob job : jobs) {
      if (job == targetJob) {
        return true;
      }
    }
    return false;
  }

  @Test
  void testCurrentPass() {
    RoutingJob job = new RoutingJob();
    assertEquals(0, job.getCurrentPass(), "Default currentPass should be 0.");

    job.setCurrentPass(5);
    assertEquals(5, job.getCurrentPass(), "currentPass should be updateable.");
  }

  @Test
  void testCancelJob() {
    // 1. Test with null job
    scheduler.cancelJob(null);
    // Should not throw exception

    // 2. Test with QUEUED job
    RoutingJob queuedJob = createTestJob();
    scheduler.enqueueJob(queuedJob);
    // enqueueJob sets state to QUEUED, which is what we want here.

    scheduler.cancelJob(queuedJob);
    assertEquals(RoutingJobState.CANCELLED, queuedJob.state, "QUEUED job should be CANCELLED.");
    assertTrue(queuedJob.isCancelledByUser(), "isCancelledByUser should be true.");

    // 3. Test with READY_TO_START job
    RoutingJob readyJob = createTestJob();
    scheduler.enqueueJob(readyJob);
    readyJob.state = RoutingJobState.READY_TO_START; // Set state AFTER enqueue

    scheduler.cancelJob(readyJob);
    assertEquals(RoutingJobState.CANCELLED, readyJob.state, "READY_TO_START job should be CANCELLED.");
    assertTrue(readyJob.isCancelledByUser(), "isCancelledByUser should be true.");

    // 4. Test with RUNNING job
    RoutingJob runningJob = createTestJob();
    scheduler.enqueueJob(runningJob);
    runningJob.state = RoutingJobState.RUNNING; // Set state AFTER enqueue
    // We don't set a thread, so thread.requestStop() won't be called, avoiding NPE
    // or mock requirement if checks are in place.

    scheduler.cancelJob(runningJob);
    assertEquals(RoutingJobState.STOPPING, runningJob.state, "RUNNING job should be set to STOPPING.");
    assertTrue(runningJob.isCancelledByUser(), "isCancelledByUser should be true.");

    // 5. Test with blocked/other state (e.g. PAUSED or INVALID if broadly
    // cancellable)
    RoutingJob pausedJob = createTestJob();
    scheduler.enqueueJob(pausedJob);
    pausedJob.state = RoutingJobState.PAUSED; // Set state AFTER enqueue

    scheduler.cancelJob(pausedJob);
    assertEquals(RoutingJobState.CANCELLED, pausedJob.state, "PAUSED job should be CANCELLED.");
    assertTrue(pausedJob.isCancelledByUser(), "isCancelledByUser should be true.");

    // 6. Test with already CANCELLED job
    RoutingJob cancelledJob = createTestJob();
    scheduler.enqueueJob(cancelledJob);
    cancelledJob.state = RoutingJobState.CANCELLED; // Set state AFTER enqueue
    cancelledJob.setCancelledByUser(true);

    scheduler.cancelJob(cancelledJob);
    // Should remain cancelled and not change state (code checks
    // !job.isCancelledByUser())
    assertEquals(RoutingJobState.CANCELLED, cancelledJob.state, "Already CANCELLED job should remain CANCELLED.");

    // 7. Test with COMPLETED job
    RoutingJob completedJob = createTestJob();
    scheduler.enqueueJob(completedJob);
    completedJob.state = RoutingJobState.COMPLETED; // Set state AFTER enqueue

    scheduler.cancelJob(completedJob);
    assertEquals(RoutingJobState.COMPLETED, completedJob.state, "COMPLETED job should not be cancelled.");
    assertFalse(completedJob.isCancelledByUser(), "isCancelledByUser should remain false for COMPLETED job.");
  }

  // Helper method to check if a job list contains a specific job
  private boolean containsJob(LinkedList<RoutingJob> jobs, RoutingJob targetJob) {
    return jobs.contains(targetJob);
  }
}