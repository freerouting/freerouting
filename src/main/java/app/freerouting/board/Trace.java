package app.freerouting.board;

import app.freerouting.board.optimize.TraceTightener;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.Nets;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Class describing functionality required for traces in the plane. */
public abstract class Trace extends Item implements Connectable, Serializable {

  private final int halfWidth; // half width of the trace pen
  private int layer; // board layer of the trace

  Trace(
      int layer,
      int halfWidth,
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      int groupId,
      FixedState fixedState,
      BasicBoard board) {
    super(netNumbers, clearanceClassIndex, id, groupId, fixedState, board);
    this.halfWidth = halfWidth;
    layer = Math.max(layer, 0);
    if (board != null) {
      layer = Math.min(layer, board.getLayerCount() - 1);
    }
    this.layer = layer;
  }

  /** Returns the first corner of the trace. */
  public abstract Point firstCorner();

  /** Returns the last corner of the trace. */
  public abstract Point lastCorner();

  @Override
  public int firstLayer() {
    return this.layer;
  }

  @Override
  public int lastLayer() {
    return this.layer;
  }

  public int getLayer() {
    return this.layer;
  }

  public void setLayer(int layer) {
    this.layer = layer;
  }

  public int getHalfWidth() {
    return halfWidth;
  }

  /** Returns the length of this trace. */
  public abstract double getLength();

