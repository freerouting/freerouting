package app.freerouting.rules;

import app.freerouting.board.BasicBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;

/** Describes the electrical nets on a board. */
public class Nets implements Serializable {

  /** The maximum legal net number for nets. */
  public static final int max_legal_net_no = 9999999;

  /** The auxiliary net number for internal use. */
  public static final int hidden_net_no = 10000001;

  /** The list of electrical nets on the board. */
  private final Vector<Net> netArr;

  private BasicBoard board;

  /** Creates a new empty net list. */
  public Nets() {
    netArr = new Vector<>();
  }

  /** Returns false if {@code netNumber} belongs to an internally used special-purpose net. */
  public static boolean isNormalNetNo(int netNumber) {
    return netNumber > 0 && netNumber <= max_legal_net_no;
  }

  /** Returns the biggest net number on the board. */
  public int maxNetNo() {
    return netArr.size();
  }

  /** Returns the net with the given name and subnet number, or null if no such net exists. */
  public Net get(String name, int subnetNumber) {
    for (Net currentNet : netArr) {
      if (currentNet != null && currentNet.name.equalsIgnoreCase(name)) {
        if (currentNet.subnetNumber == subnetNumber) {
          return currentNet;
        }
      }
    }
    return null;
  }

  /** Returns all subnets with the given name. */
  public Collection<Net> get(String name) {
    Collection<Net> result = new LinkedList<>();
    for (Net currentNet : netArr) {
      if (currentNet != null && currentNet.name.equalsIgnoreCase(name)) {
        result.add(currentNet);
      }
    }
    return result;
  }

  /** Returns the net with the given net number, or null if no such net exists. */
  public Net get(int netNumber) {
    if (netNumber < 1 || netNumber > netArr.size()) {
      return null;
    }
    Net result = netArr.elementAt(netNumber - 1);
    if (result != null && result.netNumber != netNumber) {
      FRLogger.warn("Nets.get: inconsistent netNo");
    }
    return result;
  }

  /** Generates a new net number. */
  public Net newNet(Locale locale) {
    TextManager tm = new TextManager(NetClasses.class, locale);

    String netName = tm.getText("net#") + (netArr.size() + 1);
    return add(netName, 1, false);
  }

  /**
   * Adds a new net with default properties and the given name. {@code subnetNumber} is used only
   * if a net is divided internally because of from-to rules. For normal nets it is always 1.
   */
  public Net add(String name, int subnetNumber, boolean containsPlane) {
    int newNetNo = netArr.size() + 1;
    if (newNetNo >= max_legal_net_no) {
      FRLogger.warn("Nets.add_net: maxNetNo out of range");
    }
    Net newNet = new Net(name, subnetNumber, newNetNo, this, containsPlane);
    netArr.add(newNet);
    return newNet;
  }

  /** Gets the board of this net list, which provides access to the net's items. */
  public BasicBoard getBoard() {
    return this.board;
  }

  /** Sets the board of this net list, which provides access to the net's items. */
  public void setBoard(BasicBoard board) {
    this.board = board;
  }
}
