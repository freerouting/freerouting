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
          "Edge clearance between board_edge and default class should match project rule");
    }
  }

  @Test
  void testDevBoardDsnVsIpcParity() throws Exception {
    File ipcFixture = new File("fixtures/Issue787-kicad-ipc/dev-board-ipc.json");
    File dsnFixture = new File("fixtures/Issue558-dev-board.dsn");

    assertTrue(ipcFixture.exists(), "IPC fixture file should exist");
    assertTrue(dsnFixture.exists(), "DSN fixture file should exist");

    RoutingBoard ipcBoard;
    try (FileReader reader = new FileReader(ipcFixture, StandardCharsets.UTF_8)) {
      BoardReadResult result = KiCadJsonReader.readBoard(reader, null, null);
      assertInstanceOf(BoardReadResult.Success.class, result);
      ipcBoard = (RoutingBoard) ((BoardReadResult.Success) result).board();
    }

    RoutingBoard dsnBoard;
    try (var is = new java.io.FileInputStream(dsnFixture)) {
      BoardReadResult result =
          app.freerouting.io.specctra.DsnReader.readBoard(is, null, null, dsnFixture.getName());
      assertInstanceOf(BoardReadResult.Success.class, result);
      dsnBoard = (RoutingBoard) ((BoardReadResult.Success) result).board();
    }

    assertNotNull(ipcBoard);
    assertNotNull(dsnBoard);

    // Assert structural parity between DSN and IPC extracted boards
    assertEquals(dsnBoard.getLayerCount(), ipcBoard.getLayerCount(), "Layer counts must match");
    assertEquals(
        dsnBoard.components.count(), ipcBoard.components.count(), "Component counts must match");

    // Net count check: exactly 47 nets in both boards
    assertEquals(
        dsnBoard.rules.nets.maxNetNumber(),
        ipcBoard.rules.nets.maxNetNumber(),
        "Net count must match");

    // Pin count comparison:
    // DSN board has 143 pins (2 unplaced/omitted pins in DSN export).
    // IPC extraction directly reads all footprints and pads (145 pads: 105 front SMD + 40 THT).
    assertEquals(143, dsnBoard.getPins().size(), "DSN board contains 143 pins");
    assertEquals(
        145,
        ipcBoard.getPins().size(),
        "IPC board accurately contains all 145 pads (including back-layer THT pin headers)");
  }
}
