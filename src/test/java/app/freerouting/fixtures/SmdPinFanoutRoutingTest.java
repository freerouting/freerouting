package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class SmdPinFanoutRoutingTest extends RoutingFixtureTest {

  @Test
  public void test_Issue_558_dev_board() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(10);
    testSettingsSource.setJobTimeoutString("00:02:00");

    RoutingJob job = GetRoutingJob("Issue558-dev-board.dsn", testSettingsSource);
    RunRoutingJob(job);
    assertRoutingResult(job, "Issue558-dev-board.dsn").maxIncompleteConnections(0).check();
  }

  @Test
  public void test_Issue_508_BM06() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(10);
    testSettingsSource.setJobTimeoutString("00:02:00");

    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm06.dsn", testSettingsSource);
    RunRoutingJob(job);
    assertRoutingResult(job, "Issue508-DAC2020_bm06.dsn").maxIncompleteConnections(0).check();
  }

  @Test
  public void test_Issue_508_BM10() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(10);
    testSettingsSource.setJobTimeoutString("00:02:00");

    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm10.dsn", testSettingsSource);
    RunRoutingJob(job);
    assertRoutingResult(job, "Issue508-DAC2020_bm10.dsn").maxIncompleteConnections(0).check();
  }

  @Test
  public void test_SMD_routing_issue_demo() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(10);
    testSettingsSource.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob("SMD-routing-issue-demo.dsn", testSettingsSource);
    RunRoutingJob(job);
    assertRoutingResult(job, "SMD-routing-issue-demo.dsn").maxIncompleteConnections(0).check();
  }
}
