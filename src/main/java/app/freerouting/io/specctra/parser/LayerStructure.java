package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.Iterator;

/** Describes a layer structure read from a dsn file. */
public class LayerStructure {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure from a list of layers */
  public LayerStructure(Collection<Layer> p_layer_list) {
    arr = new Layer[p_layer_list.size()];
    Iterator<Layer> it = p_layer_list.iterator();
    for (int i = 0; i < arr.length; i++) {
      arr[i] = it.next();
    }
  }

  /** Creates a dsn-LayerStructure from a board LayerStructure. */
  public LayerStructure(app.freerouting.board.LayerStructure p_board_layer_structure) {
    arr = new Layer[p_board_layer_structure.arr.length];
    for (int i = 0; i < arr.length; i++) {
      app.freerouting.board.Layer boardLayer = p_board_layer_structure.arr[i];
      arr[i] = new Layer(boardLayer.name, i, boardLayer.isSignal);
    }
  }

  /**
   * returns the number of the layer with the name p_name, -1, if no layer with name p_name exists.
   */
  public int getNo(String p_name) {
    for (int i = 0; i < arr.length; i++) {
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

  public int signalLayerCount() {
    int result = 0;
    for (Layer currLayer : arr) {
      if (currLayer.isSignal) {
        ++result;
      }
    }
    return result;
  }

  /** Returns, if the net with name p_net_name contains a power plane. */
  public boolean containsPlane(String p_net_name) {

    for (Layer currLayer : arr) {
      if (!currLayer.isSignal) {
        if (currLayer.netNames.contains(p_net_name)) {
          return true;
        }
      }
    }
    return false;
  }
}
