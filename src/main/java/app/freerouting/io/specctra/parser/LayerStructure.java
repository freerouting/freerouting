package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.Iterator;

/** Describes a layer structure read from a dsn file. */
public class LayerStructure {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure from a list of layers */
  public LayerStructure(Collection<Layer> pLayerList) {
    arr = new Layer[pLayerList.size()];
    Iterator<Layer> it = pLayerList.iterator();
    for (int i = 0; i < arr.length; i++) {
      arr[i] = it.next();
    }
  }

  /** Creates a dsn-LayerStructure from a board LayerStructure. */
  public LayerStructure(app.freerouting.board.LayerStructure pBoardLayerStructure) {
    arr = new Layer[pBoardLayerStructure.arr.length];
    for (int i = 0; i < arr.length; i++) {
      app.freerouting.board.Layer boardLayer = pBoardLayerStructure.arr[i];
      arr[i] = new Layer(boardLayer.name, i, boardLayer.isSignal);
    }
  }

  /**
   * returns the number of the layer with the name p_name, -1, if no layer with name p_name exists.
   */
  public int getNo(String pName) {
    for (int i = 0; i < arr.length; i++) {
      if (pName.equals(arr[i].name)) {
        return i;
      }
    }
    // check for special layers of the Electra autorouter used for the outline
    if (pName.contains("Top")) {
      return 0;
    }
    if (pName.contains("Bottom")) {
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
  public boolean containsPlane(String pNetName) {

    for (Layer currLayer : arr) {
      if (!currLayer.isSignal) {
        if (currLayer.netNames.contains(pNetName)) {
          return true;
        }
      }
    }
    return false;
  }
}
