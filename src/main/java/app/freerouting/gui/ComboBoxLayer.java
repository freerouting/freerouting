package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import javax.swing.DefaultComboBoxModel;

/** A Combo Box with items for individual board layers plus an additional item for all layers. */
public class ComboBoxLayer extends javax.swing.JComboBox<ComboBoxLayer.Layer> {

  /** The custom layer index in the combobox, when all layers are selected. */
  public static final int ALL_LAYER_INDEX = -1;
  /** The custom layer index in the combobox, when all inner layers are selected. */
  public static final int INNER_LAYER_INDEX = -2;
  private final Layer[] layer_arr;

  /** Creates a new instance of LayerComboBox */
  public ComboBoxLayer(LayerStructure p_layer_structure, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.gui.Default", p_locale);
    int signal_layer_count = p_layer_structure.signal_layer_count();
    int item_count = signal_layer_count + 1;

    boolean add_inner_layer_item = signal_layer_count > 2;
    if (add_inner_layer_item) {
      ++item_count;
    }

    this.layer_arr = new Layer[item_count];
    this.layer_arr[0] = new Layer(resources.getString("all"), ALL_LAYER_INDEX);
    int curr_layer_no = 0;
    if (add_inner_layer_item) {
      this.layer_arr[1] = new Layer(resources.getString("inner"), INNER_LAYER_INDEX);
      ++curr_layer_no;
    }
    for (int i = 0; i < signal_layer_count; ++i) {
      ++curr_layer_no;
      app.freerouting.board.Layer curr_signal_layer = p_layer_structure.get_signal_layer(i);
      layer_arr[curr_layer_no] =
          new Layer(curr_signal_layer.name, p_layer_structure.get_no(curr_signal_layer));
    }
    this.setModel(new DefaultComboBoxModel<>(layer_arr));
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
    /** The index in the board layer_structure, -1 for the layers with name "all" or "inner" */
    final int index;

    Layer(String p_name, int p_index) {
      name = p_name;
      index = p_index;
    }

    public String toString() {
      return name;
    }
  }
}
