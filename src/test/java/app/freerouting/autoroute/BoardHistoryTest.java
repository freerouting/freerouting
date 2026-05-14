package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.specctra.parser.DsnFile;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.settings.RouterScoringSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardHistoryTest {

  private RoutingBoard board1;
  private RoutingBoard board2;
  private RouterScoringSettings scoringSettings;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    // Load a simple board for testing
    HeadlessBoardManager boardManager1 = new HeadlessBoardManager(new RoutingJob());
    FileInputStream inputStream1 = new FileInputStream("fixtures/empty_board.dsn");
    DsnFile.ReadResult result1 = boardManager1.loadFromSpecctraDsn(inputStream1, new BoardObserverAdaptor(), new ItemIdentificationNumberGenerator());
    board1 = boardManager1.get_routing_board();

    // Load a more complex board
    HeadlessBoardManager boardManager2 = new HeadlessBoardManager(new RoutingJob());
    FileInputStream inputStream2 = new FileInputStream("fixtures/Issue159-setonix_2hp-pcb.dsn");
    DsnFile.ReadResult result2 = boardManager2.loadFromSpecctraDsn(inputStream2, new BoardObserverAdaptor(), new ItemIdentificationNumberGenerator());
    board2 = boardManager2.get_routing_board();

    SettingsMerger settingsMerger = new SettingsMerger();
    settingsMerger.addOrReplaceSources(new DefaultSettings());
    scoringSettings = settingsMerger.merge().scoring;
  }

  @Test
  void addAndRestoreBoard() {
    BoardHistory history = new BoardHistory(scoringSettings);
    history.add(board1);

    RoutingBoard restoredBoard = history.restoreBestBoard();

    assertNotNull(restoredBoard);
    assertNotSame(board1, restoredBoard, "Restored board should be a new instance");
    assertEquals(board1.get_hash(), restoredBoard.get_hash(), "Restored board should be functionally identical to the original");
  }

  @Test
  void restoreBestBoardFromMultiple() {
    BoardHistory history = new BoardHistory(scoringSettings);

    // board2 has items, so it should have a worse (lower) score than the empty board1
    history.add(board1);
    history.add(board2);

    RoutingBoard bestBoard = history.restoreBestBoard();

    assertNotNull(bestBoard);
    // The empty board (board1) should have a better score
    assertEquals(board1.get_hash(), bestBoard.get_hash(), "Should restore the board with the best score");
  }

  @Test
  void contains() {
    BoardHistory history = new BoardHistory(scoringSettings);
    history.add(board1);

    assertTrue(history.contains(board1), "History should contain the added board");
    assertFalse(history.contains(board2), "History should not contain a board that was not added");
  }

  @Test
  void clear() {
    BoardHistory history = new BoardHistory(scoringSettings);
    history.add(board1);
    history.add(board2);
    assertEquals(2, history.size());

    history.clear();
    assertEquals(0, history.size(), "History should be empty after clear()");
  }

  @Test
  void sizeCapNeverExceedsMaxHistorySize() {
    BoardHistory history = new BoardHistory(scoringSettings, BoardHistory.MAX_HISTORY_SIZE);

    // Add more entries than the configured cap and verify the cap is enforced.
    history.add(board1);
    history.add(board2);
    history.add(board1);

    assertTrue(history.size() <= BoardHistory.MAX_HISTORY_SIZE,
        "History size must never exceed MAX_HISTORY_SIZE");
  }

  @Test
  void sizeCapEvictsWorstEntry() {
    // Use a cap of 1 so we can verify eviction with only two boards.
    // board1 is empty (high score) and board2 is complex (lower score).
    BoardHistory history = new BoardHistory(scoringSettings, 1);

    // Fill to capacity with the better board.
    history.add(board1);
    assertEquals(1, history.size(), "History should have 1 entry after first add");

    // Attempting to add board2 (lower score than board1) when at capacity should not
    // evict board1 — the history only evicts the worst entry to make room for a
    // strictly better board.
    history.add(board2);
    assertEquals(1, history.size(), "History size must stay at the cap");

    // The surviving entry should be board1 (the higher-scoring board).
    RoutingBoard best = history.restoreBestBoard();
    assertNotNull(best, "History must still contain the best board");
    assertEquals(board1.get_hash(), best.get_hash(),
        "The better-scoring board (board1) must be retained when a worse board is added at capacity");
  }
}