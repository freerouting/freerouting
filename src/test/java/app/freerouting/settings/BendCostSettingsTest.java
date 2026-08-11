package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.settings.sources.DefaultSettings;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class BendCostSettingsTest {

  @Test
  void defaultBendCost() {
    DefaultSettings defaultSettings = new DefaultSettings();
    RouterSettings settings = defaultSettings.getSettings();
    assertNotNull(settings.scoring);
    assertEquals(0.0, settings.scoring.defaultBendCost);
  }

  @Test
  void setGetBendCost() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);

    // Initial value defaults to 0.0
    assertEquals(0.0, settings.getBendCost(0));
    assertEquals(0.0, settings.getBendCost(1));

    // Setting valid values
    settings.setBendCost(0, 2.5);
    settings.setBendCost(1, 5.0);

    assertEquals(2.5, settings.getBendCost(0));
    assertEquals(5.0, settings.getBendCost(1));

    // Clamping to minimum
    settings.setBendCost(0, -1.0);
    assertEquals(0.0, settings.getBendCost(0));

    // Clamping to maximum
    settings.setBendCost(1, 15.0);
    assertEquals(9.9, settings.getBendCost(1));
  }

  @Test
  void serialization() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.setBendCost(0, 1.5);
    settings.setBendCost(1, 2.5);

    Gson gson = new Gson();

    // Since layers are transient, verify defaultBendCost serialization
    settings.scoring.defaultBendCost = 3.5;
    String json = gson.toJson(settings.scoring);
    ScoringSettings deserializedScoring = gson.fromJson(json, ScoringSettings.class);
    assertEquals(3.5, deserializedScoring.defaultBendCost);
  }

  @Test
  void nullScoringSafety() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.scoring = null;

    // Testing clone with null scoring
    RouterSettings cloned = settings.clone();
    assertNotNull(cloned.scoring);

    // Testing get/set methods with null scoring
    assertEquals(1, settings.getStartRipupCosts());
    settings.setStartRipupCosts(5);
    assertEquals(5, settings.getStartRipupCosts());

    assertEquals(1, settings.getViaCosts());
    settings.setViaCosts(3);
    assertEquals(3, settings.getViaCosts());

    assertEquals(1.0, settings.getPreferredDirectionTraceCosts(0));
    settings.scoring = null; // reset to null
    settings.setPreferredDirectionTraceCosts(0, 2.0);
    assertEquals(2.0, settings.getPreferredDirectionTraceCosts(0));

    // Construct a dummy board to test applyBoardSpecificOptimizations with null scoring
    app.freerouting.board.Layer layer1 = new app.freerouting.board.Layer("Top", true);
    app.freerouting.board.Layer layer2 = new app.freerouting.board.Layer("Bottom", true);
    app.freerouting.board.LayerStructure layerStructure =
        new app.freerouting.board.LayerStructure(
            new app.freerouting.board.Layer[] {layer1, layer2});
    app.freerouting.rules.ClearanceMatrix clearanceMatrix =
        app.freerouting.rules.ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    app.freerouting.rules.BoardRules boardRules =
        new app.freerouting.rules.BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    app.freerouting.board.Communication communication = new app.freerouting.board.Communication();
    app.freerouting.board.RoutingBoard board =
        new app.freerouting.board.RoutingBoard(
            new app.freerouting.geometry.planar.IntBox(0, 0, 2000000, 2000000),
            layerStructure,
            new app.freerouting.geometry.planar.PolylineShape[] {
              app.freerouting.geometry.planar.TileShape.getInstance(0, 0, 2000000, 2000000)
            },
            0,
            boardRules,
            communication);

    settings.scoring = null;
    settings.applyBoardSpecificOptimizations(board);
    assertNotNull(settings.scoring);
  }
}
