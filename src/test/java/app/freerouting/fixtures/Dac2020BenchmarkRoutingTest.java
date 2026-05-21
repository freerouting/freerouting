package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// KiCad DAC 2020 Benchmarks
/// DAC2020_bm01.dsn: There are 195 connections in total on the board
@Tag("slow")
public class Dac2020BenchmarkRoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  @Test
  public void testIssue508_BM01_first_2_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:15");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(194)
        .check();
  }

  @Test
  public void testIssue508_BM01_first_43_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(43);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(161)
        .check();
  }

  @Test
  public void testIssue508_BM01_first_61_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(61);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(147)
        .check();
  }

  @Test
  public void testIssue508_BM01_first_111_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(111);
    testSettingsSource.setJobTimeoutString("00:01:30");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(134)
        .check();
  }

  @Test
  public void testIssue508_BM01_first_151_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(151);
    testSettingsSource.setJobTimeoutString("00:03:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(126)
        .check();
  }

  @Test
  public void test_Issue_508_BM01_first_pass_only() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:04:30");
    testingSettings.setMaxPasses(1);

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(56)
        .check();
  }

  @Test
  public void test_Issue_508_BM01_first_2_passes_only() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:05:00");
    testingSettings.setMaxPasses(2);

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm01.dsn")
        .maxIncompleteConnections(28)
        .check();
  }

  @Test
  public void test_Issue_508_BM07() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:00:30");

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm07.dsn", testingSettings);

    // Run the job and measure elapsed time via the job's own timestamps
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm07.dsn")
        .maxDuration(Duration.ofSeconds(30))
        .maxPasses(9)
        .exactIncompleteConnections(0)
        .check();
  }

  @Test
  public void test_Issue_508_BM08() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:00:20");

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm08.dsn", testingSettings);

    // Run the job and measure elapsed time via the job's own timestamps
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm08.dsn")
        .maxDuration(Duration.ofSeconds(20))
        .maxPasses(2)
        .exactIncompleteConnections(0)
        .check();
  }

  @AfterEach
  public void tearDown() {
    if (job != null) {
      RoutingJobScheduler
          .getInstance()
          .clearJobs(job.sessionId.toString());
    }
  }
}