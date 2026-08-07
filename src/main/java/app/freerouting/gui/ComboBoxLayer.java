package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
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

  /** Creates a new instance of LayerComboBox */
  public ComboBoxLayer(LayerStructure p_layer_structure, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    int signalLayerCount = p_layer_structure.signal_layer_count();
    int itemCount = signalLayerCount + 1;

    boolean addInnerLayerItem = signalLayerCount > 2;
    if (addInnerLayerItem) {
      ++itemCount;
    }

    this.layerArr = new Layer[itemCount];
    this.layerArr[0] = new Layer(tm.getText("all"), ALL_LAYER_INDEX);
    int currLayerNo = 0;
    if (addInnerLayerItem) {
      this.layerArr[1] = new Layer(tm.getText("inner"), INNER_LAYER_INDEX);
      ++currLayerNo;
    }
    for (int i = 0; i < signalLayerCount; i++) {
      ++currLayerNo;
      app.freerouting.board.Layer currSignalLayer = p_layer_structure.get_signal_layer(i);
      layerArr[currLayerNo] =
          new Layer(currSignalLayer.name, p_layer_structure.get_no(currSignalLayer));
    }
    this.setModel(new DefaultComboBoxModel<>(layerArr));
    this.setSelectedIndex(0);
  }

  public Layer get_selected_layer() {
    return (Layer) this.getSelectedItem();
  }

  /**
   * Layers of the board layer structure plus layer "all". Index is the layer number in the board
   * layer structure or -1 for layer "all".
   */
  public static class Layer {

    final String name;

    /** The index in the board layerStructure, -1 for the layers with name "all" or "inner" */
    final int index;

    Layer(String p_name, int p_index) {
      name = p_name;
      index = p_index;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
