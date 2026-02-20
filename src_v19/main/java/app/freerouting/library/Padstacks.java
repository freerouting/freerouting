package app.freerouting.library;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.logger.FRLogger;

import java.io.Serializable;
import java.util.Vector;

/** Describes a library of padstacks for pins or vias. */
public class Padstacks implements Serializable {
  /** The layer structure of each padstack. */
  public final LayerStructure board_layer_structure;
  /** The array of Padstacks in this object */
  private final Vector<Padstack> padstack_arr;

  /** Creates a new instance of Padstacks */
  public Padstacks(LayerStructure p_layer_structure) {
    board_layer_structure = p_layer_structure;
    padstack_arr = new Vector<>();
  }

  /** Returns the padstack with the input name or null, if no such padstack exists. */
  public Padstack get(String p_name) {
    for (Padstack curr_padstack : padstack_arr) {
      if (curr_padstack != null && curr_padstack.name.equalsIgnoreCase(p_name)) {
        return curr_padstack;
      }
    }
    return null;
  }

  /** Returns the count of Padstacks in this object. */
  public int count() {
    return padstack_arr.size();
  }

  /**
   * Returns the padstack with index p_padstack_no for 1 {@literal <}= p_padstack_no {@literal <}=
   * padstack_count
   */
  public Padstack get(int p_padstack_no) {
    if (p_padstack_no <= 0 || p_padstack_no > padstack_arr.size()) {
      int padstack_count = padstack_arr.size();
      FRLogger.warn(
          "Padstacks.get: 1 <= p_padstack_no <= " + padstack_count + " expected");
      return null;
    }
    Padstack result = padstack_arr.elementAt(p_padstack_no - 1);
    if (result != null && result.no != p_padstack_no) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of
   * dimension board layer_count. p_drill_allowed indicates, if vias of the own net are allowed to
   * overlap with this padstack If p_placed_absolute is false, the layers of the padstack are
   * mirrored, if it is placed on the back side.
   */
  public Padstack add(
      String p_name, ConvexShape[] p_shapes, boolean p_drill_allowed, boolean p_placed_absolute) {
    Padstack new_padstack =
        new Padstack(
            p_name, padstack_arr.size() + 1, p_shapes, p_drill_allowed, p_placed_absolute, this);
    padstack_arr.add(new_padstack);
    return new_padstack;
  }

  /**
   * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of
   * dimension board layer_count. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape[] p_shapes) {
    String new_name = "padstack#" + (padstack_arr.size() + 1);
    return add(new_name, p_shapes, false, false);
  }

  /**
   * Appends a new padstack with the input shape from p_from_layer to p_to_layer and null on the
   * other layers. The padstack name is generated internally.
   */
  public Padstack add(ConvexShape p_shape, int p_from_layer, int p_to_layer) {
    ConvexShape[] shape_arr = new ConvexShape[board_layer_structure.arr.length];
    int from_layer = Math.max(p_from_layer, 0);
    int to_layer = Math.min(p_to_layer, board_layer_structure.arr.length - 1);
    for (int i = from_layer; i <= to_layer; ++i) {
      shape_arr[i] = p_shape;
    }
    return add(shape_arr);
  }
}
