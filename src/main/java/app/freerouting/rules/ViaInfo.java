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
      String pName,
      Padstack pPadstack,
      int pClearanceClass,
      boolean pDrillToSmdAllowed,
      BoardRules pBoardRules) {
    name = pName;
    padstack = pPadstack;
    clearanceClass = pClearanceClass;
    attachSmdAllowed = pDrillToSmdAllowed;
    boardRules = pBoardRules;
  }

  public String getName() {
    return name;
  }

  public void setName(String pName) {
    name = pName;
  }

  @Override
  public String toString() {
    return this.name;
  }

  public Padstack getPadstack() {
    return padstack;
  }

  public void setPadstack(Padstack pPadstack) {
    padstack = pPadstack;
  }

  public int getClearanceClass() {
    return clearanceClass;
  }

  public void setClearanceClass(int pClearanceClass) {
    clearanceClass = pClearanceClass;
  }

  public boolean attachSmdAllowed() {
    return attachSmdAllowed;
  }

  public void setAttachSmdAllowed(boolean pAttachSmdAllowed) {
    attachSmdAllowed = pAttachSmdAllowed;
  }

  @Override
  public int compareTo(ViaInfo pOther) {
    return this.name.compareTo(pOther.name);
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("via") + " ");
    pWindow.appendBold(this.name);
    pWindow.appendBold(": ");
    pWindow.append(tm.getText("padstack") + " ");
    pWindow.append(this.padstack.name, tm.getText("padstack_info"), this.padstack);
    pWindow.append(", " + tm.getText("clearanceClass") + " ");
    String currName = boardRules.clearanceMatrix.getName(this.clearanceClass);
    pWindow.append(
        currName,
        tm.getText("clearance_class_2"),
        boardRules.clearanceMatrix.getRow(this.clearanceClass));
    pWindow.append(", " + tm.getText("attach_smd") + " ");
    if (attachSmdAllowed) {
      pWindow.append(" " + tm.getText("on"));
    } else {
      pWindow.append(" " + tm.getText("off"));
    }
    pWindow.newline();
  }
}