  /**
   * Returns the half width enlarged by the clearance compensation value for the search tree. Equals
   * getHalfWidth(), if no clearance compensation is used in this tree.
   */
  public int getCompensatedHalfWidth(ShapeSearchTree searchTree) {
    return this.halfWidth
        + searchTree.clearanceCompensationValue(clearanceClassIndex(), this.layer);
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other == this
        || other instanceof ViaObstacleArea
        || other instanceof ComponentObstacleArea) {
      return false;
    }
    if (other instanceof ConductionArea area && !area.getIsObstacle()) {
      return false;
    }
    return !other.sharesNet(this);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace equal to its first
   * corner.
   */
  public Set<Item> getStartContacts() {
    return getNormalContacts(firstCorner(), false);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace equal to its last
   * corner.
   */
  public Set<Item> getEndContacts() {
    return getNormalContacts(lastCorner(), false);
  }

  @Override
  public Point normalContactPoint(Item other) {
    return other.normalContactPoint(this);
  }

  @Override
  Point normalContactPoint(DrillItem drillItem) {
    return drillItem.normalContactPoint(this);
  }

  @Override
  Point normalContactPoint(Trace other) {
    if (this.layer != other.layer) {
      return null;
    }
    boolean contactAtFirstCorner =
        this.firstCorner().equals(other.firstCorner())
            || this.firstCorner().equals(other.lastCorner());
    boolean contactAtLastCorner =
        this.lastCorner().equals(other.firstCorner())
            || this.lastCorner().equals(other.lastCorner());
    Point result;
    if (!(contactAtFirstCorner || contactAtLastCorner)
        || contactAtFirstCorner && contactAtLastCorner) {
      // no contact point or more than 1 contact point
      result = null;
    } else if (contactAtFirstCorner) {
      result = this.firstCorner();
    } else { // contact at last corner
      result = this.lastCorner();
    }
    return result;
  }

  @Override
  public Set<Item> getNormalContacts() {
    Set<Item> result = new TreeSet<>();
    Point startCorner = this.firstCorner();
    if (startCorner != null) {
      result.addAll(getNormalContacts(startCorner, false));
    }
    Point endCorner = this.lastCorner();
    if (endCorner != null) {
      result.addAll(getNormalContacts(endCorner, false));
    }
    return result;
  }

  /**
   * Get a list of all items having a connection point at point on the layer of this trace. If
   * ignoreNet is false, only contacts to items sharing a net with this trace are calculated. This
   * is the normal case.
   */
  public Set<Item> getNormalContacts(Point point, boolean ignoreNet) {
    if (point == null || !(point.equals(this.firstCorner()) || point.equals(this.lastCorner()))) {
      return new TreeSet<>();
    }
    TileShape searchShape = TileShape.getInstance(point);
    Set<SearchTreeObject> overlaps = board.overlappingObjects(searchShape, this.layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currentObject : overlaps) {
      if (!(currentObject instanceof Item currentItem)) {
        continue;
      }
      if (currentItem != this
          && currentItem.sharesLayer(this)
          && (ignoreNet || currentItem.sharesNet(this))) {
        if (currentItem instanceof Trace currentTrace) {
          if (point.equals(currentTrace.firstCorner()) || point.equals(currentTrace.lastCorner())) {
            result.add(currentItem);
          }
        } else if (currentItem instanceof DrillItem currentDrillItem) {
          if (point.equals(currentDrillItem.getCenter())) {
            result.add(currentItem);
          }
        } else if (currentItem instanceof ConductionArea currentArea) {
          if (currentArea.getArea().contains(point)) {
            result.add(currentItem);
          }
        }
      }
    }
    return result;
  }

  @Override
  public boolean isRoutable() {
    return !isUserFixed() && (this.netCount() > 0);
  }

  /** Returns true, if this trace is not contacted at its first or at its last point. */
  @Override
  public boolean isTail() {
    Collection<Item> contactList = this.getStartContacts();
    if (contactList.isEmpty()) {
      return true;
    }
    contactList = this.getEndContacts();
    return contactList.isEmpty();
  }

  @Override
  public boolean isDrillable(int netNumber) {
    return this.containsNet(netNumber);
  }

  /** Looks, if this trace is connected to the same object at its start and its end point. */
  @Override
  public boolean isOverlap() {
    Set<Item> startContacts = this.getStartContacts();
    Set<Item> endContacts = this.getEndContacts();
    return !Collections.disjoint(startContacts, endContacts);
  }

  /**
   * Returns true, if it is not allowed to change the location of this item by the push algorithm.
   */
  @Override
  public boolean isShoveFixed() {
    if (super.isShoveFixed()) {
      return true;
    }

    // check, if the trace belongs to a net, which is not shovable.
    Nets nets = this.board.rules.nets;
    for (int currentNetNumber : this.netNumbers) {
      if (Nets.isNormalNetNumber(currentNetNumber)) {
        if (nets.get(currentNetNumber).getNetClass().isShoveFixed()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the endpoint of this trace with the shortest distance to fromPoint. */
  public Point nearestEndPoint(Point fromPoint) {
    Point p1 = firstCorner();
    Point p2 = lastCorner();
    FloatPoint fromPointFloat = fromPoint.toFloat();
    double d1 = fromPointFloat.distance(p1.toFloat());
    double d2 = fromPointFloat.distance(p2.toFloat());
    Point result;
    if (d1 < d2) {
      result = p1;
    } else {
      result = p2;
    }
    return result;
  }

  /** Checks, if this trace can be reached by other items via more than one path. */
  public boolean isCycle() {
    boolean debugNet49 =
        this.netNumbers != null && this.netNumbers.length > 0 && this.netNumbers[0] == 49;
    if (this.isOverlap()) {
      if (debugNet49) {
        FRLogger.trace(
            "compare_trace_is_cycle_overlap net=49, id="
                + this.getId()
                + ", first="
                + this.firstCorner()
                + ", last="
                + this.lastCorner()
                + ", startContacts="
                + this.getStartContacts().stream()
                    .map(i -> i.getId() + "")
                    .collect(java.util.stream.Collectors.joining(","))
                + ", endContacts="
                + this.getEndContacts().stream()
                    .map(i -> i.getId() + "")
                    .collect(java.util.stream.Collectors.joining(",")));
      }
      return true;
    }
    Collection<Item> startContacts = this.getStartContacts();
    // a cycle exists if through expanding the start contact we reach
    // this trace again via an end contact
    // make sure, that all direct neighbours are
    // expanded from here, to block coming back to
    // this trace via a start contact.
    Set<Item> visitedItems = new TreeSet<>(startContacts);
    boolean ignoreAreas = false;
    if (this.netNumbers.length > 0) {
      Net currentNet = this.board.rules.nets.get(this.netNumbers[0]);
      if (currentNet != null && currentNet.getNetClass() != null) {
        ignoreAreas = currentNet.getNetClass().getIgnoreCyclesWithAreas();
      }
    }
    for (Item currentContact : startContacts) {
      if (currentContact.isCycleRecu(visitedItems, this, this, ignoreAreas)) {
        if (debugNet49) {
          FRLogger.trace(
              "compare_trace_is_cycle_dfs net=49, id="
                  + this.getId()
                  + ", first="
                  + this.firstCorner()
                  + ", last="
                  + this.lastCorner()
                  + ", startContacts="
                  + startContacts.stream()
                      .map(i -> i.getId() + "")
                      .collect(java.util.stream.Collectors.joining(","))
                  + ", found_via="
                  + currentContact.getId());
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public int shapeLayer(int index) {
    return layer;
  }

  @Override
  public Point[] getRatsnestCorners() {
    // Use only uncontacted endpoints of the trace.
    // Otherwise, the allocated memory in the calculation of the incompletes might
    // become very big.
    int stubCount = 0;
    boolean stubAtStart = false;
    boolean stubAtEnd = false;
    if (getStartContacts().isEmpty()) {
      ++stubCount;
      stubAtStart = true;
    }
    if (getEndContacts().isEmpty()) {
      ++stubCount;
      stubAtEnd = true;
    }
    Point[] result = new Point[stubCount];
    int stubNo = 0;
    if (stubAtStart) {
      result[stubNo] = firstCorner();
      ++stubNo;
    }
    if (stubAtEnd) {
      result[stubNo] = lastCorner();
    }
    for (int i = 0; i < result.length; i++) {
      if (result[i] == null) {
        return new Point[0]; // Trace is inconsistent
      }
    }
    return result;
  }

  /**
   * Checks that the connection restrictions to the contact pins are satisfied.
   *
   * <p>If atStart, the start of this trace is checked, else the end. Returns false if a pin is at
   * that end where the connection is checked and the connection is not ok.
   */
  public abstract boolean checkConnectionToPin(boolean atStart);

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.TRACES);
  }

  /**
   * Looks up touching pins at the first corner and the last corner of the trace. Used to avoid acid
   * traps.
   */
  public Set<Pin> touchingPinsAtEndCorners() {
    Set<Pin> result = new TreeSet<>();
    if (this.board == null) {
      return result;
    }
    Point currentEndPoint = this.firstCorner();
    for (int i = 0; i < 2; i++) {
      IntOctagon currentOct = currentEndPoint.surroundingOctagon();
      currentOct = currentOct.enlarge(this.halfWidth);
      Set<Item> currentOverlaps =
          this.board.overlappingItemsWithClearance(
              currentOct, this.layer, new int[0], this.clearanceClassIndex());
      for (Item currentItem : currentOverlaps) {
        if ((currentItem instanceof Pin pin) && currentItem.sharesNet(this)) {
          result.add(pin);
        }
      }
      currentEndPoint = this.lastCorner();
    }
    return result;
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("trace"));
    printer.append(" " + tm.getText("from") + " ");
    printer.append(this.firstCorner().toFloat());
    printer.append(" " + tm.getText("to") + " ");
    printer.append(this.lastCorner().toFloat());
    printer.append(" " + tm.getText("on_layer") + " ");
    printer.append(this.board.layerStructure.layers[this.layer].name);
    printer.append(", " + tm.getText("width") + " ");
    printer.append(2 * this.halfWidth);
    printer.append(", " + tm.getText("length") + " ");
    printer.append(this.getLength());
    this.printConnectableItemInfo(printer, locale);
    printer.newline();
  }

  @Override
  public String getHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    double mmResolution = this.board.communication.getResolution(Unit.MM);
    double widthInMm = (2 * this.halfWidth) / mmResolution;
    double lengthInMm = this.getLength() / mmResolution;

    String layerName = this.board.layerStructure.layers[this.layer].name;
    String widthStr = String.format(locale, "%.4f", widthInMm);
    String lengthStr = String.format(locale, "%.4f", lengthInMm);
    String connInfo = this.getConnectableItemHoverInfo(locale);

    return tm.getText("trace_hover_info", layerName, widthStr, lengthStr, connInfo);
  }

  @Override
  public boolean validate() {
    boolean result = super.validate();

    if (this.firstCorner().equals(this.lastCorner())) {
      FRLogger.warn("Trace.validate: first and last corner are equal");
      result = false;
    }
    return result;
  }

  /**
   * Checks if this trace can be combined with other traces.
   *
   * <p>Returns true if something has been combined.
   */
  abstract boolean combine();

  /**
   * Looks up traces intersecting with this trace and splits them at the intersection points. In
   * case of an overlaps, the traces are split at their first and their last common point. Returns
   * the pieces resulting from splitting. If nothing is split, the result will contain just this
   * Trace. If clipShape != null, the split may be restricted to clipShape.
   */
  public abstract Collection<PolylineTrace> split(IntOctagon clipShape);

  /**
   * Splits this trace into two at point. Returns the 2 pieces of the split trace, or null if
   * nothing was split because for example point is not located on this trace.
   */
  public abstract Trace[] split(Point point);

  /**
   * Tries to make this trace shorter according to its rules. Returns true if the geometry of the
   * trace was changed.
   */
  public abstract boolean pullTight(TraceTightener pullTightAlgo);
}
