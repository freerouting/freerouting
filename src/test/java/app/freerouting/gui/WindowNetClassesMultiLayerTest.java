package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import org.junit.jupiter.api.Test;

public class WindowNetClassesMultiLayerTest {

  @Test
  public void applyActiveLayerSelection_multiLayerBoard_updatesNetClassAndSummary() {
    Layer[] layers = new Layer[] {
        new Layer("Top", true),
        new Layer("Plane1", false),
        new Layer("Inner1", true),
        new Layer("Inner2", true),
        new Layer("Plane2", false),
        new Layer("Bottom", true)
    };

    LayerStructure ls = new LayerStructure(layers);
    NetClass netClass = new NetClass("signal", ls, ClearanceMatrix.get_default_instance(ls, 200), false);

    // Select only inner signal layers (Inner1, Inner2)
    boolean[] selection = new boolean[layers.length];
    selection[2] = true;
    selection[3] = true;

    WindowNetClasses.applyActiveLayerSelection(netClass, selection);

    // verify internal flags
    assertFalse(netClass.is_active_routing_layer(0));
    assertFalse(netClass.is_active_routing_layer(1));
    assertTrue(netClass.is_active_routing_layer(2));
    assertTrue(netClass.is_active_routing_layer(3));
    assertFalse(netClass.is_active_routing_layer(4));
    assertFalse(netClass.is_active_routing_layer(5));

    // summary should indicate inner selection canonical form
    String summary = WindowNetClasses.summarizeActiveLayerSelection(ls, selection);
    assertEquals("__inner__", summary);
  }
}
