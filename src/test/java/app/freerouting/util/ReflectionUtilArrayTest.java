package app.freerouting.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.LayerSettings;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

class ReflectionUtilArrayTest {

  @Test
  void testSetSimpleProperty() throws Exception {
    RouterSettings settings = new RouterSettings();
    ReflectionUtil.setFieldValue(settings, "enabled", "false");
    assertFalse(settings.enabled);

    ReflectionUtil.setFieldValue(settings, "enabled", "true");
    assertTrue(settings.enabled);
  }

  @Test
  void testSetNestedArrayPropertiesWhenNull() throws Exception {
    RouterSettings settings = new RouterSettings();
    // settings.layers is initially null
    ReflectionUtil.setFieldValue(settings, "layers.routable", "false,true");

    assertNotNull(settings.layers);
    assertEquals(2, settings.layers.length);
    assertFalse(settings.layers[0].routable);
    assertTrue(settings.layers[1].routable);
  }

  @Test
  void testSetNestedArrayPropertiesWhenInitialized() throws Exception {
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
  void testSerializedNameOnlyMatching() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);

    // Should succeed because preferred_direction_horizontal is the SerializedName value
    try {
      ReflectionUtil.setFieldValue(settings, "layers.preferred_direction_horizontal", "true,false");
      assertTrue(settings.layers[0].preferredDirectionHorizontal);
    } catch (Exception e) {
      throw new AssertionError("Should not have thrown exception", e);
    }

    // Should throw NoSuchFieldException because the Java field name is preferredDirectionHorizontal
    // but the SerializedName annotation value is preferred_direction_horizontal, so only the annotation value must match.
    assertThrows(NoSuchFieldException.class, () -> {
      ReflectionUtil.setFieldValue(settings, "layers.preferredDirectionHorizontal", "true,false");
    });

    // Similarly for routable - it matches because the SerializedName is "routable"
    try {
      ReflectionUtil.setFieldValue(settings, "layers.routable", "true,true");
      assertTrue(settings.layers[0].routable);
    } catch (Exception e) {
      throw new AssertionError("Should not have thrown exception", e);
    }
  }
}

