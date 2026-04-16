package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntOctagon.
 * It is used in the
 * 45-degree autorouter algorithm.
 */
public class ShapeSearchTree45Degree extends ShapeSearchTree {
  /** Creates a new instance of ShapeSearchTree45Degree */
  public ShapeSearchTree45Degree(BasicBoard p_board, int p_compensated_clearance_class_no) {
    super(FortyfiveDegreeBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
  }

  /**
   * Checks, if the border line segment with index p_obstacle_border_line_no
   * intersects with the
   * inside of p_room_shape.
   */
  private static boolean obstacle_segment_touches_inside(
      IntOctagon p_obstacle_shape, int p_obstacle_border_line_no, IntOctagon p_room_shape) {
    int curr_border_line_no = p_obstacle_border_line_no;
    int curr_obstacle_corner_x = p_obstacle_shape.corner_x(p_obstacle_border_line_no);
    int curr_obstacle_corner_y = p_obstacle_shape.corner_y(p_obstacle_border_line_no);
    for (int j = 0; j < 5; ++j) {

      if (p_room_shape.side_of_border_line(
          curr_obstacle_corner_x, curr_obstacle_corner_y, curr_border_line_no) != Side.ON_THE_LEFT) {
        return false;
      }
      curr_border_line_no = (curr_border_line_no + 1) % 8;
    }

    int next_obstacle_border_line_no = (p_obstacle_border_line_no + 1) % 8;
    int next_obstacle_corner_x = p_obstacle_shape.corner_x(next_obstacle_border_line_no);
    int next_obstacle_corner_y = p_obstacle_shape.corner_y(next_obstacle_border_line_no);
    curr_border_line_no = (p_obstacle_border_line_no + 5) % 8;
    for (int j = 0; j < 3; ++j) {
      if (p_room_shape.side_of_border_line(
          next_obstacle_corner_x, next_obstacle_corner_y, curr_border_line_no) != Side.ON_THE_LEFT) {
        return false;
      }
      curr_border_line_no = (curr_border_line_no + 1) % 8;
    }
    return true;
  }

  private static double signed_line_distance(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_contained_shape) {
    return switch (p_obstacle_line_no) {
      case 0 -> p_obstacle_shape.ly - p_contained_shape.uy;
      case 2 -> p_contained_shape.lx - p_obstacle_shape.rx;
      case 4 -> p_contained_shape.ly - p_obstacle_shape.uy;
      case 6 -> p_obstacle_shape.lx - p_contained_shape.rx;

      // factor 0.5 used instead to 1 / sqrt(2) to prefer orthogonal lines slightly to
      // diagonal
      // restraining lines.
      case 1 -> 0.5 * (p_contained_shape.ulx - p_obstacle_shape.lrx);
      case 3 -> 0.5 * (p_contained_shape.llx - p_obstacle_shape.urx);
      case 5 -> 0.5 * (p_obstacle_shape.ulx - p_contained_shape.lrx);
      case 7 -> 0.5 * (p_obstacle_shape.llx - p_contained_shape.urx);
      default -> {
        FRLogger.warn(
            "ShapeSearchTree45Degree.signed_line_distance: p_obstacle_line_no out of range");
        yield 0;
      }
    };
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
    if (!(p_room.get_contained_shape().is_IntOctagon())) {
      FRLogger.warn("ShapeSearchTree45Degree.complete_shape: unexpected p_shape_to_be_contained");
      return new LinkedList<>();
    }
    IntOctagon shape_to_be_contained = p_room.get_contained_shape().bounding_octagon();
    if (this.root == null) {
      FRLogger.debug("ShapeSearchTree45Degree.complete_shape: root is null");
      return new LinkedList<>();
    }
    IntOctagon start_shape = board.get_bounding_box().bounding_octagon();
    if (p_room.get_shape() != null) {
      if (!(p_room.get_shape() instanceof IntOctagon)) {
        FRLogger.warn(
            "ShapeSearchTree45Degree.complete_shape: p_start_shape of type IntOctagon expected");
        return new LinkedList<>();
      }
      start_shape = p_room.get_shape().bounding_octagon().intersection(start_shape);
    }
    IntOctagon bounding_shape = start_shape;
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
          boolean is_obstacle = curr_object.is_trace_obstacle(p_net_no);

          int shape_index = curr_leaf.shape_index_in_object;
          int objectLayer = curr_object.shape_layer(shape_index);
          boolean sameLayer = objectLayer == room_layer;
          boolean ignoredObject = curr_object == p_ignore_object;
          if (debugAnchor) {
            trace_complete_shape_filter(
                debugStep,
                p_net_no,
                room_layer,
                shape_index,
                objectLayer,
                is_obstacle,
                sameLayer,
                ignoredObject,
                curr_object);
          }
          if (is_obstacle && sameLayer && !ignoredObject) {

            IntOctagon curr_object_shape = curr_object.get_tree_shape(this, shape_index).bounding_octagon();
            if (debugAnchor) {
              trace_complete_shape_candidate(
                  debugStep, p_net_no, room_layer, curr_object, curr_object_shape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> new_result = new LinkedList<>();
            IntOctagon new_bounding_shape = IntOctagon.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom curr_room : result) {
              IntOctagon curr_shape = (IntOctagon) curr_room.get_shape();
              boolean overlaps = curr_shape.overlaps(curr_object_shape);
              if (overlaps) {
                if (curr_object instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null) {
                  IntOctagon intersection = curr_shape.intersection(curr_object_shape);
                  if (p_ignore_shape.contains(intersection)) {
                    if (debugAnchor) {
                      trace_complete_shape_decision(
                          debugStep,
                          p_net_no,
                          room_layer,
                          "SKIP_BY_IGNORE_SHAPE",
                          overlaps,
                          curr_shape,
                          curr_object_shape);
                    }
                    // ignore also all objects, whose intersection is contained in the
                    // 2-dim overlap-door with the from_room.
                    if (!p_ignore_shape.contains(curr_shape)) {
                      new_result.add(curr_room);
                      new_bounding_shape = new_bounding_shape.union(curr_shape.bounding_box());
                    }
                    continue;
                  }
                }
                if (debugAnchor) {
                  trace_complete_shape_decision(
                      debugStep,
                      p_net_no,
                      room_layer,
                      "RESTRAIN",
                      overlaps,
                      curr_shape,
                      curr_object_shape);
                }
                Collection<IncompleteFreeSpaceExpansionRoom> new_restrained_shapes = restrain_shape(curr_room,
                    curr_object_shape);
                new_result.addAll(new_restrained_shapes);

                for (IncompleteFreeSpaceExpansionRoom tmp_shape : new_result) {
                  new_bounding_shape = new_bounding_shape.union(tmp_shape.get_shape().bounding_box());
                }
              } else {
                if (debugAnchor) {
                  trace_complete_shape_decision(
                      debugStep,
                      p_net_no,
                      room_layer,
                      "KEEP_NON_OVERLAP",
                      overlaps,
                      curr_shape,
                      curr_object_shape);
                }
                new_result.add(curr_room);
                new_bounding_shape = new_bounding_shape.union(curr_shape.bounding_box());
              }
            }
            if (hadRoomsBeforeObstacle && new_result.isEmpty()) {
              FRLogger.trace("COMPLETE_SHAPE_BLOCKED net=" + p_net_no + ", layer=" + room_layer
                  + ", contained=" + describe_bounds(shape_to_be_contained.bounding_box())
                  + ", obstacle_type=" + curr_object.getClass().getSimpleName()
                  + ", obstacle_id=" + obstacle_id(curr_object)
                  + ", obstacle_bounds=" + describe_bounds(curr_object_shape.bounding_box()));
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
    result = divide_large_room(result, board.get_bounding_box());
    // remove rooms with shapes equal to the contained shape to prevent endless
    // loop.
    result.removeIf(room -> room.get_contained_shape().contains(room.get_shape()));
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1
   * IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm
   * gets problems with
   * vias.
   */
  @Override
  protected Collection<IncompleteFreeSpaceExpansionRoom> divide_large_room(
      Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box) {
    Collection<IncompleteFreeSpaceExpansionRoom> result = super.divide_large_room(p_room_list, p_board_bounding_box);
    for (IncompleteFreeSpaceExpansionRoom curr_room : result) {
      curr_room.set_shape(curr_room.get_shape().bounding_octagon());
      curr_room.set_contained_shape(curr_room.get_contained_shape().bounding_octagon());
    }
    return result;
  }

  /**
   * Restrains the shape of p_incomplete_room to an octagon shape, which does not
   * intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be
   * contained in the
   * shape of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntOctagon p_obstacle_shape) {
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
      FRLogger.debug("ShapeSearchTree45Degree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntOctagon room_shape = p_incomplete_room.get_shape().bounding_octagon();
    IntOctagon shape_to_be_contained = p_incomplete_room.get_contained_shape().bounding_octagon();
    double cut_line_distance = -1;
    int restraining_line_no = -1;

    for (int obstacle_line_no = 0; obstacle_line_no < 8; ++obstacle_line_no) {
      double curr_distance = signed_line_distance(p_obstacle_shape, obstacle_line_no, shape_to_be_contained);
      if (curr_distance > cut_line_distance) {
        if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, room_shape)) {
          cut_line_distance = curr_distance;
          restraining_line_no = obstacle_line_no;
        }
      }
    }
    if (cut_line_distance >= 0) {
      IntOctagon restrained_shape = calc_outside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrained_shape, p_incomplete_room.get_layer(), shape_to_be_contained));
      return result;
    }

    // There is no cut line, so that all p_shape_to_be_contained is
    // completely on the right side of that line. Search a cut line, so that
    // at least part of p_shape_to_be_contained is on the right side.
    if (shape_to_be_contained.dimension() < 1) {
      // There is already a completed expansion room around p_shape_to_be_contained.
      return result;
    }

    restraining_line_no = -1;
    for (int obstacle_line_no = 0; obstacle_line_no < 8; ++obstacle_line_no) {
      if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, room_shape)) {
        Line curr_line = p_obstacle_shape.border_line(obstacle_line_no);
        if (shape_to_be_contained.side_of(curr_line) == Side.COLLINEAR) {
          // curr_line intersects with the interior of p_shape_to_be_contained
          restraining_line_no = obstacle_line_no;
          break;
        }
      }
    }
    if (restraining_line_no < 0) {
      // cut line not found, parts or the whole of p_shape may be already
      // occupied from somewhere else.
      return result;
    }
    IntOctagon restrained_shape = calc_outside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
    if (restrained_shape.dimension() == 2) {
      IntOctagon new_shape_to_be_contained = shape_to_be_contained.intersection(restrained_shape);
      if (new_shape_to_be_contained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                restrained_shape, p_incomplete_room.get_layer(), new_shape_to_be_contained));
      }
    }

    IntOctagon rest_piece = calc_inside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
    if (rest_piece.dimension() >= 2) {
      TileShape rest_shape_to_be_contained = shape_to_be_contained.intersection(rest_piece);
      if (rest_shape_to_be_contained.dimension() >= 0) {
        IncompleteFreeSpaceExpansionRoom rest_incomplete_room = new IncompleteFreeSpaceExpansionRoom(
            rest_piece, p_incomplete_room.get_layer(), rest_shape_to_be_contained);
        result.addAll(restrain_shape(rest_incomplete_room, p_obstacle_shape));
      }
    }
    return result;
  }

  /**
   * Intersects p_room_shape with the half plane defined by the outside of the
   * borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calc_outside_restrained_shape(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape) {
    int lx = p_room_shape.lx;
    int ly = p_room_shape.ly;
    int rx = p_room_shape.rx;
    int uy = p_room_shape.uy;
    int ulx = p_room_shape.ulx;
    int lrx = p_room_shape.lrx;
    int llx = p_room_shape.llx;
    int urx = p_room_shape.urx;

    switch (p_obstacle_line_no) {
      case 0 -> uy = p_obstacle_shape.ly;
      case 2 -> lx = p_obstacle_shape.rx;
      case 4 -> ly = p_obstacle_shape.uy;
      case 6 -> rx = p_obstacle_shape.lx;
      case 1 -> ulx = p_obstacle_shape.lrx;
      case 3 -> llx = p_obstacle_shape.urx;
      case 5 -> lrx = p_obstacle_shape.ulx;
      case 7 -> urx = p_obstacle_shape.llx;
      default -> FRLogger.warn(
          "ShapeSearchTree45Degree.calc_outside_restrained_shape: p_obstacle_line_no out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
  }

  /**
   * Intersects p_room_shape with the half plane defined by the inside of the
   * borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calc_inside_restrained_shape(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape) {
    int lx = p_room_shape.lx;
    int ly = p_room_shape.ly;
    int rx = p_room_shape.rx;
    int uy = p_room_shape.uy;
    int ulx = p_room_shape.ulx;
    int lrx = p_room_shape.lrx;
    int llx = p_room_shape.llx;
    int urx = p_room_shape.urx;

    switch (p_obstacle_line_no) {
      case 0 -> ly = p_obstacle_shape.ly;
      case 2 -> rx = p_obstacle_shape.rx;
      case 4 -> uy = p_obstacle_shape.uy;
      case 6 -> lx = p_obstacle_shape.lx;
      case 1 -> lrx = p_obstacle_shape.lrx;
      case 3 -> urx = p_obstacle_shape.urx;
      case 5 -> ulx = p_obstacle_shape.ulx;
      case 7 -> llx = p_obstacle_shape.llx;
      default -> FRLogger.warn(
          "ShapeSearchTree45Degree.calc_inside_restrained_shape: p_obstacle_line_no out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
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
        TileShape curr_tile_shape = curr_shape.bounding_octagon();
        if (curr_tile_shape.is_IntBox()) {
          curr_tile_shape = curr_shape.bounding_box();

          // To avoid small corner cutoffs when taking the offset as an octagon.
          // That may complicate the room division in the maze expand algorithm
          // unnecessary.
        }

        int offset_width = this.clearance_compensation_value(
            p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
        curr_tile_shape = (TileShape) curr_tile_shape.offset(offset_width);
        result[i] = curr_tile_shape.bounding_octagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area) {
    TileShape[] result = super.calculate_tree_shapes(p_obstacle_area);
    for (int i = 0; i < result.length; ++i) {
      result[i] = result[i].bounding_octagon();
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(BoardOutline p_outline) {
    TileShape[] result = super.calculate_tree_shapes(p_outline);
    for (int i = 0; i < result.length; ++i) {
      result[i] = result[i].bounding_octagon();
    }
    return result;
  }

  private static String describe_bounds(IntBox p_bounds) {
    return "[("
        + p_bounds.ll.x
        + ","
        + p_bounds.ll.y
        + ")..("
        + p_bounds.ur.x
        + ","
        + p_bounds.ur.y
        + ")]";
  }

  private static boolean is_complete_shape_debug_anchor(
      int p_net_no, int p_room_layer, IntOctagon p_start_shape) {
    return p_net_no == 77
        && p_room_layer == 0
        && p_start_shape.lx == 1762393
        && p_start_shape.ly == -1080137
        && p_start_shape.rx == 1910447
        && p_start_shape.uy == -1006110;
  }

  private static void trace_complete_shape_filter(
      int p_step,
      int p_net_no,
      int p_room_layer,
      int p_shape_index,
      int p_object_layer,
      boolean p_is_obstacle,
      boolean p_same_layer,
      boolean p_ignored_object,
      SearchTreeObject p_object) {
    FRLogger.trace(
        "COMPLETE_SHAPE_FILTER"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", shape_index="
            + p_shape_index
            + ", object_layer="
            + p_object_layer
            + ", is_trace_obstacle="
            + p_is_obstacle
            + ", same_layer="
            + p_same_layer
            + ", ignored_object="
            + p_ignored_object
            + ", accepted="
            + (p_is_obstacle && p_same_layer && !p_ignored_object)
            + ", obstacle_id="
            + obstacle_id(p_object)
            + ", obstacle_nets="
            + obstacle_nets(p_object)
            + ", obstacle="
            + p_object);
  }

  private static void trace_complete_shape_candidate(
      int p_step,
      int p_net_no,
      int p_room_layer,
      SearchTreeObject p_object,
      IntOctagon p_obstacle_shape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_OBS candidate"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", obstacle="
            + p_object
            + ", obstacle_id="
            + obstacle_id(p_object)
            + ", obstacle_nets="
            + obstacle_nets(p_object)
            + ", obstacle_bounds="
            + describe_bounds(p_obstacle_shape.bounding_box()));
  }

  private static void trace_complete_shape_decision(
      int p_step,
      int p_net_no,
      int p_room_layer,
      String p_action,
      boolean p_overlap,
      IntOctagon p_room_shape,
      IntOctagon p_obstacle_shape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_DECISION"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", action="
            + p_action
            + ", overlap="
            + p_overlap
            + ", room_bounds="
            + describe_bounds(p_room_shape.bounding_box())
            + ", obstacle_bounds="
            + describe_bounds(p_obstacle_shape.bounding_box()));
  }

  private static int obstacle_id(SearchTreeObject p_object) {
    return p_object instanceof Item ? ((Item) p_object).get_id_no() : -1;
  }

  private static String obstacle_nets(SearchTreeObject p_object) {
    return p_object instanceof Item
        ? java.util.Arrays.toString(((Item) p_object).net_no_arr)
        : "[]";
  }
}