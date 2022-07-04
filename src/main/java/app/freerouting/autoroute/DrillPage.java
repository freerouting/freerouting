package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

class DrillPage implements ExpandableObject {

  /** The shape of the page */
  final IntBox shape;
  private final MazeSearchElement[] maze_search_info_arr;
  private final RoutingBoard board;
  /** The list of expansion drills on this page. Null, if not yet calculated. */
  private Collection<ExpansionDrill> drills = null;
  /** The number of the net, for which the drills are calculated */
  private int net_no = -1;

  /** Creates a new instance of DrillPage */
  public DrillPage(IntBox p_shape, RoutingBoard p_board) {
    shape = p_shape;
    board = p_board;
    maze_search_info_arr = new MazeSearchElement[p_board.get_layer_count()];
    for (int i = 0; i < maze_search_info_arr.length; ++i) {
      maze_search_info_arr[i] = new MazeSearchElement();
    }
  }

  /**
   * Looks if p_drill_shape contains the center of a drillable Pin on p_layer. Returns null, if no
   * such Pin was found.
   */
  private static Point calc_pin_center_in_drill(
      TileShape p_drill_shape, int p_layer, RoutingBoard p_board) {
    Collection<Item> overlapping_items = p_board.overlapping_items(p_drill_shape, p_layer);
    Point result = null;
    for (Item curr_item : overlapping_items) {
      if (curr_item instanceof app.freerouting.board.Pin) {
        app.freerouting.board.Pin curr_pin = (app.freerouting.board.Pin) curr_item;
        if (curr_pin.drill_allowed() && p_drill_shape.contains_inside(curr_pin.get_center())) {
          result = curr_pin.get_center();
        }
      }
    }
    return result;
  }

  /** Returns the drills on this page. If p_atttach_smd, drilling to smd pins is allowed. */
  public Collection<ExpansionDrill> get_drills(
      AutorouteEngine p_autoroute_engine, boolean p_attach_smd) {
    if (this.drills == null || p_autoroute_engine.get_net_no() != this.net_no) {
      this.net_no = p_autoroute_engine.get_net_no();
      this.drills = new LinkedList<ExpansionDrill>();
      ShapeSearchTree search_tree = this.board.search_tree_manager.get_default_tree();
      Collection<TreeEntry> overlaps = new LinkedList<TreeEntry>();
      search_tree.overlapping_tree_entries(this.shape, -1, overlaps);
      Collection<TileShape> cutout_shapes = new LinkedList<TileShape>();
      // drills on top of existing vias are used in the ripup algorithm
      TileShape prev_obstacle_shape = IntBox.EMPTY;
      for (TreeEntry curr_entry : overlaps) {
        if (!(curr_entry.object instanceof Item)) {
          continue;
        }
        Item curr_item = (Item) curr_entry.object;
        if (curr_item.is_drillable(this.net_no)) {
          continue;
        }
        if (curr_item instanceof app.freerouting.board.Pin) {
          if (p_attach_smd && ((app.freerouting.board.Pin) curr_item).drill_allowed()) {
            continue;
          }
        }
        TileShape curr_obstacle_shape =
            curr_item.get_tree_shape(search_tree, curr_entry.shape_index_in_object);
        if (!prev_obstacle_shape.contains(curr_obstacle_shape)) {
          // Checked to avoid multiple cutout for example for vias with the same shape on all
          // layers.
          TileShape curr_cutout_shape = curr_obstacle_shape.intersection(this.shape);
          if (curr_cutout_shape.dimension() == 2) {
            cutout_shapes.add(curr_cutout_shape);
          }
        }
        prev_obstacle_shape = curr_obstacle_shape;
      }
      TileShape[] holes = new TileShape[cutout_shapes.size()];
      Iterator<TileShape> it = cutout_shapes.iterator();
      for (int i = 0; i < holes.length; ++i) {
        holes[i] = it.next();
      }
      PolylineArea shape_with_holes = new PolylineArea(this.shape, holes);
      TileShape[] drill_shapes =
          shape_with_holes.split_to_convex(p_autoroute_engine.stoppable_thread);

      // Use the center points of these drill shapes to try making a via.
      int drill_first_layer = 0;
      int drill_last_layer = this.board.get_layer_count() - 1;
      for (int i = 0; i < drill_shapes.length; ++i) {
        TileShape curr_drill_shape = drill_shapes[i];
        Point curr_drill_location = null;
        if (p_attach_smd) {
          curr_drill_location =
              calc_pin_center_in_drill(
                  curr_drill_shape, drill_first_layer, p_autoroute_engine.board);
          if (curr_drill_location == null) {
            curr_drill_location =
                calc_pin_center_in_drill(
                    curr_drill_shape, drill_last_layer, p_autoroute_engine.board);
          }
        }
        if (curr_drill_location == null) {
          curr_drill_location = curr_drill_shape.centre_of_gravity().round();
        }
        ExpansionDrill new_drill =
            new ExpansionDrill(
                curr_drill_shape, curr_drill_location, drill_first_layer, drill_last_layer);
        if (new_drill.calculate_expansion_rooms(p_autoroute_engine)) {
          this.drills.add(new_drill);
        }
      }
    }
    return this.drills;
  }

  public TileShape get_shape() {
    return this.shape;
  }

  public int get_dimension() {
    return 2;
  }

  public int maze_search_element_count() {
    return this.maze_search_info_arr.length;
  }

  public MazeSearchElement get_maze_search_element(int p_no) {
    return this.maze_search_info_arr[p_no];
  }

  /** Resets all drills of this page for autorouting the next connection. */
  public void reset() {
    if (this.drills != null) {
      for (ExpansionDrill curr_drill : this.drills) {
        curr_drill.reset();
      }
    }
    for (MazeSearchElement curr_info : maze_search_info_arr) {
      curr_info.reset();
    }
  }

  /**
   * Invalidates the drills of this page so that they are recalculated at the next call of
   * get_drills().
   */
  public void invalidate() {
    this.drills = null;
  }

  /*
   * Test draw of the drills on this page.
   */
  public void draw(
      java.awt.Graphics p_graphics,
      app.freerouting.boardgraphics.GraphicsContext p_graphics_context,
      double p_intensity) {
    if (true || drills == null) {
      return;
    }
    for (ExpansionDrill curr_drill : drills) {
      curr_drill.draw(p_graphics, p_graphics_context, p_intensity);
    }
  }

  public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
    return null;
  }
}
