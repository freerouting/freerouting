package app.freerouting.autoroute;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/**
 * An expansion room, whose shape is completely calculated, so that it can be stored in a shape
 * tree.
 */
public class CompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom
    implements CompleteExpansionRoom, SearchTreeObject {

  // ** identification number for implementing the Comparable interface */
  private final int idNo;

  /** The array of entries in the SearchTree. Consists of just one element */
  private ShapeTree.Leaf[] treeEntries;

  /** The list of doors to items of the own net */
  private Collection<TargetItemExpansionDoor> targetDoors;

  private boolean roomIsNetDependent;

  /** Creates a new instance of CompleteFreeSpaceExpansionRoom */
  public CompleteFreeSpaceExpansionRoom(TileShape p_shape, int p_layer, int p_id_no) {
    super(p_shape, p_layer);
    targetDoors = new LinkedList<>();
    idNo = p_id_no;
  }

  @Override
  public void set_search_tree_entries(ShapeTree.Leaf[] p_entries, ShapeTree p_tree) {
    treeEntries = p_entries;
  }

  @Override
  public int compareTo(Object p_other) {
    int result;
    if (p_other instanceof FreeSpaceExpansionRoom) {
      result = ((CompleteFreeSpaceExpansionRoom) p_other).idNo - this.idNo;
    } else {
      result = -1;
    }
    return result;
  }

  /** Removes the tree entries of this room from p_shape_tree. */
  public void remove_from_tree(ShapeTree p_shape_tree) {
    p_shape_tree.remove(this.treeEntries);
  }

  @Override
  public int tree_shape_count(ShapeTree p_shape_tree) {
    return 1;
  }

  @Override
  public TileShape get_tree_shape(ShapeTree p_shape_tree, int p_index) {
    return this.get_shape();
  }

  @Override
  public int shape_layer(int p_index) {
    return this.get_layer();
  }

  @Override
  public boolean is_obstacle(int p_net_no) {
    return true;
  }

  @Override
  public boolean is_trace_obstacle(int p_net_no) {
    return true;
  }

  /** Will be called, when the room overlaps with net dependent objects. */
  public void set_net_dependent() {
    this.roomIsNetDependent = true;
  }

  /**
   * Returns, if the room overlaps with net dependent objects. In this case it cannot be retained,
   * when the net number changes in autorouting.
   */
  public boolean is_net_dependent() {
    return this.roomIsNetDependent;
  }

  @Override
  public int get_id_no() {
    return idNo;
  }

  /** Returns the list doors to target items of this room */
  @Override
  public Collection<TargetItemExpansionDoor> get_target_doors() {
    return this.targetDoors;
  }

  /** Adds p_door to the list of target doors of this room. */
  public void add_target_door(TargetItemExpansionDoor p_door) {
    this.targetDoors.add(p_door);
  }

  @Override
  public boolean remove_door(ExpandableObject p_door) {
    boolean result;
    if (p_door instanceof TargetItemExpansionDoor) {
      result = this.targetDoors.remove(p_door);
    } else {
      result = super.remove_door(p_door);
    }
    return result;
  }

  @Override
  public SearchTreeObject get_object() {
    return this;
  }

  /** Calculates the doors to the start and destination items of the autoroute algorithm. */
  public void calculate_target_doors(
      ShapeTree.TreeEntry p_own_net_object, int p_net_no, ShapeSearchTree p_autoroute_search_tree) {
    this.set_net_dependent();

    if (p_own_net_object.object instanceof Connectable currObject) {
      if (currObject.contains_net(p_net_no)) {
        TileShape currConnectionShape =
            currObject.get_trace_connection_shape(
                p_autoroute_search_tree, p_own_net_object.shapeIndexInObject);
        if (currConnectionShape != null && this.get_shape().intersects(currConnectionShape)) {
          Item currItem = (Item) currObject;
          TargetItemExpansionDoor newTargetDoor =
              new TargetItemExpansionDoor(
                  currItem, p_own_net_object.shapeIndexInObject, this, p_autoroute_search_tree);
          this.add_target_door(newTargetDoor);
        }
      }
    }
  }

  /** Draws the shape of this room. */
  @Override
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    Color drawColor = p_graphics_context.get_trace_colors(false)[this.get_layer()];
    double layerVisibility = p_graphics_context.get_layer_visibility(this.get_layer());
    p_graphics_context.fill_area(
        this.get_shape(), p_graphics, drawColor, p_intensity * layerVisibility);
    p_graphics_context.draw_boundary(this.get_shape(), 0, drawColor, p_graphics, layerVisibility);
  }

  /** Check, if this FreeSpaceExpansionRoom is valid. */
  public boolean validate(AutorouteEngine p_autoroute_engine) {
    boolean result = true;
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    int[] netNoArr = new int[1];
    netNoArr[0] = p_autoroute_engine.get_net_no();
    p_autoroute_engine.autorouteSearchTree.overlapping_tree_entries(
        this.get_shape(), this.get_layer(), netNoArr, overlappingObjects);
    for (ShapeTree.TreeEntry currEntry : overlappingObjects) {
      if (currEntry.object == this) {
        continue;
      }
      SearchTreeObject currObject = (SearchTreeObject) currEntry.object;
      if (!currObject.is_trace_obstacle(p_autoroute_engine.get_net_no())) {
        continue;
      }
      if (currObject.shape_layer(currEntry.shapeIndexInObject) != get_layer()) {
        continue;
      }
      TileShape currShape =
          currObject.get_tree_shape(
              p_autoroute_engine.autorouteSearchTree, currEntry.shapeIndexInObject);
      TileShape intersection = this.get_shape().intersection(currShape);
      if (intersection.dimension() > 1) {
        FRLogger.warn("ExpansionRoom overlap conflict");
        result = false;
      }
    }
    return result;
  }

  /** Removes all doors and target doors from this room. */
  @Override
  public void clear_doors() {
    super.clear_doors();
    this.targetDoors = new LinkedList<>();
  }

  @Override
  public void reset_doors() {
    super.reset_doors();
    for (ExpandableObject currDoor : this.targetDoors) {
      currDoor.reset();
    }
  }
}
