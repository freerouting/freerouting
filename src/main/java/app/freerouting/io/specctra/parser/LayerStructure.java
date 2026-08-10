package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.Iterator;

/** Describes a layer structure read from a dsn file. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class LayerStructure {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure from a list of layers. */
  public LayerStructure(Collection<Layer> layerList) {
    arr = new Layer[layerList.size()];
    Iterator<Layer> it = layerList.iterator();
    for (int i = 0; i < arr.length; i++) {
      arr[i] = it.next();
    }
  }

  /** Creates a dsn-LayerStructure from a board LayerStructure. */
  public LayerStructure(app.freerouting.board.LayerStructure boardLayerStructure) {
    arr = new Layer[boardLayerStructure.arr.length];
    for (int i = 0; i < arr.length; i++) {
      app.freerouting.board.Layer boardLayer = boardLayerStructure.arr[i];
      arr[i] = new Layer(boardLayer.name, i, boardLayer.isSignal);
    }
  }

  /** Returns the number of the named layer, or {@code -1} when it does not exist. */
  public int getNo(String name) {
    for (int i = 0; i < arr.length; i++) {
      if (name.equals(arr[i].name)) {
        return i;
      }
    }
    // check for special layers of the Electra autorouter used for the outline
    if (name.contains("Top")) {
      return 0;
    }
    if (name.contains("Bottom")) {
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
  public boolean containsPlane(String netName) {

    for (Layer currLayer : arr) {
      if (!currLayer.isSignal) {
        if (currLayer.netNames.contains(netName)) {
          return true;
        }
      }
    }
    return false;
  }
}
