package app.freerouting.board;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.rules.Net;
import app.freerouting.rules.Nets;
import java.awt.Color;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class describing functionality required for traces in the plane.
 */
public abstract class Trace extends Item implements Connectable, Serializable {

  private final int half_width; // half width of the trace pen
  private int layer; // board layer of the trace

  Trace(int p_layer, int p_half_width, int[] p_net_no_arr, int p_clearance_type, int p_id_no, int p_group_no,
      FixedState p_fixed_state, BasicBoard p_board) {
    super(p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
    half_width = p_half_width;
    p_layer = Math.max(p_layer, 0);
    if (p_board != null) {
      p_layer = Math.min(p_layer, p_board.get_layer_count() - 1);
    }
    layer = p_layer;
  }

  private static String formatNetLabel(BasicBoard board, int netNo) {
    String netName = "Unknown";
    if (board != null && board.rules != null && board.rules.nets != null
        && netNo >= 0 && netNo <= board.rules.nets.max_net_no()
        && board.rules.nets.get(netNo) != null) {
      netName = board.rules.nets.get(netNo).name;
    }
    return "Net #" + netNo + " (" + netName + ")";
  }

  /**
   * returns the first corner of the trace
   */
  public abstract Point first_corner();

  /**
   * returns the last corner of the trace
   */
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
    return half_width;
  }

  /**
   * Returns the length of this trace.
   */
  public abstract double get_length();

