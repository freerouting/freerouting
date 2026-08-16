package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.core.library.Padstack;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/**
 * Information about a combination of a via padstack, via clearance class, and drill-to-SMD setting
 * used in interactive and automatic routing.
 */
public class ViaInfo implements Comparable<ViaInfo>, ObjectInfoPanel.Printable, Serializable {

  private final BoardRules boardRules;
  private String name;
  private Padstack padstack;
  private int clearanceClassIndex;
  private boolean attachSmdAllowed;

  /** Creates a via definition. */
  public ViaInfo(
      String name,
      Padstack padstack,
      int clearanceClassIndex,
      boolean drillToSmdAllowed,
      BoardRules boardRules) {
    this.name = name;
    this.padstack = padstack;
    this.clearanceClassIndex = clearanceClassIndex;
    this.attachSmdAllowed = drillToSmdAllowed;
    this.boardRules = boardRules;
  }

  /** Returns the name of this via definition. */
  public String getName() {
    return name;
  }

  /** Sets the name of this via definition. */
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Returns the padstack used by this via definition. */
  public Padstack getPadstack() {
    return padstack;
  }

  /** Sets the padstack used by this via definition. */
  public void setPadstack(Padstack padstack) {
    this.padstack = padstack;
  }

  /** Returns the clearance class index used by this via definition. */
  public int getClearanceClassIndex() {
    return clearanceClassIndex;
  }

  /** Sets the clearance class index used by this via definition. */
  public void setClearanceClassIndex(int clearanceClassIndex) {
    this.clearanceClassIndex = clearanceClassIndex;
  }

  /** Returns whether this via may attach to an SMD pad. */
  public boolean attachSmdAllowed() {
    return attachSmdAllowed;
  }

  /** Sets whether this via may attach to an SMD pad. */
  public void setAttachSmdAllowed(boolean attachSmdAllowed) {
    this.attachSmdAllowed = attachSmdAllowed;
  }

  @Override
  public int compareTo(ViaInfo other) {
    return this.name.compareTo(other.name);
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("via") + " ");
    window.appendBold(this.name);
    window.appendBold(": ");
    window.append(tm.getText("padstack") + " ");
    window.append(this.padstack.name, tm.getText("padstack_info"), this.padstack);
    window.append(", " + tm.getText("clearanceClass") + " ");
    String currentName = boardRules.clearanceMatrix.getName(this.clearanceClassIndex);
    window.append(
        currentName,
        tm.getText("clearance_class_2"),
        boardRules.clearanceMatrix.getRow(this.clearanceClassIndex));
    window.append(", " + tm.getText("attach_smd") + " ");
    if (attachSmdAllowed) {
      window.append(" " + tm.getText("on"));
    } else {
      window.append(" " + tm.getText("off"));
    }
    window.newline();
  }
}
