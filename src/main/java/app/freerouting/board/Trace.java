package app.freerouting.board;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.Nets;
import app.freerouting.util.TextManager;
import java.awt.Color;
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
      int[] netNoArr,
      int clearanceType,
      int idNo,
      int groupNo,
      FixedState fixedState,
      BasicBoard board) {
    super(netNoArr, clearanceType, idNo, groupNo, fixedState, board);
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
   * Returns the half with enlarged by the clearance compensation value for the tree with id number
   * p_tree_id_no Equals get_half_width(), if no clearance compensation is used in this tree.
   */
  public int getCompensatedHalfWidth(ShapeSearchTree searchTree) {
    return this.halfWidth + searchTree.clearanceCompensationValue(clearanceClassNo(), this.layer);
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
  public Color[] getDrawColors(GraphicsContext graphicsContext) {
    return graphicsContext.getTraceColors(this.isUserFixed());
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MAX_DRAW_PRIORITY;
  }

  @Override
  public double getDrawIntensity(GraphicsContext graphicsContext) {
    return graphicsContext.getTraceColorIntensity();
  }

  /**
   * Get a list of all items having a connection point at p_point on the layer of this trace. If
   * p_ignore_net is false, only contacts to items sharing a net with this trace are calculated.
   * This is the normal case.
   */
  public Set<Item> getNormalContacts(Point point, boolean ignoreNet) {
    if (point == null
        || !(point.equals(this.firstCorner()) || point.equals(this.lastCorner()))) {
      return new TreeSet<>();
    }
    TileShape searchShape = TileShape.getInstance(point);
    Set<SearchTreeObject> overlaps = board.overlappingObjects(searchShape, this.layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currOb : overlaps) {
      if (!(currOb instanceof Item currItem)) {
        continue;
      }
      if (currItem != this
          && currItem.sharesLayer(this)
          && (ignoreNet || currItem.sharesNet(this))) {
        if (currItem instanceof Trace currTrace) {
          if (point.equals(currTrace.firstCorner()) || point.equals(currTrace.lastCorner())) {
            result.add(currItem);
          }
        } else if (currItem instanceof DrillItem currDrillItem) {
          if (point.equals(currDrillItem.getCenter())) {
            result.add(currItem);
          }
        } else if (currItem instanceof ConductionArea currArea) {
          if (currArea.getArea().contains(point)) {
            result.add(currItem);
          }
        }
      }
    }
    return result;
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
  public boolean isDrillable(int netNo) {
    return this.containsNet(netNo);
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
    for (int currNetNo : this.netNoArr) {
      if (Nets.isNormalNetNo(currNetNo)) {
        if (nets.get(currNetNo).getNetClass().isShoveFixed()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the endpoint of this trace with the shortest distance to p_from_point. */
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
        this.netNoArr != null && this.netNoArr.length > 0 && this.netNoArr[0] == 49;
    if (this.isOverlap()) {
      if (debugNet49) {
        FRLogger.trace(
            "compare_trace_is_cycle_overlap net=49, id="
                + this.getIdNo()
                + ", first="
                + this.firstCorner()
                + ", last="
                + this.lastCorner()
                + ", startContacts="
                + this.getStartContacts().stream()
                    .map(i -> i.getIdNo() + "")
                    .collect(java.util.stream.Collectors.joining(","))
                + ", endContacts="
                + this.getEndContacts().stream()
                    .map(i -> i.getIdNo() + "")
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
    if (this.netNoArr.length > 0) {
      Net currentNet = this.board.rules.nets.get(this.netNoArr[0]);
      if (currentNet != null && currentNet.getNetClass() != null) {
        ignoreAreas = currentNet.getNetClass().getIgnoreCyclesWithAreas();
      }
    }
    for (Item currContact : startContacts) {
      if (currContact.isCycleRecu(visitedItems, this, this, ignoreAreas)) {
        if (debugNet49) {
          FRLogger.trace(
              "compare_trace_is_cycle_dfs net=49, id="
                  + this.getIdNo()
                  + ", first="
                  + this.firstCorner()
                  + ", last="
                  + this.lastCorner()
                  + ", startContacts="
                  + startContacts.stream()
                      .map(i -> i.getIdNo() + "")
                      .collect(java.util.stream.Collectors.joining(","))
                  + ", found_via="
                  + currContact.getIdNo());
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
   * checks, that the connection restrictions to the contact pins are satisfied. If p_at_start, the.
   * start of this trace is checked, else the end. Returns false, if a pin is at that end, where the
   * connection is checked and the connection is not ok.
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
  Set<Pin> touchingPinsAtEndCorners() {
    Set<Pin> result = new TreeSet<>();
    if (this.board == null) {
      return result;
    }
    Point currEndPoint = this.firstCorner();
    for (int i = 0; i < 2; i++) {
      IntOctagon currOct = currEndPoint.surroundingOctagon();
      currOct = currOct.enlarge(this.halfWidth);
      Set<Item> currOverlaps =
          this.board.overlappingItemsWithClearance(
              currOct, this.layer, new int[0], this.clearanceClassNo());
      for (Item currItem : currOverlaps) {
        if ((currItem instanceof Pin pin) && currItem.sharesNet(this)) {
          result.add(pin);
        }
      }
      currEndPoint = this.lastCorner();
    }
    return result;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("trace"));
    window.append(" " + tm.getText("from") + " ");
    window.append(this.firstCorner().toFloat());
    window.append(" " + tm.getText("to") + " ");
    window.append(this.lastCorner().toFloat());
    window.append(" " + tm.getText("on_layer") + " ");
    window.append(this.board.layerStructure.arr[this.layer].name);
    window.append(", " + tm.getText("width") + " ");
    window.append(2 * this.halfWidth);
    window.append(", " + tm.getText("length") + " ");
    window.append(this.getLength());
    this.printConnectableItemInfo(window, locale);
    window.newline();
  }

  @Override
  public String getHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    double mmResolution = this.board.communication.getResolution(Unit.MM);
    double widthInMm = (2 * this.halfWidth) / mmResolution;
    double lengthInMm = this.getLength() / mmResolution;

    String layerName = this.board.layerStructure.arr[this.layer].name;
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
   * looks, if this trace can be combined with other traces . Returns true, if something has been.
   * combined.
   */
  abstract boolean combine();

  /**
   * Looks up traces intersecting with this trace and splits them at the intersection points. In
   * case of an overlaps, the traces are split at their first and their last common point. Returns
   * the pieces resulting from splitting. If nothing is split, the result will contain just this
   * Trace. If p_clip_shape != null, the split may be restricted to p_clip_shape.
   */
  public abstract Collection<PolylineTrace> split(IntOctagon clipShape);

  /**
   * Splits this trace into two at p_point. Returns the 2 pieces of the split trace, or null if
   * nothing was split because for example p_point is not located on this trace.
   */
  public abstract Trace[] split(Point point);

  /**
   * Tries to make this trace shorter according to its rules. Returns true if the geometry of the
   * trace was changed.
   */
  public abstract boolean pullTight(PullTightAlgo pullTightAlgo);
}
