package app.freerouting.board;

import java.io.Serializable;

/** Describes the layer structure of the board. */
public class LayerStructure implements Serializable {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure. */
  public LayerStructure(Layer[] layerArr) {
    arr = layerArr;
  }

  /**
   * Returns the index of the layer with the name name in the array arr, -1, if arr contains no
   * layer with name name.
   */
  public int getNo(String name) {
    for (int i = 0; i < arr.length; i++) {
      if (name.equals(arr[i].name)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the index of layer in the array arr, or -1, if arr does not contain layer. */
  public int getNo(Layer layer) {
    for (int i = 0; i < arr.length; i++) {
      if (layer == arr[i]) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the count of signal layers of this layerStructure. */
  public int signalLayerCount() {
    int foundSignalLayers = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].isSignal) {
        ++foundSignalLayers;
      }
    }
    return foundSignalLayers;
  }

  /** Gets the no-th signal layer of this layer structure. */
  public Layer getSignalLayer(int no) {
    int foundSignalLayers = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].isSignal) {
        if (no == foundSignalLayers) {
          return arr[i];
        }
        ++foundSignalLayers;
      }
    }
    return arr[arr.length - 1];
  }

  /** Returns the count of signal layers with a smaller number than layer. */
  public int getSignalLayerNo(Layer layer) {
    int foundSignalLayers = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i] == layer) {
        return foundSignalLayers;
      }
      if (arr[i].isSignal) {
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
