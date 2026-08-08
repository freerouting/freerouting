package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class Dac2020Bm05RoutingTest extends RoutingFixtureTest {

  @Test
  void issue508BM05First2Items() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:15");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm05.dsn", testSettingsSource);
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm05.dsn").maxIncompleteConnections(106).check();
  }

  @Test
  void issue508BM05First5Items() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(5);
    testSettingsSource.setJobTimeoutString("00:00:30");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm05.dsn", testSettingsSource);
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm05.dsn").maxIncompleteConnections(104).check();
  }

  @Test
  @Tag("slow")
  void issue508BM05FirstPass() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setJobTimeoutString("00:02:00");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm05.dsn", testSettingsSource);
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm05.dsn").maxIncompleteConnections(51).check();
  }

  @Test
  @Tag("slow")
  void issue508BM05FullRouting() {
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(20);
    testSettingsSource.setJobTimeoutString("00:05:00");

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm05.dsn", testSettingsSource);
    runRoutingJob(job);

    assertRoutingResult(job, "Issue508-DAC2020_bm05.dsn").maxIncompleteConnections(44).check();
  }
}
