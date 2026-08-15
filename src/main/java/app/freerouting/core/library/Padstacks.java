package app.freerouting.core.library;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Vector;

/** Describes a library of padstacks for pins or vias. */
public class Padstacks implements Serializable {

  /** The layer structure of each padstack. */
  public final LayerStructure boardLayerStructure;

  /** The array of padstacks in this object. */
  private final Vector<Padstack> padstacks;

  /** Creates a new instance of Padstacks. */
  public Padstacks(LayerStructure layerStructure) {
    boardLayerStructure = layerStructure;
    padstacks = new Vector<>();
  }

  /** Returns the padstack with the input name or null, if no such padstack exists. */
  public Padstack get(String name) {
    for (Padstack currentPadstack : padstacks) {
      if (currentPadstack != null && currentPadstack.name.equalsIgnoreCase(name)) {
        return currentPadstack;
      }
    }
    return null;
  }

  /** Returns the padstack with the specified index. Padstack numbers start at 1. */
  public Padstack get(int padstackNo) {
    if (padstackNo <= 0 || padstackNo > padstacks.size()) {
      int padstackCount = padstacks.size();
      FRLogger.warn("Padstacks.get: 1 <= padstackNo <= " + padstackCount + " expected");
      return null;
    }
    Padstack result = padstacks.elementAt(padstackNo - 1);
    if (result != null && result.no != padstackNo) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /** Returns the count of padstacks in this object. */
  public int count() {
    return padstacks.size();
  }

  /** Appends a new padstack with the input shapes to this padstacks. */
  public Padstack add(
      String name, ConvexShape[] shapes, boolean drillAllowed, boolean placedAbsolute) {
    Padstack newPadstack =
        new Padstack(name, padstacks.size() + 1, shapes, drillAllowed, placedAbsolute, this);
    padstacks.add(newPadstack);
    return newPadstack;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. The dimension board layerCount.
   * The padstack name is generated internally.
   */
  public Padstack add(ConvexShape[] shapes) {
    String newName = "padstack#" + (padstacks.size() + 1);
    return add(newName, shapes, false, false);
  }

  /**
   * Appends a new padstack with the input shape from one layer to another and null on the other
   * layers. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape shape, int fromLayer, int toLayer) {
    ConvexShape[] shapes = new ConvexShape[boardLayerStructure.layers.length];
    int firstLayer = Math.max(fromLayer, 0);
    int lastLayer = Math.min(toLayer, boardLayerStructure.layers.length - 1);
    for (int i = firstLayer; i <= lastLayer; i++) {
      shapes[i] = shape;
    }
    return add(shapes);
  }
}
