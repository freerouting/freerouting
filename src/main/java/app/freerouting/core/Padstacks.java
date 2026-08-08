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
  public Padstacks(LayerStructure pLayerStructure) {
    boardLayerStructure = pLayerStructure;
    padstackArr = new Vector<>();
  }

  /** Returns the padstack with the input name or null, if no such padstack exists. */
  public Padstack get(String pName) {
    for (Padstack currPadstack : padstackArr) {
      if (currPadstack != null && currPadstack.name.equalsIgnoreCase(pName)) {
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
  public Padstack get(int pPadstackNo) {
    if (pPadstackNo <= 0 || pPadstackNo > padstackArr.size()) {
      int padstackCount = padstackArr.size();
      FRLogger.warn("Padstacks.get: 1 <= p_padstack_no <= " + padstackCount + " expected");
      return null;
    }
    Padstack result = padstackArr.elementAt(pPadstackNo - 1);
    if (result != null && result.no != pPadstackNo) {
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
      String pName, ConvexShape[] pShapes, boolean pDrillAllowed, boolean pPlacedAbsolute) {
    Padstack newPadstack =
        new Padstack(pName, padstackArr.size() + 1, pShapes, pDrillAllowed, pPlacedAbsolute, this);
    padstackArr.add(newPadstack);
    return newPadstack;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of
   * dimension board layerCount. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape[] pShapes) {
    String newName = "padstack#" + (padstackArr.size() + 1);
    return add(newName, pShapes, false, false);
  }

  /**
   * Appends a new padstack with the input shape from p_from_layer to p_to_layer and null on the
   * other layers. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape pShape, int pFromLayer, int pToLayer) {
    ConvexShape[] shapeArr = new ConvexShape[boardLayerStructure.arr.length];
    int fromLayer = Math.max(pFromLayer, 0);
    int toLayer = Math.min(pToLayer, boardLayerStructure.arr.length - 1);
    for (int i = fromLayer; i <= toLayer; i++) {
      shapeArr[i] = pShape;
    }
    return add(shapeArr);
  }
}
