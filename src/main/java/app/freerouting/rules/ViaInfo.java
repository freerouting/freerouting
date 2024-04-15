package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.library.Padstack;

import app.freerouting.management.TextManager;

import java.io.Serializable;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Information about a combination of via_padstack, via clearance class and drill_to_smd_allowed
 * used in interactive and automatic routing.
 */
public class ViaInfo implements Comparable<ViaInfo>, ObjectInfoPanel.Printable, Serializable
{

  private final BoardRules board_rules;
  private String name;
  private Padstack padstack;
  private int clearance_class;
  private boolean attach_smd_allowed;

  /**
   * Creates a new instance of ViaRule
   */
  public ViaInfo(String p_name, Padstack p_padstack, int p_clearance_class, boolean p_drill_to_smd_allowed, BoardRules p_board_rules)
  {
    name = p_name;
    padstack = p_padstack;
    clearance_class = p_clearance_class;
    attach_smd_allowed = p_drill_to_smd_allowed;
    board_rules = p_board_rules;
  }

  public String get_name()
  {
    return name;
  }

  public void set_name(String p_name)
  {
    name = p_name;
  }

  @Override
  public String toString()
  {
    return this.name;
  }

  public Padstack get_padstack()
  {
    return padstack;
  }

  public void set_padstack(Padstack p_padstack)
  {
    padstack = p_padstack;
  }

  public int get_clearance_class()
  {
    return clearance_class;
  }

  public void set_clearance_class(int p_clearance_class)
  {
    clearance_class = p_clearance_class;
  }

  public boolean attach_smd_allowed()
  {
    return attach_smd_allowed;
  }

  public void set_attach_smd_allowed(boolean p_attach_smd_allowed)
  {
    attach_smd_allowed = p_attach_smd_allowed;
  }

  @Override
  public int compareTo(ViaInfo p_other)
  {
    return this.name.compareTo(p_other.name);
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale)
  {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("via") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(": ");
    p_window.append(tm.getText("padstack") + " ");
    p_window.append(this.padstack.name, tm.getText("padstack_info"), this.padstack);
    p_window.append(", " + tm.getText("clearance_class") + " ");
    String curr_name = board_rules.clearance_matrix.get_name(this.clearance_class);
    p_window.append(curr_name, tm.getText("clearance_class_2"), board_rules.clearance_matrix.get_row(this.clearance_class));
    p_window.append(", " + tm.getText("attach_smd") + " ");
    if (attach_smd_allowed)
    {
      p_window.append(" " + tm.getText("on"));
    }
    else
    {
      p_window.append(" " + tm.getText("off"));
    }
    p_window.newline();
  }
}