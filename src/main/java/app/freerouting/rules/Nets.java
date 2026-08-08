package app.freerouting.rules;

import app.freerouting.board.BasicBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;

/** Describes the electrical Nets on a board. */
public class Nets implements Serializable {

  /** The maximum legal net number for nets. */
  public static final int max_legal_net_no = 9999999;

  /** auxiliary net number for internal use */
  public static final int hidden_net_no = 10000001;

  /** The list of electrical nets on the board */
  private final Vector<Net> netArr;

  private BasicBoard board;

  /** Creates a new empty net list */
  public Nets() {
    netArr = new Vector<>();
  }

  /** Returns false, if p_net_no belongs to a net internally used for special purposes. */
  public static boolean isNormalNetNo(int p_net_no) {
    return p_net_no > 0 && p_net_no <= max_legal_net_no;
  }

  /** Returns the biggest net number on the board. */
  public int maxNetNo() {
    return netArr.size();
  }

  /** Returns the net with the input name and subnetNumber , or null, if no such net exists. */
  public Net get(String p_name, int p_subnet_number) {
    for (Net currNet : netArr) {
      if (currNet != null && currNet.name.equalsIgnoreCase(p_name)) {
        if (currNet.subnetNumber == p_subnet_number) {
          return currNet;
        }
      }
    }
    return null;
  }

  /** Returns all subnets with the input name. */
  public Collection<Net> get(String p_name) {
    Collection<Net> result = new LinkedList<>();
    for (Net currNet : netArr) {
      if (currNet != null && currNet.name.equalsIgnoreCase(p_name)) {
        result.add(currNet);
      }
    }
    return result;
  }

  /** Returns the net with the input net number or null, if no such net exists. */
  public Net get(int p_net_no) {
    if (p_net_no < 1 || p_net_no > netArr.size()) {
      return null;
    }
    Net result = netArr.elementAt(p_net_no - 1);
    if (result != null && result.netNumber != p_net_no) {
      FRLogger.warn("Nets.get: inconsistent netNo");
    }
    return result;
  }

  /** Generates a new net number. */
  public Net newNet(Locale p_locale) {
    TextManager tm = new TextManager(NetClasses.class, p_locale);

    String netName = tm.getText("net#") + (netArr.size() + 1);
    return add(netName, 1, false);
  }

  /**
   * Adds a new net with default properties with the input name. p_subnet_number is used only if a
   * net is divided internally because of fromto rules for example. For normal nets it is always 1.
   */
  public Net add(String p_name, int p_subnet_number, boolean p_contains_plane) {
    int newNetNo = netArr.size() + 1;
    if (newNetNo >= max_legal_net_no) {
      FRLogger.warn("Nets.add_net: maxNetNo out of range");
    }
    Net newNet = new Net(p_name, p_subnet_number, newNetNo, this, p_contains_plane);
    netArr.add(newNet);
    return newNet;
  }

  /** Gets the Board of this net list. Used for example to get access to the Items of the net. */
  public BasicBoard getBoard() {
    return this.board;
  }

  /** Sets the Board of this net list. Used for example to get access to the Items of the net. */
  public void setBoard(BasicBoard p_board) {
    this.board = p_board;
  }
}
