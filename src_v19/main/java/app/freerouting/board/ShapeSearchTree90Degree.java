package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.OrthogonalBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntBox. It is
 * used in the
 * 90-degree autorouter algorithm.
 */
public class ShapeSearchTree90Degree extends ShapeSearchTree {
  /** Creates a new instance of ShapeSearchTree90Degree */
  public ShapeSearchTree90Degree(BasicBoard p_board, int p_compensated_clearance_class_no) {
    super(OrthogonalBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
  }

  /**
   * Calculates a new incomplete room with a maximal TileShape contained in the
   * shape of p_room,
   * which may overlap only with items of the input net on the input layer.
   * p_room.get_contained_shape() will be contained in the shape of the result
   * room. If that is not
   * possible, several rooms are returned with shapes, which intersect with
   * p_room.get_contained_shape(). The result room is not yet complete, because
   * its doors are not
   * yet calculated.
   */
  @Override
  public Collection<IncompleteFreeSpaceExpansionRoom> complete_shape(
      IncompleteFreeSpaceExpansionRoom p_room,
      int p_net_no,
      SearchTreeObject p_ignore_object,
      TileShape p_ignore_shape) {
    if (!(p_room.get_contained_shape() instanceof IntBox)) {
      FRLogger.warn("BoxShapeSearchTree.complete_shape: unexpected p_shape_to_be_contained");
      return new LinkedList<>();
    }
    IntBox shape_to_be_contained = (IntBox) p_room.get_contained_shape();
    FRLogger.debug("ShapeSearchTree90Degree.complete_shape entered");
    if (this.root == null) {
      FRLogger.debug("ShapeSearchTree90Degree.complete_shape: root is null");
      return new LinkedList<>();
    }
    IntBox start_shape = board.get_bounding_box();
    if (p_room.get_shape() != null) {
      if (!(p_room.get_shape() instanceof IntBox)) {
        FRLogger.warn("BoxShapeSearchTree.complete_shape: p_start_shape of type IntBox expected");
        return new LinkedList<>();
      }
      start_shape = ((IntBox) p_room.get_shape()).intersection(start_shape);
    }
    IntBox bounding_shape = start_shape;
    int room_layer = p_room.get_layer();
    boolean debugAnchor = is_complete_shape_debug_anchor(p_net_no, room_layer, start_shape);
    int debugStep = 0;
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    result.add(
        new IncompleteFreeSpaceExpansionRoom(start_shape, room_layer, shape_to_be_contained));
    this.node_stack.reset();
    this.node_stack.push(this.root);
    TreeNode curr_node;

    for (;;) {
      curr_node = this.node_stack.pop();
      if (curr_node == null) {
        break;
      }
      if (curr_node.bounding_shape.intersects(bounding_shape)) {
        if (curr_node instanceof Leaf) {
          Leaf curr_leaf = (Leaf) curr_node;
          SearchTreeObject curr_object = (SearchTreeObject) curr_leaf.object;
          int shape_index = curr_leaf.shape_index_in_object;
          boolean is_obstacle = curr_object.is_trace_obstacle(p_net_no);
          int objectLayer = curr_object.shape_layer(shape_index);
          boolean sameLayer = objectLayer == room_layer;
          boolean ignoredObject = curr_object == p_ignore_object;
          if (debugAnchor) {
            trace_complete_shape_filter(debugStep, p_net_no, room_layer, shape_index, objectLayer, is_obstacle, sameLayer, ignoredObject, curr_object);
          }
          if (is_obstacle && sameLayer && !ignoredObject) {

            IntBox curr_object_shape = curr_object.get_tree_shape(this, shape_index).bounding_box();
            if (debugAnchor) {
              trace_complete_shape_candidate(debugStep, p_net_no, room_layer, curr_object, curr_object_shape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> new_result = new LinkedList<>();
            IntBox new_bounding_shape = IntBox.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom curr_room : result) {
              IntBox curr_shape = (IntBox) curr_room.get_shape();
              boolean overlaps = curr_shape.overlaps(curr_object_shape);
              if (overlaps) {
                if (curr_object instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null) {
                  IntBox intersection = curr_shape.intersection(curr_object_shape);
                  if (p_ignore_shape.contains(intersection)) {
                    if (debugAnchor) {
                      trace_complete_shape_decision(debugStep, p_net_no, room_layer, "SKIP_BY_IGNORE_SHAPE", overlaps, curr_shape, curr_object_shape);
                    }
                    // ignore also all objects, whose intersection is contained in the
                    // 2-dim overlap-door with the from_room.
                    continue;
                  }
                }
                if (debugAnchor) {
                  trace_complete_shape_decision(debugStep, p_net_no, room_layer, "RESTRAIN", overlaps, curr_shape, curr_object_shape);
                }
                Collection<IncompleteFreeSpaceExpansionRoom> new_restrained_shapes = restrain_shape(curr_room,
                    curr_object_shape);
                new_result.addAll(new_restrained_shapes);

                for (IncompleteFreeSpaceExpansionRoom tmp_shape : new_result) {
                  new_bounding_shape = new_bounding_shape.union(tmp_shape.get_shape().bounding_box());
                }
              } else {
                if (debugAnchor) {
                  trace_complete_shape_decision(debugStep, p_net_no, room_layer, "KEEP_NON_OVERLAP", overlaps, curr_shape, curr_object_shape);
                }
                new_result.add(curr_room);
                new_bounding_shape = new_bounding_shape.union(curr_shape.bounding_box());
              }
            }
            if (hadRoomsBeforeObstacle && new_result.isEmpty()) {
              FRLogger.trace("COMPLETE_SHAPE_BLOCKED net=" + p_net_no + ", layer=" + room_layer
                  + ", contained=" + describe_bounds(shape_to_be_contained)
                  + ", obstacle_type=" + curr_object.getClass().getSimpleName()
                  + ", obstacle_id=" + obstacle_id(curr_object)
                  + ", obstacle_bounds=" + describe_bounds(curr_object_shape));
            }
            result = new_result;
            bounding_shape = new_bounding_shape;
          }
          if (debugAnchor) {
            debugStep++;
          }
        } else {
          this.node_stack.push(((InnerNode) curr_node).first_child);
          this.node_stack.push(((InnerNode) curr_node).second_child);
        }
      }
    }
    return result;
  }

  private static String describe_bounds(IntBox p_bounds) {
    return "[(" + p_bounds.ll.x + "," + p_bounds.ll.y + ")..(" + p_bounds.ur.x + "," + p_bounds.ur.y + ")]";
  }

  /**
   * Returns true for the specific room being diagnosed in the current parity investigation.
   * Update these coordinates to anchor detailed per-leaf logging to a different room.
   */
  private static boolean is_complete_shape_debug_anchor(int p_net_no, int p_room_layer, IntBox p_start_shape) {
    return p_net_no == 84
        && p_room_layer == 0
        && p_start_shape.ll.x == 1767436
        && p_start_shape.ll.y == -1206395
        && p_start_shape.ur.x == 1994010
        && p_start_shape.ur.y == -782336;
  }

  private static void trace_complete_shape_filter(
      int p_step, int p_net_no, int p_room_layer, int p_shape_index, int p_object_layer,
      boolean p_is_obstacle, boolean p_same_layer, boolean p_ignored_object, SearchTreeObject p_object) {
    FRLogger.trace("COMPLETE_SHAPE_FILTER"
        + ", step=" + p_step
        + ", net=" + p_net_no
        + ", layer=" + p_room_layer
        + ", shape_index=" + p_shape_index
        + ", object_layer=" + p_object_layer
        + ", is_trace_obstacle=" + p_is_obstacle
        + ", same_layer=" + p_same_layer
        + ", ignored_object=" + p_ignored_object
        + ", accepted=" + (p_is_obstacle && p_same_layer && !p_ignored_object)
        + ", obstacle_id=" + obstacle_id(p_object)
        + ", obstacle_nets=" + obstacle_nets(p_object)
        + ", obstacle=" + p_object);
  }

  private static void trace_complete_shape_candidate(
      int p_step, int p_net_no, int p_room_layer, SearchTreeObject p_object, IntBox p_obstacle_shape) {
    FRLogger.trace("COMPLETE_SHAPE_OBS candidate"
        + ", step=" + p_step
        + ", net=" + p_net_no
        + ", layer=" + p_room_layer
        + ", obstacle=" + p_object
        + ", obstacle_id=" + obstacle_id(p_object)
        + ", obstacle_nets=" + obstacle_nets(p_object)
        + ", obstacle_bounds=" + describe_bounds(p_obstacle_shape));
  }

  private static void trace_complete_shape_decision(
      int p_step, int p_net_no, int p_room_layer, String p_action,
      boolean p_overlap, IntBox p_room_shape, IntBox p_obstacle_shape) {
    FRLogger.trace("COMPLETE_SHAPE_DECISION"
        + ", step=" + p_step
        + ", net=" + p_net_no
        + ", layer=" + p_room_layer
        + ", action=" + p_action
        + ", overlap=" + p_overlap
        + ", room_bounds=" + describe_bounds(p_room_shape)
        + ", obstacle_bounds=" + describe_bounds(p_obstacle_shape));
  }

  private static int obstacle_id(SearchTreeObject p_object) {
    return p_object instanceof Item ? ((Item) p_object).get_id_no() : -1;
  }

  private static String obstacle_nets(SearchTreeObject p_object) {
    return p_object instanceof Item ? java.util.Arrays.toString(((Item) p_object).net_no_arr) : "[]";
  }

  /**
   * Restrains the shape of p_incomplete_room to a box shape, which does not
   * intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be
   * contained in the
   * shape of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntBox p_obstacle_shape) {
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from the shape_to_be_contained
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.

    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();

    TileShape contained_shape = p_incomplete_room.get_contained_shape();
    if (contained_shape == null || contained_shape.is_empty()) {
      FRLogger.warn("BoxShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntBox room_shape = p_incomplete_room.get_shape().bounding_box();
    IntBox shape_to_be_contained = p_incomplete_room.get_contained_shape().bounding_box();
    int cut_line_distance = 0;
    IntBox restrained_shape = null;

    if (room_shape.ll.x < p_obstacle_shape.ur.x
        && room_shape.ur.x > p_obstacle_shape.ur.x
        && room_shape.ur.y > p_obstacle_shape.ll.y
        && room_shape.ll.y < p_obstacle_shape.ur.y) {
      // The right line segment of the obstacle_shape intersects the interior of
      // p_shape
      int curr_distance = shape_to_be_contained.ll.x - p_obstacle_shape.ur.x;
      if (curr_distance > cut_line_distance) {
        cut_line_distance = curr_distance;
        restrained_shape = new IntBox(p_obstacle_shape.ur.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
      }
    }
    if (room_shape.ll.x < p_obstacle_shape.ll.x
        && room_shape.ur.x > p_obstacle_shape.ll.x
        && room_shape.ur.y > p_obstacle_shape.ll.y
        && room_shape.ll.y < p_obstacle_shape.ur.y) {
      // The left line segment of the obstacle_shape intersects the interior of
      // p_shape
      int curr_distance = p_obstacle_shape.ll.x - shape_to_be_contained.ur.x;
      if (curr_distance > cut_line_distance) {
        cut_line_distance = curr_distance;
        restrained_shape = new IntBox(room_shape.ll.x, room_shape.ll.y, p_obstacle_shape.ll.x, room_shape.ur.y);
      }
    }
    if (room_shape.ll.y < p_obstacle_shape.ll.y
        && room_shape.ur.y > p_obstacle_shape.ll.y
        && room_shape.ur.x > p_obstacle_shape.ll.x
        && room_shape.ll.x < p_obstacle_shape.ur.x) {
      // The lower line segment of the obstacle_shape intersects the interior of
      // p_shape
      int curr_distance = p_obstacle_shape.ll.y - shape_to_be_contained.ur.y;
      if (curr_distance > cut_line_distance) {
        cut_line_distance = curr_distance;
        restrained_shape = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, p_obstacle_shape.ll.y);
      }
    }
    if (room_shape.ll.y < p_obstacle_shape.ur.y
        && room_shape.ur.y > p_obstacle_shape.ur.y
        && room_shape.ur.x > p_obstacle_shape.ll.x
        && room_shape.ll.x < p_obstacle_shape.ur.x) {
      // The upper line segment of the obstacle_shape intersects the interior of
      // p_shape
      int curr_distance = shape_to_be_contained.ll.y - p_obstacle_shape.ur.y;
      if (curr_distance > cut_line_distance) {
        cut_line_distance = curr_distance;
        restrained_shape = new IntBox(room_shape.ll.x, p_obstacle_shape.ur.y, room_shape.ur.x, room_shape.ur.y);
      }
    }
    if (restrained_shape != null) {
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrained_shape, p_incomplete_room.get_layer(), shape_to_be_contained));
      return result;
    }

    // Now shape_to_be_contained intersects with the obstacle_shape.
    // shape_to_be_contained and p_shape evtl. need to be divided in two.
    IntBox is = shape_to_be_contained.intersection(p_obstacle_shape);
    if (is.is_empty()) {
      FRLogger.warn(
          "BoxShapeSearchTree.restrain_shape: Intersection between obstacle_shape and shape_to_be_contained expected");
      return result;
    }
    IntBox new_shape_1 = null;
    IntBox new_shape_2 = null;
    if (is.ll.x > room_shape.ll.x
        && is.ll.x == p_obstacle_shape.ll.x
        && is.ll.x < room_shape.ur.x) {
      new_shape_1 = new IntBox(room_shape.ll.x, room_shape.ll.y, is.ll.x, room_shape.ur.y);
      new_shape_2 = new IntBox(is.ll.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
    } else if (is.ur.x > room_shape.ll.x
        && is.ur.x == p_obstacle_shape.ur.x
        && is.ur.x < room_shape.ur.x) {
      new_shape_2 = new IntBox(room_shape.ll.x, room_shape.ll.y, is.ur.x, room_shape.ur.y);
      new_shape_1 = new IntBox(is.ur.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
    } else if (is.ll.y > room_shape.ll.y
        && is.ll.y == p_obstacle_shape.ll.y
        && is.ll.y < room_shape.ur.y) {
      new_shape_1 = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, is.ll.y);
      new_shape_2 = new IntBox(room_shape.ll.x, is.ll.y, room_shape.ur.x, room_shape.ur.y);
    } else if (is.ur.y > room_shape.ll.y
        && is.ur.y == p_obstacle_shape.ur.y
        && is.ur.y < room_shape.ur.y) {
      new_shape_2 = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, is.ur.y);
      new_shape_1 = new IntBox(room_shape.ll.x, is.ur.y, room_shape.ur.x, room_shape.ur.y);
    }
    if (new_shape_1 != null) {
      IntBox new_shape_to_be_contained = shape_to_be_contained.intersection(new_shape_1);
      if (new_shape_to_be_contained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                new_shape_1, p_incomplete_room.get_layer(), new_shape_to_be_contained));
        IncompleteFreeSpaceExpansionRoom new_incomplete_room = new IncompleteFreeSpaceExpansionRoom(
            new_shape_2,
            p_incomplete_room.get_layer(),
            shape_to_be_contained.intersection(new_shape_2));
        result.addAll(restrain_shape(new_incomplete_room, p_obstacle_shape));
      }
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(DrillItem p_drill_item) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[p_drill_item.tile_shape_count()];
    for (int i = 0; i < result.length; ++i) {
      Shape curr_shape = p_drill_item.get_shape(i);
      if (curr_shape == null) {
        result[i] = null;
      } else {
        IntBox curr_tile_shape = curr_shape.bounding_box();
        int offset_width = this.clearance_compensation_value(
            p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
        if (curr_tile_shape == null) {
          FRLogger.warn("BoxShapeSearchTree.calculate_tree_shapes: shape is null");
        } else {
          curr_tile_shape = curr_tile_shape.offset(offset_width);
        }
        result[i] = curr_tile_shape;
      }
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area) {
    TileShape[] result = super.calculate_tree_shapes(p_obstacle_area);
    for (int i = 0; i < result.length; ++i) {
      result[i] = result[i].bounding_box();
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(BoardOutline p_outline) {
    TileShape[] result = super.calculate_tree_shapes(p_outline);
    for (int i = 0; i < result.length; ++i) {
      result[i] = result[i].bounding_box();
    }
    return result;
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  TileShape offset_shape(Polyline p_polyline, int p_half_width, int p_no) {
    return p_polyline.offset_box(p_half_width, p_no);
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  public TileShape[] offset_shapes(
      Polyline p_polyline, int p_half_width, int p_from_no, int p_to_no) {
    int from_no = Math.max(p_from_no, 0);
    int to_no = Math.min(p_to_no, p_polyline.arr.length - 1);
    int shape_count = Math.max(to_no - from_no - 1, 0);
    TileShape[] shape_arr = new TileShape[shape_count];
    for (int j = from_no; j < to_no - 1; ++j) {
      shape_arr[j - from_no] = p_polyline.offset_box(p_half_width, j);
    }
    return shape_arr;
  }
}
