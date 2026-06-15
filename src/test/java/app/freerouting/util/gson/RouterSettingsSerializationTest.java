package app.freerouting.util.gson;

import static org.junit.jupiter.api.Assertions.*;

import app.freerouting.settings.LayerSettings;
import app.freerouting.settings.RouterSettings;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class RouterSettingsSerializationTest {

  @Test
  void testLayersFieldRemainsTransient() throws NoSuchFieldException {
    var field = RouterSettings.class.getField("layers");
    assertTrue(Modifier.isTransient(field.getModifiers()), "The layers field MUST remain transient");
  }

  @Test
  void testRouterSettingsSerializationAndDeserialization() {
    // 1. Setup a RouterSettings object with non-null layers
    RouterSettings original = new RouterSettings();
    original.setLayerCount(2);
    original.layers[0] = new LayerSettings(false, true);
    original.layers[1] = new LayerSettings(true, false);
    original.maxPasses = 42;

    // 2. Serialize to JSON using GsonProvider.GSON (must NOT contain layers since we never serialize it)
    String json = GsonProvider.GSON.toJson(original);
    assertNotNull(json);
    assertFalse(json.contains("\"layers\""), "Serialized JSON must NOT contain the 'layers' field");
    assertTrue(json.contains("\"max_passes\""), "Serialized JSON must contain other settings like 'max_passes'");

    // 3. Deserialize from the serialized JSON
    RouterSettings deserializedFromEmptyLayers = GsonProvider.GSON.fromJson(json, RouterSettings.class);
    assertNotNull(deserializedFromEmptyLayers);
    assertEquals(42, deserializedFromEmptyLayers.maxPasses);
    assertNull(deserializedFromEmptyLayers.layers, "Layers array should be null since it was not serialized");

    // 4. Deserialize from a JSON payload that contains the layers array (simulating API call)
    String apiJson = "{\n"
        + "  \"max_passes\": 42,\n"
        + "  \"layers\": [\n"
        + "    {\"routable\": false, \"preferred_direction_horizontal\": true},\n"
        + "    {\"routable\": true, \"preferred_direction_horizontal\": false}\n"
        + "  ]\n"
        + "}";
    RouterSettings deserializedFromApi = GsonProvider.GSON.fromJson(apiJson, RouterSettings.class);
    assertNotNull(deserializedFromApi);
    assertEquals(42, deserializedFromApi.maxPasses);
    assertNotNull(deserializedFromApi.layers, "The transient layers field must be successfully deserialized when present in the JSON");
    assertEquals(2, deserializedFromApi.layers.length);
    assertFalse(deserializedFromApi.layers[0].routable);
    assertTrue(deserializedFromApi.layers[0].preferredDirectionHorizontal);
    assertTrue(deserializedFromApi.layers[1].routable);
    assertFalse(deserializedFromApi.layers[1].preferredDirectionHorizontal);
  }
}

