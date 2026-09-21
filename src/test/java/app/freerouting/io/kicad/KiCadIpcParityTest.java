package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.settings.GlobalSettings;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KiCadIpcParityTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testDevBoardIpcExtractionLoadsSuccessfully() throws Exception {
    File fixtureFile = new File("fixtures/Issue787-kicad-ipc/dev-board-ipc.json");
    assertTrue(fixtureFile.exists(), "IPC fixture file should exist");

    try (FileReader reader = new FileReader(fixtureFile, StandardCharsets.UTF_8)) {
      BoardReadResult result = KiCadJsonReader.readBoard(reader, null, null);
      assertInstanceOf(BoardReadResult.Success.class, result);

      BoardReadResult.Success success = (BoardReadResult.Success) result;
      RoutingBoard board = (RoutingBoard) success.board();

      assertNotNull(board);
      assertEquals(2, board.getLayerCount());
      assertEquals(21, board.components.count(), "All 21 components should be present");

      var outline = board.getOutline();
      assertNotNull(outline);

      int edgeClassNo =
          board.rules.clearanceMatrix.getNo(KiCadJsonReader.BOARD_EDGE_CLEARANCE_CLASS_NAME);
      assertTrue(edgeClassNo > 1, "board_edge clearance class should be registered");
      assertEquals(edgeClassNo, outline.clearanceClassIndex());

      // Resolution for mm default is 10000 (0.1 um units). 0.5 mm = 5000 units.
      int expectedClearance = (int) Math.round(0.5 * board.communication.resolution);
      assertEquals(
          expectedClearance,
          board.rules.clearanceMatrix.getValue(edgeClassNo, 1, 0, false),
          "Edge clearance between board_edge and default class should match project design rule (0.5 mm)");
    }
  }
}
