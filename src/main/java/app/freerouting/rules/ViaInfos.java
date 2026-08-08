package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Contains the lists of different ViaInfo's, which can be used in interactive and automatic
 * routing.
 */
public class ViaInfos implements Serializable, ObjectInfoPanel.Printable {

  private final List<ViaInfo> list = new LinkedList<>();

  /**
   * Adds a via info consisting of padstack, clearance class and drill_to_smd_allowed. Return false,
   * if the insertion failed, for example if the name existed already.
   */
  public boolean add(ViaInfo p_via_info) {
    if (nameExists(p_via_info.getName())) {
      return false;
    }
    this.list.add(p_via_info);
    return true;
  }

  /** Returns the number of different vias, which can be used for routing. */
  public int count() {
    return this.list.size();
  }

  /** Returns the p_no-th via af the via types, which can be used for routing. */
  public ViaInfo get(int p_no) {
    assert p_no >= 0 && p_no < this.list.size();
    return this.list.get(p_no);
  }

  /** Returns the via info with name p_name, or null, if no such via exists. */
  public ViaInfo get(String p_name) {
    for (ViaInfo currVia : this.list) {
      if (currVia.getName().equals(p_name)) {
        return currVia;
      }
    }
    return null;
  }

  /** Returns true, if a via info with name p_name is already wyisting in the list. */
  public boolean nameExists(String p_name) {
    for (ViaInfo currVia : this.list) {
      if (currVia.getName().equals(p_name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes p_via_info from this list. Returns false, if p_via_info was not contained in the list.
   */
  public boolean remove(ViaInfo p_via_info) {
    return this.list.remove(p_via_info);
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("vias") + ": ");
    int counter = 0;
    boolean firstTime = true;
    final int maxViasPerRow = 5;
    for (ViaInfo currVia : this.list) {
      if (firstTime) {
        firstTime = false;
      } else {
        p_window.append(", ");
      }
      if (counter == 0) {
        p_window.newline();
        p_window.indent();
      }
      p_window.append(currVia.getName(), tm.getText("viaInfo"), currVia);
      counter = (counter + 1) % maxViasPerRow;
    }
  }
}
