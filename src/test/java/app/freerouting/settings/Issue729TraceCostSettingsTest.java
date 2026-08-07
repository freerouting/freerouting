package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Communication;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
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
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.create_default_net_class();
    board =
        new RoutingBoard(
            new IntBox(0, 0, 2_000_000, 1_000_000),
            layerStructure,
            new PolylineShape[] {TileShape.get_instance(0, 0, 2_000_000, 1_000_000)},
            0,
            boardRules,
            new Communication());

    settings = new RouterSettings();
    settings.setLayerCount(2);
  }

  @Test
  void applyBoardSpecificOptimizations_initializesTraceCostsOnce() {
    settings.applyBoardSpecificOptimizations(board);

    assertTrue(settings.areBoardSpecificTraceCostsApplied());
    double firstPrefLayer0 = settings.get_preferred_direction_trace_costs(0);
    double firstAgainstLayer0 = settings.get_against_preferred_direction_trace_costs(0);
    assertTrue(
        firstAgainstLayer0 > settings.scoring.defaultUndesiredDirectionTraceCost,
        "Board-tuned against-preferred cost should include aspect-ratio penalty on a 2:1 board");

    settings.applyBoardSpecificOptimizations(board);

    assertEquals(firstPrefLayer0, settings.get_preferred_direction_trace_costs(0));
    assertEquals(firstAgainstLayer0, settings.get_against_preferred_direction_trace_costs(0));
  }

  @Test
  void applyBoardSpecificOptimizations_preservesUserTraceCostsOnSecondCall() {
    settings.applyBoardSpecificOptimizations(board);

    settings.set_against_preferred_direction_trace_costs(0, 4.5);
    settings.set_against_preferred_direction_trace_costs(1, 3.2);
    settings.set_preferred_direction_trace_costs(0, 2.0);

    settings.applyBoardSpecificOptimizations(board);

    assertEquals(4.5, settings.get_against_preferred_direction_trace_costs(0));
    assertEquals(3.2, settings.get_against_preferred_direction_trace_costs(1));
    assertEquals(2.0, settings.get_preferred_direction_trace_costs(0));
  }

  @Test
  void applyBoardSpecificOptimizationsIfNeeded_skipsWhenAlreadyBoardTuned() {
    settings.applyBoardSpecificOptimizations(board);
    settings.set_against_preferred_direction_trace_costs(0, 5.0);
    settings.set_against_preferred_direction_trace_costs(1, 6.0);

    settings.applyBoardSpecificOptimizationsIfNeeded(board);

    assertEquals(5.0, settings.get_against_preferred_direction_trace_costs(0));
    assertEquals(6.0, settings.get_against_preferred_direction_trace_costs(1));
  }

  @Test
  void applyBoardSpecificOptimizationsIfNeeded_runsWhenLayerCountMismatch() {
    RouterSettings untuned = new RouterSettings();
    assertEquals(0, untuned.getLayerCount());

    untuned.applyBoardSpecificOptimizationsIfNeeded(board);

    assertEquals(2, untuned.getLayerCount());
    assertTrue(untuned.areBoardSpecificTraceCostsApplied());
    assertTrue(untuned.get_layer_active(0));
    assertTrue(untuned.get_layer_active(1));
  }

  @Test
  void setLayerCount_resetsTraceCostAppliedFlag() {
    settings.applyBoardSpecificOptimizations(board);
    assertTrue(settings.areBoardSpecificTraceCostsApplied());

    settings.setLayerCount(4);

    assertFalse(settings.areBoardSpecificTraceCostsApplied());
  }
}
