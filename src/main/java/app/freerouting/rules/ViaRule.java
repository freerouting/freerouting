package app.freerouting.rules;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.core.Padstack;
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

  public ViaRule(String pName) {
    name = pName;
  }

  public void appendVia(ViaInfo pVia) {
    list.add(pVia);
  }

  /** Removes p_via from the rule. Returns false, if p_via was not contained in the rule. */
  public boolean removeVia(ViaInfo pVia) {
    return list.remove(pVia);
  }

  public int viaCount() {
    return list.size();
  }

  public ViaInfo getVia(int pIndex) {
    assert pIndex >= 0 && pIndex < list.size();
    return list.get(pIndex);
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Returns true, if p_via_info is contained in the via list of this rule. */
  public boolean contains(ViaInfo pViaInfo) {
    for (ViaInfo currInfo : this.list) {
      if (pViaInfo == currInfo) {
        return true;
      }
    }
    return false;
  }

  /** Returns true, if this rule contains a via with padstack p_padstack */
  public boolean containsPadstack(Padstack pPadstack) {
    for (ViaInfo currInfo : this.list) {
      if (currInfo.getPadstack() == pPadstack) {
        return true;
      }
    }
    return false;
  }

  /**
   * Searches a via in this rule with first layer = p_from_layer and last layer = p_to_layer.
   * Returns null, if no such via exists.
   */
  public ViaInfo getLayerRange(int pFromLayer, int pToLayer) {
    for (ViaInfo currInfo : this.list) {
      if (currInfo.getPadstack().fromLayer() == pFromLayer
          && currInfo.getPadstack().toLayer() == pToLayer) {
        return currInfo;
      }
    }
    return null;
  }

  /**
   * Swaps the locations of p_1 and p_2 in the rule. Returns false, if p_1 or p_2 were not found in
   * the list.
   */
  public boolean swap(ViaInfo p1, ViaInfo p2) {
    int index1 = this.list.indexOf(p1);
    int index2 = this.list.indexOf(p2);
    if (index1 < 0 || index2 < 0) {
      return false;
    }
    if (index1 == index2) {
      return true;
    }
    this.list.set(index1, p2);
    this.list.set(index2, p1);
    return true;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("via_rule_2") + " ");
    pWindow.appendBold(this.name);
    pWindow.appendBold(":");
    int counter = 0;
    boolean firstTime = true;
    final int maxViasPerRow = 5;
    for (ViaInfo currVia : this.list) {
      if (firstTime) {
        firstTime = false;
      } else {
        pWindow.append(", ");
      }
      if (counter == 0) {
        pWindow.newline();
        pWindow.indent();
      }
      pWindow.append(currVia.getName(), tm.getText("viaInfo"), currVia);
      counter = (counter + 1) % maxViasPerRow;
    }
  }
}
