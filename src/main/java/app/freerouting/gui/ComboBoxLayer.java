package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.util.TextManager;
import java.util.Locale;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/** A Combo Box with items for individual board layers plus an additional item for all layers. */
public class ComboBoxLayer extends JComboBox<ComboBoxLayer.Layer> {

  /** The custom layer index in the combobox, when all layers are selected. */
  public static final int ALL_LAYER_INDEX = -1;

  /** The custom layer index in the combobox, when all inner layers are selected. */
  public static final int INNER_LAYER_INDEX = -2;

  private final Layer[] layerArr;

  /** Creates a new instance of LayerComboBox. */
  public ComboBoxLayer(LayerStructure layerStructure, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    int signalLayerCount = layerStructure.signalLayerCount();
    int itemCount = signalLayerCount + 1;

    boolean addInnerLayerItem = signalLayerCount > 2;
    if (addInnerLayerItem) {
      ++itemCount;
    }

    this.layerArr = new Layer[itemCount];
    this.layerArr[0] = new Layer(tm.getText("all"), ALL_LAYER_INDEX);
    int currentLayerNo = 0;
    if (addInnerLayerItem) {
      this.layerArr[1] = new Layer(tm.getText("inner"), INNER_LAYER_INDEX);
      ++currentLayerNo;
    }
    for (int i = 0; i < signalLayerCount; i++) {
      ++currentLayerNo;
      app.freerouting.board.Layer currentSignalLayer = layerStructure.getSignalLayer(i);
      layerArr[currentLayerNo] =
          new Layer(currentSignalLayer.name, layerStructure.getNo(currentSignalLayer));
    }
    this.setModel(new DefaultComboBoxModel<>(layerArr));
    this.setSelectedIndex(0);

    // Accessibility (D22): stable locator + translated accessible name/description.
    A11y.tag(this, GuiLocators.TOOLBAR_LAYER_SELECT);
    A11y.describe(this, tm.getText("change_layer"), tm.getText("change_layer_tooltip"));
  }

  public Layer getSelectedLayer() {
    return (Layer) this.getSelectedItem();
  }

  /**
   * Layers of the board layer structure plus layer "all". Index is the layer number in the board
   * layer structure or -1 for layer "all".
   */
  public static class Layer {

    final String name;

    /** The index in the board layerStructure, -1 for the layers with name "all" or "inner". */
    final int index;

    Layer(String name, int index) {
      this.name = name;
      this.index = index;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
