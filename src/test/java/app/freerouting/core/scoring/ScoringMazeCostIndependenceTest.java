package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Maze-search costs live on {@link app.freerouting.settings.RoutingCostSettings}. Changing V2
 * board-score weights must not change via, rip-up, or preferred-direction search costs.
 */
class ScoringMazeCostIndependenceTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void mazeSearchCostsStayPutWhenV2BoardScoreWeightsChange() {
    RouterSettings settings = new DefaultSettings().getSettings();
    RouterSettings before = settings.clone();

    settings.routerScoring.unroutedFirstHalfWeight = 9999.0f;
    settings.routerScoring.unroutedSecondHalfWeight = 8888.0f;
    settings.routerScoring.clearanceViolationCountWeight = 1.0f;
    settings.routerScoring.clearanceViolationDepthWeight = 2.0f;
    settings.optimizerScoring.excessWireLengthWeight = 3.0f;
    settings.optimizerScoring.excessViaWeight = 4.0f;
    settings.optimizerScoring.excessBendWeight = 5.0f;

    assertEquals(before.getViaCosts(), settings.getViaCosts());
    assertEquals(before.getPlaneViaCosts(), settings.getPlaneViaCosts());
    assertEquals(before.scoring.startRipupCosts, settings.scoring.startRipupCosts);
    assertEquals(
        before.scoring.defaultPreferredDirectionTraceCost,
        settings.scoring.defaultPreferredDirectionTraceCost,
        0.0);
    assertEquals(
        before.scoring.defaultUndesiredDirectionTraceCost,
        settings.scoring.defaultUndesiredDirectionTraceCost,
        0.0);
    assertEquals(before.scoring.unroutedNetPenalty, settings.scoring.unroutedNetPenalty, 0.0f);
    assertEquals(
        before.scoring.clearanceViolationPenalty, settings.scoring.clearanceViolationPenalty, 0.0f);
    assertEquals(before.scoring.bendPenalty, settings.scoring.bendPenalty, 0.0f);
    assertEquals(DefaultSettings.DEFAULT_VIA_COSTS, settings.scoring.viaCosts);
  }

  @Test
  void autorouteControlViaCostTracksMazeSettingsNotV2Weights() {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource("scoring-zero-length.dsn"), null, null);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.applyBoardSpecificOptimizations(board);
    double viaCostBefore = new AutorouteControl(board, 1, settings).minNormalViaCost;

    settings.routerScoring.unroutedFirstHalfWeight = 50_000.0f;
    settings.optimizerScoring.excessViaWeight = 9_000.0f;

    AutorouteControl afterWeights = new AutorouteControl(board, 1, settings);
    assertEquals(viaCostBefore, afterWeights.minNormalViaCost, 0.001);
    assertEquals(DefaultSettings.DEFAULT_VIA_COSTS, settings.getViaCosts());
  }
}
