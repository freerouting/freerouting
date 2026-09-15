package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.Iterator;

/** Describes a layer structure read from a dsn file. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class LayerStructure {

  public final Layer[] layers;

  /** Creates a new instance of LayerStructure from a list of layers. */
  public LayerStructure(Collection<Layer> layerList) {
    layers = new Layer[layerList.size()];
    Iterator<Layer> it = layerList.iterator();
    for (int i = 0; i < layers.length; i++) {
      layers[i] = it.next();
    }
  }

  /** Creates a dsn-LayerStructure from a board LayerStructure. */
  public LayerStructure(app.freerouting.board.model.structure.LayerStructure boardLayerStructure) {
    layers = new Layer[boardLayerStructure.layers.length];
    for (int i = 0; i < layers.length; i++) {
      app.freerouting.board.model.structure.Layer boardLayer = boardLayerStructure.layers[i];
      layers[i] = new Layer(boardLayer.name, i, boardLayer.isSignal);
    }
  }

  /** Returns the number of the named layer, or {@code -1} when it does not exist. */
  public int getNo(String name) {
    for (int i = 0; i < layers.length; i++) {
      if (name.equals(layers[i].name)) {
        return i;
      }
    }
    // check for special layers of the Electra autorouter used for the outline
    if (name.contains("Top")) {
      return 0;
    }
    if (name.contains("Bottom")) {
      return layers.length - 1;
    }
    return -1;
  }

  public int signalLayerCount() {
    int result = 0;
    for (Layer currentLayer : layers) {
      if (currentLayer.isSignal) {
        ++result;
      }
    }
    return result;
  }

  /** Returns, if the net with name netName contains a power plane. */
  public boolean containsPlane(String netName) {

    for (Layer currentLayer : layers) {
      if (!currentLayer.isSignal) {
        if (currentLayer.netNames.contains(netName)) {
          return true;
        }
      }
    }
    return false;
  }
}
