package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Item;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RoutingFailureLog} unified item and net-level failure tracking. */
class RoutingFailureLogTest {

  private static Item createMockItem(int id, int netNo) {
    Item item = mock(Item.class);
    when(item.getId()).thenReturn(id);
    when(item.netCount()).thenReturn(1);
    when(item.getNetNumber(0)).thenReturn(netNo);
    return item;
  }

  @Test
  void recordFailureUpdatesItemAndNetReasons() {
    RoutingFailureLog log = new RoutingFailureLog();
    Item item = createMockItem(42, 5);
    FailureReason reason =
        new FailureReason(
            FailureReason.FailureType.START_PIN_ESCAPE_FAILED, "Escape doors blocked.");

    log.recordFailure(item, 1, AutorouteAttemptState.FAILED, "Door blocked", reason, "NET_CLK");

    assertEquals(1, log.getFailureCount(item));
    assertEquals(reason, log.getNetFailureReasons().get("NET_CLK"));

    // Record another failure on pass 2
    FailureReason reason2 =
        new FailureReason(FailureReason.FailureType.CLEARANCE_WALKED_EXHAUSTED, "No path found.");
    log.recordFailure(item, 2, AutorouteAttemptState.FAILED, "No path", reason2, "NET_CLK");

    assertEquals(2, log.getFailureCount(item));
    assertEquals(reason2, log.getNetFailureReasons().get("NET_CLK"));
  }

  @Test
  void recordNetFailureExplicitly() {
    RoutingFailureLog log = new RoutingFailureLog();
    FailureReason reason =
        new FailureReason(
            FailureReason.FailureType.VIA_PLACEMENT_BLOCKED, "All via candidate layers blocked.");

    log.recordNetFailure("NET_DATA", reason);
    assertEquals(reason, log.getNetFailureReasons().get("NET_DATA"));
  }

  @Test
  void clearResetsAllRecords() {
    RoutingFailureLog log = new RoutingFailureLog();
    Item item = createMockItem(10, 1);
    FailureReason reason =
        new FailureReason(FailureReason.FailureType.CLEARANCE_WALKED_EXHAUSTED, "Exhausted.");

    log.recordFailure(item, 1, AutorouteAttemptState.FAILED, "Failed", reason, "NET_1");
    assertEquals(1, log.getFailureCount(item));
    assertEquals(1, log.getNetFailureReasons().size());

    log.clear();
    assertEquals(0, log.getFailureCount(item));
    assertTrue(log.getNetFailureReasons().isEmpty());
  }

  @Test
  void nullItemAndNetHandledSafely() {
    RoutingFailureLog log = new RoutingFailureLog();
    log.recordFailure(null, 1, AutorouteAttemptState.FAILED, "details", null, null);
    log.recordNetFailure(null, null);

    assertEquals(0, log.getFailureCount(null));
    assertTrue(log.getNetFailureReasons().isEmpty());
  }
}
