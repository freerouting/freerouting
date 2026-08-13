package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Characterizes the neutral board metadata that a GUI renderer may consume.
 *
 * <p>This test deliberately imports no GUI or AWT rendering type. Paint APIs remain in place during
 * the first Phase 6 commit; the test protects the semantic boundary before those APIs are moved.
 */
class BoardItemRenderingMetadataTest {

  private static final String FIXTURE = "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";

  private static BasicBoard loadBoard() throws Exception {
    BoardReadResult result;
    try (FileInputStream in = new FileInputStream("fixtures/" + FIXTURE)) {
      result = DsnReader.readBoard(in, null, null, "phase6-metadata");
    }
    return switch (result) {
      case BoardReadResult.Success s -> (BasicBoard) s.board();
      case BoardReadResult.OutlineMissing o -> (BasicBoard) o.board();
      default -> throw new IllegalStateException("Failed to read board: " + result);
    };
  }

  @Test
  void everyBoardItemExposesNeutralSemanticMetadata() throws Exception {
    BasicBoard board = loadBoard();
    Set<BoardItemType> observedTypes = EnumSet.noneOf(BoardItemType.class);

    for (Item item : board.getItems()) {
      BoardItemType type = item.getBoardItemType();
      observedTypes.add(type);
      assertNotNull(type, "every board item must expose a semantic category");
      assertNotNull(item.boundingBox(), "every board item must expose neutral geometry bounds");
      assertTrue(item.firstLayer() <= item.lastLayer(), "item layer range must be ordered");

      if (item instanceof Pin) {
        assertEquals(BoardItemType.PIN, type);
      } else if (item instanceof Via) {
        assertEquals(BoardItemType.VIA, type);
      } else if (item instanceof Trace) {
        assertEquals(BoardItemType.TRACE, type);
      } else if (item instanceof ConductionArea) {
        assertEquals(BoardItemType.CONDUCTION_AREA, type);
      } else if (item instanceof ViaObstacleArea) {
        assertEquals(BoardItemType.VIA_OBSTACLE_AREA, type);
      } else if (item instanceof ComponentObstacleArea) {
        assertEquals(BoardItemType.COMPONENT_OBSTACLE_AREA, type);
      } else if (item instanceof ObstacleArea) {
        assertEquals(BoardItemType.OBSTACLE_AREA, type);
      } else if (item instanceof ComponentOutline) {
        assertEquals(BoardItemType.COMPONENT_OUTLINE, type);
      } else if (item instanceof BoardOutline) {
        assertEquals(BoardItemType.BOARD_OUTLINE, type);
      } else {
        assertEquals(BoardItemType.OTHER, type);
      }
    }

    assertTrue(observedTypes.contains(BoardItemType.PIN), "fixture must contain pins");
    assertTrue(observedTypes.contains(BoardItemType.TRACE), "fixture must contain traces");
  }
}
