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
      p_layer = Math.min(p_layer, p_board.get_layer_count() - 1);
    }
    layer = p_layer;
  }

  /** returns the first corner of the trace */
  public abstract Point first_corner();

  /** returns the last corner of the trace */
  public abstract Point last_corner();

  @Override
  public int first_layer() {
    return this.layer;
  }

  @Override
  public int last_layer() {
    return this.layer;
  }

  public int get_layer() {
    return this.layer;
  }

  public void set_layer(int p_layer) {
    this.layer = p_layer;
  }

  public int get_half_width() {
    return halfWidth;
  }

  /** Returns the length of this trace. */
  public abstract double get_length();

  /**
   * Returns the half with enlarged by the clearance compensation value for the tree with id number
   * p_tree_id_no Equals get_half_width(), if no clearance compensation is used in this tree.
   */
  public int get_compensated_half_width(ShapeSearchTree p_search_tree) {
    return this.halfWidth
        + p_search_tree.clearance_compensation_value(clearance_class_no(), this.layer);
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (p_other == this
        || p_other instanceof ViaObstacleArea
        || p_other instanceof ComponentObstacleArea) {
      return false;
    }
    if (p_other instanceof ConductionArea area && !area.get_is_obstacle()) {
      return false;
    }
    return !p_other.shares_net(this);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace equal to its first
   * corner.
   */
  public Set<Item> get_start_contacts() {
    return get_normal_contacts(first_corner(), false);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace equal to its last
   * corner.
   */
  public Set<Item> get_end_contacts() {
    return get_normal_contacts(last_corner(), false);
  }

  @Override
  public Point normal_contact_point(Item p_other) {
    return p_other.normal_contact_point(this);
  }

  @Override
  public Set<Item> get_normal_contacts() {
    Set<Item> result = new TreeSet<>();
    Point startCorner = this.first_corner();
    if (startCorner != null) {
      result.addAll(get_normal_contacts(startCorner, false));
    }
    Point endCorner = this.last_corner();
    if (endCorner != null) {
      result.addAll(get_normal_contacts(endCorner, false));
    }
    return result;
  }

  @Override
  public boolean is_routable() {
    return !is_user_fixed() && (this.net_count() > 0);
  }

  /** Returns true, if this trace is not contacted at its first or at its last point. */
  @Override
  public boolean is_tail() {
    Collection<Item> contactList = this.get_start_contacts();
    if (contactList.isEmpty()) {
      return true;
    }
    contactList = this.get_end_contacts();
    return contactList.isEmpty();
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_trace_colors(this.is_user_fixed());
  }

  @Override
  public int get_draw_priority() {
    return Drawable.MAX_DRAW_PRIORITY;
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_trace_color_intensity();
  }

  /**
   * Get a list of all items having a connection point at p_point on the layer of this trace. If
   * p_ignore_net is false, only contacts to items sharing a net with this trace are calculated.
   * This is the normal case.
   */
  public Set<Item> get_normal_contacts(Point p_point, boolean p_ignore_net) {
    if (p_point == null
        || !(p_point.equals(this.first_corner()) || p_point.equals(this.last_corner()))) {
      return new TreeSet<>();
    }
    TileShape searchShape = TileShape.get_instance(p_point);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(searchShape, this.layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currOb : overlaps) {
      if (!(currOb instanceof Item currItem)) {
        continue;
      }
      if (currItem != this
          && currItem.shares_layer(this)
          && (p_ignore_net || currItem.shares_net(this))) {
        if (currItem instanceof Trace currTrace) {
          if (p_point.equals(currTrace.first_corner()) || p_point.equals(currTrace.last_corner())) {
            result.add(currItem);
          }
        } else if (currItem instanceof DrillItem curr_drill_item) {
          if (p_point.equals(curr_drill_item.get_center())) {
            result.add(currItem);
          }
        } else if (currItem instanceof ConductionArea currArea) {
          if (currArea.get_area().contains(p_point)) {
            result.add(currItem);
          }
        }
      }
    }
    return result;
  }

  @Override
  Point normal_contact_point(DrillItem p_drill_item) {
    return p_drill_item.normal_contact_point(this);
  }

  @Override
  Point normal_contact_point(Trace p_other) {
    if (this.layer != p_other.layer) {
      return null;
    }
    boolean contactAtFirstCorner =
        this.first_corner().equals(p_other.first_corner())
            || this.first_corner().equals(p_other.last_corner());
    boolean contactAtLastCorner =
        this.last_corner().equals(p_other.first_corner())
            || this.last_corner().equals(p_other.last_corner());
    Point result;
    if (!(contactAtFirstCorner || contactAtLastCorner)
        || contactAtFirstCorner && contactAtLastCorner) {
      // no contact point or more than 1 contact point
      result = null;
    } else if (contactAtFirstCorner) {
      result = this.first_corner();
    } else // contact at last corner
    {
      result = this.last_corner();
    }
    return result;
  }

  @Override
  public boolean is_drillable(int p_net_no) {
    return this.contains_net(p_net_no);
  }

  /** looks, if this trace is connected to the same object at its start and its end point */
  @Override
  public boolean is_overlap() {
    Set<Item> startContacts = this.get_start_contacts();
    Set<Item> endContacts = this.get_end_contacts();
    return !Collections.disjoint(startContacts, endContacts);
  }

  /**
   * Returns true, if it is not allowed to change the location of this item by the push algorithm.
   */
  @Override
  public boolean is_shove_fixed() {
    if (super.is_shove_fixed()) {
      return true;
    }

    // check, if the trace belongs to a net, which is not shovable.
    Nets nets = this.board.rules.nets;
    for (int currNetNo : this.netNoArr) {
      if (Nets.is_normal_net_no(currNetNo)) {
        if (nets.get(currNetNo).getNetClass().is_shove_fixed()) {
          return true;
        }
      }
    }
    return false;
  }

  /** returns the endpoint of this trace with the shortest distance to p_from_point */
  public Point nearest_end_point(Point p_from_point) {
    Point p1 = first_corner();
    Point p2 = last_corner();
    FloatPoint fromPoint = p_from_point.to_float();
    double d1 = fromPoint.distance(p1.to_float());
    double d2 = fromPoint.distance(p2.to_float());
    Point result;
    if (d1 < d2) {
      result = p1;
    } else {
      result = p2;
    }
    return result;
  }

  /** Checks, if this trace can be reached by other items via more than one path */
  public boolean is_cycle() {
    boolean debugNet49 =
        this.netNoArr != null && this.netNoArr.length > 0 && this.netNoArr[0] == 49;
    if (this.is_overlap()) {
      if (debugNet49) {
        FRLogger.trace(
            "compare_trace_is_cycle_overlap net=49, id="
                + this.get_id_no()
                + ", first="
                + this.first_corner()
                + ", last="
                + this.last_corner()
                + ", startContacts="
                + this.get_start_contacts().stream()
                    .map(i -> i.get_id_no() + "")
                    .collect(java.util.stream.Collectors.joining(","))
                + ", endContacts="
                + this.get_end_contacts().stream()
                    .map(i -> i.get_id_no() + "")
                    .collect(java.util.stream.Collectors.joining(",")));
      }
      return true;
    }
    Collection<Item> startContacts = this.get_start_contacts();
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
        ignoreAreas = currNet.getNetClass().get_ignore_cycles_with_areas();
      }
    }
    for (Item currContact : startContacts) {
      if (currContact.is_cycle_recu(visitedItems, this, this, ignoreAreas)) {
        if (debugNet49) {
          FRLogger.trace(
              "compare_trace_is_cycle_dfs net=49, id="
                  + this.get_id_no()
                  + ", first="
                  + this.first_corner()
                  + ", last="
                  + this.last_corner()
                  + ", startContacts="
                  + startContacts.stream()
                      .map(i -> i.get_id_no() + "")
                      .collect(java.util.stream.Collectors.joining(","))
                  + ", found_via="
                  + currContact.get_id_no());
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public int shape_layer(int p_index) {
    return layer;
  }

  @Override
  public Point[] get_ratsnest_corners() {
    // Use only uncontacted endpoints of the trace.
    // Otherwise, the allocated memory in the calculation of the incompletes might
    // become very big.
    int stubCount = 0;
    boolean stubAtStart = false;
    boolean stubAtEnd = false;
    if (get_start_contacts().isEmpty()) {
      ++stubCount;
      stubAtStart = true;
    }
    if (get_end_contacts().isEmpty()) {
      ++stubCount;
      stubAtEnd = true;
    }
    Point[] result = new Point[stubCount];
    int stubNo = 0;
    if (stubAtStart) {
      result[stubNo] = first_corner();
      ++stubNo;
    }
    if (stubAtEnd) {
      result[stubNo] = last_corner();
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
  public abstract boolean check_connection_to_pin(boolean p_at_start);

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.TRACES);
  }

  /**
   * Looks up touching pins at the first corner and the last corner of the trace. Used to avoid acid
   * traps.
   */
  Set<Pin> touching_pins_at_end_corners() {
    Set<Pin> result = new TreeSet<>();
    if (this.board == null) {
      return result;
    }
    Point currEndPoint = this.first_corner();
    for (int i = 0; i < 2; i++) {
      IntOctagon currOct = currEndPoint.surrounding_octagon();
      currOct = currOct.enlarge(this.halfWidth);
      Set<Item> currOverlaps =
          this.board.overlapping_items_with_clearance(
              currOct, this.layer, new int[0], this.clearance_class_no());
      for (Item currItem : currOverlaps) {
        if ((currItem instanceof Pin pin) && currItem.shares_net(this)) {
          result.add(pin);
        }
      }
      currEndPoint = this.last_corner();
    }
    return result;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("trace"));
    p_window.append(" " + tm.getText("from") + " ");
    p_window.append(this.first_corner().to_float());
    p_window.append(" " + tm.getText("to") + " ");
    p_window.append(this.last_corner().to_float());
    p_window.append(" " + tm.getText("on_layer") + " ");
    p_window.append(this.board.layerStructure.arr[this.layer].name);
    p_window.append(", " + tm.getText("width") + " ");
    p_window.append(2 * this.halfWidth);
    p_window.append(", " + tm.getText("length") + " ");
    p_window.append(this.get_length());
    this.print_connectable_item_info(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String get_hover_info(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    double mmResolution = this.board.communication.get_resolution(Unit.MM);
    double widthInMm = (2 * this.halfWidth) / mmResolution;
    double lengthInMm = this.get_length() / mmResolution;

    String layerName = this.board.layerStructure.arr[this.layer].name;
    String widthStr = String.format(p_locale, "%.4f", widthInMm);
    String lengthStr = String.format(p_locale, "%.4f", lengthInMm);
    String connInfo = this.get_connectable_item_hover_info(p_locale);

    return tm.getText("trace_hover_info", layerName, widthStr, lengthStr, connInfo);
  }

  @Override
  public boolean validate() {
    boolean result = super.validate();

    if (this.first_corner().equals(this.last_corner())) {
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
  public abstract boolean pull_tight(PullTightAlgo p_pull_tight_algo);
}
