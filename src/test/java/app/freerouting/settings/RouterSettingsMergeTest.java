package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.management.ReflectionUtil;
import org.junit.jupiter.api.Test;

class RouterSettingsMergeTest {

  @Test
  void testMergeLayersArray() {
    RouterSettings source = new RouterSettings();
    source.setLayerCount(2);
    source.layers[0].routable = false;
    source.layers[1].routable = true;
    source.layers[0].preferredDirectionHorizontal = true;
    source.layers[1].preferredDirectionHorizontal = false;

    RouterSettings target = new RouterSettings();

    // Perform the merge using ReflectionUtil.copyFields / applyNewValuesFrom
    target.applyNewValuesFrom(source);

    assertNotNull(target.layers);
    assertEquals(2, target.layers.length);
    assertFalse(target.layers[0].routable);
    assertTrue(target.layers[1].routable);
    assertTrue(target.layers[0].preferredDirectionHorizontal);
    assertFalse(target.layers[1].preferredDirectionHorizontal);

    // Verify deep copy: changing target should not affect source
    target.layers[0].routable = true;
    assertFalse(source.layers[0].routable);
    assertNotSame(source.layers[0], target.layers[0]);
  }

  @Test
  void testApplyBoardSpecificOptimizationsPreservesSettings() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.layers[0].routable = false;
    settings.layers[1].routable = true;
    settings.layers[0].preferredDirectionHorizontal = true;
    settings.layers[1].preferredDirectionHorizontal = false;

    // Create a mock/real board with 2 signal layers
    app.freerouting.board.Layer layer1 = new app.freerouting.board.Layer("Top", true);
    app.freerouting.board.Layer layer2 = new app.freerouting.board.Layer("Bottom", true);
    app.freerouting.board.LayerStructure layerStructure = new app.freerouting.board.LayerStructure(new app.freerouting.board.Layer[] { layer1, layer2 });
    app.freerouting.rules.ClearanceMatrix clearanceMatrix = app.freerouting.rules.ClearanceMatrix.get_default_instance(layerStructure, 10);
    app.freerouting.rules.BoardRules boardRules = new app.freerouting.rules.BoardRules(layerStructure, clearanceMatrix);
    boardRules.create_default_net_class();
    app.freerouting.board.Communication communication = new app.freerouting.board.Communication();
    app.freerouting.board.RoutingBoard board = new app.freerouting.board.RoutingBoard(
        new app.freerouting.geometry.planar.IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new app.freerouting.geometry.planar.PolylineShape[] { app.freerouting.geometry.planar.TileShape.get_instance(0, 0, 2000000, 2000000) },
        0,
        boardRules,
        communication);

    // Run optimizations
    settings.applyBoardSpecificOptimizations(board);

    // Verify settings were preserved and not reset to is_signal (true, true)
    assertFalse(settings.layers[0].routable);
    assertTrue(settings.layers[1].routable);
    assertTrue(settings.layers[0].preferredDirectionHorizontal);
    assertFalse(settings.layers[1].preferredDirectionHorizontal);
  }
}
