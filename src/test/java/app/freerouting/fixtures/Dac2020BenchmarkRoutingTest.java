package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// KiCad DAC 2020 Benchmarks
/// DAC2020_bm01.dsn: There are 195 connections in total on the board
@Tag("slow")
class Dac2020BenchmarkRoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  @Test
  void issue508Bm01First2NetsOnly() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:30");

    // Get the job with injected settings
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(194).check();
  }

  @Test
  void issue508Bm01First43NetsOnly() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(43);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(161).check();
  }

  @Test
  void issue508Bm01First61NetsOnly() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(61);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(147).check();
  }

  @Test
  void issue508Bm01First111NetsOnly() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(111);
    testSettingsSource.setJobTimeoutString("00:01:30");

    // Get the job with injected settings
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(134).check();
  }

  @Test
  void issue508Bm01First151NetsOnly() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(151);
    testSettingsSource.setJobTimeoutString("00:03:00");

    // Get the job with injected settings
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(126).check();
  }

  @Test
  void issue508Bm01FirstPassOnly() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:04:30");
    testingSettings.setMaxPasses(1);

    // Get a routing job
    job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(56).check();
  }

  @Test
  void issue508Bm01First2PassesOnly() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:05:00");
    testingSettings.setMaxPasses(2);

    // Get a routing job
    job = getRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn").maxIncompleteConnections(28).check();
  }

  @Test
  void issue508Bm07() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:00:30");

    // Get a routing job
    job = getRoutingJob("Issue508-DAC2020_bm07.dsn", testingSettings);

    // Run the job and measure elapsed time via the job's own timestamps
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm07.dsn")
        .maxDuration(Duration.ofSeconds(30))
        .maxPasses(9)
        .exactIncompleteConnections(0)
        .check();
  }

  @Test
  void issue508Bm08() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:00:30");

    // Get a routing job
    job = getRoutingJob("Issue508-DAC2020_bm08.dsn", testingSettings);

    // Run the job and measure elapsed time via the job's own timestamps
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm08.dsn")
        .maxDuration(Duration.ofSeconds(20))
        .maxPasses(2)
        .exactIncompleteConnections(0)
        .check();
  }

  @AfterEach
  void tearDown() {
    if (job != null) {
      RoutingJobScheduler.getInstance().clearJobs(job.sessionId.toString());
    }
  }
}
