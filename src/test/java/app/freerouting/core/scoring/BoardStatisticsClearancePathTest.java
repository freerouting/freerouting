package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scoring clearance counts must come from {@link DesignRulesChecker#getAllClearanceViolations()},
 * not from an outline-only shortcut.
 */
class BoardStatisticsClearancePathTest {

  private static final String VIOLATION_FIXTURE =
      "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void liveBoardStatisticsUsesFullDrcCount() {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource(VIOLATION_FIXTURE), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    int fullDrcCount = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    BoardStatistics stats = new BoardStatistics(board);

    assertEquals(fullDrcCount, stats.clearanceViolations.totalCount.intValue());
    assertEquals(2, fullDrcCount);
  }

  @Test
  void outlineOnlyCountIsNotUsedAsTheScoringSignal() {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource(VIOLATION_FIXTURE), null, null);
    BasicBoard board = (BasicBoard) ((BoardReadResult.Success) result).board();
    int outlineCount = board.getOutline().clearanceViolationCount();
    int fullCount = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    BoardStatistics stats = new BoardStatistics(board);

    assertEquals(fullCount, stats.clearanceViolations.totalCount.intValue());
    // Outline-only is a different, incomplete view (Issue 558). It may be 0 here.
    assertTrue(
        outlineCount <= fullCount, "outline-only count must not exceed the full DRC pair scan");
  }
}
