package app.freerouting.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

class ReflectionUtilArrayTest {

  @Test
  void setSimpleProperty() throws Exception {
    RouterSettings settings = new RouterSettings();
    ReflectionUtil.setFieldValue(settings, "enabled", "false");
    assertFalse(settings.enabled);

    ReflectionUtil.setFieldValue(settings, "enabled", "true");
    assertTrue(settings.enabled);
  }

  @Test
  void setNestedArrayPropertiesWhenNull() throws Exception {
    RouterSettings settings = new RouterSettings();
    // settings.layers is initially null
    ReflectionUtil.setFieldValue(settings, "layers.routable", "false,true");

    assertNotNull(settings.layers);
    assertEquals(2, settings.layers.length);
    assertFalse(settings.layers[0].routable);
    assertTrue(settings.layers[1].routable);
  }

  @Test
  void setNestedArrayPropertiesWhenInitialized() throws Exception {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);

    ReflectionUtil.setFieldValue(settings, "layers.routable", "false,true");
    assertFalse(settings.layers[0].routable);
    assertTrue(settings.layers[1].routable);

    // Verify setting preferred direction
    ReflectionUtil.setFieldValue(settings, "layers.preferred_direction_horizontal", "true,false");
    assertTrue(settings.layers[0].preferredDirectionHorizontal);
    assertFalse(settings.layers[1].preferredDirectionHorizontal);
  }

  @Test
  void caseInsensitiveAndSerializedNameMatching() throws Exception {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);

    // Matches via SerializedName value (preferred_direction_horizontal)
    ReflectionUtil.setFieldValue(settings, "layers.preferred_direction_horizontal", "true,false");
    assertTrue(settings.layers[0].preferredDirectionHorizontal);
    assertFalse(settings.layers[1].preferredDirectionHorizontal);

    // Matches via Java field name in camelCase (preferredDirectionHorizontal)
    ReflectionUtil.setFieldValue(settings, "layers.preferredDirectionHorizontal", "false,true");
    assertFalse(settings.layers[0].preferredDirectionHorizontal);
    assertTrue(settings.layers[1].preferredDirectionHorizontal);

    // Matches via uppercase SCREAMING_SNAKE_CASE
    ReflectionUtil.setFieldValue(settings, "LAYERS.PREFERRED_DIRECTION_HORIZONTAL", "true,false");
    assertTrue(settings.layers[0].preferredDirectionHorizontal);
    assertFalse(settings.layers[1].preferredDirectionHorizontal);

    // Matches routable via SerializedName / field name
    ReflectionUtil.setFieldValue(settings, "layers.routable", "true,false");
    assertTrue(settings.layers[0].routable);
    assertFalse(settings.layers[1].routable);
  }
}
