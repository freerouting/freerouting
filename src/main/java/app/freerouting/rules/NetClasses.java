package app.freerouting.rules;

import app.freerouting.board.LayerStructure;
import java.io.Serializable;
import java.util.Vector;

/** Contains the array of net classes for interactive routing. */
public class NetClasses implements Serializable {

  private final Vector<NetClass> classArr = new Vector<>();

  /** Returns the number of classes in this array. */
  public int count() {
    return classArr.size();
  }

  /** Returns the net class with index p_index. */
  public NetClass get(int p_index) {
    assert p_index >= 0 && p_index <= classArr.size() - 1;
    return classArr.get(p_index);
  }

  /** Returns the net class with name p_name, or null, if no such class exists. */
  public NetClass get(String p_name) {
    for (NetClass currClass : this.classArr) {
      if (currClass.get_name().equals(p_name)) {
        return currClass;
      }
    }
    return null;
  }

  /** Appends a new empty class with name p_name to the class array */
  public NetClass append(
      String p_name,
      LayerStructure p_layer_structure,
      ClearanceMatrix p_clearance_matrix,
      boolean p_is_ignored_by_autorouter) {
    NetClass newClass =
        new NetClass(p_name, p_layer_structure, p_clearance_matrix, p_is_ignored_by_autorouter);
    classArr.add(newClass);
    return newClass;
  }

  /** Appends a new empty class to the class array. A name for the class is created internally */
  public NetClass append(LayerStructure p_layer_structure, ClearanceMatrix p_clearance_matrix) {
    String newName;
    int index = 0;
    do {
      ++index;
      newName = "class" + index;
    } while (this.get(newName) != null);
    return append(newName, p_layer_structure, p_clearance_matrix, false);
  }

  /**
   * Looks, if the list contains a net class with trace half widths all equal to p_trace_half_width,
   * trace clearance class equal to p_trace_clearance_class and via rule equal to p_cia_rule.
   * Returns null, if no such net class was found.
   */
  public NetClass find(int p_trace_half_width, int p_trace_clearance_class, ViaRule p_via_rule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.get_trace_clearance_class() == p_trace_clearance_class
          && currClass.get_via_rule() == p_via_rule) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layer_count(); i++) {
          if (currClass.get_trace_half_width(i) != p_trace_half_width) {
            traceWidthsEqual = false;
            break;
          }
        }
        if (traceWidthsEqual) {
          return currClass;
        }
      }
    }
    return null;
  }

  /**
   * Looks, if the list contains a net class with trace half width[i] all equal to
   * p_trace_half_width_arr[i] for 0 {@literal <}= i {@literal <} layerCount, trace clearance class
   * equal to p_trace_clearance_class and via rule equal to p_via_rule. Returns null, if no such net
   * class was found.
   */
  public NetClass find(
      int[] p_trace_half_width_arr, int p_trace_clearance_class, ViaRule p_via_rule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.get_trace_clearance_class() == p_trace_clearance_class
          && currClass.get_via_rule() == p_via_rule
          && p_trace_half_width_arr.length == currClass.layer_count()) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layer_count(); i++) {
          if (currClass.get_trace_half_width(i) != p_trace_half_width_arr[i]) {
            traceWidthsEqual = false;
            break;
          }
        }
        if (traceWidthsEqual) {
          return currClass;
        }
      }
    }
    return null;
  }

  /**
   * Removes p_net_class from this list. Returns false, if p_net_class was not contained in the
   * list.
   */
  public boolean remove(NetClass p_net_class) {
    return this.classArr.remove(p_net_class);
  }
}
