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
}
