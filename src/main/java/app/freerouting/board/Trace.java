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
      int p_layer,
      int p_half_width,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
    halfWidth = p_half_width;
    p_layer = Math.max(p_layer, 0);
    if (p_board != null) {
      p_layer = Math.min(p_layer, p_board.getLayerCount() - 1);
    }
    layer = p_layer;
  }

  /** returns the first corner of the trace */
  public abstract Point firstCorner();

  /** returns the last corner of the trace */
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

  public void setLayer(int p_layer) {
    this.layer = p_layer;
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
  public int getCompensatedHalfWidth(ShapeSearchTree p_search_tree) {
    return this.halfWidth
        + p_search_tree.clearanceCompensationValue(clearanceClassNo(), this.layer);
  }

  @Override
  public boolean isObstacle(Item p_other) {
    if (p_other == this
        || p_other instanceof ViaObstacleArea
        || p_other instanceof ComponentObstacleArea) {
      return false;
    }
    if (p_other instanceof ConductionArea area && !area.getIsObstacle()) {
      return false;
    }
    return !p_other.sharesNet(this);
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
  public Point normalContactPoint(Item p_other) {
    return p_other.normalContactPoint(this);
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
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    return p_graphics_context.getTraceColors(this.isUserFixed());
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MAX_DRAW_PRIORITY;
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.getTraceColorIntensity();
  }

  /**
   * Get a list of all items having a connection point at p_point on the layer of this trace. If
   * p_ignore_net is false, only contacts to items sharing a net with this trace are calculated.
   * This is the normal case.
   */
  public Set<Item> getNormalContacts(Point p_point, boolean p_ignore_net) {
    if (p_point == null
        || !(p_point.equals(this.firstCorner()) || p_point.equals(this.lastCorner()))) {
      return new TreeSet<>();
    }
    TileShape searchShape = TileShape.getInstance(p_point);
    Set<SearchTreeObject> overlaps = board.overlappingObjects(searchShape, this.layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currOb : overlaps) {
      if (!(currOb instanceof Item currItem)) {
        continue;
      }
      if (currItem != this
          && currItem.sharesLayer(this)
          && (p_ignore_net || currItem.sharesNet(this))) {
        if (currItem instanceof Trace currTrace) {
          if (p_point.equals(currTrace.firstCorner()) || p_point.equals(currTrace.lastCorner())) {
            result.add(currItem);
          }
        } else if (currItem instanceof DrillItem curr_drill_item) {
          if (p_point.equals(curr_drill_item.getCenter())) {
            result.add(currItem);
          }
        } else if (currItem instanceof ConductionArea currArea) {
          if (currArea.getArea().contains(p_point)) {
            result.add(currItem);
          }
        }
      }
    }
    return result;
  }

  @Override
  Point normalContactPoint(DrillItem p_drill_item) {
    return p_drill_item.normalContactPoint(this);
  }

  @Override
  Point normalContactPoint(Trace p_other) {
    if (this.layer != p_other.layer) {
      return null;
    }
    boolean contactAtFirstCorner =
        this.firstCorner().equals(p_other.firstCorner())
            || this.firstCorner().equals(p_other.lastCorner());
    boolean contactAtLastCorner =
        this.lastCorner().equals(p_other.firstCorner())
            || this.lastCorner().equals(p_other.lastCorner());
    Point result;
    if (!(contactAtFirstCorner || contactAtLastCorner)
        || contactAtFirstCorner && contactAtLastCorner) {
      // no contact point or more than 1 contact point
      result = null;
    } else if (contactAtFirstCorner) {
      result = this.firstCorner();
    } else // contact at last corner
    {
      result = this.lastCorner();
    }
    return result;
  }

  @Override
  public boolean isDrillable(int p_net_no) {
    return this.containsNet(p_net_no);
  }

  /** looks, if this trace is connected to the same object at its start and its end point */
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

  /** returns the endpoint of this trace with the shortest distance to p_from_point */
  public Point nearestEndPoint(Point p_from_point) {
    Point p1 = firstCorner();
    Point p2 = lastCorner();
    FloatPoint fromPoint = p_from_point.toFloat();
    double d1 = fromPoint.distance(p1.toFloat());
    double d2 = fromPoint.distance(p2.toFloat());
    Point result;
    if (d1 < d2) {
      result = p1;
    } else {
      result = p2;
    }
    return result;
  }

  /** Checks, if this trace can be reached by other items via more than one path */
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
      Net currNet = this.board.rules.nets.get(this.netNoArr[0]);
      if (currNet != null && currNet.getNetClass() != null) {
        ignoreAreas = currNet.getNetClass().getIgnoreCyclesWithAreas();
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
  public int shapeLayer(int p_index) {
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
   * checks, that the connection restrictions to the contact pins are satisfied. If p_at_start, the
   * start of this trace is checked, else the end. Returns false, if a pin is at that end, where the
   * connection is checked and the connection is not ok.
   */
  public abstract boolean checkConnectionToPin(boolean p_at_start);

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    if (!this.isSelectedByFixedFilter(p_filter)) {
      return false;
    }
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.TRACES);
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
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("trace"));
    p_window.append(" " + tm.getText("from") + " ");
    p_window.append(this.firstCorner().toFloat());
    p_window.append(" " + tm.getText("to") + " ");
    p_window.append(this.lastCorner().toFloat());
    p_window.append(" " + tm.getText("on_layer") + " ");
    p_window.append(this.board.layerStructure.arr[this.layer].name);
    p_window.append(", " + tm.getText("width") + " ");
    p_window.append(2 * this.halfWidth);
    p_window.append(", " + tm.getText("length") + " ");
    p_window.append(this.getLength());
    this.printConnectableItemInfo(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String getHoverInfo(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    double mmResolution = this.board.communication.getResolution(Unit.MM);
    double widthInMm = (2 * this.halfWidth) / mmResolution;
    double lengthInMm = this.getLength() / mmResolution;

    String layerName = this.board.layerStructure.arr[this.layer].name;
    String widthStr = String.format(p_locale, "%.4f", widthInMm);
    String lengthStr = String.format(p_locale, "%.4f", lengthInMm);
    String connInfo = this.getConnectableItemHoverInfo(p_locale);

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
   * looks, if this trace can be combined with other traces . Returns true, if something has been
   * combined.
   */
  abstract boolean combine();

  /**
   * Looks up traces intersecting with this trace and splits them at the intersection points. In
   * case of an overlaps, the traces are split at their first and their last common point. Returns
   * the pieces resulting from splitting. If nothing is split, the result will contain just this
   * Trace. If p_clip_shape != null, the split may be restricted to p_clip_shape.
   */
  public abstract Collection<PolylineTrace> split(IntOctagon p_clip_shape);

  /**
   * Splits this trace into two at p_point. Returns the 2 pieces of the split trace, or null if
   * nothing was split because for example p_point is not located on this trace.
   */
  public abstract Trace[] split(Point p_point);

  /**
   * Tries to make this trace shorter according to its rules. Returns true if the geometry of the
   * trace was changed.
   */
  public abstract boolean pullTight(PullTightAlgo p_pull_tight_algo);
}
