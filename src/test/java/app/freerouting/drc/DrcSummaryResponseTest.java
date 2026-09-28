package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.management.BoardLoader;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DrcSummaryResponseTest extends RoutingFixtureTest {

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testGenerateSummary() {
    RoutingJob sampleJob = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    assertNotNull(sampleJob);
    BoardLoader.loadBoardIfNeeded(sampleJob);
    assertNotNull(sampleJob.board);

    DesignRulesChecker checker =
        new DesignRulesChecker(sampleJob.board, Freerouting.globalSettings.drcSettings);
    DrcSummaryResponse summary = checker.generateSummary();

    assertNotNull(summary);
    assertNotNull(summary.violations);
    assertNotNull(summary.congestionZones);
    assertNotNull(summary.hints);
    assertTrue(summary.clearanceViolationsCount >= 0);
    assertTrue(summary.unconnectedNetsCount >= 0);
  }
}
