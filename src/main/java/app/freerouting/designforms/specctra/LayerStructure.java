package app.freerouting.designforms.specctra;

import java.util.Collection;
import java.util.Iterator;

/** Describes a layer structure read from a dsn file. */
public class LayerStructure {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure from a list of layers */
  public LayerStructure(Collection<Layer> p_layer_list) {
    arr = new Layer[p_layer_list.size()];
    Iterator<Layer> it = p_layer_list.iterator();
    for (int i = 0; i < arr.length; ++i) {
      arr[i] = it.next();
    }
  }

  /** Creates a dsn-LayerStructure from a board LayerStructure. */
  public LayerStructure(app.freerouting.board.LayerStructure p_board_layer_structure) {
    arr = new Layer[p_board_layer_structure.arr.length];
    for (int i = 0; i < arr.length; ++i) {
      app.freerouting.board.Layer board_layer = p_board_layer_structure.arr[i];
      arr[i] = new Layer(board_layer.name, i, board_layer.is_signal);
    }
  }

  /**
   * returns the number of the layer with the name p_name, -1, if no layer with name p_name exists.
   */
  public int get_no(String p_name) {
    for (int i = 0; i < arr.length; ++i) {
      if (p_name.equals(arr[i].name)) {
        return i;
      }
    }
    // check for special layers of the Electra autorouter used for the outline
    if (p_name.contains("Top")) {
      return 0;
    }
    if (p_name.contains("Bottom")) {
      return arr.length - 1;
    }
    return -1;
  }

  public int signal_layer_count() {
    int result = 0;
    for (Layer curr_layer : arr) {
      if (curr_layer.is_signal) {
        ++result;
      }
    }
    return result;
  }

  /** Returns, if the net with name p_net_name contains a powwer plane. */
  public boolean contains_plane(String p_net_name) {

    for (Layer curr_layer : arr) {
      if (!curr_layer.is_signal) {
        if (curr_layer.net_names.contains(p_net_name)) {
          return true;
        }
      }
    }
    return false;
  }
}
