package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.core.Padstack;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/**
 * Information about a combination of viaPadstack, via clearance class and drill_to_smd_allowed used
 * in interactive and automatic routing.
 */
public class ViaInfo implements Comparable<ViaInfo>, ObjectInfoPanel.Printable, Serializable {

  private final BoardRules boardRules;
  private String name;
  private Padstack padstack;
  private int clearanceClass;
  private boolean attachSmdAllowed;

  /** Creates a new instance of ViaRule */
  public ViaInfo(
      String p_name,
      Padstack p_padstack,
      int p_clearance_class,
      boolean p_drill_to_smd_allowed,
      BoardRules p_board_rules) {
    name = p_name;
    padstack = p_padstack;
    clearanceClass = p_clearance_class;
    attachSmdAllowed = p_drill_to_smd_allowed;
    boardRules = p_board_rules;
  }

  public String getName() {
    return name;
  }

  public void setName(String p_name) {
    name = p_name;
  }

  @Override
  public String toString() {
    return this.name;
  }

  public Padstack getPadstack() {
    return padstack;
  }

  public void setPadstack(Padstack p_padstack) {
    padstack = p_padstack;
  }

  public int getClearanceClass() {
    return clearanceClass;
  }

  public void setClearanceClass(int p_clearance_class) {
    clearanceClass = p_clearance_class;
  }

  public boolean attachSmdAllowed() {
    return attachSmdAllowed;
  }

  public void setAttachSmdAllowed(boolean p_attach_smd_allowed) {
    attachSmdAllowed = p_attach_smd_allowed;
  }

  @Override
  public int compareTo(ViaInfo p_other) {
    return this.name.compareTo(p_other.name);
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("via") + " ");
    p_window.appendBold(this.name);
    p_window.appendBold(": ");
    p_window.append(tm.getText("padstack") + " ");
    p_window.append(this.padstack.name, tm.getText("padstack_info"), this.padstack);
    p_window.append(", " + tm.getText("clearanceClass") + " ");
    String currName = boardRules.clearanceMatrix.getName(this.clearanceClass);
    p_window.append(
        currName,
        tm.getText("clearance_class_2"),
        boardRules.clearanceMatrix.getRow(this.clearanceClass));
    p_window.append(", " + tm.getText("attach_smd") + " ");
    if (attachSmdAllowed) {
      p_window.append(" " + tm.getText("on"));
    } else {
      p_window.append(" " + tm.getText("off"));
    }
    p_window.newline();
  }
}
