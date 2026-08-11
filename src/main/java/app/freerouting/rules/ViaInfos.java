package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Contains the list of different via definitions that can be used in interactive and automatic
 * routing.
 */
public class ViaInfos implements Serializable, ObjectInfoPanel.Printable {

  private final List<ViaInfo> list = new LinkedList<>();

  /**
   * Adds a via definition consisting of a padstack, clearance class, and drill-to-SMD setting.
   * Returns false if the insertion failed, for example because the name already exists.
   */
  public boolean add(ViaInfo viaInfo) {
    if (nameExists(viaInfo.getName())) {
      return false;
    }
    this.list.add(viaInfo);
    return true;
  }

  /** Returns the number of different vias that can be used for routing. */
  public int count() {
    return this.list.size();
  }

  /** Returns the via at the given index. */
  public ViaInfo get(int index) {
    assert index >= 0 && index < this.list.size();
    return this.list.get(index);
  }

  /** Returns the via definition with the given name, or null if no such via exists. */
  public ViaInfo get(String name) {
    for (ViaInfo currVia : this.list) {
      if (currVia.getName().equals(name)) {
        return currVia;
      }
    }
    return null;
  }

  /** Returns true if a via definition with the given name already exists. */
  public boolean nameExists(String name) {
    for (ViaInfo currVia : this.list) {
      if (currVia.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /** Removes {@code viaInfo} from this list. Returns false if it was not contained in the list. */
  public boolean remove(ViaInfo viaInfo) {
    return this.list.remove(viaInfo);
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("vias") + ": ");
    int counter = 0;
    boolean firstTime = true;
    final int maxViasPerRow = 5;
    for (ViaInfo currVia : this.list) {
      if (firstTime) {
        firstTime = false;
      } else {
        window.append(", ");
      }
      if (counter == 0) {
        window.newline();
        window.indent();
      }
      window.append(currVia.getName(), tm.getText("viaInfo"), currVia);
      counter = (counter + 1) % maxViasPerRow;
    }
  }
}
