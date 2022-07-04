package app.freerouting.rules;

import app.freerouting.logger.FRLogger;

/** Describes routing rules for individual nets. */
public class NetClass
    implements java.io.Serializable, app.freerouting.board.ObjectInfoPanel.Printable {

  private final ClearanceMatrix clearance_matrix;
  private final app.freerouting.board.LayerStructure board_layer_structure;
  /**
   * The clearance classes of the item types, if this net class comes from a class in a Speccctra
   * dsn-file Should eventually be moved to NetClass and used only when reading a dsn-file.
   */
  public DefaultItemClearanceClasses default_item_clearance_classes =
      new DefaultItemClearanceClasses();
  public boolean is_ignored_by_autorouter = false;
  private String name;
  private ViaRule via_rule;
  private int trace_clearance_class;
  private final int[] trace_half_width_arr;
  private final boolean[] active_routing_layer_arr;
  /** if null, all signal layers may be used for routing */
  private boolean shove_fixed = false;
  private boolean pull_tight = true;
  private boolean ignore_cycles_with_areas = false;
  private double minimum_trace_length = 0;
  private double maximum_trace_length = 0;

  /** Creates a new instance of NetClass */
  public NetClass(
      String p_name,
      app.freerouting.board.LayerStructure p_layer_structure,
      ClearanceMatrix p_clearance_matrix,
      boolean p_is_ignored_by_autorouter) {
    this.name = p_name;
    this.board_layer_structure = p_layer_structure;
    this.clearance_matrix = p_clearance_matrix;
    this.trace_half_width_arr = new int[p_layer_structure.arr.length];
    this.active_routing_layer_arr = new boolean[p_layer_structure.arr.length];
    for (int i = 0; i < p_layer_structure.arr.length; ++i) {
      this.active_routing_layer_arr[i] = p_layer_structure.arr[i].is_signal;
    }
    this.is_ignored_by_autorouter = p_is_ignored_by_autorouter;
  }

  public String toString() {
    return this.name;
  }

  /** Gets the name of this net class. */
  public String get_name() {
    return this.name;
  }

  /** Changes the name of this net class. */
  public void set_name(String p_name) {
    this.name = p_name;
  }

  /** Sets the trace half width used for routing to p_value on all layers. */
  public void set_trace_half_width(int p_value) {
    java.util.Arrays.fill(trace_half_width_arr, p_value);
  }

  /** Sets the trace half width used for routing to p_value on all inner layers. */
  public void set_trace_half_width_on_inner(int p_value) {
    for (int i = 1; i < trace_half_width_arr.length - 1; ++i) {
      trace_half_width_arr[i] = p_value;
    }
  }

  /** Sets the trace half width used for routing to p_value on the input layer. */
  public void set_trace_half_width(int p_layer, int p_value) {
    trace_half_width_arr[p_layer] = p_value;
  }

  public int layer_count() {
    return trace_half_width_arr.length;
  }

  /** Gets the trace half width used for routing on the input layer. */
  public int get_trace_half_width(int p_layer) {
    if (p_layer < 0 || p_layer >= trace_half_width_arr.length) {
      FRLogger.warn(" NetClass.get_trace_half_width: p_layer out of range");
      return 0;
    }
    return trace_half_width_arr[p_layer];
  }

  /** Gets the clearance class used for routing traces with this net class. */
  public int get_trace_clearance_class() {
    return this.trace_clearance_class;
  }

  /** Sets the clearance class used for routing traces with this net rclass. */
  public void set_trace_clearance_class(int p_clearance_class_no) {
    this.trace_clearance_class = p_clearance_class_no;
  }

  /** Gets the via rule of this net rule. */
  public ViaRule get_via_rule() {
    return this.via_rule;
  }

  /** Sets the via rule of this net class. */
  public void set_via_rule(ViaRule p_via_rule) {
    this.via_rule = p_via_rule;
  }

  /** Returns, if traces and vias of this net class can be pushed. */
  public boolean is_shove_fixed() {
    return this.shove_fixed;
  }

  /** Sets, if traces and vias of this net class can be pushed. */
  public void set_shove_fixed(boolean p_value) {
    this.shove_fixed = p_value;
  }

  /** Returns, if traces of this nets class are pulled tight. */
  public boolean get_pull_tight() {
    return this.pull_tight;
  }

  /** Sets, if traces of this nets class are pulled tight. */
  public void set_pull_tight(boolean p_value) {
    this.pull_tight = p_value;
  }

  /** Returns, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public boolean get_ignore_cycles_with_areas() {
    return this.ignore_cycles_with_areas;
  }

  /** Sets, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public void set_ignore_cycles_with_areas(boolean p_value) {
    this.ignore_cycles_with_areas = p_value;
  }

  /**
   * Returns the minimum trace length of this net class. If the result is {@literal <}= 0, there is
   * no minimal trace length restriction.
   */
  public double get_minimum_trace_length() {
    return minimum_trace_length;
  }

  /**
   * Sets the minimum trace length of this net class to p_value. If p_value is {@literal <}= 0,
   * there is no minimal trace length restriction.
   */
  public void set_minimum_trace_length(double p_value) {
    minimum_trace_length = p_value;
  }

  /**
   * Returns the maximum trace length of this net class. If the result is {@literal <}= 0, there is
   * no maximal trace length restriction.
   */
  public double get_maximum_trace_length() {
    return maximum_trace_length;
  }

  /**
   * Sets the maximum trace length of this net class to p_value. If p_value is {@literal <}= 0,
   * there is no maximal trace length restriction.
   */
  public void set_maximum_trace_length(double p_value) {
    maximum_trace_length = p_value;
  }

  /** Returns if the layer with index p_layer_no is active for routing */
  public boolean is_active_routing_layer(int p_layer_no) {
    if (p_layer_no < 0 || p_layer_no >= this.active_routing_layer_arr.length) {
      return false;
    }
    return this.active_routing_layer_arr[p_layer_no];
  }

  /** Sets the layer with index p_layer_no to p_active. */
  public void set_active_routing_layer(int p_layer_no, boolean p_active) {
    if (p_layer_no < 0 || p_layer_no >= this.active_routing_layer_arr.length) {
      return;
    }
    this.active_routing_layer_arr[p_layer_no] = p_active;
  }

  /** Activates or deactivates all layers for routing */
  public void set_all_layers_active(boolean p_value) {
    java.util.Arrays.fill(this.active_routing_layer_arr, p_value);
  }

  /** Activates or deactivates all inner layers for routing */
  public void set_all_inner_layers_active(boolean p_value) {
    for (int i = 1; i < trace_half_width_arr.length - 1; ++i) {
      active_routing_layer_arr[i] = p_value;
    }
  }

  public void print_info(
      app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("net_class_2") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(":");
    p_window.append(" " + resources.getString("trace_clearance_class") + " ");
    String cl_name = clearance_matrix.get_name(this.trace_clearance_class);
    p_window.append(
        cl_name,
        resources.getString("trace_clearance_class_2"),
        clearance_matrix.get_row(this.trace_clearance_class));
    if (this.shove_fixed) {
      p_window.append(", " + resources.getString("shove_fixed"));
    }
    p_window.append(", " + resources.getString("via_rule") + " ");
    p_window.append(via_rule.name, resources.getString("via_rule_2"), via_rule);
    if (trace_width_is_layer_dependent()) {
      for (int i = 0; i < trace_half_width_arr.length; ++i) {
        p_window.newline();
        p_window.indent();
        p_window.append(resources.getString("trace_width") + " ");
        p_window.append(2 * trace_half_width_arr[i]);
        p_window.append(" " + resources.getString("on_layer") + " ");
        p_window.append(this.board_layer_structure.arr[i].name);
      }
    } else {
      p_window.append(", " + resources.getString("trace_width") + " ");
      p_window.append(2 * trace_half_width_arr[0]);
    }
    p_window.newline();
  }

  /** Returns true, if the trace width of this class is not equal on all layers. */
  public boolean trace_width_is_layer_dependent() {
    int compare_value = trace_half_width_arr[0];
    for (int i = 1; i < trace_half_width_arr.length; ++i) {
      if (this.board_layer_structure.arr[i].is_signal) {
        if (trace_half_width_arr[i] != compare_value) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns true, if the trace width of this class is not equal on all inner layers. */
  public boolean trace_width_is_inner_layer_dependent() {

    if (trace_half_width_arr.length <= 3) {
      return false;
    }
    int first_inner_layer_no = 1;
    while (!this.board_layer_structure.arr[first_inner_layer_no].is_signal) {
      ++first_inner_layer_no;
    }
    if (first_inner_layer_no >= trace_half_width_arr.length - 1) {
      return false;
    }
    int compare_width = trace_half_width_arr[first_inner_layer_no];
    for (int i = first_inner_layer_no + 1; i < trace_half_width_arr.length - 1; ++i) {
      if (this.board_layer_structure.arr[i].is_signal) {
        if (trace_half_width_arr[i] != compare_width) {
          return true;
        }
      }
    }
    return false;
  }
}
