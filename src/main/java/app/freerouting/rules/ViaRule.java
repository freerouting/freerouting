package app.freerouting.rules;

import java.util.LinkedList;
import java.util.List;

/**
 * Contains an array of vias used for routing. Vias at the beginning of the array are preferred to
 * later vias.
 */
public class ViaRule
    implements java.io.Serializable, app.freerouting.board.ObjectInfoPanel.Printable {

  /** Empty via rule. Must not be changed. */
  public static final ViaRule EMPTY = new ViaRule("empty");
  public final String name;
  private final List<ViaInfo> list = new LinkedList<ViaInfo>();

  public ViaRule(String p_name) {
    name = p_name;
  }

  public void append_via(ViaInfo p_via) {
    list.add(p_via);
  }

  /** Removes p_via from the rule. Returns false, if p_via was not contained in the rule. */
  public boolean remove_via(ViaInfo p_via) {
    return list.remove(p_via);
  }

  public int via_count() {
    return list.size();
  }

  public ViaInfo get_via(int p_index) {
    assert p_index >= 0 && p_index < list.size();
    return list.get(p_index);
  }

  public String toString() {
    return this.name;
  }

  /** Returns true, if p_via_info is contained in the via list of this rule. */
  public boolean contains(ViaInfo p_via_info) {
    for (ViaInfo curr_info : this.list) {
      if (p_via_info == curr_info) {
        return true;
      }
    }
    return false;
  }

  /** Returns true, if this rule contains a via with padstack p_padstack */
  public boolean contains_padstack(app.freerouting.library.Padstack p_padstack) {
    for (ViaInfo curr_info : this.list) {
      if (curr_info.get_padstack() == p_padstack) {
        return true;
      }
    }
    return false;
  }

  /**
   * Searchs a via in this rule with first layer = p_from_layer and last layer = p_to_layer. Returns
   * null, if no such via exists.
   */
  public ViaInfo get_layer_range(int p_from_layer, int p_to_layer) {
    for (ViaInfo curr_info : this.list) {
      if (curr_info.get_padstack().from_layer() == p_from_layer
          && curr_info.get_padstack().to_layer() == p_to_layer) {
        return curr_info;
      }
    }
    return null;
  }

  /**
   * Swaps the locations of p_1 and p_2 in the rule. Returns false, if p_1 or p_2 were not found in
   * the list.
   */
  public boolean swap(ViaInfo p_1, ViaInfo p_2) {
    int index_1 = this.list.indexOf(p_1);
    int index_2 = this.list.indexOf(p_2);
    if (index_1 < 0 || index_2 < 0) {
      return false;
    }
    if (index_1 == index_2) {
      return true;
    }
    this.list.set(index_1, p_2);
    this.list.set(index_2, p_1);
    return true;
  }

  public void print_info(
      app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("via_rule_2") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(":");
    int counter = 0;
    boolean first_time = true;
    final int max_vias_per_row = 5;
    for (ViaInfo curr_via : this.list) {
      if (first_time) {
        first_time = false;
      } else {
        p_window.append(", ");
      }
      if (counter == 0) {
        p_window.newline();
        p_window.indent();
      }
      p_window.append(curr_via.get_name(), resources.getString("via_info"), curr_via);
      counter = (counter + 1) % max_vias_per_row;
    }
  }
}
