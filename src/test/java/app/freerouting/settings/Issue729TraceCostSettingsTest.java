package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for GitHub Issue #729 — autorouter trace costs must survive repeated {@link
 * RouterSettings#applyBoardSpecificOptimizations} calls (e.g. on every GUI autoroute start).
 */
class Issue729TraceCostSettingsTest {

  private RoutingBoard board;
  private RouterSettings settings;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    board =
        new RoutingBoard(
            new IntBox(0, 0, 2_000_000, 1_000_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 2_000_000, 1_000_000)},
            0,
            boardRules,
            new Communication());

    settings = new RouterSettings();
    settings.setLayerCount(2);
  }

  @Test
  void applyBoardSpecificOptimizationsInitializesTraceCostsOnce() {
    settings.applyBoardSpecificOptimizations(board);

    assertTrue(settings.areBoardSpecificTraceCostsApplied());
    double firstPrefLayer0 = settings.getPreferredDirectionTraceCosts(0);
    double firstAgainstLayer0 = settings.getAgainstPreferredDirectionTraceCosts(0);
    assertTrue(
        firstAgainstLayer0 > settings.scoring.defaultUndesiredDirectionTraceCost,
        "Board-tuned against-preferred cost should include aspect-ratio penalty on a 2:1 board");

    settings.applyBoardSpecificOptimizations(board);

    assertEquals(firstPrefLayer0, settings.getPreferredDirectionTraceCosts(0));
    assertEquals(firstAgainstLayer0, settings.getAgainstPreferredDirectionTraceCosts(0));
  }

  @Test
  void applyBoardSpecificOptimizationsPreservesUserTraceCostsOnSecondCall() {
    settings.applyBoardSpecificOptimizations(board);

    settings.setAgainstPreferredDirectionTraceCosts(0, 4.5);
    settings.setAgainstPreferredDirectionTraceCosts(1, 3.2);
    settings.setPreferredDirectionTraceCosts(0, 2.0);

    settings.applyBoardSpecificOptimizations(board);

    assertEquals(4.5, settings.getAgainstPreferredDirectionTraceCosts(0));
    assertEquals(3.2, settings.getAgainstPreferredDirectionTraceCosts(1));
    assertEquals(2.0, settings.getPreferredDirectionTraceCosts(0));
  }

  @Test
  void applyBoardSpecificOptimizationsIfNeededSkipsWhenAlreadyBoardTuned() {
    settings.applyBoardSpecificOptimizations(board);
    settings.setAgainstPreferredDirectionTraceCosts(0, 5.0);
    settings.setAgainstPreferredDirectionTraceCosts(1, 6.0);

    settings.applyBoardSpecificOptimizationsIfNeeded(board);

    assertEquals(5.0, settings.getAgainstPreferredDirectionTraceCosts(0));
    assertEquals(6.0, settings.getAgainstPreferredDirectionTraceCosts(1));
  }

  @Test
  void applyBoardSpecificOptimizationsIfNeededRunsWhenLayerCountMismatch() {
    RouterSettings untuned = new RouterSettings();
    assertEquals(0, untuned.getLayerCount());

    untuned.applyBoardSpecificOptimizationsIfNeeded(board);

    assertEquals(2, untuned.getLayerCount());
    assertTrue(untuned.areBoardSpecificTraceCostsApplied());
    assertTrue(untuned.getLayerActive(0));
    assertTrue(untuned.getLayerActive(1));
  }

  @Test
  void setLayerCountResetsTraceCostAppliedFlag() {
    settings.applyBoardSpecificOptimizations(board);
    assertTrue(settings.areBoardSpecificTraceCostsApplied());

    settings.setLayerCount(4);

    assertFalse(settings.areBoardSpecificTraceCostsApplied());
  }
}
