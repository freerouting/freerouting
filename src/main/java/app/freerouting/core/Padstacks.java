package app.freerouting.core;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Vector;

/** Describes a library of padstacks for pins or vias. */
public class Padstacks implements Serializable {

  /** The layer structure of each padstack. */
  public final LayerStructure boardLayerStructure;

  /** The array of Padstacks in this object */
  private final Vector<Padstack> padstackArr;

  /** Creates a new instance of Padstacks */
  public Padstacks(LayerStructure p_layer_structure) {
    boardLayerStructure = p_layer_structure;
    padstackArr = new Vector<>();
  }

  /** Returns the padstack with the input name or null, if no such padstack exists. */
  public Padstack get(String p_name) {
    for (Padstack currPadstack : padstackArr) {
      if (currPadstack != null && currPadstack.name.equalsIgnoreCase(p_name)) {
        return currPadstack;
      }
    }
    return null;
  }

  /** Returns the count of Padstacks in this object. */
  public int count() {
    return padstackArr.size();
  }

  /**
   * Returns the padstack with index p_padstack_no for 1 {@literal <}= p_padstack_no {@literal <}=
   * padstackCount
   */
  public Padstack get(int p_padstack_no) {
    if (p_padstack_no <= 0 || p_padstack_no > padstackArr.size()) {
      int padstackCount = padstackArr.size();
      FRLogger.warn("Padstacks.get: 1 <= p_padstack_no <= " + padstackCount + " expected");
      return null;
    }
    Padstack result = padstackArr.elementAt(p_padstack_no - 1);
    if (result != null && result.no != p_padstack_no) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of
   * dimension board layerCount. p_drill_allowed indicates, if vias of the own net are allowed to
   * overlap with this padstack If p_placed_absolute is false, the layers of the padstack are
   * mirrored, if it is placed on the back side.
   */
  public Padstack add(
      String p_name, ConvexShape[] p_shapes, boolean p_drill_allowed, boolean p_placed_absolute) {
    Padstack newPadstack =
        new Padstack(
            p_name, padstackArr.size() + 1, p_shapes, p_drill_allowed, p_placed_absolute, this);
    padstackArr.add(newPadstack);
    return newPadstack;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of
   * dimension board layerCount. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape[] p_shapes) {
    String newName = "padstack#" + (padstackArr.size() + 1);
    return add(newName, p_shapes, false, false);
  }

  /**
   * Appends a new padstack with the input shape from p_from_layer to p_to_layer and null on the
   * other layers. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape p_shape, int p_from_layer, int p_to_layer) {
    ConvexShape[] shapeArr = new ConvexShape[boardLayerStructure.arr.length];
    int fromLayer = Math.max(p_from_layer, 0);
    int toLayer = Math.min(p_to_layer, boardLayerStructure.arr.length - 1);
    for (int i = fromLayer; i <= toLayer; i++) {
      shapeArr[i] = p_shape;
    }
    return add(shapeArr);
  }
}
