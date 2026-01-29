package app.freerouting.board;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A trace object whose geometry is described by a polyline (a sequence of connected line segments).
 * <p>
 * This class represents electrical traces on a PCB board. Each trace has a width, belongs to one or more
 * electrical nets, and consists of a series of connected straight line segments forming a path.
 * <p>
 * Key operations include:
 * <ul>
 *   <li>Splitting traces at intersection points</li>
 *   <li>Combining adjacent traces that share endpoints</li>
 *   <li>Normalizing trace geometry to remove redundancies</li>
 *   <li>Optimizing trace paths (pull tight operations)</li>
 * </ul>
 */
public class PolylineTrace extends Trace implements Serializable {

  private static final int MAX_NORMALIZATION_DEPTH = 16;
  // primary data
  private Polyline lines;

  /**
   * Creates a new polyline trace with the specified parameters.
   * <p>
   * A polyline must have at least 3 lines to form a valid trace segment with at least 2 corners.
   *
   * @param p_polyline the geometric path of the trace as a sequence of connected lines
   * @param p_layer the board layer number where this trace is located
   * @param p_half_width half of the trace width (radius from centerline)
   * @param p_net_no_arr array of net numbers this trace belongs to
   * @param p_clearance_type the clearance class that defines spacing rules
   * @param p_id_no unique identifier for this trace
   * @param p_group_no component group number (0 if not part of a component)
   * @param p_fixed_state whether the trace can be moved (USER_FIXED, SHOVE_FIXED, or not fixed)
   * @param p_board reference to the board containing this trace
   */
  public PolylineTrace(Polyline p_polyline, int p_layer, int p_half_width, int[] p_net_no_arr, int p_clearance_type,
      int p_id_no, int p_group_no, FixedState p_fixed_state, BasicBoard p_board) {
    super(p_layer, p_half_width, p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
    if (p_polyline.arr.length < 3) {
      FRLogger.warn("PolylineTrace: p_polyline.arr.length >= 3 expected");
    }
    lines = p_polyline;
  }

  @Override
  public Item copy(int p_id_no) {
    int[] curr_net_no_arr = new int[this.net_count()];
    for (int i = 0; i < curr_net_no_arr.length; i++) {
      curr_net_no_arr[i] = get_net_no(i);
    }
    return new PolylineTrace(lines, get_layer(), get_half_width(), curr_net_no_arr, clearance_class_no(), p_id_no,
        get_component_no(), get_fixed_state(), board);
  }

  /**
   * checks, if this trace is on layer p_layer
   */
  @Override
  public boolean is_on_layer(int p_layer) {
    return get_layer() == p_layer;
  }

  /**
   * Returns the first corner point of this trace.
   * <p>
   * The first corner is where the first two line segments of the polyline meet.
   *
   * @return the starting corner point of the trace
   */
  @Override
  public Point first_corner() {
    return lines.corner(0);
  }

  /**
   * Returns the last corner point of this trace.
   * <p>
   * The last corner is where the last two line segments of the polyline meet.
   *
   * @return the ending corner point of the trace
   */
  @Override
  public Point last_corner() {
    return lines.corner(lines.arr.length - 2);
  }

  /**
   * Returns the number of corner points in this trace.
   * <p>
   * The corner count equals the number of lines minus one, since corners are formed
   * where consecutive lines meet.
   *
   * @return the number of corners (turning points) in the trace
   */
  public int corner_count() {
    return lines.arr.length - 1;
  }

  @Override
  public double get_length() {
    return lines.length_approx();
  }

  @Override
  public IntBox bounding_box() {
    IntBox result = this.lines.bounding_box();
    return result.offset(this.get_half_width());
  }

  @Override
  public void draw(Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null) {
      return;
    }
    int layer = this.get_layer();
    Color color = p_color_arr[layer];
    double display_width = get_half_width();
    double intensity = p_intensity * p_graphics_context.get_layer_visibility(layer);
    p_graphics_context.draw(lines.corner_approx_arr(), display_width, color, p_g, intensity);
  }

  /**
   * Returns the polyline of this trace.
   */
  public Polyline polyline() {
    return lines;
  }

