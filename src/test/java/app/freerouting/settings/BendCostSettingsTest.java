package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.settings.sources.DefaultSettings;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class BendCostSettingsTest {

  @Test
  public void testDefaultBendCost() {
    DefaultSettings defaultSettings = new DefaultSettings();
    RouterSettings settings = defaultSettings.getSettings();
    assertNotNull(settings.scoring);
    assertEquals(0.0, settings.scoring.defaultBendCost);
  }

  @Test
  public void testSetGetBendCost() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);

    // Initial value defaults to 0.0
    assertEquals(0.0, settings.get_bend_cost(0));
    assertEquals(0.0, settings.get_bend_cost(1));

    // Setting valid values
    settings.set_bend_cost(0, 2.5);
    settings.set_bend_cost(1, 5.0);

    assertEquals(2.5, settings.get_bend_cost(0));
    assertEquals(5.0, settings.get_bend_cost(1));

    // Clamping to minimum
    settings.set_bend_cost(0, -1.0);
    assertEquals(0.0, settings.get_bend_cost(0));

    // Clamping to maximum
    settings.set_bend_cost(1, 15.0);
    assertEquals(9.9, settings.get_bend_cost(1));
  }

  @Test
  public void testSerialization() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.set_bend_cost(0, 1.5);
    settings.set_bend_cost(1, 2.5);

    Gson gson = new Gson();

    // Since layers are transient, verify defaultBendCost serialization
    settings.scoring.defaultBendCost = 3.5;
    String json = gson.toJson(settings.scoring);
    RouterScoringSettings deserializedScoring = gson.fromJson(json, RouterScoringSettings.class);
    assertEquals(3.5, deserializedScoring.defaultBendCost);
  }

  @Test
  public void testNullScoringSafety() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.scoring = null;

    // Testing clone with null scoring
    RouterSettings cloned = settings.clone();
    assertNotNull(cloned.scoring);

    // Testing get/set methods with null scoring
    assertEquals(1, settings.get_start_ripup_costs());
    settings.set_start_ripup_costs(5);
    assertEquals(5, settings.get_start_ripup_costs());

    assertEquals(1, settings.get_via_costs());
    settings.set_via_costs(3);
    assertEquals(3, settings.get_via_costs());

    assertEquals(1.0, settings.get_preferred_direction_trace_costs(0));
    settings.scoring = null; // reset to null
    settings.set_preferred_direction_trace_costs(0, 2.0);
    assertEquals(2.0, settings.get_preferred_direction_trace_costs(0));

    // Construct a dummy board to test applyBoardSpecificOptimizations with null scoring
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

    settings.scoring = null;
    settings.applyBoardSpecificOptimizations(board);
    assertNotNull(settings.scoring);
  }
}

