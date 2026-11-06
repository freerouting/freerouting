package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.management.BoardLoader;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The fanout statistics, which walk the contacts of each item once for all pins, count the pins
 * connected to their whole net as each pin's own unconnected set does.
 */
class FanoutStatisticsTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(strings = {"Issue367-Charger.dsn", "Issue508-DAC2020_bm01.dsn"})
  void connectedPinsMatchTheUnconnectedSets(String fixture) {
    TestingSettings testingSettings = new TestingSettings();
    // A partly routed board: some pins connected to their whole net, some not.
    testingSettings.setMaxItems(40);
    testingSettings.setOptimizerEnabled(false);
    var job = getRoutingJob(fixture, testingSettings);
    BoardLoader.loadBoardIfNeeded(job);
    assertFanoutCounts(job.board, fixture + ", unrouted");

    runRoutingJob(job);
    int connected = assertFanoutCounts(job.board, fixture + ", routed");
    assertTrue(connected > 0, fixture + ": no SMD pin connected to its whole net after routing");
  }

  private static int assertFanoutCounts(BasicBoard board, String message) {
    int total = 0;
    int connected = 0;
    for (Pin pin : board.getSmdPins()) {
      if (pin.netCount() > 0) {
        total++;
        if (pin.getUnconnectedSet(pin.getNetNumber(0)).isEmpty()) {
          connected++;
        }
      }
    }
    BoardStatistics statistics = new BoardStatistics(board, null, false);
    assertTrue(total > 0, message + ": no SMD pin");
    assertEquals(total, statistics.fanout.totalSmdPins, message);
    assertEquals(total - connected, statistics.fanout.pinsToEscape, message);
    return connected;
  }
}