  /**
   * Returns the half with enlarged by the clearance compensation value for the
   * tree with id number p_tree_id_no Equals get_half_width(), if no clearance
   * compensation is used in this tree.
   */
  public int get_compensated_half_width(ShapeSearchTree p_search_tree) {
    return this.half_width + p_search_tree.clearance_compensation_value(clearance_class_no(), this.layer);
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (p_other == this || p_other instanceof ViaObstacleArea || p_other instanceof ComponentObstacleArea) {
      return false;
    }
    if (p_other instanceof ConductionArea area && !area.get_is_obstacle()) {
      return false;
    }
    return !p_other.shares_net(this);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace
   * equal to its first corner.
   */
  public Set<Item> get_start_contacts() {
    return get_normal_contacts(first_corner(), false);
  }

  /**
   * Get a list of all items with a connection point on the layer of this trace
   * equal to its last corner.
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
    Point start_corner = this.first_corner();
    if (start_corner != null) {
      result.addAll(get_normal_contacts(start_corner, false));
    }
    Point end_corner = this.last_corner();
    if (end_corner != null) {
      result.addAll(get_normal_contacts(end_corner, false));
    }
    return result;
  }

  @Override
  public boolean is_routable() {
    return !is_user_fixed() && (this.net_count() > 0);
  }

  /**
   * Returns true, if this trace is not contacted at its first or at its last
   * point.
   */
  @Override
  public boolean is_tail() {
    Collection<Item> start_contacts = this.get_start_contacts();
    Collection<Item> end_contacts = this.get_end_contacts();
    boolean is_tail = start_contacts.isEmpty() || end_contacts.isEmpty();

    if (is_tail) {
      int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
      String reason = "";
      if (start_contacts.isEmpty() && end_contacts.isEmpty()) {
        reason = "both endpoints have no contacts";
      } else if (start_contacts.isEmpty()) {
        reason = "start endpoint has no contacts (end has " + end_contacts.size() + ")";
      } else {
        reason = "end endpoint has no contacts (start has " + start_contacts.size() + ")";
      }

      FRLogger.trace("Trace.is_tail", "tail_detected",
          "Trace detected as tail: trace_id=" + this.get_id_no()
              + ", reason=" + reason
              + ", start_contacts=" + start_contacts.size()
              + ", end_contacts=" + end_contacts.size()
              + ", from=" + this.first_corner()
              + ", to=" + this.last_corner()
              + ", layer=" + this.get_layer(),
          formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
          new Point[] { this.first_corner(), this.last_corner() });
    }

    return is_tail;
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
   * Get a list of all items having a connection point at p_point on the layer of
   * this trace. If p_ignore_net is false, only contacts to items sharing a net
   * with this trace are calculated. This is the
   * normal case.
   */
  public Set<Item> get_normal_contacts(Point p_point, boolean p_ignore_net) {
    if (p_point == null || !(p_point.equals(this.first_corner()) || p_point.equals(this.last_corner()))) {
      return new TreeSet<>();
    }
    // Use tolerance for connectivity detection: max(half_width + 1, 3000) to ensure
    // bidirectional visibility
    int tolerance = Math.max(this.half_width + 1, 3000);
    TileShape search_shape = p_point.surrounding_octagon().enlarge(tolerance);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(search_shape, this.layer);
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.get_normal_contacts for net #94 at " + p_point + " on layer " + this.layer + ": found "
          + overlaps.size() + " overlaps");
    }
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject curr_ob : overlaps) {
      if (!(curr_ob instanceof Item curr_item)) {
        continue;
      }
      if (this.contains_net(94)) {
        FRLogger.debug("  Checking item id=" + curr_item.get_id_no() + " (net #"
            + (curr_item.net_count() > 0 ? curr_item.get_net_no(0) : -1) + ") on layer " + curr_item.shape_layer(0));
      }
      if (curr_item != this && curr_item.shares_layer(this) && (p_ignore_net || curr_item.shares_net(this))) {
        if (curr_item instanceof Trace curr_trace) {
          // Check if points are within tolerance distance
          double d1 = p_point.to_float().distance(curr_trace.first_corner().to_float());
          double d2 = p_point.to_float().distance(curr_trace.last_corner().to_float());
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against trace id=" + curr_trace.get_id_no() + ": d1=" + d1 + ", d2=" + d2
                + ", tolerance=" + tolerance);
          }
          if (isWithinTolerance(p_point, curr_trace.first_corner(), tolerance) ||
              isWithinTolerance(p_point, curr_trace.last_corner(), tolerance)) {
            result.add(curr_item);
          }
        } else if (curr_item instanceof DrillItem curr_drill_item) {
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against drill id=" + curr_drill_item.get_id_no());
          }
          app.freerouting.geometry.planar.Shape drill_shape = curr_drill_item.get_shape_on_layer(this.get_layer());
          // Enlarge by trace tolerance to account for snapping/trace width and ensure
          // robustness
          if (drill_shape != null && drill_shape.enlarge(tolerance).contains(p_point)) {
            result.add(curr_item);
          }
        } else if (curr_item instanceof ConductionArea curr_area) {
          if (curr_area.get_area().contains(p_point)) {
            result.add(curr_item);
          }
        }
      }
    }
    return result;
  }

  /**
   * Checks if two points are within the specified tolerance distance.
   * Uses Manhattan distance for efficiency.
   */
  private boolean isWithinTolerance(Point p1, Point p2, int tolerance) {
    if (p1 == null || p2 == null) {
      return false;
    }
    // Convert to FloatPoint for distance calculation
    FloatPoint fp1 = p1.to_float();
    FloatPoint fp2 = p2.to_float();

    // Use Euclidean distance for correct any-angle handling
    double dx = fp1.x - fp2.x;
    double dy = fp1.y - fp2.y;
    return (dx * dx + dy * dy) <= tolerance * tolerance;
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
    boolean contact_at_first_corner = this.first_corner().equals(p_other.first_corner())
        || this.first_corner().equals(p_other.last_corner());
    boolean contact_at_last_corner = this.last_corner().equals(p_other.first_corner())
        || this.last_corner().equals(p_other.last_corner());
    Point result;
    if (!(contact_at_first_corner || contact_at_last_corner) || contact_at_first_corner && contact_at_last_corner) {
      // no contact point or more than 1 contact point
      result = null;
    } else if (contact_at_first_corner) {
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

  /**
   * looks, if this trace is connected to the same object at its start and its end
   * point
   */
  @Override
  public boolean is_overlap() {
    Set<Item> start_contacts = this.get_start_contacts();
    Set<Item> end_contacts = this.get_end_contacts();
    return !Collections.disjoint(start_contacts, end_contacts);
  }

  /**
   * Returns true, if it is not allowed to change the location of this item by the
   * push algorithm.
   */
  @Override
  public boolean is_shove_fixed() {
    if (super.is_shove_fixed()) {
      return true;
    }

    // check, if the trace belongs to a net, which is not shovable.
    Nets nets = this.board.rules.nets;
    for (int curr_net_no : this.net_no_arr) {
      if (Nets.is_normal_net_no(curr_net_no)) {
        if (nets.get(curr_net_no).get_class().is_shove_fixed()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * returns the endpoint of this trace with the shortest distance to p_from_point
   */
  public Point nearest_end_point(Point p_from_point) {
    Point p1 = first_corner();
    Point p2 = last_corner();
    FloatPoint from_point = p_from_point.to_float();
    double d1 = from_point.distance(p1.to_float());
    double d2 = from_point.distance(p2.to_float());
    Point result;
    if (d1 < d2) {
      result = p1;
    } else {
      result = p2;
    }
    return result;
  }

  /**
   * Checks, if this trace can be reached by other items via more than one path
   */
  public boolean is_cycle() {
    // Check for direct overlaps (e.g. both ends touching the same item).
    // Modified to allow "stubs" on DrillItems/ConductionAreas which are common with
    // fuzzy tolerance.
    Set<Item> start_contacts = this.get_start_contacts();
    Set<Item> end_contacts = this.get_end_contacts();

    if ((globalSettings != null) && (globalSettings.debugSettings != null) && (globalSettings.debugSettings.enableDetailedLogging)) {
      StringBuilder start_info = new StringBuilder();
      StringBuilder end_info = new StringBuilder();

      for (Item contact : start_contacts) {
        start_info.append(contact.getClass().getSimpleName())
            .append("#").append(contact.get_id_no()).append(" ");
      }
      for (Item contact : end_contacts) {
        end_info.append(contact.getClass().getSimpleName())
            .append("#").append(contact.get_id_no()).append(" ");
      }

      FRLogger.trace("Trace.is_cycle", "cycle_check_start",
          "Checking cycle for trace id=" + this.get_id_no()
              + ", from=" + this.first_corner()
              + ", to=" + this.last_corner()
              + ", start_contacts=[" + start_info.toString().trim() + "]"
              + ", end_contacts=[" + end_info.toString().trim() + "]",
          "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
          new Point[] { this.first_corner(), this.last_corner() });
    }

    for (Item contact : start_contacts) {
      if (end_contacts.contains(contact)) {
        if (contact instanceof Trace) {
          if ((globalSettings != null) && (globalSettings.debugSettings != null) && (globalSettings.debugSettings.enableDetailedLogging)) {
            FRLogger.trace("Trace.is_cycle", "cycle_detected_direct",
                "CYCLE DETECTED: Both ends touch same trace id=" + contact.get_id_no()
                    + ", this_id=" + this.get_id_no()
                    + ", from=" + this.first_corner()
                    + ", to=" + this.last_corner(),
                "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
                new Point[] { this.first_corner(), this.last_corner() });
          }
          return true; // Overlapping another trace is a redundant cycle
        }
        if ((globalSettings != null) && (globalSettings.debugSettings != null) && (globalSettings.debugSettings.enableDetailedLogging)) {
          FRLogger.trace("Trace.is_cycle", "stub_allowed",
              "Stub allowed on " + contact.getClass().getSimpleName() + " id=" + contact.get_id_no()
                  + ", this_id=" + this.get_id_no(),
              "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
              new Point[] { this.first_corner(), this.last_corner() });
        }
        return false; // Stubs on Pins/Areas are allowed
      }
    }

    Collection<Item> expansion_contacts = start_contacts;
    // a cycle exists if through expanding the start contact we reach
    // this trace again via an end contact
    // make sure, that all direct neighbours are
    // expanded from here, to block coming back to
    // this trace via a start contact.
    Set<Item> visited_items = new TreeSet<>(expansion_contacts);
    boolean ignore_areas = false;
    if (this.net_no_arr.length > 0) {
      Net curr_net = this.board.rules.nets.get(this.net_no_arr[0]);
      if (curr_net != null && curr_net.get_class() != null) {
        ignore_areas = curr_net.get_class().get_ignore_cycles_with_areas();
      }
    }
    for (Item curr_contact : expansion_contacts) {
      if (curr_contact.is_cycle_recu(visited_items, this, this, ignore_areas)) {
        if ((globalSettings != null) && (globalSettings.debugSettings != null) && (globalSettings.debugSettings.enableDetailedLogging)) {
          FRLogger.trace("Trace.is_cycle", "cycle_detected_recursive",
              "CYCLE DETECTED via recursive search: trace id=" + this.get_id_no()
                  + ", from=" + this.first_corner()
                  + ", to=" + this.last_corner()
                  + ", via contact " + curr_contact.getClass().getSimpleName() + "#" + curr_contact.get_id_no(),
              "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
              new Point[] { this.first_corner(), this.last_corner() });
        }
        return true;
      }
    }

    if ((globalSettings != null) && (globalSettings.debugSettings != null) && (globalSettings.debugSettings.enableDetailedLogging)) {
      FRLogger.trace("Trace.is_cycle", "no_cycle",
          "No cycle detected for trace id=" + this.get_id_no(),
          "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
          new Point[] { this.first_corner(), this.last_corner() });
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
    int stub_count = 0;
    boolean stub_at_start = false;
    boolean stub_at_end = false;
    if (get_start_contacts().isEmpty()) {
      ++stub_count;
      stub_at_start = true;
    }
    if (get_end_contacts().isEmpty()) {
      ++stub_count;
      stub_at_end = true;
    }
    Point[] result = new Point[stub_count];
    int stub_no = 0;
    if (stub_at_start) {
      result[stub_no] = first_corner();
      ++stub_no;
    }
    if (stub_at_end) {
      result[stub_no] = last_corner();
    }
    for (int i = 0; i < result.length; i++) {
      if (result[i] == null) {
        return new Point[0]; // Trace is inconsistent
      }
    }
    return result;
  }

  /**
   * checks, that the connection restrictions to the contact pins are satisfied.
   * If p_at_start, the start of this trace is checked, else the end. Returns
   * false, if a pin is at that end, where the
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
   * Looks up touching pins at the first corner and the last corner of the trace.
   * Used to avoid acid traps.
   */
  Set<Pin> touching_pins_at_end_corners() {
    Set<Pin> result = new TreeSet<>();
    if (this.board == null) {
      return result;
    }
    Point curr_end_point = this.first_corner();
    for (int i = 0; i < 2; i++) {
      IntOctagon curr_oct = curr_end_point.surrounding_octagon();
      curr_oct = curr_oct.enlarge(this.half_width);
      Set<Item> curr_overlaps = this.board.overlapping_items_with_clearance(curr_oct, this.layer, new int[0],
          this.clearance_class_no());
      for (Item curr_item : curr_overlaps) {
        if ((curr_item instanceof Pin pin) && curr_item.shares_net(this)) {
          result.add(pin);
        }
      }
      curr_end_point = this.last_corner();
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
    p_window.append(this.board.layer_structure.arr[this.layer].name);
    p_window.append(", " + tm.getText("width") + " ");
    p_window.append(2 * this.half_width);
    p_window.append(", " + tm.getText("length") + " ");
    p_window.append(this.get_length());
    this.print_connectable_item_info(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String get_hover_info(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    String hover_info = tm.getText("trace") + " " + tm.getText("on_layer") + " : "
        + this.board.layer_structure.arr[this.layer].name + " " + tm.getText("width") + " : " + 2 * this.half_width
        + " " + tm.getText(
            "length")
        + " : " + (int) this.get_length() + " " + this.get_connectable_item_hover_info(p_locale);
    return hover_info;
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
   * looks, if this trace can be combined with other traces . Returns true, if
   * something has been combined.
   */
  abstract boolean combine();

  /**
   * Looks up traces intersecting with this trace and splits them at the
   * intersection points. In case of an overlaps, the traces are split at their
   * first and their last common point. Returns the
   * pieces resulting from splitting. If nothing is split, the result will contain
   * just this Trace. If p_clip_shape != null, the split may be restricted to
   * p_clip_shape.
   */
  public abstract Collection<PolylineTrace> split(IntOctagon p_clip_shape);

  /**
   * Splits this trace into two at p_point. Returns the 2 pieces of the split
   * trace, or null if nothing was split because for example p_point is not
   * located on this trace.
   */
  public abstract Trace[] split(Point p_point);

  /**
   * Tries to make this trace shorter according to its rules. Returns true if the
   * geometry of the trace was changed.
   */
  public abstract boolean pull_tight(PullTightAlgo p_pull_tight_algo);

  /**
   * Looks up all items (traces, vias, pads) connected to this trace at a specific
   * point (start or end). This is used for the push and shove algorithms to find
   * all impacted items when moving this trace.
   */
  public Set<Item> get_impacted_items() {
    Set<Item> result = new TreeSet<>();

    // Add start and end contacts as impacted items for traces
    Point start_corner = this.first_corner();
    Point end_corner = this.last_corner();
    if (start_corner != null) {
      result.addAll(get_normal_contacts(start_corner, false));
    }
    if (end_corner != null) {
      result.addAll(get_normal_contacts(end_corner, false));
    }

    // Log the impacted items for debugging
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.get_impacted_items for net #94, trace id=" + this.get_id_no() + ":");
      for (Item item : result) {
        FRLogger.debug("  Impacted item id=" + item.get_id_no() + " (net #"
            + (item.net_count() > 0 ? item.get_net_no(0) : -1) + ")");
      }
    }

    return result;
  }

  /**
   * Looks up all items (traces, vias, pads) connected to this trace at a specific
   * point (start or end). This is used for the push and shove algorithms to find
   * all impacted items when moving this trace.
   */
  Set<Item> get_impacted_items_at(Point p_point) {
    Set<Item> result = new TreeSet<>();

    // Use tolerance for connectivity detection: max(half_width + 1, 3000) to ensure
    // bidirectional visibility
    int tolerance = Math.max(this.half_width + 1, 3000);
    TileShape search_shape = p_point.surrounding_octagon().enlarge(tolerance);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(search_shape, this.layer);
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.get_impacted_items_at for net #94 at " + p_point + " on layer " + this.layer + ": found "
          + overlaps.size() + " overlaps");
    }
    for (SearchTreeObject curr_ob : overlaps) {
      if (!(curr_ob instanceof Item curr_item)) {
        continue;
      }
      if (this.contains_net(94)) {
        FRLogger.debug("  Checking item id=" + curr_item.get_id_no() + " (net #"
            + (curr_item.net_count() > 0 ? curr_item.get_net_no(0) : -1) + ") on layer " + curr_item.shape_layer(0));
      }
      if (curr_item != this && curr_item.shares_layer(this) && curr_item.shares_net(this)) {
        if (curr_item instanceof Trace curr_trace) {
          // Check if points are within tolerance distance
          double d1 = p_point.to_float().distance(curr_trace.first_corner().to_float());
          double d2 = p_point.to_float().distance(curr_trace.last_corner().to_float());
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against trace id=" + curr_trace.get_id_no() + ": d1=" + d1 + ", d2=" + d2
                + ", tolerance=" + tolerance);
          }
          if (isWithinTolerance(p_point, curr_trace.first_corner(), tolerance) ||
              isWithinTolerance(p_point, curr_trace.last_corner(), tolerance)) {
            result.add(curr_item);
          }
        } else if (curr_item instanceof DrillItem curr_drill_item) {
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against drill id=" + curr_drill_item.get_id_no());
          }
          app.freerouting.geometry.planar.Shape drill_shape = curr_drill_item.get_shape_on_layer(this.get_layer());
          // Enlarge by trace tolerance to account for snapping/trace width and ensure
          // robustness
          if (drill_shape != null && drill_shape.enlarge(tolerance).contains(p_point)) {
            result.add(curr_item);
          }
        } else if (curr_item instanceof ConductionArea curr_area) {
          if (curr_area.get_area().contains(p_point)) {
            result.add(curr_item);
          }
        }
      }
    }

    // Log the impacted items for debugging
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.get_impacted_items_at for net #94, trace id=" + this.get_id_no() + ", point=" + p_point + ":");
      for (Item item : result) {
        FRLogger.debug("  Impacted item id=" + item.get_id_no() + " (net #"
            + (item.net_count() > 0 ? item.get_net_no(0) : -1) + ")");
      }
    }

    return result;
  }

  /**
   * Adds or updates a contact for this trace at the specified point. This is used
   * when traces are moved or reshaped, and their contacts need to be updated.
   */
  void add_or_update_contact(Point p_point) {
    if (p_point == null) {
      return;
    }
    // Use tolerance for connectivity detection: max(half_width + 1, 3000) to ensure
    // bidirectional visibility
    int tolerance = Math.max(this.half_width + 1, 3000);
    TileShape search_shape = p_point.surrounding_octagon().enlarge(tolerance);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(search_shape, this.layer);
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.add_or_update_contact for net #94 at " + p_point + " on layer " + this.layer + ": found "
          + overlaps.size() + " overlaps");
    }
    boolean contact_found = false;
    for (SearchTreeObject curr_ob : overlaps) {
      if (!(curr_ob instanceof Item curr_item)) {
        continue;
      }
      if (this.contains_net(94)) {
        FRLogger.debug("  Checking item id=" + curr_item.get_id_no() + " (net #"
            + (curr_item.net_count() > 0 ? curr_item.get_net_no(0) : -1) + ") on layer " + curr_item.shape_layer(0));
      }
      if (curr_item != this && curr_item.shares_layer(this) && curr_item.shares_net(this)) {
        if (curr_item instanceof Trace curr_trace) {
          // Check if points are within tolerance distance
          double d1 = p_point.to_float().distance(curr_trace.first_corner().to_float());
          double d2 = p_point.to_float().distance(curr_trace.last_corner().to_float());
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against trace id=" + curr_trace.get_id_no() + ": d1=" + d1 + ", d2=" + d2
                + ", tolerance=" + tolerance);
          }
          if (isWithinTolerance(p_point, curr_trace.first_corner(), tolerance) ||
              isWithinTolerance(p_point, curr_trace.last_corner(), tolerance)) {
            contact_found = true;
            FRLogger.trace("Trace.add_or_update_contact", "trace_contact",
                "Found trace contact at " + p_point + ", layer=" + this.get_layer()
                    + ", id=" + this.get_id_no(),
                formatNetLabel(this.board, curr_item.get_net_no(0)) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        } else if (curr_item instanceof DrillItem curr_drill_item) {
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against drill id=" + curr_drill_item.get_id_no());
          }
          app.freerouting.geometry.planar.Shape drill_shape = curr_drill_item.get_shape_on_layer(this.get_layer());
          // Enlarge by trace tolerance to account for snapping/trace width and ensure
          // robustness
          if (drill_shape != null && drill_shape.enlarge(tolerance).contains(p_point)) {
            contact_found = true;
            FRLogger.trace("Trace.add_or_update_contact", "drill_contact",
                "Found drill contact at " + p_point + ", layer=" + this.get_layer()
                    + ", id=" + this.get_id_no(),
                formatNetLabel(this.board, curr_item.get_net_no(0)) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        } else if (curr_item instanceof ConductionArea curr_area) {
          if (curr_area.get_area().contains(p_point)) {
            contact_found = true;
            FRLogger.trace("Trace.add_or_update_contact", "area_contact",
                "Found area contact at " + p_point + ", layer=" + this.get_layer()
                    + ", id=" + this.get_id_no(),
                formatNetLabel(this.board, curr_item.get_net_no(0)) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        }
      }
    }
    if (!contact_found) {
      int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
      FRLogger.trace("Trace.add_or_update_contact", "add_contact",
          "Adding contact at " + p_point + ", id=" + this.get_id_no(),
          formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
          new Point[] { p_point });
    }
  }

  /**
   * Removes a contact for this trace at the specified point. This is used when
   * traces are moved or reshaped, and their contacts need to be updated.
   */
  void remove_contact(Point p_point) {
    if (p_point == null) {
      return;
    }
    // Use tolerance for connectivity detection: max(half_width + 1, 3000) to ensure
    // bidirectional visibility
    int tolerance = Math.max(this.half_width + 1, 3000);
    TileShape search_shape = p_point.surrounding_octagon().enlarge(tolerance);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(search_shape, this.layer);
    if (this.contains_net(94)) {
      FRLogger.debug("Trace.remove_contact for net #94 at " + p_point + " on layer " + this.layer + ": found "
          + overlaps.size() + " overlaps");
    }
    boolean contact_found = false;
    for (SearchTreeObject curr_ob : overlaps) {
      if (!(curr_ob instanceof Item curr_item)) {
        continue;
      }
      if (this.contains_net(94)) {
        FRLogger.debug("  Checking item id=" + curr_item.get_id_no() + " (net #"
            + (curr_item.net_count() > 0 ? curr_item.get_net_no(0) : -1) + ") on layer " + curr_item.shape_layer(0));
      }
      if (curr_item != this && curr_item.shares_layer(this) && curr_item.shares_net(this)) {
        if (curr_item instanceof Trace curr_trace) {
          // Check if points are within tolerance distance
          double d1 = p_point.to_float().distance(curr_trace.first_corner().to_float());
          double d2 = p_point.to_float().distance(curr_trace.last_corner().to_float());
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against trace id=" + curr_trace.get_id_no() + ": d1=" + d1 + ", d2=" + d2
                + ", tolerance=" + tolerance);
          }
          if (isWithinTolerance(p_point, curr_trace.first_corner(), tolerance) ||
              isWithinTolerance(p_point, curr_trace.last_corner(), tolerance)) {
            contact_found = true;
            int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
            FRLogger.trace("Trace.remove_contact", "remove_trace_contact",
                "Trace contact removed at " + p_point + ", id=" + this.get_id_no()
                    + ", from=" + this.first_corner() + " to " + this.last_corner(),
                formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        } else if (curr_item instanceof DrillItem curr_drill_item) {
          if (this.contains_net(94)) {
            FRLogger.debug("    Checking against drill id=" + curr_drill_item.get_id_no());
          }
          app.freerouting.geometry.planar.Shape drill_shape = curr_drill_item.get_shape_on_layer(this.get_layer());
          // Enlarge by trace tolerance to account for snapping/trace width and ensure
          // robustness
          if (drill_shape != null && drill_shape.enlarge(tolerance).contains(p_point)) {
            contact_found = true;
            int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
            FRLogger.trace("Trace.remove_contact", "remove_trace_contact",
                "Trace contact removed at " + p_point + ", id=" + this.get_id_no()
                    + ", from=" + this.first_corner() + " to " + this.last_corner(),
                formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        } else if (curr_item instanceof ConductionArea curr_area) {
          if (curr_area.get_area().contains(p_point)) {
            contact_found = true;
            int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
            FRLogger.trace("Trace.remove_contact", "remove_trace_contact",
                "Trace contact removed at " + p_point + ", id=" + this.get_id_no()
                    + ", from=" + this.first_corner() + " to " + this.last_corner(),
                formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
                new Point[] { p_point });
            break;
          }
        }
      }
    }
    if (!contact_found) {
      int netNo = this.net_count() > 0 ? this.get_net_no(0) : -1;
      FRLogger.trace("Trace.remove_contact", "remove_trace_contact_missing",
          "Trace contact removed at " + p_point + ", but no contact found, id=" + this.get_id_no()
              + ", from=" + this.first_corner() + " to " + this.last_corner(),
          formatNetLabel(this.board, netNo) + ", Trace #" + this.get_id_no(),
          new Point[] { p_point });
    }
  }
}