package app.freerouting.autoroute;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * An expansion room, whose shape is completely calculated, so that it can be stored in a shape
 * tree.
 */
public class CompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom
    implements CompleteExpansionRoom, SearchTreeObject {

  // ** identification number for implementong the Comparable interfacw */
  private final int id_no;
  /** The array of entries in the SearchTree. Consists of just one element */
  private ShapeTree.Leaf[] tree_entries = null;
  /** The list of doors to items of the own net */
  private Collection<TargetItemExpansionDoor> target_doors;
  private boolean room_is_net_dependent = false;

  /** Creates a new instance of CompleteFreeSpaceExpansionRoom */
  public CompleteFreeSpaceExpansionRoom(TileShape p_shape, int p_layer, int p_id_no) {
    super(p_shape, p_layer);
    target_doors = new LinkedList<TargetItemExpansionDoor>();
    id_no = p_id_no;
  }

  public void set_search_tree_entries(ShapeTree.Leaf[] p_entries, ShapeTree p_tree) {
    tree_entries = p_entries;
  }

  public int compareTo(Object p_other) {
    int result;
    if (p_other instanceof FreeSpaceExpansionRoom) {
      result = ((CompleteFreeSpaceExpansionRoom) p_other).id_no - this.id_no;
    } else {
      result = -1;
    }
    return result;
  }

  /** Removes the tree entries of this roomm from p_shape_tree. */
  public void remove_from_tree(ShapeTree p_shape_tree) {
    p_shape_tree.remove(this.tree_entries);
  }

  public int tree_shape_count(ShapeTree p_shape_tree) {
    return 1;
  }

  public TileShape get_tree_shape(ShapeTree p_shape_tree, int p_index) {
    return this.get_shape();
  }

  public int shape_layer(int p_index) {
    return this.get_layer();
  }

  public boolean is_obstacle(int p_net_no) {
    return true;
  }

  public boolean is_trace_obstacle(int p_net_no) {
    return true;
  }

  /** Will be called, when the room overlaps with net dependent objects. */
  public void set_net_dependent() {
    this.room_is_net_dependent = true;
  }

  /**
   * Returns, if the room overlaps with net dependent objects. In this case it cannot be retained,
   * when the net number changes in autorouting.
   */
  public boolean is_net_dependent() {
    return this.room_is_net_dependent;
  }

  /** Returns the list doors to target items of this room */
  public Collection<TargetItemExpansionDoor> get_target_doors() {
    return this.target_doors;
  }

  /** Adds p_door to the list of target doors of this room. */
  public void add_target_door(TargetItemExpansionDoor p_door) {
    this.target_doors.add(p_door);
  }

  public boolean remove_door(ExpandableObject p_door) {
    boolean result;
    if (p_door instanceof TargetItemExpansionDoor) {
      result = this.target_doors.remove(p_door);
    } else {
      result = super.remove_door(p_door);
    }
    return result;
  }

  public SearchTreeObject get_object() {
    return this;
  }

  /** Calculates the doors to the start and destination items of the autoroute algorithm. */
  public void calculate_target_doors(
      ShapeTree.TreeEntry p_own_net_object, int p_net_no, ShapeSearchTree p_autoroute_search_tree) {
    this.set_net_dependent();

    if (p_own_net_object.object instanceof Connectable) {
      Connectable curr_object = (Connectable) p_own_net_object.object;
      if (curr_object.contains_net(p_net_no)) {
        TileShape curr_connection_shape =
            curr_object.get_trace_connection_shape(
                p_autoroute_search_tree, p_own_net_object.shape_index_in_object);
        if (curr_connection_shape != null && this.get_shape().intersects(curr_connection_shape)) {
          Item curr_item = (Item) curr_object;
          TargetItemExpansionDoor new_target_door =
              new TargetItemExpansionDoor(
                  curr_item, p_own_net_object.shape_index_in_object, this, p_autoroute_search_tree);
          this.add_target_door(new_target_door);
        }
      }
    }
  }

  /** Draws the shape of this room. */
  public void draw(
      java.awt.Graphics p_graphics,
      app.freerouting.boardgraphics.GraphicsContext p_graphics_context,
      double p_intensity) {
    java.awt.Color draw_color = p_graphics_context.get_trace_colors(false)[this.get_layer()];
    double layer_visibility = p_graphics_context.get_layer_visibility(this.get_layer());
    p_graphics_context.fill_area(
        this.get_shape(), p_graphics, draw_color, p_intensity * layer_visibility);
    p_graphics_context.draw_boundary(this.get_shape(), 0, draw_color, p_graphics, layer_visibility);
  }

  /** Check, if this FreeSpaceExpansionRoom is valid. */
  public boolean validate(AutorouteEngine p_autoroute_engine) {
    boolean result = true;
    Collection<ShapeTree.TreeEntry> overlapping_objects = new LinkedList<ShapeTree.TreeEntry>();
    int[] net_no_arr = new int[1];
    net_no_arr[0] = p_autoroute_engine.get_net_no();
    p_autoroute_engine.autoroute_search_tree.overlapping_tree_entries(
        this.get_shape(), this.get_layer(), net_no_arr, overlapping_objects);
    Iterator<ShapeTree.TreeEntry> it = overlapping_objects.iterator();
    while (it.hasNext()) {
      ShapeTree.TreeEntry curr_entry = it.next();
      if (curr_entry.object == this) {
        continue;
      }
      SearchTreeObject curr_object = (SearchTreeObject) curr_entry.object;
      if (!curr_object.is_trace_obstacle(p_autoroute_engine.get_net_no())) {
        continue;
      }
      if (curr_object.shape_layer(curr_entry.shape_index_in_object) != get_layer()) {
        continue;
      }
      TileShape curr_shape =
          curr_object.get_tree_shape(
              p_autoroute_engine.autoroute_search_tree, curr_entry.shape_index_in_object);
      TileShape intersection = this.get_shape().intersection(curr_shape);
      if (intersection.dimension() > 1) {
        FRLogger.warn("ExpansionRoom overlap conflict");
        result = false;
      }
    }
    return result;
  }

  /** Removes all doors and target doors from this room. */
  public void clear_doors() {
    super.clear_doors();
    this.target_doors = new LinkedList<TargetItemExpansionDoor>();
  }

  public void reset_doors() {
    super.reset_doors();
    for (ExpandableObject curr_door : this.target_doors) {
      curr_door.reset();
    }
  }
}
