package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BoardIndexTest {

  @Test
  void testBoardIndexOnRealFixture() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();

    BoardIndex index = BoardIndex.build(board, config);

    assertNotNull(index);
    assertTrue(index.totalComponents() > 0, "Board should contain components");
    assertTrue(index.totalPins() > 0, "Board should contain pins");
    assertFalse(
        index.getTwoPinComponentIds().isEmpty(), "Board should have two-pin SMD components");

    // Check that indexing of pins is non-empty for two-pin components
    int firstTwoPinId = index.getTwoPinComponentIds().get(0);
    assertEquals(ComponentType.TWO_PIN_SMD, index.getComponentType(firstTwoPinId));
    assertEquals(2, index.getPinsOfComponent(firstTwoPinId).size());

    // Check ground score calculation
    boolean foundPositiveGroundScore = false;
    for (int netNum = 1; netNum <= board.rules.nets.maxNetNumber(); netNum++) {
      if (index.getGroundScore(netNum) > 0.0) {
        foundPositiveGroundScore = true;
        break;
      }
    }
    assertTrue(foundPositiveGroundScore, "At least one net should have a positive ground score");
  }

  private static void assertEquals(Object expected, Object actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
