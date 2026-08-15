package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.core.library.Padstack;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Contains an array of vias used for routing. Vias at the beginning of the array are preferred to
 * later vias.
 */
public class ViaRule implements Serializable, ObjectInfoPanel.Printable {

  /** Empty via rule. Must not be changed. */
  public static final ViaRule EMPTY = new ViaRule("empty");

  public final String name;
  private final List<ViaInfo> list = new LinkedList<>();

  /** Creates a via rule with the given name. */
  public ViaRule(String name) {
    this.name = name;
  }

  /** Appends a via to this rule. */
  public void appendVia(ViaInfo via) {
    list.add(via);
  }

  /** Removes {@code via} from the rule. Returns false if it was not contained in the rule. */
  public boolean removeVia(ViaInfo via) {
    return list.remove(via);
  }

  /** Returns the number of vias in this rule. */
  public int viaCount() {
    return list.size();
  }

  /** Returns the via at the given index. */
  public ViaInfo getVia(int index) {
    assert index >= 0 && index < list.size();
    return list.get(index);
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Returns true if {@code viaInfo} is contained in the via list of this rule. */
  public boolean contains(ViaInfo viaInfo) {
    for (ViaInfo currInfo : this.list) {
      if (viaInfo == currInfo) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if this rule contains a via with the given padstack. */
  public boolean containsPadstack(Padstack padstack) {
    for (ViaInfo currInfo : this.list) {
      if (currInfo.getPadstack() == padstack) {
        return true;
      }
    }
    return false;
  }

  /**
   * Searches for a via in this rule with the given first and last layers. Returns null if no such
   * via exists.
   */
  public ViaInfo getLayerRange(int fromLayer, int toLayer) {
    for (ViaInfo currInfo : this.list) {
      if (currInfo.getPadstack().fromLayer() == fromLayer
          && currInfo.getPadstack().toLayer() == toLayer) {
        return currInfo;
      }
    }
    return null;
  }

  /**
   * Swaps the locations of {@code first} and {@code second} in the rule. Returns false if either
   * was not found in the list.
   */
  public boolean swap(ViaInfo first, ViaInfo second) {
    int index1 = this.list.indexOf(first);
    int index2 = this.list.indexOf(second);
    if (index1 < 0 || index2 < 0) {
      return false;
    }
    if (index1 == index2) {
      return true;
    }
    this.list.set(index1, second);
    this.list.set(index2, first);
    return true;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("via_rule_2") + " ");
    window.appendBold(this.name);
    window.appendBold(":");
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
