package app.freerouting.rules;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemInfoPrinter.Printable;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

/** Describes properties for an individual electrical net. */
public class Net implements Comparable<Net>, ItemInfoPrinter.Printable, Serializable {

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

  /**
   * Optional explicit trace length constraint for this net, or null if inheriting from netClass.
   */
  private NetLengthConstraint lengthConstraint;

  /**
   * Optional explicit meander (length tuning) constraints for this net by target, or empty if
   * inheriting from netClass.
   */
  private Map<NetMeanderConstraint.MeanderTarget, NetMeanderConstraint> meanderConstraints =
      new EnumMap<>(NetMeanderConstraint.MeanderTarget.class);

  private NetMeanderConstraint meanderConstraint;

  /** Creates a new net. */
  public Net(String name, int subnetNumber, int number, Nets netList, boolean containsPlane) {
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

  /**
   * Returns the minimum trace length of this net in board coordinate units. If no explicit
   * net-level restriction is set, falls back to the net class constraint. If {@literal <}= 0, there
   * is no minimal trace length restriction.
   */
  public double getMinimumTraceLength() {
    if (this.lengthConstraint != null && this.lengthConstraint.hasMin()) {
      return this.lengthConstraint.minLength();
    }
    return this.netClass != null ? this.netClass.getMinimumTraceLength() : 0.0;
  }

  /**
   * Sets the explicit minimum trace length of this net. If {@code value} is {@literal <}= 0, there
   * is no minimal trace length restriction at the net level.
   */
  public void setMinimumTraceLength(double value) {
    double currentMax = (this.lengthConstraint != null) ? this.lengthConstraint.maxLength() : 0.0;
    this.lengthConstraint = new NetLengthConstraint(value, currentMax);
  }

  /**
   * Returns the maximum trace length of this net in board coordinate units. If no explicit
   * net-level restriction is set, falls back to the net class constraint. If {@literal <}= 0, there
   * is no maximal trace length restriction.
   */
  public double getMaximumTraceLength() {
    if (this.lengthConstraint != null && this.lengthConstraint.hasMax()) {
      return this.lengthConstraint.maxLength();
    }
    return this.netClass != null ? this.netClass.getMaximumTraceLength() : 0.0;
  }

  /**
   * Sets the explicit maximum trace length of this net. If {@code value} is {@literal <}= 0, there
   * is no maximal trace length restriction at the net level.
   */
  public void setMaximumTraceLength(double value) {
    double currentMin = (this.lengthConstraint != null) ? this.lengthConstraint.minLength() : 0.0;
    this.lengthConstraint = new NetLengthConstraint(currentMin, value);
  }

  /**
   * Returns the effective length constraint of this net. Combines net-level explicit constraints
   * with inherited net class constraints where applicable.
   */
  public NetLengthConstraint getLengthConstraint() {
    return new NetLengthConstraint(getMinimumTraceLength(), getMaximumTraceLength());
  }

  /**
   * Sets the explicit length constraint for this net. Pass null or {@link
   * NetLengthConstraint#UNCONSTRAINED} to remove the explicit net-level constraint and inherit
   * entirely from the net class.
   */
  public void setLengthConstraint(NetLengthConstraint constraint) {
    this.lengthConstraint = constraint;
  }

  /**
   * Returns true if this net has an explicit net-level trace length constraint set, false if
   * inheriting entirely from its net class.
   */
  public boolean hasExplicitLengthConstraint() {
    return this.lengthConstraint != null && this.lengthConstraint.isConstrained();
  }

  /**
   * Returns the explicit net-level length constraint of this net, or null if unconstrained directly
   * at the net level.
   */
  public NetLengthConstraint getExplicitLengthConstraint() {
    return this.lengthConstraint;
  }

  /**
   * Returns the effective meander constraint of this net for the specified target. If an explicit
   * net-level constraint is present, it is merged with any inherited net-class constraint;
   * otherwise falls back to the net-class constraint.
   */
  public NetMeanderConstraint getMeanderConstraint(NetMeanderConstraint.MeanderTarget target) {
    if (target == null) {
      target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }
    NetMeanderConstraint classConstraint =
        (this.netClass != null) ? this.netClass.getMeanderConstraint(target) : null;
    NetMeanderConstraint explicit = getExplicitMeanderConstraint(target);
    return NetMeanderConstraint.merge(explicit, classConstraint);
  }

  /**
   * Returns the effective single-track meander constraint of this net. If an explicit net-level
   * constraint is present, it is merged with any inherited net-class constraint; otherwise falls
   * back to the net-class constraint.
   */
  public NetMeanderConstraint getMeanderConstraint() {
    return getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
  }

  /**
   * Sets the explicit meander constraint for this net for the specified target. Pass null to
   * inherit from net-class.
   */
  public void setMeanderConstraint(
      NetMeanderConstraint.MeanderTarget target, NetMeanderConstraint constraint) {
    if (target == null) {
      target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }
    if (this.meanderConstraints == null) {
      this.meanderConstraints = new EnumMap<>(NetMeanderConstraint.MeanderTarget.class);
    }
    if (constraint == null) {
      this.meanderConstraints.remove(target);
      if (target == NetMeanderConstraint.MeanderTarget.SINGLE_TRACK) {
        this.meanderConstraint = null;
      }
    } else {
      NetMeanderConstraint tagged = constraint.withTarget(target);
      this.meanderConstraints.put(target, tagged);
      if (target == NetMeanderConstraint.MeanderTarget.SINGLE_TRACK) {
        this.meanderConstraint = tagged;
      }
    }
  }

  /**
   * Sets the explicit single-track meander constraint for this net. Pass null to inherit from
   * net-class.
   */
  public void setMeanderConstraint(NetMeanderConstraint constraint) {
    setMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK, constraint);
  }

