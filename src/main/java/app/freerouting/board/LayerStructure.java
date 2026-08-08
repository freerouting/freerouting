package app.freerouting.board;

import java.io.Serializable;

/** Describes the layer structure of the board. */
public class LayerStructure implements Serializable {

  public final Layer[] arr;

  /** Creates a new instance of LayerStructure */
  public LayerStructure(Layer[] p_layer_arr) {
    arr = p_layer_arr;
  }

  /**
   * Returns the index of the layer with the name p_name in the array arr, -1, if arr contains no
   * layer with name p_name.
   */
  public int getNo(String p_name) {
    for (int i = 0; i < arr.length; i++) {
      if (p_name.equals(arr[i].name)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the index of p_layer in the array arr, or -1, if arr does not contain p_layer. */
  public int getNo(Layer p_layer) {
    for (int i = 0; i < arr.length; i++) {
      if (p_layer == arr[i]) {
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

  /** Gets the p_no-th signal layer of this layer structure. */
  public Layer getSignalLayer(int p_no) {
    int foundSignalLayers = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].isSignal) {
        if (p_no == foundSignalLayers) {
          return arr[i];
        }
        ++foundSignalLayers;
      }
    }
    return arr[arr.length - 1];
  }

  /** Returns the count of signal layers with a smaller number than p_layer */
  public int getSignalLayerNo(Layer p_layer) {
    int foundSignalLayers = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i] == p_layer) {
        return foundSignalLayers;
      }
      if (arr[i].isSignal) {
        ++foundSignalLayers;
      }
    }
    return -1;
  }

  /** Gets the layer number of the p_signal_layer_no-th signal layer in this layer structure */
  public int getLayerNo(int p_signal_layer_no) {
    Layer currSignalLayer = getSignalLayer(p_signal_layer_no);
    return getNo(currSignalLayer);
  }
}
