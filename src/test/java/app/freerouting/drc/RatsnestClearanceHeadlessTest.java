package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BasicBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 (compute vs. presentation, D13/D14): proves that the ratsnest (incomplete connections)
 * and the clearance-violation compute are reachable entirely <em>headless</em> — via {@link
 * DesignRulesChecker} and {@link ClearanceViolation} — without instantiating any GUI façade ({@code
 * interactive.RatsNest} / {@code interactive.ClearanceViolations}) or any Swing class.
 *
 * <p>This test deliberately imports only {@code drc}/{@code board}/{@code io} types; the absence of
 * a GUI dependency is also enforced architecturally by the module-boundary ArchUnit rules (the
 * pipeline/support packages must not depend on {@code gui}/{@code interactive}/{@code
 * gui.rendering}).
 */
class RatsnestClearanceHeadlessTest {

  /** Board with both incompletes and clearance violations: 9 unconnected, 2 unique violations. */
  private static final String VIOLATION_FIXTURE =
      "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";

  private static final int EXPECTED_UNCONNECTED = 9;
  private static final int EXPECTED_UNIQUE_VIOLATIONS = 2;

  private static BasicBoard loadBoard(String filename) throws Exception {
    BoardReadResult result;
    try (FileInputStream in = new FileInputStream("fixtures/" + filename)) {
      result = DsnReader.readBoard(in, null, null, "test");
    }
    return switch (result) {
      case BoardReadResult.Success s -> (BasicBoard) s.board();
      case BoardReadResult.OutlineMissing o -> (BasicBoard) o.board();
      default -> throw new IllegalStateException("Failed to read board: " + result);
    };
  }

  // ── Ratsnest / incompletes (no interactive.RatsNest) ─────────────────────

  @Test
  void incompletesAreComputableViaDesignRulesCheckerWithoutGuiFacade() throws Exception {
    BasicBoard board = loadBoard(VIOLATION_FIXTURE);

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();

    assertEquals(
        EXPECTED_UNCONNECTED,
        drc.getIncompleteCount(),
        "incomplete count must be computable via DesignRulesChecker alone (no RatsNest)");

    AirLine[] airlines = drc.getAllAirlines();
    assertNotNull(airlines, "getAllAirlines must not return null");
    assertFalse(airlines.length == 0, "a board with 9 incompletes must produce airlines");
    assertEquals(
        EXPECTED_UNCONNECTED, airlines.length, "one airline per incomplete connection expected");

    // Per-net query must also be headless-reachable.
    int sumPerNet = 0;
    for (int netNo = 1; netNo <= board.rules.nets.maxNetNo(); netNo++) {
      sumPerNet += drc.getIncompleteCount(netNo);
    }
    assertEquals(EXPECTED_UNCONNECTED, sumPerNet, "per-net incomplete counts must sum to total");
  }

  // ── Clearance violations (no interactive.ClearanceViolations) ────────────

  @Test
  void clearanceViolationsAreComputableViaDrcWithoutGuiFacade() throws Exception {
    BasicBoard board = loadBoard(VIOLATION_FIXTURE);

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> deduped = drc.getAllClearanceViolations();
    assertEquals(
        EXPECTED_UNIQUE_VIOLATIONS,
        deduped.size(),
        "deduplicated violation count via getAllClearanceViolations (no ClearanceViolations)");
  }

  @Test
  void clearanceViolationAggregationHelpersAreHeadlessAndSeveritySorted() throws Exception {
    BasicBoard board = loadBoard(VIOLATION_FIXTURE);

    // The compute that interactive.ClearanceViolations presents, reachable headless via drc.
    List<ClearanceViolation> aggregated =
        ClearanceViolation.aggregateSortedBySeverity(board.getItems());
    assertFalse(aggregated.isEmpty(), "aggregation must find violations on this board");
    // The per-item aggregation double-counts each pair, so it must be >= the unique count.
    assertTrue(
        aggregated.size() >= EXPECTED_UNIQUE_VIOLATIONS,
        "aggregated (double-counted) violations must be at least the unique count");

    // Verify the list is sorted by severity (expected - actual, descending).
    for (int i = 1; i < aggregated.size(); i++) {
      double prev = aggregated.get(i - 1).expectedClearance - aggregated.get(i - 1).actualClearance;
      double current = aggregated.get(i).expectedClearance - aggregated.get(i).actualClearance;
      assertTrue(prev >= current, "aggregated violations must be sorted by severity, descending");
    }

    double smallest = ClearanceViolation.smallestClearance(board.getItems());
    assertTrue(
        smallest > 0 && smallest < Double.MAX_VALUE,
        "smallestClearance must be a positive finite value on a board with violations");
  }

  @Test
  void emptyBoardHasNoIncompletesAndNoViolations() throws Exception {
    BasicBoard board = loadBoard("empty_board.dsn");

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    assertEquals(0, drc.getIncompleteCount(), "empty board must have no incomplete connections");
    assertTrue(
        drc.getAllClearanceViolations().isEmpty(), "empty board must have no clearance violations");
    assertTrue(
        ClearanceViolation.aggregateSortedBySeverity(board.getItems()).isEmpty(),
        "empty board aggregation must be empty");
  }
}
