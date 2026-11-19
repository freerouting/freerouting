package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardStatisticsBoundsCalculatorTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void emptyNetAndSinglePinNetContributeZeroBounds() {
    BoardStatistics stats = statisticsFromFixture("scoring-empty-net.dsn");
    assertEquals(0.0f, stats.bounds.minTraceLengthMm, 0.001f);
    assertEquals(0, stats.bounds.minViaCount);
    assertEquals(0, stats.bounds.minBendCount);
  }

  @Test
  void coincidentPinsHaveZeroLengthAndNoBendBound() {
    BoardStatistics stats = statisticsFromFixture("scoring-zero-length.dsn");
    assertEquals(0.0f, stats.bounds.minTraceLengthMm, 0.001f);
    assertEquals(0, stats.bounds.minViaCount);
    assertEquals(0, stats.bounds.minBendCount);
  }

  @Test
  void mixedLayerPinsRequireOneThroughViaAndManhattanBend() {
    BoardStatistics stats = statisticsFromFixture("scoring-mixed-layer.dsn");
    assertEquals(1, stats.bounds.minViaCount);
    assertTrue(stats.bounds.minTraceLengthMm > 0.0f, "offset pads must produce a positive L_min");
    assertEquals(1, stats.bounds.minBendCount);
    assertEquals(stats.difficulty.complexityC.intValue(), stats.difficulty.difficultyD.intValue());
  }

  @Test
  void diagonalConnectionUsesFortyFiveDegreeDistance() {
    // U1 (2, 2) mm to U2 (8, 8) mm: a 45-degree trace is 6 * sqrt(2) mm long, shorter than the
    // 12 mm Manhattan distance, so the lower bound must not exceed it.
    BoardStatistics stats = statisticsFromFixture("scoring-mixed-layer.dsn");
    assertEquals(6.0f * (float) Math.sqrt(2), stats.bounds.minTraceLengthMm, 0.01f);
  }

  @Test
  void pinsCoveredByAPlaneNeedNoTrace() {
    // U1 and U2 lie inside the GND plane (1..9 mm square), which connects them without traces.
    // U3 at (12, 5) mm is 3 mm from its edge.
    BoardStatistics stats = statisticsFromFixture("scoring-plane-net.dsn");
    assertEquals(3.0f, stats.bounds.minTraceLengthMm, 0.01f);
  }

  @Test
  void angleRestrictionChangeInvalidatesCachedTraceLengthBound() {
    RoutingBoard board = loadFixture("scoring-mixed-layer.dsn");
    board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    BoardStatistics ninetyStats = new BoardStatistics(board);
    assertEquals(12.0f, ninetyStats.bounds.minTraceLengthMm, 0.01f);

    board.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);
    BoardStatistics fortyFiveStats = new BoardStatistics(board);
    assertEquals(6.0f * (float) Math.sqrt(2), fortyFiveStats.bounds.minTraceLengthMm, 0.01f);
  }

  @Test
  void circularConductionAreaComputesDistanceWithoutNullPointerException() {
    RoutingBoard board = loadFixture("scoring-mixed-layer.dsn");
    // Resolution is 10 units/um (10,000 units/mm). Circle centered at U1 (2, 2) mm with radius 1 mm
    // covers U1 and leaves U2 (8, 8) mm at Euclidean distance 6 * sqrt(2) - 1 mm from its border.
    board.insertConductionArea(
        new Circle(new IntPoint(20_000, 20_000), 10_000),
        0,
        new int[] {1},
        1,
        false,
        FixedState.USER_FIXED);
    BoardStatistics stats = new BoardStatistics(board);
    assertEquals(6.0f * (float) Math.sqrt(2) - 1.0f, stats.bounds.minTraceLengthMm, 0.01f);
  }

  @Test
  void emptyBoardHasZeroBoundsAndDefinedDifficulty() {
    BoardStatistics stats = statisticsFromFixture("empty_board.dsn");
    assertEquals(0.0f, stats.bounds.minTraceLengthMm, 0.001f);
    assertEquals(0, stats.bounds.minViaCount);
    assertEquals(0, stats.bounds.minBendCount);
    assertEquals(1, stats.difficulty.complexityC);
    assertEquals(1.0f, stats.difficulty.difficultyD, 0.001f);
  }

  private static RoutingBoard loadFixture(String filename) {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource(filename), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result, filename + " must parse");
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertNotNull(board);
    return board;
  }

  private static BoardStatistics statisticsFromFixture(String filename) {
    return new BoardStatistics(loadFixture(filename));
  }
}
