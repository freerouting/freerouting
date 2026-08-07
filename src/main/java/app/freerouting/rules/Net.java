package app.freerouting.rules;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.board.ObjectInfoPanel.Printable;
import app.freerouting.board.Pin;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

/** Describes properties for an individual electrical net. */
public class Net implements Comparable<Net>, ObjectInfoPanel.Printable, Serializable {

  /** The name of the net */
  public final String name;

  /**
   * Used only if a net is divided internally because of fromto rules for example For normal nets it
   * is always 1.
   */
  public final int subnetNumber;

  /** The unique strict positive number of the net */
  public final int netNumber;

  /** The net list, where this net belongs to. */
  public final Nets netList;

  /** Indicates, if this net contains a power plane */
  private boolean containsPlane;

  /** The routing rule of this net */
  private NetClass netClass;

  /** Creates a new instance of Net. p_net_list is the net list, where this net belongs to. */
  public Net(
      String p_name, int p_subnet_number, int p_no, Nets p_net_list, boolean p_contains_plane) {
    name = p_name;
    subnetNumber = p_subnet_number;
    netNumber = p_no;
    containsPlane = p_contains_plane;
    netList = p_net_list;
    netClass = p_net_list.get_board().rules.get_default_net_class();
  }

  @Override
  public String toString() {
    return "Net #" + this.netNumber + " (" + this.name + ")";
  }

  /** Compares 2 nets by name. Useful for example to display nets in alphabetic order. */
  @Override
  public int compareTo(Net p_other) {
    return this.name.compareToIgnoreCase(p_other.name);
  }

  /** Returns the class of this net. */
  public NetClass get_class() {
    return this.netClass;
  }

  /** Sets the class of this net */
  public void set_class(NetClass p_rule) {
    this.netClass = p_rule;
  }

  /** Returns the pins and conduction areas of this net. */
  public Collection<Item> get_terminal_items() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.netList.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) board.itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof Connectable) {
        if (currItem.contains_net(this.netNumber) && !currItem.is_routable()) {
          result.add(currItem);
        }
      }
    }
    return result;
  }

  /** Returns the pins of this net. */
  public Collection<Pin> get_pins() {
    Collection<Pin> result = new LinkedList<>();
    BasicBoard board = this.netList.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) board.itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof Pin pin) {
        if (currItem.contains_net(this.netNumber)) {
          result.add(pin);
        }
      }
    }
    return result;
  }

  /** Returns all items of this net. */
  public Collection<Item> get_items() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.netList.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) board.itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem.contains_net(this.netNumber)) {
        result.add(currItem);
      }
    }
    return result;
  }

  /** Returns the cumulative trace length of all traces on the board belonging to this net. */
  public double get_trace_length() {
    double cumulativeTraceLength = 0;
    Collection<Item> netItems = netList.get_board().get_connectable_items(this.netNumber);
    for (Item currItem : netItems) {

      if (currItem instanceof Trace trace) {
        cumulativeTraceLength += trace.get_length();
      }
    }
    return cumulativeTraceLength;
  }

  /** Returns the count of vias on the board belonging to this net. */
  public int get_via_count() {
    int result = 0;
    Collection<Item> netItems = netList.get_board().get_connectable_items(this.netNumber);
    for (Item currItem : netItems) {
      if (currItem instanceof Via) {
        ++result;
      }
    }
    return result;
  }

  public void set_contains_plane(boolean p_value) {
    containsPlane = p_value;
  }

  /**
   * Indicates, if this net contains a power plane. Used by the autorouter for setting the via costs
   * to the cheap plane via costs. May also be true, if a layer covered with a conductionArea of
   * this net is a signal layer.
   */
  public boolean contains_plane() {
    return containsPlane;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    int viaCount = this.get_via_count();
    double cumulativeTraceLength = this.get_trace_length();
    Collection<Item> terminalItems = this.get_terminal_items();
    Collection<Printable> terminals = new LinkedList<>(terminalItems);
    int terminalItemCount = terminals.size();

    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("net") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(": ");
    p_window.append(tm.getText("class") + " ");
    p_window.append(netClass.get_name(), tm.getText("netClass"), netClass);
    p_window.append(", ");
    p_window.append_objects(
        String.valueOf(terminalItemCount), tm.getText("terminal_items_2"), terminals);
    p_window.append(" " + tm.getText("terminalItems"));
    p_window.append(", " + tm.getText("viaCount") + " ");
    p_window.append(String.valueOf(viaCount));
    p_window.append(", " + tm.getText("traceLength") + " ");
    p_window.append(cumulativeTraceLength);
    p_window.newline();
  }
}