  /** Returns true if this net has an explicit net-level meander constraint for the given target. */
  public boolean hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget target) {
    return getExplicitMeanderConstraint(target) != null;
  }

  /**
   * Returns true if this net has an explicit net-level single-track meander constraint configured.
   */
  public boolean hasExplicitMeanderConstraint() {
    return hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
  }

  /**
   * Returns true if this net has an explicit net-level meander constraint configured for any
   * target.
   */
  public boolean hasAnyExplicitMeanderConstraint() {
    if (this.meanderConstraints != null && !this.meanderConstraints.isEmpty()) {
      return true;
    }
    return this.meanderConstraint != null;
  }

  /**
   * Returns the explicit net-level meander constraint of this net for the given target, or null if
   * inheriting from the net class.
   */
  public NetMeanderConstraint getExplicitMeanderConstraint(
      NetMeanderConstraint.MeanderTarget target) {
    if (target == null) {
      target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }
    if (this.meanderConstraints == null) {
      this.meanderConstraints = new EnumMap<>(NetMeanderConstraint.MeanderTarget.class);
    }
    NetMeanderConstraint constraint = this.meanderConstraints.get(target);
    if (constraint == null && target == NetMeanderConstraint.MeanderTarget.SINGLE_TRACK) {
      return this.meanderConstraint;
    }
    return constraint;
  }

  /**
   * Returns the explicit net-level single-track meander constraint of this net, or null if
   * inheriting from the net class.
   */
  public NetMeanderConstraint getExplicitMeanderConstraint() {
    return getExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
  }

  /** Returns the pins and conduction areas of this net. */
  public Collection<Item> getTerminalItems() {
    Collection<Item> result = new LinkedList<>();
    BasicBoard board = this.netList.getBoard();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof Connectable) {
        if (currentItem.containsNet(this.netNumber) && !currentItem.isRoutable()) {
          result.add(currentItem);
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
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof Pin pin) {
        if (currentItem.containsNet(this.netNumber)) {
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
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem.containsNet(this.netNumber)) {
        result.add(currentItem);
      }
    }
    return result;
  }

  /** Returns the cumulative trace length of all traces on the board belonging to this net. */
  public double getTraceLength() {
    double cumulativeTraceLength = 0;
    Collection<Item> netItems = netList.getBoard().getConnectableItems(this.netNumber);
    for (Item currentItem : netItems) {

      if (currentItem instanceof Trace trace) {
        cumulativeTraceLength += trace.getLength();
      }
    }
    return cumulativeTraceLength;
  }

  /** Returns the count of vias on the board belonging to this net. */
  public int getViaCount() {
    int result = 0;
    Collection<Item> netItems = netList.getBoard().getConnectableItems(this.netNumber);
    for (Item currentItem : netItems) {
      if (currentItem instanceof Via) {
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
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    int viaCount = this.getViaCount();
    double cumulativeTraceLength = this.getTraceLength();
    Collection<Item> terminalItems = this.getTerminalItems();
    Collection<Printable> terminals = new LinkedList<>(terminalItems);
    int terminalItemCount = terminals.size();

    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("net") + " ");
    printer.appendBold(this.name);
    printer.appendBold(": ");
    printer.append(tm.getText("class") + " ");
    printer.append(netClass.getName(), tm.getText("netClass"), netClass);
    printer.append(", ");
    printer.appendObjects(
        String.valueOf(terminalItemCount), tm.getText("terminal_items_2"), terminals);
    printer.append(" " + tm.getText("terminalItems"));
    printer.append(", " + tm.getText("viaCount") + " ");
    printer.append(String.valueOf(viaCount));
    printer.append(", " + tm.getText("traceLength") + " ");
    printer.append(cumulativeTraceLength);
    printer.newline();
  }
}
