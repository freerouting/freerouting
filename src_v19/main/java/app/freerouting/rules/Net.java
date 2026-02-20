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

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.ResourceBundle;

/** Describes properties for an individual electrical net. */
public class Net
    implements Comparable<Net>,
        ObjectInfoPanel.Printable,
        Serializable {

  /** The name of the net */
  public final String name;
  /**
   * Used only if a net is divided internally because of fromto rules for example For normal nets it
   * is always 1.
   */
  public final int subnet_number;
  /** The unique strict positive number of the net */
  public final int net_number;
  /** The net list, where this net belongs to. */
  public final Nets net_list;
  /** Indicates, if this net contains a power plane */
  private boolean contains_plane;
  /** The routing rule of this net */
  private NetClass net_class;

  /** Creates a new instance of Net. p_net_list is the net list, where this net belongs to. */
  public Net(
      String p_name, int p_subnet_number, int p_no, Nets p_net_list, boolean p_contains_plane) {
    name = p_name;
    subnet_number = p_subnet_number;
    net_number = p_no;
    contains_plane = p_contains_plane;
    net_list = p_net_list;
    net_class = p_net_list.get_board().rules.get_default_net_class();
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Compares 2 nets by name. Useful for example to display nets in alphabetic order. */
  @Override
  public int compareTo(Net p_other) {
    return this.name.compareToIgnoreCase(p_other.name);
  }

  /** Returns the class of this net. */
  public NetClass get_class() {
    return this.net_class;
  }

  /** Sets the class of this net */
  public void set_class(NetClass p_rule) {
    this.net_class = p_rule;
  }

  /** Returns the pins and conduction areas of this net. */
  public Collection<Item> get_terminal_items() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.net_list.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (; ; ) {
      Item curr_item = (Item) board.item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Connectable) {
        if (curr_item.contains_net(this.net_number) && !curr_item.is_routable()) {
          result.add(curr_item);
        }
      }
    }
    return result;
  }

  /** Returns the pins of this net. */
  public Collection<Pin> get_pins() {
    Collection<Pin> result =
        new LinkedList<>();
    BasicBoard board = this.net_list.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (; ; ) {
      Item curr_item = (Item) board.item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Pin) {
        if (curr_item.contains_net(this.net_number)) {
          result.add((Pin) curr_item);
        }
      }
    }
    return result;
  }

  /** Returns all items of this net. */
  public Collection<Item> get_items() {
    Collection<Item> result =
        new LinkedList<>();
    BasicBoard board = this.net_list.get_board();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (; ; ) {
      Item curr_item = (Item) board.item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.contains_net(this.net_number)) {
        result.add(curr_item);
      }
    }
    return result;
  }

  /** Returns the cumulative trace length of all traces on the board belonging to this net. */
  public double get_trace_length() {
    double cumulative_trace_length = 0;
    Collection<Item> net_items =
        net_list.get_board().get_connectable_items(this.net_number);
    for (Item curr_item : net_items) {

      if (curr_item instanceof Trace) {
        cumulative_trace_length += ((Trace) curr_item).get_length();
      }
    }
    return cumulative_trace_length;
  }

  /** Returns the count of vias on the board belonging to this net. */
  public int get_via_count() {
    int result = 0;
    Collection<Item> net_items =
        net_list.get_board().get_connectable_items(this.net_number);
    for (Item curr_item : net_items) {
      if (curr_item instanceof Via) {
        ++result;
      }
    }
    return result;
  }

  public void set_contains_plane(boolean p_value) {
    contains_plane = p_value;
  }

  /**
   * Indicates, if this net contains a power plane. Used by the autorouter for setting the via costs
   * to the cheap plane via costs. May also be true, if a layer covered with a conduction_area of
   * this net is a signal layer.
   */
  public boolean contains_plane() {
    return contains_plane;
  }

  @Override
  public void print_info(
      ObjectInfoPanel p_window, Locale p_locale) {
    int via_count = this.get_via_count();
    double cumulative_trace_length = this.get_trace_length();
    Collection<Item> terminal_items = this.get_terminal_items();
    Collection<Printable> terminals = new LinkedList<>(terminal_items);
    int terminal_item_count = terminals.size();

    ResourceBundle resources =
        ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("net") + " ");
    p_window.append_bold(this.name);
    p_window.append_bold(": ");
    p_window.append(resources.getString("class") + " ");
    p_window.append(net_class.get_name(), resources.getString("net_class"), net_class);
    p_window.append(", ");
    p_window.append_objects(
        String.valueOf(terminal_item_count), resources.getString("terminal_items_2"), terminals);
    p_window.append(" " + resources.getString("terminal_items"));
    p_window.append(", " + resources.getString("via_count") + " ");
    p_window.append(String.valueOf(via_count));
    p_window.append(", " + resources.getString("trace_length") + " ");
    p_window.append(cumulative_trace_length);
    p_window.newline();
  }
}
