package app.freerouting.rules;

import app.freerouting.library.Padstack;

/**
 * Information about a combination of via_padstack, via clearance class and drill_to_smd_allowed
 * used in interactive and automatic routing.
 */
public class ViaInfo
    implements Comparable<ViaInfo>,
        app.freerouting.board.ObjectInfoPanel.Printable,
        java.io.Serializable {

  private final BoardRules board_rules;
  private String name;
  private Padstack padstack;
  private int clearance_class;
  private boolean attach_smd_allowed;

  /** Creates a new instance of ViaRule */
  public ViaInfo(
      String p_name,
      Padstack p_padstack,
      int p_clearance_class,
      boolean p_drill_to_smd_allowed,
      BoardRules p_board_rules) {
    name = p_name;
    padstack = p_padstack;
    clearance_class = p_clearance_class;
    attach_smd_allowed = p_drill_to_smd_allowed;
    board_rules = p_board_rules;
  }

  public String get_name() {
    return name;
  }

  public void set_name(String p_name) {
    name = p_name;
  }

  public String toString() {
    return this.name;
  }

  public Padstack get_padstack() {
    return padstack;
  }

  public void set_padstack(Padstack p_padstack) {
    padstack = p_padstack;
  }

  public int get_clearance_class() {
    return clearance_class;
  }

  public void set_clearance_class(int p_clearance_class) {
    clearance_class = p_clearance_class;
  }

  public boolean attach_smd_allowed() {
    return attach_smd_allowed;
  }

  public void set_attach_smd_allowed(boolean p_attach_smd_allowed) {
    attach_smd_allowed = p_attach_smd_allowed;
  }

  public int compareTo(ViaInfo p_other) {
    return this.name.compareTo(p_other.name);
  }

  public void print_info(
      app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("via") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(": ");
    p_window.append(resources.getString("padstack") + " ");
    p_window.append(this.padstack.name, resources.getString("padstack_info"), this.padstack);
    p_window.append(", " + resources.getString("clearance_class") + " ");
    String curr_name = board_rules.clearance_matrix.get_name(this.clearance_class);
    p_window.append(
        curr_name,
        resources.getString("clearance_class_2"),
        board_rules.clearance_matrix.get_row(this.clearance_class));
    p_window.append(", " + resources.getString("attach_smd") + " ");
    if (attach_smd_allowed) {
      p_window.append(" " + resources.getString("on"));
    } else {
      p_window.append(" " + resources.getString("off"));
    }
    p_window.newline();
  }
}
