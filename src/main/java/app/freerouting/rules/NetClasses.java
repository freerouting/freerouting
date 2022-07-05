package app.freerouting.rules;

/** Contains the array of net classes for interactive routing. */
public class NetClasses implements java.io.Serializable {
  private final java.util.Vector<NetClass> class_arr = new java.util.Vector<NetClass>();

  /** Returns the number of classes in this array. */
  public int count() {
    return class_arr.size();
  }

  /** Returns the net class with index p_index. */
  public NetClass get(int p_index) {
    assert p_index >= 0 && p_index <= class_arr.size() - 1;
    return class_arr.get(p_index);
  }

  /** Returns the net class with name p_name, or null, if no such class exists. */
  public NetClass get(String p_name) {
    for (NetClass curr_class : this.class_arr) {
      if (curr_class.get_name().equals(p_name)) {
        return curr_class;
      }
    }
    return null;
  }

  /** Appends a new empty class with name p_name to the class array */
  NetClass append(
      String p_name,
      app.freerouting.board.LayerStructure p_layer_structure,
      ClearanceMatrix p_clearance_matrix,
      boolean p_is_ignored_by_autorouter) {
    NetClass new_class =
        new NetClass(p_name, p_layer_structure, p_clearance_matrix, p_is_ignored_by_autorouter);
    class_arr.add(new_class);
    return new_class;
  }

  /** Appends a new empty class to the class array. A name for the class is created internally */
  NetClass append(
      app.freerouting.board.LayerStructure p_layer_structure,
      ClearanceMatrix p_clearance_matrix,
      java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.rules.Rules", p_locale);
    String name_front = resources.getString("class");
    String new_name = null;
    Integer index = 0;
    for (; ; ) {
      ++index;
      new_name = name_front + index;
      if (this.get(new_name) == null) {
        break;
      }
    }
    return append(new_name, p_layer_structure, p_clearance_matrix, false);
  }

  /**
   * Looks, if the list contains a net class with trace half widths all equal to p_trace_half_width,
   * trace clearance class equal to p_trace_clearance_class and via rule equal to p_cia_rule.
   * Returns null, if no such net class was found.
   */
  public NetClass find(int p_trace_half_width, int p_trace_clearance_class, ViaRule p_via_rule) {
    for (NetClass curr_class : this.class_arr) {
      if (curr_class.get_trace_clearance_class() == p_trace_clearance_class
          && curr_class.get_via_rule() == p_via_rule) {
        boolean trace_widths_equal = true;
        for (int i = 0; i < curr_class.layer_count(); ++i) {
          if (curr_class.get_trace_half_width(i) != p_trace_half_width) {
            trace_widths_equal = false;
            break;
          }
        }
        if (trace_widths_equal) {
          return curr_class;
        }
      }
    }
    return null;
  }

  /**
   * Looks, if the list contains a net class with trace half width[i] all equal to
   * p_trace_half_width_arr[i] for 0 {@literal <}= i {@literal <} layer_count, trace clearance class
   * equal to p_trace_clearance_class and via rule equal to p_via_rule. Returns null, if no such net
   * class was found.
   */
  public NetClass find(
      int[] p_trace_half_width_arr, int p_trace_clearance_class, ViaRule p_via_rule) {
    for (NetClass curr_class : this.class_arr) {
      if (curr_class.get_trace_clearance_class() == p_trace_clearance_class
          && curr_class.get_via_rule() == p_via_rule
          && p_trace_half_width_arr.length == curr_class.layer_count()) {
        boolean trace_widths_equal = true;
        for (int i = 0; i < curr_class.layer_count(); ++i) {
          if (curr_class.get_trace_half_width(i) != p_trace_half_width_arr[i]) {
            trace_widths_equal = false;
            break;
          }
        }
        if (trace_widths_equal) {
          return curr_class;
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
    return this.class_arr.remove(p_net_class);
  }
}
