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
    assertEquals(10.0, settings.get_bend_cost(1));
  }

  @Test
  public void testSerialization() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.set_bend_cost(0, 1.5);
    settings.set_bend_cost(1, 2.5);

    Gson gson = new Gson();
    String json = gson.toJson(settings);

    // Since layers are transient, verify defaultBendCost serialization
    settings.scoring.defaultBendCost = 3.5;
    json = gson.toJson(settings.scoring);
    RouterScoringSettings deserializedScoring = gson.fromJson(json, RouterScoringSettings.class);
    assertEquals(3.5, deserializedScoring.defaultBendCost);
  }
}