  @Override
  protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculate_tree_shapes(this);
  }

  /**
   * returns the count of tile shapes of this polyline
   */
  @Override
  public int tile_shape_count() {
    return Math.max(lines.arr.length - 2, 0);
  }

  @Override
  public void translate_by(Vector p_vector) {
    lines = lines.translate_by(p_vector);
    this.clear_derived_data();
  }

  @Override
  public void turn_90_degree(int p_factor, IntPoint p_pole) {
    lines = lines.turn_90_degree(p_factor, p_pole);
    this.clear_derived_data();
  }

  @Override
  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    this.lines = this.lines.rotate_approx(Math.toRadians(p_angle_in_degree), p_pole);
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    lines = lines.mirror_vertical(p_pole);

    if (this.board != null) {
      this.set_layer(board.get_layer_count() - this.get_layer() - 1);
    }
    this.clear_derived_data();
  }

  /**
   * Attempts to combine this trace with adjacent traces that share endpoints.
   * <p>
   * This method looks at both ends of the trace and tries to merge with other traces that:
   * <ul>
   *   <li>Share the same endpoint</li>
   *   <li>Are on the same layer</li>
   *   <li>Have the same width</li>
   *   <li>Belong to the same nets</li>
   *   <li>Have the same fixed state</li>
   * </ul>
   * <p>
   * After combining, the method calls itself recursively to continue combining if possible.
   * This process helps reduce the number of separate trace objects and simplifies the board.
   * <p>
   * <b>Note:</b> This method triggers {@link #normalize(IntOctagon)} after successful combinations.
   *
   * @return true if any traces were combined, false otherwise
   */
  @Override
  public boolean combine() {
    if (!this.is_on_the_board()) {
      return false;
    }
    Point combined_at;
    if (this.combine_at_start(true)) {
      combined_at = this.first_corner();
      this.combine();
    } else if (this.combine_at_end(true)) {
      combined_at = this.last_corner();
      this.combine();
    } else {
      combined_at = null;
    }
    if (combined_at != null) {
      FRLogger.trace("PolylineTrace.combine()", "combine_traces",
          "Combined traces at " + combined_at + " into trace id=" + this.get_id_no(),
          this.toString(),
          new Point[] { combined_at });

      // let the observers synchronize the changes
      if ((board.communication != null) && (board.communication.observers != null)) {
        board.communication.observers.notify_changed(this);
      }
    }
    return (combined_at != null);
  }

  /**
   * Looks for a trace to combine at the start point of this trace.
   * <p>
   * This is an internal helper method that checks if another trace connects to the first corner
   * of this trace and can be merged with it. If found, the other trace's corners are added
   * to the beginning of this trace, and the other trace is removed from the board.
   *
   * @param p_ignore_areas if true, conduction areas are not considered as blocking the combination
   * @return true if a trace was combined at the start, false otherwise
   */
  private boolean combine_at_start(boolean p_ignore_areas) {
    Point start_corner = first_corner();
    Collection<Item> contacts = get_normal_contacts(start_corner, false);
    if (p_ignore_areas) {
      // remove conduction areas from the list
      contacts.removeIf(c -> c instanceof ConductionArea);
    }
    if (contacts.size() != 1) {
      return false;
    }
    PolylineTrace other_trace = null;
    boolean trace_found = false;
    boolean reverse_order = false;
    for (Item curr_ob : contacts) {
      if (curr_ob instanceof PolylineTrace trace) {
        other_trace = trace;
        if (other_trace.get_layer() == get_layer() && other_trace.nets_equal(this)
            && other_trace.get_half_width() == get_half_width()
            && other_trace.get_fixed_state() == this.get_fixed_state()) {
          if (start_corner.equals(other_trace.last_corner())) {
            trace_found = true;
            break;
          } else if (start_corner.equals(other_trace.first_corner())) {
            reverse_order = true;
            trace_found = true;
            break;
          }
        }
      }
    }
    if (!trace_found) {
      return false;
    }

    if (globalSettings.debugSettings.enableDetailedLogging) {
      FRLogger.trace("PolylineTrace.combine()", "combine_at_start",
          "combining traces at start: this_id=" + this.get_id_no()
              + ", other_id=" + other_trace.get_id_no()
              + ", start=" + start_corner
              + ", other_first=" + other_trace.first_corner()
              + ", other_last=" + other_trace.last_corner()
              + ", reverse_order=" + reverse_order,
          "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1),
          new Point[] { start_corner, other_trace.first_corner(), other_trace.last_corner() });
    }

    board.item_list.save_for_undo(this);
    // create the lines of the joined polyline
    Line[] this_lines = lines.arr;
    Line[] other_lines;
    if (reverse_order) {
      other_lines = new Line[other_trace.lines.arr.length];
      for (int i = 0; i < other_lines.length; i++) {
        other_lines[i] = other_trace.lines.arr[other_lines.length - 1 - i].opposite();
      }
    } else {
      other_lines = other_trace.lines.arr;
    }
    boolean skip_line = other_lines[other_lines.length - 2].is_equal_or_opposite(this_lines[1]);
    int new_line_count = this_lines.length + other_lines.length - 2;
    if (skip_line) {
      --new_line_count;
    }
    Line[] new_lines = new Line[new_line_count];
    System.arraycopy(other_lines, 0, new_lines, 0, other_lines.length - 1);
    int join_pos = other_lines.length - 1;
    if (skip_line) {
      --join_pos;
    }
    System.arraycopy(this_lines, 1, new_lines, join_pos, this_lines.length - 1);
    Polyline joined_polyline = new Polyline(new_lines);
    if (joined_polyline.arr.length != new_line_count) {
      // consecutive parallel lines where skipped at the join location
      // combine without performance optimization
      board.search_tree_manager.remove(this);
      this.lines = joined_polyline;
      this.clear_derived_data();
      board.search_tree_manager.insert(this);
    } else {
      // reuse the tree entries for better performance
      // create the changed line shape at the join location
      int to_no = other_lines.length;
      if (skip_line) {
        --to_no;
      }
      board.search_tree_manager.merge_entries_in_front(other_trace, this, joined_polyline, other_lines.length - 3,
          to_no);
      other_trace.clear_search_tree_entries();
      this.lines = joined_polyline;
    }
    if (this.lines.arr.length < 3) {
      board.remove_item(this);
    }
    board.remove_item(other_trace);
    if (board instanceof RoutingBoard routingBoard) {
      routingBoard.join_changed_area(start_corner.to_float(), get_layer());
    }
    return true;
  }

  /**
   * Looks for a trace to combine at the end point of this trace.
   * <p>
   * This is an internal helper method that checks if another trace connects to the last corner
   * of this trace and can be merged with it. If found, the other trace's corners are added
   * to the end of this trace, and the other trace is removed from the board.
   *
   * @param p_ignore_areas if true, conduction areas are not considered as blocking the combination
   * @return true if a trace was combined at the end, false otherwise
   */
  private boolean combine_at_end(boolean p_ignore_areas) {
    Point end_corner = last_corner();
    Collection<Item> contacts = get_normal_contacts(end_corner, false);
    if (p_ignore_areas) {
      // remove conduction areas from the list
      contacts.removeIf(c -> c instanceof ConductionArea);
    }
    if (contacts.size() != 1) {
      return false;
    }
    PolylineTrace other_trace = null;
    boolean trace_found = false;
    boolean reverse_order = false;
    for (Item curr_ob : contacts) {
      if (curr_ob instanceof PolylineTrace trace) {
        other_trace = trace;
        if (other_trace.get_layer() == get_layer() && other_trace.nets_equal(this)
            && other_trace.get_half_width() == get_half_width()
            && other_trace.get_fixed_state() == this.get_fixed_state()) {
          if (end_corner.equals(other_trace.first_corner())) {
            trace_found = true;
            break;
          } else if (end_corner.equals(other_trace.last_corner())) {
            reverse_order = true;
            trace_found = true;
            break;
          }
        }
      }
    }
    if (!trace_found) {
      return false;
    }

    if (globalSettings.debugSettings.enableDetailedLogging) {
      FRLogger.trace("PolylineTrace.combine()", "combine_at_end",
          "combining traces at end: this_id=" + this.get_id_no()
              + ", other_id=" + other_trace.get_id_no()
              + ", end=" + end_corner
              + ", other_first=" + other_trace.first_corner()
              + ", other_last=" + other_trace.last_corner()
              + ", reverse_order=" + reverse_order,
          "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1),
          new Point[] { end_corner, other_trace.first_corner(), other_trace.last_corner() });
    }

    board.item_list.save_for_undo(this);
    // create the lines of the joined polyline
    Line[] this_lines = lines.arr;
    Line[] other_lines;
    if (reverse_order) {
      other_lines = new Line[other_trace.lines.arr.length];
      for (int i = 0; i < other_lines.length; i++) {
        other_lines[i] = other_trace.lines.arr[other_lines.length - 1 - i].opposite();
      }
    } else {
      other_lines = other_trace.lines.arr;
    }
    boolean skip_line = this_lines[this_lines.length - 2].is_equal_or_opposite(other_lines[1]);
    int new_line_count = this_lines.length + other_lines.length - 2;
    if (skip_line) {
      --new_line_count;
    }
    Line[] new_lines = new Line[new_line_count];
    System.arraycopy(this_lines, 0, new_lines, 0, this_lines.length - 1);
    int join_pos = this_lines.length - 1;
    if (skip_line) {
      --join_pos;
    }
    System.arraycopy(other_lines, 1, new_lines, join_pos, other_lines.length - 1);
    Polyline joined_polyline = new Polyline(new_lines);
    if (joined_polyline.arr.length != new_line_count) {
      // consecutive parallel lines where skipped at the join location
      // combine without performance optimization
      board.search_tree_manager.remove(this);
      this.clear_search_tree_entries();
      this.lines = joined_polyline;
      this.clear_derived_data();
      board.search_tree_manager.insert(this);
    } else {
      // reuse tree entries for better performance
      // create the changed line shape at the join location
      int to_no = this_lines.length;
      if (skip_line) {
        --to_no;
      }
      board.search_tree_manager.merge_entries_at_end(other_trace, this, joined_polyline, this_lines.length - 3, to_no);
      other_trace.clear_search_tree_entries();
      this.lines = joined_polyline;
    }
    if (this.lines.arr.length < 3) {
      board.remove_item(this);
    }
    board.remove_item(other_trace);
    if (board instanceof RoutingBoard routingBoard) {
      routingBoard.join_changed_area(end_corner.to_float(), get_layer());
    }
    return true;
  }

  /**
   * Splits this trace and any overlapping traces, then combines adjacent segments.
   * <p>
   * This is a comprehensive cleanup operation that:
   * <ol>
   *   <li>Finds traces that intersect or overlap with this trace</li>
   *   <li>Splits both traces at intersection points</li>
   *   <li>Removes any circular connections (cycles) that were created</li>
   *   <li>Combines adjacent trace segments that can be merged</li>
   *   <li>Removes degenerate traces (traces with only one corner point)</li>
   * </ol>
   * <p>
   * The splitting process ensures that traces only touch at their endpoints, not along their length.
   * This makes it easier to route, optimize, and analyze the board.
   * <p>
   * <b>When called:</b>
   * <ul>
   *   <li>After inserting a new trace to clean up intersections</li>
   *   <li>After pulling a trace tight (optimization)</li>
   *   <li>After changing trace geometry</li>
   *   <li>After combining traces</li>
   * </ul>
   *
   * @param p_clip_shape optional shape to restrict splitting to a specific area; if null, the entire trace is processed
   * @return true if the trace was modified (split, combined, or removed), false if no changes were made
   */
  @Override
  public Collection<PolylineTrace> split(IntOctagon p_clip_shape) {
    Collection<PolylineTrace> result = new LinkedList<>();
    if (!this.nets_normal()) {
      // only normal nets are split
      result.add(this);
      return result;
    }
    boolean own_trace_split = false;
    ShapeSearchTree default_tree = board.search_tree_manager.get_default_tree();
    for (int i = 0; i < this.lines.arr.length - 2; i++) {
      if (p_clip_shape != null) {
        LineSegment curr_segment = new LineSegment(this.lines, i + 1);
        if (!p_clip_shape.intersects(curr_segment.bounding_box())) {
          continue;
        }
      }
      TileShape curr_shape = this.get_tree_shape(default_tree, i);
      LineSegment curr_line_segment = new LineSegment(this.lines, i + 1);
      Collection<ShapeSearchTree.TreeEntry> overlapping_tree_entries = new LinkedList<>();
      // look for intersecting traces with the i-th line segment
      default_tree.overlapping_tree_entries(curr_shape, get_layer(), overlapping_tree_entries);
      Iterator<ShapeSearchTree.TreeEntry> it = overlapping_tree_entries.iterator();
      while (it.hasNext()) {
        if (!this.is_on_the_board()) {
          // this trace has been deleted in a cleanup operation
          return result;
        }
        ShapeSearchTree.TreeEntry found_entry = it.next();
        if (!(found_entry.object instanceof Item found_item)) {
          continue;
        }
        if (found_item == this) {

          if (found_entry.shape_index_in_object >= i - 1 && found_entry.shape_index_in_object <= i + 1) {
            // don't split own trace at this line or at neighbour lines
            continue;
          }
          // try to handle intermediate segments of length 0 by comparing end corners
          if (i < found_entry.shape_index_in_object) {
            if (lines
                .corner(i + 1)
                .equals(lines.corner(found_entry.shape_index_in_object))) {
              continue;
            }
          } else {
            if (lines
                .corner(found_entry.shape_index_in_object + 1)
                .equals(lines.corner(i))) {
              continue;
            }
          }
        }
        if (!found_item.shares_net(this)) {
          continue;
        }
        if (found_item instanceof PolylineTrace found_trace) {
          LineSegment found_line_segment = new LineSegment(found_trace.lines, found_entry.shape_index_in_object + 1);
          Line[] intersecting_lines = found_line_segment.intersection(curr_line_segment);

          // Skip if trace overlaps with itself
          if (found_trace == this) {
            if (globalSettings.debugSettings.enableDetailedLogging && intersecting_lines.length > 0) {
              FRLogger.trace("PolylineTrace.split", "split_overlap",
                  "skipping self-overlap: trace id=" + this.get_id_no()
                      + ", line_index=" + (found_entry.shape_index_in_object + 1)
                      + ", intersections=" + intersecting_lines.length,
                  "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1),
                  new Point[] { curr_line_segment.start_point(), curr_line_segment.end_point() });
            }
            continue;
          }

          // Skip if there are no intersections
          if (intersecting_lines.length == 0) {
            if (globalSettings.debugSettings.enableDetailedLogging) {
              FRLogger.trace("PolylineTrace.split", "split_overlap",
                  "skipping non-intersecting overlap with trace id=" + found_trace.get_id_no()
                      + ", this_id=" + this.get_id_no()
                      + ", line_index=" + (found_entry.shape_index_in_object + 1),
                  "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1),
                  new Point[] { curr_line_segment.start_point(), curr_line_segment.end_point() });
            }
            continue;
          }

          Collection<PolylineTrace> split_pieces = new LinkedList<>();

          if (globalSettings.debugSettings.enableDetailedLogging) {
            FRLogger.trace("PolylineTrace.split", "split_overlap",
                "overlap with trace id=" + found_trace.get_id_no()
                    + ", this_id=" + this.get_id_no()
                    + ", line_index=" + (found_entry.shape_index_in_object + 1)
                    + ", intersections=" + intersecting_lines.length,
                "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1),
                new Point[] { curr_line_segment.start_point(), curr_line_segment.end_point() });
          }

          // try splitting the found trace first
          boolean found_trace_split = false;

          if (found_trace != this) {
            for (int j = 0; j < intersecting_lines.length; j++) {
              int line_no = found_entry.shape_index_in_object + 1;
              PolylineTrace[] curr_split_pieces = found_trace.split(line_no, intersecting_lines[j]);
              if (curr_split_pieces != null) {

                for (int k = 0; k < 2; k++) {
                  if (curr_split_pieces[k] != null) {
                    found_trace_split = true;
                    split_pieces.add(curr_split_pieces[k]);
                    if (globalSettings.debugSettings.enableDetailedLogging) {
                      FRLogger.trace("PolylineTrace.split", "split_piece",
                          "found trace split piece id=" + curr_split_pieces[k].get_id_no()
                              + ", from=" + curr_split_pieces[k].first_corner()
                              + ", to=" + curr_split_pieces[k].last_corner(),
                          "Net #" + (curr_split_pieces[k].net_count() > 0 ? curr_split_pieces[k].get_net_no(0) : -1),
                          new Point[] { curr_split_pieces[k].first_corner(), curr_split_pieces[k].last_corner() });
                    }
                  }
                }
                if (found_trace_split) {
                  // reread the overlapping tree entries and reset the iterator,
                  // because the board has changed
                  default_tree.overlapping_tree_entries(curr_shape, get_layer(), overlapping_tree_entries);
                  it = overlapping_tree_entries.iterator();
                  break;
                }
              }
            }
            if (!found_trace_split) {
              split_pieces.add(found_trace);
            }
          }
          // now try splitting the own trace

          intersecting_lines = curr_line_segment.intersection(found_line_segment);
          for (int j = 0; j < intersecting_lines.length; j++) {
            PolylineTrace[] curr_split_pieces = split(i + 1, intersecting_lines[j]);
            if (curr_split_pieces != null) {
              own_trace_split = true;
              if (globalSettings.debugSettings.enableDetailedLogging) {
                for (int k = 0; k < 2; k++) {
                  if (curr_split_pieces[k] != null) {
                    FRLogger.trace("PolylineTrace.split", "split_piece",
                        "own trace split piece id=" + curr_split_pieces[k].get_id_no()
                            + ", from=" + curr_split_pieces[k].first_corner()
                            + ", to=" + curr_split_pieces[k].last_corner(),
                        "Net #" + (curr_split_pieces[k].net_count() > 0 ? curr_split_pieces[k].get_net_no(0) : -1),
                        new Point[] { curr_split_pieces[k].first_corner(), curr_split_pieces[k].last_corner() });
                  }
                }
              }
              // this trace was split itself into 2.
              if (curr_split_pieces[0] != null) {
                result.addAll(curr_split_pieces[0].split(p_clip_shape));
              }
              if (curr_split_pieces[1] != null) {
                result.addAll(curr_split_pieces[1].split(p_clip_shape));
              }
              break;
            }
          }
          if (found_trace_split || own_trace_split) {
            // something was split,
            // remove cycles containing a split piece
            Iterator<PolylineTrace> it2 = split_pieces.iterator();
            for (int j = 0; j < 2; j++) {
              while (it2.hasNext()) {
                PolylineTrace curr_piece = it2.next();
                if (globalSettings.debugSettings.enableDetailedLogging) {
                  FRLogger.trace("PolylineTrace.split", "remove_cycle_candidate",
                      "remove_if_cycle on trace id=" + curr_piece.get_id_no()
                          + ", from=" + curr_piece.first_corner()
                          + ", to=" + curr_piece.last_corner(),
                      "Net #" + (curr_piece.net_count() > 0 ? curr_piece.get_net_no(0) : -1),
                      new Point[] { curr_piece.first_corner(), curr_piece.last_corner() });
                }
                board.remove_if_cycle(curr_piece);
              }

              // remove cycles in the own split pieces last
              // to preserve them, if possible
              it2 = result.iterator();
            }
          }
          if (own_trace_split) {
            break;
          }
        } else if (found_item instanceof DrillItem curr_drill_item) {
          Point split_point = curr_drill_item.get_center();
          if (curr_line_segment.contains(split_point)) {
            Direction split_line_direction = curr_line_segment
                .get_line()
                .direction()
                .turn_45_degree(2);
            Line split_line = new Line(split_point, split_line_direction);
            split(i + 1, split_line);
          }
        } else if (!this.is_user_fixed() && (found_item instanceof ConductionArea)) {
          boolean ignore_areas = false;
          if (this.net_no_arr.length > 0) {
            Net curr_net = this.board.rules.nets.get(this.net_no_arr[0]);
            if (curr_net != null && curr_net.get_class() != null) {
              ignore_areas = curr_net
                  .get_class()
                  .get_ignore_cycles_with_areas();
            }
          }
          if (!ignore_areas && this
              .get_start_contacts()
              .contains(found_item)
              && this
                  .get_end_contacts()
                  .contains(found_item)) {
            // this trace can be removed because of cycle with conduction area
            board.remove_item(this);
            return result;
          }
        }
      }
      if (own_trace_split) {
        break;
      }
    }
    if (!own_trace_split) {
      result.add(this);
    }
    if (result.size() > 1) {
      for (Item curr_item : result) {
        board.additional_update_after_change(curr_item);
      }
    }
    return result;
  }

  /**
   * Checks if splitting at a specific line intersection would cut through a drill pad.
   * <p>
   * Splitting inside a pin's pad area is generally prohibited because it can create
   * connection problems. However, splitting is allowed at the exact center of a pin.
   *
   * @param p_line_no the index of the line in this trace's polyline
   * @param p_line the line to check for intersection
   * @return true if splitting at this intersection is prohibited, false if it's allowed
   */
  private boolean split_inside_drill_pad_prohibited(int p_line_no, Line p_line) {
    if (this.board == null) {
      return false;
    }
    Point intersection = this.lines.arr[p_line_no].intersection(p_line);
    Collection<Item> overlap_items = this.board.pick_items(intersection, this.get_layer(), null);
    boolean pad_found = false;
    for (Item curr_item : overlap_items) {
      if (!curr_item.shares_net(this)) {
        continue;
      }
      if (curr_item instanceof Pin curr_drill_item) {
        if (curr_drill_item
            .get_center()
            .equals(intersection)) {
          return false; // split always at the center of a drill item.
        }
        pad_found = true;
      } else if (curr_item instanceof Trace curr_trace) {
        if (curr_trace != this && curr_trace
            .first_corner()
            .equals(intersection) || curr_trace
                .last_corner()
                .equals(intersection)) {
          return false;
        }
      }
    }
    return pad_found;
  }

  /**
   * Splits this trace into two pieces at a specific point.
   * <p>
   * The point must lie on one of the line segments of this trace. The trace is divided
   * into two new traces that meet at the split point.
   *
   * @param p_point the point where the trace should be split
   * @return an array containing the two resulting trace pieces, or null if the point doesn't lie on the trace
   */
  @Override
  public Trace[] split(Point p_point) {
    for (int i = 0; i < this.lines.arr.length - 2; i++) {
      LineSegment curr_line_segment = new LineSegment(this.lines, i + 1);
      if (curr_line_segment.contains(p_point)) {
        Direction split_line_direction = curr_line_segment
            .get_line()
            .direction()
            .turn_45_degree(2);
        Line split_line = new Line(p_point, split_line_direction);
        Trace[] result = split(i + 1, split_line);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Splits this trace at a specific line by inserting a new endpoint line.
   * <p>
   * This is an internal helper method that performs the actual splitting operation.
   * It creates two new traces: one ending at the split line, and one starting at the split line.
   *
   * @param p_line_no the index of the line where the split should occur
   * @param p_new_end_line the line that will become the endpoint of the first piece and as
   * the start line of the second split piece. Returns the 2 pieces of the split trace,
   * or null, if nothing was split.
   */
  private PolylineTrace[] split(int p_line_no, Line p_new_end_line) {
    if (!this.is_on_the_board()) {
      return null;
    }
    Polyline[] split_polylines = lines.split(p_line_no, p_new_end_line);
    if (split_polylines == null) {
      return null;
    }
    if (split_polylines.length != 2) {
      FRLogger.warn("PolylineTrace.split: array of length 2 expected for split_polylines");
      return null;
    }
    if (split_inside_drill_pad_prohibited(p_line_no, p_new_end_line)) {
      return null;
    }
    board.remove_item(this);
    PolylineTrace[] result = new PolylineTrace[2];
    result[0] = board.insert_trace_without_cleaning(split_polylines[0], get_layer(), get_half_width(), net_no_arr,
        clearance_class_no(), get_fixed_state());
    result[1] = board.insert_trace_without_cleaning(split_polylines[1], get_layer(), get_half_width(), net_no_arr,
        clearance_class_no(), get_fixed_state());
    return result;
  }

  /**
   * Normalizes this trace by splitting overlaps and combining adjacent segments.
   * <p>
   * This is the main entry point for trace normalization. It performs a complete cleanup
   * of the trace geometry to ensure optimal routing. The normalization process:
   * <ol>
   *   <li>Removes tail traces (traces with only one connection)</li>
   *   <li>Splits this trace at intersections with other traces of the same net</li>
   *   <li>Combines adjacent trace segments that can be merged</li>
   *   <li>Removes degenerate traces (single-point traces)</li>
   *   <li>Recursively normalizes any resulting traces</li>
   * </ol>
   * <p>
   * <b>Why normalization is important:</b><br>
   * During routing and optimization, traces can overlap, create redundant segments, or form
   * unnecessary branches. Normalization cleans up these issues to maintain a clean, efficient
   * routing pattern. It ensures that traces only connect at endpoints and that there are no
   * unnecessary segments.
   * <p>
   * <b>When this method is called:</b>
   * <ul>
   *   <li>After {@link #change(Polyline)} - when trace geometry is modified</li>
   *   <li>After {@link #combine()} - when traces are merged together</li>
   *   <li>During {@link #split(IntOctagon)} - after splitting traces at intersections</li>
   *   <li>After {@link #pull_tight(PullTightAlgo)} - when traces are optimized</li>
   * </ul>
   * <p>
   * <b>Recursion depth:</b><br>
   * The method includes protection against infinite recursion by limiting the depth to
   * {@link #MAX_NORMALIZATION_DEPTH}. This prevents stack overflow in pathological cases
   * where traces form complex overlapping patterns.
   *
   * @param p_clip_shape optional shape to restrict normalization to a specific area; null means process entire trace
   * @return true if the trace was modified during normalization, false if no changes were needed
   * @throws Exception if maximum normalization depth is exceeded, indicating a potential infinite loop
   */
  public boolean normalize(IntOctagon p_clip_shape) throws Exception {
    return normalize(p_clip_shape, 0, true);
  }

  /**
   * Normalizes this trace without removing tails.
   * Used during incremental routing to prevent premature removal of traces that don't have
   * all connections yet.
   */
  public boolean normalize_without_tail_removal(IntOctagon p_clip_shape) throws Exception {
    return normalize(p_clip_shape, 0, false);
  }

  /**
   * Normalizes this trace, optionally removing tails.
   */
  private boolean normalize(IntOctagon p_clip_shape, int normalization_depth, boolean remove_tails) throws Exception {
    if (normalization_depth > MAX_NORMALIZATION_DEPTH) {
      throw new Exception("Max normalization depth reached with trace '" + this.get_id_no() + "'");
    }

    // Early exit if trace is very simple (only 2 points)
    if (this.corner_count() <= 2) {
      return false;
    }

    if (remove_tails && this.is_tail()) {
      FRLogger.trace("PolylineTrace.normalize", "remove_tail",
          "removing tail trace id=" + this.get_id_no() + " (net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1)
              + ")",
          "Net #" + (this.net_count() > 0 ? this.get_net_no(0) : -1) + ", Trace #" + this.get_id_no(),
          new Point[] { this.first_corner(), this.last_corner() });
      this.board.remove_item(this);
      return true;
    }

    boolean observers_activated = false;
    BasicBoard routing_board = this.board;
    if (this.board != null) {
      // Let the observers know the trace changes.
      observers_activated = !routing_board.observers_active();
      if (observers_activated) {
        routing_board.start_notify_observers();
      }
    }
    Collection<PolylineTrace> split_pieces = this.split(p_clip_shape);
    boolean result = split_pieces.size() != 1;
    for (PolylineTrace curr_split_trace : split_pieces) {
      if (curr_split_trace.is_on_the_board()) {
        // Save state before combine to detect if anything actually changed
        int corner_count_before = curr_split_trace.corner_count();
        Point first_corner_before = curr_split_trace.first_corner();
        Point last_corner_before = curr_split_trace.last_corner();

        boolean trace_combined = curr_split_trace.combine();

        if (curr_split_trace.corner_count() == 2 && curr_split_trace
            .first_corner()
            .equals(curr_split_trace.last_corner())) {
          // remove trace with only 1 corner
          FRLogger.trace("PolylineTrace.normalize", "remove_one_corner",
              "removing one-corner trace id=" + curr_split_trace.get_id_no() + " (net #" + (curr_split_trace.net_count() > 0 ? curr_split_trace.get_net_no(0) : -1)
                  + ")",
              "Net #" + (curr_split_trace.net_count() > 0 ? curr_split_trace.get_net_no(0) : -1) + ", Trace #" + curr_split_trace.get_id_no(),
              new Point[] { curr_split_trace.first_corner() });
          board.remove_item(curr_split_trace);
          result = true;
        } else if (trace_combined) {
          // Check if the trace actually changed after combine
          // If it's the same (same corners), don't recurse - prevents endless loops
          boolean actually_changed = (curr_split_trace.corner_count() != corner_count_before)
              || !curr_split_trace.first_corner().equals(first_corner_before)
              || !curr_split_trace.last_corner().equals(last_corner_before);

          if (actually_changed) {
            curr_split_trace.normalize(p_clip_shape, normalization_depth + 1, remove_tails);
          } else {
            FRLogger.trace("PolylineTrace.normalize", "skip_recursive_normalize",
                "Skipping recursive normalize because trace didn't actually change after combine()"
                    + " (corners=" + curr_split_trace.corner_count()
                    + ", from " + curr_split_trace.first_corner() + " to " + curr_split_trace.last_corner() + ")",
                "Net #" + (curr_split_trace.net_count() > 0 ? curr_split_trace.get_net_no(0) : -1) + ", Trace #" + curr_split_trace.get_id_no(),
                new Point[] { curr_split_trace.first_corner(), curr_split_trace.last_corner() });
          }
          result = true;
        }
      }
    }
    if (observers_activated) {
      routing_board.end_notify_observers();
    }
    return result;
  }

  /**
   * Attempts to shorten this trace without creating clearance violations.
   * <p>
   * This optimization operation tries to find a shorter path for the trace while maintaining
   * all design rules. It can remove unnecessary corners and straighten the trace path.
   * The operation is constrained by:
   * <ul>
   *   <li>Clearance rules with other objects</li>
   *   <li>Net class settings (some net classes disable pull tight)</li>
   *   <li>Fixed state (fixed traces won't be modified)</li>
   *   <li>Angle restrictions (45-degree, 90-degree routing rules)</li>
   * </ul>
   * <p>
   * <b>Note:</b> After pull tight operations, {@link #normalize(IntOctagon)} is typically called
   * to clean up any resulting trace intersections or combinations.
   *
   * @param p_pull_tight_algo the algorithm instance that performs the optimization
   * @return true if the trace was modified, false otherwise
   */
  @Override
  public boolean pull_tight(PullTightAlgo p_pull_tight_algo) {
    if (!this.is_on_the_board()) {
      // This trace may have been deleted in a trace split for example
      return false;
    }
    if (this.is_shove_fixed()) {
      return false;
    }
    if (!this.nets_normal()) {
      return false;
    }
    if (p_pull_tight_algo.only_net_no_arr.length > 0 && !this.nets_equal(p_pull_tight_algo.only_net_no_arr)) {
      return false;
    }
    if (this.net_no_arr.length > 0) {
      if (!this.board.rules.nets
          .get(this.net_no_arr[0])
          .get_class()
          .get_pull_tight()) {
        return false;
      }
    }
    Polyline new_lines = p_pull_tight_algo.pull_tight(lines, get_layer(), get_half_width(), net_no_arr,
        clearance_class_no(), this.touching_pins_at_end_corners());
    if (new_lines != lines) {
      change(new_lines);
      return true;
    }
    AngleRestriction angle_restriction = this.board.rules.get_trace_angle_restriction();
    if (angle_restriction != AngleRestriction.NINETY_DEGREE && this.board.rules.get_pin_edge_to_turn_dist() > 0) {
      if (this.swap_connection_to_pin(true)) {
        pull_tight(p_pull_tight_algo);
        return true;
      }
      if (this.swap_connection_to_pin(false)) {
        pull_tight(p_pull_tight_algo);
        return true;
      }
      // optimize algorithm could not improve the trace, try to remove acid traps
      if (this.correct_connection_to_pin(true, angle_restriction)) {
        pull_tight(p_pull_tight_algo);
        return true;
      }
      if (this.correct_connection_to_pin(false, angle_restriction)) {
        pull_tight(p_pull_tight_algo);
        return true;
      }
    }
    return false;
  }

  /**
   * Attempts to optimize this trace using the pull-tight algorithm.
   * <p>
   * This is a convenience method that creates a pull-tight algorithm instance with the
   * specified parameters and applies it to this trace.
   *
   * @param p_own_net_only if true, only considers objects on the same net for clearance
   * @param p_pull_tight_accuracy the accuracy level for the optimization (higher = more thorough)
   * @param p_stoppable_thread thread control object to allow cancellation of long operations
   * @return true if the trace was modified, false otherwise
   */
  public boolean pull_tight(boolean p_own_net_only, int p_pull_tight_accuracy, Stoppable p_stoppable_thread) {
    if (!(this.board instanceof RoutingBoard)) {
      return false;
    }
    int[] opt_net_no_arr;
    if (p_own_net_only) {
      opt_net_no_arr = this.net_no_arr;
    } else {
      opt_net_no_arr = new int[0];
    }
    PullTightAlgo pull_tight_algo = PullTightAlgo.get_instance((RoutingBoard) this.board, opt_net_no_arr, null,
        p_pull_tight_accuracy, p_stoppable_thread, -1, null, -1);
    return pull_tight(pull_tight_algo);
  }

  /**
   * Attempts to smooth the corner points at the ends of this trace where it forks with other traces.
   * <p>
   * This operation tries to create more gradual angles at fork points to improve signal quality
   * and make the routing look more professional.
   *
   * @param p_own_net_only if true, only considers objects on the same net
   * @param p_pull_tight_accuracy the accuracy level for the smoothing operation
   * @param p_stoppable_thread thread control object to allow cancellation
   * @return true if any smoothing was performed, false otherwise
   */
  public boolean smoothen_end_corners_fork(boolean p_own_net_only, int p_pull_tight_accuracy,
      Stoppable p_stoppable_thread) {
    if (!(this.board instanceof RoutingBoard)) {
      return false;
    }
    int[] opt_net_no_arr;
    if (p_own_net_only) {
      opt_net_no_arr = this.net_no_arr;
    } else {
      opt_net_no_arr = new int[0];
    }
    PullTightAlgo pull_tight_algo = PullTightAlgo.get_instance((RoutingBoard) this.board, opt_net_no_arr, null,
        p_pull_tight_accuracy, p_stoppable_thread, -1, null, -1);
    return pull_tight_algo.smoothen_end_corners_at_trace(this);
  }

  @Override
  public TileShape get_trace_connection_shape(ShapeSearchTree p_search_tree, int p_index) {
    if (p_index < 0 || p_index >= this.tile_shape_count()) {
      FRLogger.warn("PolylineTrace.get_trace_connection_shape p_index out of range");
      return null;
    }
    LineSegment curr_line_segment = new LineSegment(this.lines, p_index + 1);
    return curr_line_segment
        .to_simplex()
        .simplify();
  }

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }

  /**
   * changes the geometry of this trace to p_new_polyline
   */
  void change(Polyline p_new_polyline) {
    if (!this.is_on_the_board()) {
      // Just change the polyline of this trace.
      lines = p_new_polyline;
      return;
    }

    board.additional_update_after_change(this);

    // The precalculated tile shapes must not be cleared here because they are used
    // and
    // modified
    // in ShapeSearchTree.change_entries.

    board.item_list.save_for_undo(this);

    // for performance reasons there is some effort to reuse
    // ShapeTree entries of the old trace in the changed trace

    // look for the first line in p_new_polyline different from
    // the lines of the existing trace
    int last_index = Math.min(p_new_polyline.arr.length, lines.arr.length);
    int index_of_first_different_line = last_index;
    for (int i = 0; i < last_index; i++) {
      if (p_new_polyline.arr[i] != lines.arr[i]) {
        index_of_first_different_line = i;
        break;
      }
    }
    if (index_of_first_different_line == last_index) {
      return; // both polylines are equal, no change necessary
    }
    // look for the last line in p_new_polyline different from
    // the lines of the existing trace
    int index_of_last_different_line = -1;
    for (int i = 1; i <= last_index; i++) {
      if (p_new_polyline.arr[p_new_polyline.arr.length - i] != lines.arr[lines.arr.length - i]) {
        index_of_last_different_line = p_new_polyline.arr.length - i;
        break;
      }
    }
    if (index_of_last_different_line < 0) {
      return; // both polylines are equal, no change necessary
    }
    int keep_at_start_count = Math.max(index_of_first_different_line - 2, 0);
    int keep_at_end_count = Math.max(p_new_polyline.arr.length - index_of_last_different_line - 3, 0);
    board.search_tree_manager.change_entries(this, p_new_polyline, keep_at_start_count, keep_at_end_count);
    lines = p_new_polyline;

    // let the observers synchronize the changes
    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notify_changed(this);
    }

    IntOctagon clip_shape = null;
    if (board instanceof RoutingBoard routingBoard) {
      ChangedArea changed_area = routingBoard.changed_area;
      if (changed_area != null) {
        clip_shape = changed_area.get_area(this.get_layer());
      }
    }

    // NOTE: Skip tail removal during intermediate normalize() from change()
    // Tail removal will happen later during the final normalize_traces() call
    // This prevents premature removal of traces during incremental routing
    try {
      this.normalize_without_tail_removal(clip_shape);
    } catch (Exception e) {
      FRLogger.error("Couldn't change the trace, because its normalization failed.", e);
    }
  }

  /**
   * Verifies that this trace connects properly to a pin according to the pin's connection restrictions.
   * <p>
   * Some pins have restrictions on how traces can connect (e.g., traces must exit in specific
   * directions or maintain minimum distances before turning). This method checks if those
   * restrictions are satisfied.
   *
   * @param p_at_start if true, checks the start of this trace; if false, checks the end
   * @return true if the connection is valid or no pin is present; false if a pin is present
   *         and the connection violates its restrictions
   */
  @Override
  public boolean check_connection_to_pin(boolean p_at_start) {
    if (this.board == null) {
      return true;
    }
    if (this.corner_count() < 2) {
      return true;
    }
    Collection<Item> contact_list;
    if (p_at_start) {
      contact_list = this.get_start_contacts();
    } else {
      contact_list = this.get_end_contacts();
    }
    Pin contact_pin = null;
    for (Item curr_contact : contact_list) {
      if (curr_contact instanceof Pin pin) {
        contact_pin = pin;
        break;
      }
    }
    if (contact_pin == null) {
      return true;
    }
    Collection<Pin.TraceExitRestriction> trace_exit_restrictions = contact_pin
        .get_trace_exit_restrictions(this.get_layer());
    if (trace_exit_restrictions.isEmpty()) {
      return true;
    }
    Point end_corner;
    Point prev_end_corner;
    if (p_at_start) {
      end_corner = this.first_corner();
      prev_end_corner = this.lines.corner(1);
    } else {
      end_corner = this.last_corner();
      prev_end_corner = this.lines.corner(this.lines.corner_count() - 2);
    }
    Direction trace_end_direction = Direction.get_instance(end_corner, prev_end_corner);
    if (trace_end_direction == null) {
      return true;
    }
    Pin.TraceExitRestriction matching_exit_restriction = null;
    for (Pin.TraceExitRestriction curr_exit_restriction : trace_exit_restrictions) {
      if (curr_exit_restriction.direction.equals(trace_end_direction)) {
        matching_exit_restriction = curr_exit_restriction;
        break;
      }
    }
    if (matching_exit_restriction == null) {
      return false;
    }
    final double edge_to_turn_dist = this.board.rules.get_pin_edge_to_turn_dist();
    if (edge_to_turn_dist < 0) {
      return false;
    }
    double end_line_length = end_corner
        .to_float()
        .distance(prev_end_corner.to_float());
    double curr_clearance = board.clearance_value(this.clearance_class_no(), contact_pin.clearance_class_no(),
        this.get_layer());
    double add_width = Math.max(edge_to_turn_dist, curr_clearance + 1);
    double preserve_length = matching_exit_restriction.min_length + this.get_half_width() + add_width;
    return !(preserve_length > end_line_length);
  }

  /**
   * Attempts to fix an invalid connection to a pin by rerouting the trace.
   * <p>
   * If {@link #check_connection_to_pin(boolean)} returns false, this method can be called
   * to automatically correct the connection by adding segments that satisfy the pin's
   * connection restrictions. It may add a fixed stub trace at the pin exit point.
   *
   * @param p_at_start if true, corrects the start of this trace; if false, corrects the end
   * @param p_angle_restriction the routing angle restrictions (45-degree, 90-degree, etc.)
   * @return true if the connection was corrected, false if no correction was possible or needed
   */
  public boolean correct_connection_to_pin(boolean p_at_start, AngleRestriction p_angle_restriction) {
    if (this.check_connection_to_pin(p_at_start)) {
      return false;
    }

    Polyline trace_polyline;
    Collection<Item> contact_list;
    if (p_at_start) {
      trace_polyline = this.polyline();
      contact_list = this.get_start_contacts();
    } else {
      trace_polyline = this
          .polyline()
          .reverse();
      contact_list = this.get_end_contacts();
    }
    Pin contact_pin = null;
    for (Item curr_contact : contact_list) {
      if (curr_contact instanceof Pin pin) {
        contact_pin = pin;
        break;
      }
    }
    if (contact_pin == null) {
      return false;
    }
    Collection<Pin.TraceExitRestriction> trace_exit_restrictions = contact_pin
        .get_trace_exit_restrictions(this.get_layer());
    if (trace_exit_restrictions.isEmpty()) {
      return false;
    }
    Shape pin_shape = contact_pin.get_shape(this.get_layer() - contact_pin.first_layer());
    if (!(pin_shape instanceof TileShape)) {
      return false;
    }
    Point pin_center = contact_pin.get_center();

    final double edge_to_turn_dist = this.board.rules.get_pin_edge_to_turn_dist();
    if (edge_to_turn_dist < 0) {
      return false;
    }
    double curr_clearance = board.clearance_value(this.clearance_class_no(), contact_pin.clearance_class_no(),
        this.get_layer());
    double add_width = Math.max(edge_to_turn_dist, curr_clearance + 1);
    TileShape offset_pin_shape = (TileShape) ((TileShape) pin_shape).offset(this.get_half_width() + add_width);
    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE || offset_pin_shape.is_IntBox()) {
      offset_pin_shape = offset_pin_shape.bounding_box();
    } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      offset_pin_shape = offset_pin_shape.bounding_octagon();
    }
    int[][] entries = offset_pin_shape.entrance_points(trace_polyline);
    if (entries.length == 0) {
      return false;
    }
    int[] latest_entry_tuple = entries[entries.length - 1];
    FloatPoint trace_entry_location_approx = trace_polyline.arr[latest_entry_tuple[0]]
        .intersection_approx(offset_pin_shape.border_line(latest_entry_tuple[1]));
    // calculate the nearest legal pin exit point to trace_entry_location_approx
    double min_exit_corner_distance = Double.MAX_VALUE;
    Line nearest_pin_exit_ray = null;
    int nearest_border_line_no = -1;
    Direction pin_exit_direction = null;
    FloatPoint nearest_exit_corner = null;
    final double TOLERANCE = 1;
    for (Pin.TraceExitRestriction curr_exit_restriction : trace_exit_restrictions) {
      int curr_intersecting_border_line_no = offset_pin_shape.intersecting_border_line_no(pin_center,
          curr_exit_restriction.direction);
      Line curr_pin_exit_ray = new Line(pin_center, curr_exit_restriction.direction);
      FloatPoint curr_exit_corner = curr_pin_exit_ray
          .intersection_approx(offset_pin_shape.border_line(curr_intersecting_border_line_no));
      double curr_exit_corner_distance = curr_exit_corner.distance_square(trace_entry_location_approx);
      boolean new_nearest_corner_found = false;
      if (curr_exit_corner_distance + TOLERANCE < min_exit_corner_distance) {
        new_nearest_corner_found = true;
      } else if (curr_exit_corner_distance < min_exit_corner_distance + TOLERANCE) {
        // the distances are near equal, compare to the previous corners of
        // p_trace_polyline
        for (int i = 1; i < trace_polyline.corner_count(); i++) {
          FloatPoint curr_trace_corner = trace_polyline.corner_approx(i);
          double curr_trace_corner_distance = curr_trace_corner.distance_square(curr_exit_corner);
          double old_trace_corner_distance = curr_trace_corner.distance_square(nearest_exit_corner);
          if (curr_trace_corner_distance + TOLERANCE < old_trace_corner_distance) {
            new_nearest_corner_found = true;
            break;
          } else if (curr_trace_corner_distance > old_trace_corner_distance + TOLERANCE) {
            break;
          }
        }
      }
      if (new_nearest_corner_found) {
        min_exit_corner_distance = curr_exit_corner_distance;
        nearest_pin_exit_ray = curr_pin_exit_ray;
        nearest_border_line_no = curr_intersecting_border_line_no;
        pin_exit_direction = curr_exit_restriction.direction;
        nearest_exit_corner = curr_exit_corner;
      }
    }

    // append the polygon piece around the border of the pin shape.

    Line[] curr_lines;

    int corner_count = offset_pin_shape.border_line_count();
    int clock_wise_side_diff = (nearest_border_line_no - latest_entry_tuple[1] + corner_count) % corner_count;
    int counter_clock_wise_side_diff = (latest_entry_tuple[1] - nearest_border_line_no + corner_count) % corner_count;
    int curr_border_line_no = nearest_border_line_no;
    if (counter_clock_wise_side_diff <= clock_wise_side_diff) {
      curr_lines = new Line[counter_clock_wise_side_diff + 3];
      for (int i = 0; i <= counter_clock_wise_side_diff; i++) {
        curr_lines[i + 1] = offset_pin_shape.border_line(curr_border_line_no);
        curr_border_line_no = (curr_border_line_no + 1) % corner_count;
      }
    } else {
      curr_lines = new Line[clock_wise_side_diff + 3];
      for (int i = 0; i <= clock_wise_side_diff; i++) {
        curr_lines[i + 1] = offset_pin_shape.border_line(curr_border_line_no);
        curr_border_line_no = (curr_border_line_no - 1 + corner_count) % corner_count;
      }
    }
    curr_lines[0] = nearest_pin_exit_ray;
    curr_lines[curr_lines.length - 1] = trace_polyline.arr[latest_entry_tuple[0]];

    Polyline border_polyline = new Polyline(curr_lines);
    if (!this.board.check_polyline_trace(border_polyline, this.get_layer(), this.get_half_width(), this.net_no_arr,
        this.clearance_class_no())) {
      return false;
    }

    Line[] cut_lines = new Line[trace_polyline.arr.length - latest_entry_tuple[0] + 1];
    cut_lines[0] = curr_lines[curr_lines.length - 2];
    System.arraycopy(trace_polyline.arr, latest_entry_tuple[0], cut_lines, 1, cut_lines.length - 1);
    Polyline cut_polyline = new Polyline(cut_lines);
    Polyline changed_polyline;
    if (cut_polyline
        .first_corner()
        .equals(cut_polyline.last_corner())) {
      changed_polyline = border_polyline;
    } else {
      changed_polyline = border_polyline.combine(cut_polyline);
    }
    if (!p_at_start) {
      changed_polyline = changed_polyline.reverse();
    }
    this.change(changed_polyline);

    // create a shove_fixed exit line.
    curr_lines = new Line[3];
    curr_lines[0] = new Line(pin_center, pin_exit_direction.turn_45_degree(2));
    curr_lines[1] = nearest_pin_exit_ray;
    curr_lines[2] = offset_pin_shape.border_line(nearest_border_line_no);
    Polyline exit_line_segment = new Polyline(curr_lines);
    this.board.insert_trace(exit_line_segment, this.get_layer(), this.get_half_width(), this.net_no_arr,
        this.clearance_class_no(), FixedState.SHOVE_FIXED);
    return true;
  }

  /**
   * Looks, if another pin connection restriction fits better than the current
   * connection restriction and changes this trace in this case. If p_at_start,
   * the start of the trace polygon is changed,
   * else the end. Returns true, if this trace was changed.
   */
  public boolean swap_connection_to_pin(boolean p_at_start) {
    Polyline trace_polyline;
    Collection<Item> contact_list;
    if (p_at_start) {
      trace_polyline = this.polyline();
      contact_list = this.get_start_contacts();
    } else {
      trace_polyline = this
          .polyline()
          .reverse();
      contact_list = this.get_end_contacts();
    }
    if (contact_list.size() != 1) {
      return false;
    }
    Item curr_contact = contact_list
        .iterator()
        .next();
    if (!(curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED
        && (curr_contact instanceof PolylineTrace contact_trace))) {
      return false;
    }
    Polyline contact_polyline = contact_trace.polyline();
    Line contact_last_line = contact_polyline.arr[contact_polyline.arr.length - 2];
    // look, if this trace has a sharp angle with the contact trace.
    Line first_line = trace_polyline.arr[1];
    // check for sharp angle
    boolean check_swap = contact_last_line
        .direction()
        .projection(first_line.direction()) == Signum.NEGATIVE;
    if (!check_swap) {
      double half_width = this.get_half_width();
      if (trace_polyline.arr.length > 3 && trace_polyline
          .corner_approx(0)
          .distance_square(trace_polyline.corner_approx(1)) <= half_width * half_width) {
        // check also for sharp angle with the second line
        check_swap = contact_last_line
            .direction()
            .projection(trace_polyline.arr[2].direction()) == Signum.NEGATIVE;
      }
    }
    if (!check_swap) {
      return false;
    }
    Pin contact_pin = null;
    Collection<Item> curr_contacts = contact_trace.get_start_contacts();
    for (Item tmp_contact : curr_contacts) {
      if (tmp_contact instanceof Pin pin) {
        contact_pin = pin;
        break;
      }
    }
    if (contact_pin == null) {
      return false;
    }
    Polyline combined_polyline = contact_polyline.combine(trace_polyline);
    Direction nearest_pin_exit_direction = contact_pin.calc_nearest_exit_restriction_direction(combined_polyline,
        this.get_half_width(), this.get_layer());
    if (nearest_pin_exit_direction == null || nearest_pin_exit_direction.equals(contact_polyline.arr[1].direction())) {
      return false; // direction would not be changed
    }
    contact_trace.set_fixed_state(this.get_fixed_state());
    this.combine();
    return true;
  }
}