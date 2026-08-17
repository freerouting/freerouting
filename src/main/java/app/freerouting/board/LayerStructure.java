package app.freerouting.board;

import java.io.Serializable;

/** Describes the layer structure of the board. */
public class LayerStructure implements Serializable {

  public final Layer[] layers;

  /** Creates a new instance of LayerStructure. */
  public LayerStructure(Layer[] layers) {
    this.layers = layers;
  }

  /**
   * Returns the index of the layer with the name name in the array layers, -1, if layers contains
   * no layer with name name.
   */
  public int getNo(String name) {
    for (int i = 0; i < layers.length; i++) {
      if (name.equals(layers[i].name)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the index of layer in the array layers, or -1, if layers does not contain layer. */
  public int getNo(Layer layer) {
    for (int i = 0; i < layers.length; i++) {
      if (layer == layers[i]) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the count of signal layers of this layerStructure. */
  public int signalLayerCount() {
    int foundSignalLayers = 0;
    for (int i = 0; i < layers.length; i++) {
      if (layers[i].isSignal) {
        ++foundSignalLayers;
      }
    }
    return foundSignalLayers;
  }

  /** Gets the no-th signal layer of this layer structure. */
  public Layer getSignalLayer(int no) {
    int foundSignalLayers = 0;
    for (int i = 0; i < layers.length; i++) {
      if (layers[i].isSignal) {
        if (no == foundSignalLayers) {
          return layers[i];
        }
        ++foundSignalLayers;
      }
    }
    return layers[layers.length - 1];
  }

  /** Returns the count of signal layers with a smaller number than layer. */
  public int getSignalLayerNo(Layer layer) {
    int foundSignalLayers = 0;
    for (int i = 0; i < layers.length; i++) {
      if (layers[i] == layer) {
        return foundSignalLayers;
      }
      if (layers[i].isSignal) {
        ++foundSignalLayers;
      }
    }
    return -1;
  }

  /** Gets the layer number of the signalLayerNo-th signal layer in this layer structure. */
  public int getLayerNo(int signalLayerNo) {
    Layer currentSignalLayer = getSignalLayer(signalLayerNo);
    return getNo(currentSignalLayer);
  }
}
