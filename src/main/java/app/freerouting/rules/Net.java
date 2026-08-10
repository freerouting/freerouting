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

  /** The name of the net. */
  public final String name;

  /**
   * Used only if a net is divided internally because of fromto rules for example For normal nets it
   * is always 1.
   */
  public final int subnetNumber;

  /** The unique strict positive number of the net. */
  public final int netNumber;

  /** The net list, where this net belongs to. */
  public final Nets netList;

  /** Indicates whether this net contains a power plane. */
  private boolean containsPlane;

  /** The routing rule of this net. */
  private NetClass netClass;

  /** Creates a new net. */
  public Net(
      String name, int subnetNumber, int number, Nets netList, boolean containsPlane) {
    this.name = name;
    this.subnetNumber = subnetNumber;
    this.netNumber = number;
    this.containsPlane = containsPlane;
    this.netList = netList;
    this.netClass = netList.getBoard().rules.getDefaultNetClass();
  }

  @Override
  public String toString() {
    return "Net #" + this.netNumber + " (" + this.name + ")";
  }

  /** Compares two nets by name, which is useful for displaying nets alphabetically. */
  @Override
  public int compareTo(Net other) {
    return this.name.compareToIgnoreCase(other.name);
  }

  /** Returns the class of this net. */
  public NetClass getNetClass() {
    return this.netClass;
  }

  /** Sets the class of this net. */
  public void setClass(NetClass netClass) {
    this.netClass = netClass;
  }

  /** Returns the pins and conduction areas of this net. */
  public Collection<Item> getTerminalItems() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.netList.getBoard();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof Connectable) {
        if (currItem.containsNet(this.netNumber) && !currItem.isRoutable()) {
          result.add(currItem);
        }
      }
    }
    return result;
  }

  /** Returns the pins of this net. */
  public Collection<Pin> getPins() {
    Collection<Pin> result = new LinkedList<>();
    BasicBoard board = this.netList.getBoard();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof Pin pin) {
        if (currItem.containsNet(this.netNumber)) {
          result.add(pin);
        }
      }
    }
    return result;
  }

  /** Returns all items of this net. */
  public Collection<Item> getItems() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.netList.getBoard();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem.containsNet(this.netNumber)) {
        result.add(currItem);
      }
    }
    return result;
  }

  /** Returns the cumulative trace length of all traces on the board belonging to this net. */
  public double getTraceLength() {
    double cumulativeTraceLength = 0;
    Collection<Item> netItems = netList.getBoard().getConnectableItems(this.netNumber);
    for (Item currItem : netItems) {

      if (currItem instanceof Trace trace) {
        cumulativeTraceLength += trace.getLength();
      }
    }
    return cumulativeTraceLength;
  }

  /** Returns the count of vias on the board belonging to this net. */
  public int getViaCount() {
    int result = 0;
    Collection<Item> netItems = netList.getBoard().getConnectableItems(this.netNumber);
    for (Item currItem : netItems) {
      if (currItem instanceof Via) {
        ++result;
      }
    }
    return result;
  }

  /** Sets whether this net contains a power plane. */
  public void setContainsPlane(boolean value) {
    containsPlane = value;
  }

  /**
   * Indicates, if this net contains a power plane. Used by the autorouter for setting the via costs
   * to the cheap plane via costs. May also be true, if a layer covered with a conductionArea of
   * this net is a signal layer.
   */
  public boolean containsPlane() {
    return containsPlane;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    int viaCount = this.getViaCount();
    double cumulativeTraceLength = this.getTraceLength();
    Collection<Item> terminalItems = this.getTerminalItems();
    Collection<Printable> terminals = new LinkedList<>(terminalItems);
    int terminalItemCount = terminals.size();

    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("net") + " ");
    window.appendBold(this.name);
    window.appendBold(": ");
    window.append(tm.getText("class") + " ");
    window.append(netClass.getName(), tm.getText("netClass"), netClass);
    window.append(", ");
    window.appendObjects(
        String.valueOf(terminalItemCount), tm.getText("terminal_items_2"), terminals);
    window.append(" " + tm.getText("terminalItems"));
    window.append(", " + tm.getText("viaCount") + " ");
    window.append(String.valueOf(viaCount));
    window.append(", " + tm.getText("traceLength") + " ");
    window.append(cumulativeTraceLength);
    window.newline();
  }
}
