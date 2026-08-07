package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;

public class LocateFoundConnectionAlgo45Degree extends LocateFoundConnectionAlgo {

  /** Creates a new instance of LocateFoundConnectionAlgo45Degree */
  public LocateFoundConnectionAlgo45Degree(
      MazeSearchAlgo.Result p_maze_search_result,
      AutorouteControl p_ctrl,
      ShapeSearchTree p_search_tree,
      AngleRestriction p_angle_restriction,
      SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs) {
    super(
        p_maze_search_result,
        p_ctrl,
        p_search_tree,
        p_angle_restriction,
        p_ripped_item_list,
        p_ripup_costs);
  }

  private static FloatPoint round_to_integer(FloatPoint p_point) {
    return p_point.round().to_float();
  }

  /**
   * Calculates, if the next 45-degree angle should be horizontal first when coming from
   * p_from_point on p_from_door.
   */
  private static boolean calc_horizontal_first_from_door(
      ExpandableObject p_from_door, FloatPoint p_from_point, FloatPoint p_to_point) {
    TileShape doorShape = p_from_door.get_shape();
    IntBox fromDoorBox = doorShape.bounding_box();
    if (p_from_door.get_dimension() != 1) {
      return fromDoorBox.height() >= fromDoorBox.width();
    }

    FloatLine doorLineSegment = doorShape.diagonal_corner_segment();
    FloatPoint leftCorner;
    FloatPoint rightCorner;
    if (doorLineSegment.a.x < doorLineSegment.b.x
        || doorLineSegment.a.x == doorLineSegment.b.x
            && doorLineSegment.a.y <= doorLineSegment.b.y) {
      leftCorner = doorLineSegment.a;
      rightCorner = doorLineSegment.b;
    } else {
      leftCorner = doorLineSegment.b;
      rightCorner = doorLineSegment.a;
    }
    double doorDx = rightCorner.x - leftCorner.x;
    double doorDy = rightCorner.y - leftCorner.y;
    double absDoorDy = Math.abs(doorDy);
    double doorMaxWidth = Math.max(doorDx, absDoorDy);
    boolean result;
    double doorHalfMaxWidth = 0.5 * doorMaxWidth;
    if (fromDoorBox.width() <= doorHalfMaxWidth) {
      // door is about vertical
      result = true;
    } else if (fromDoorBox.height() <= doorHalfMaxWidth) {
      // door is about horizontal
      result = false;
    } else {
      double dx = p_to_point.x - p_from_point.x;
      double dy = p_to_point.y - p_from_point.y;
      if (leftCorner.y < rightCorner.y) {
        // door is about right diagonal
        if (Signum.of(dx) == Signum.of(dy)) {
          result = Math.abs(dx) > Math.abs(dy);
        } else {
          result = Math.abs(dx) < Math.abs(dy);
        }

      } else {
        // door is about left diagonal
        if (Signum.of(dx) == Signum.of(dy)) {
          result = Math.abs(dx) < Math.abs(dy);
        } else {
          result = Math.abs(dx) > Math.abs(dy);
        }
      }
    }
    return result;
  }

  @Override
  protected Collection<FloatPoint> calculate_next_trace_corners() {
    Collection<FloatPoint> result = new LinkedList<>();

    if (this.currentToDoorIndex > this.currentTargetDoorIndex) {
      if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
        FRLogger.trace(
            "compare_trace_next_corners_raw net="
                + this.ctrl.netNo
                + ", mode=45, branch=NO_MORE_DOORS"
                + ", layer="
                + this.currentTraceLayer
                + ", from_door="
                + this.currentFromDoorIndex
                + ", to_door="
                + this.currentToDoorIndex
                + ", target_door="
                + this.currentTargetDoorIndex
                + ", result_size="
                + result.size());
      }
      return result;
    }

    BacktrackElement currFromInfo = this.backtrackArray[this.currentToDoorIndex - 1];

    if (currFromInfo.nextRoom == null) {
      FRLogger.warn(
          "LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: nextRoom is null");
      return result;
    }

    TileShape roomShape = currFromInfo.nextRoom.get_shape();

    int traceHalfwidth = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    int traceHalfwidthAdd =
        traceHalfwidth
            + AutorouteEngine
                .TRACE_WIDTH_TOLERANCE; // add some tolerance for free space expansion rooms.
    int shrinkOffset;
    if (currFromInfo.nextRoom instanceof ObstacleExpansionRoom) {

      shrinkOffset = traceHalfwidth;
    } else {
      shrinkOffset = traceHalfwidthAdd;
    }

    TileShape shrinkedRoomShape = (TileShape) roomShape.offset(-shrinkOffset);
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      FRLogger.trace(
          "compare_trace_room_shrink_raw net="
              + this.ctrl.netNo
              + ", mode=45"
              + ", layer="
              + this.currentTraceLayer
              + ", from_door="
              + this.currentFromDoorIndex
              + ", to_door="
              + this.currentToDoorIndex
              + ", target_door="
              + this.currentTargetDoorIndex
              + ", next_room_type="
              + currFromInfo.nextRoom.getClass().getSimpleName()
              + ", shrinkOffset="
              + shrinkOffset
              + ", room_empty="
              + roomShape.is_empty()
              + ", shrinked_empty="
              + shrinkedRoomShape.is_empty()
              + ", current_from="
              + this.currentFromPoint);
    }
    if (!shrinkedRoomShape.is_empty()) {
      // enter the shrunk room shape by a 45-degree angle first
      FloatPoint nearestRoomPoint = shrinkedRoomShape.nearest_point_approx(this.currentFromPoint);
      boolean horizontalFirst =
          calc_horizontal_first_from_door(
              currFromInfo.door, this.currentFromPoint, nearestRoomPoint);
      nearestRoomPoint = round_to_integer(nearestRoomPoint);
      result.add(
          calculate_additional_corner(
              this.currentFromPoint, nearestRoomPoint, horizontalFirst, this.angleRestriction));
      result.add(nearestRoomPoint);
      this.currentFromPoint = nearestRoomPoint;
    } else {
      shrinkedRoomShape = roomShape;
    }

    if (this.currentToDoorIndex == this.currentTargetDoorIndex) {
      FloatPoint nearestPoint = this.currentTargetShape.nearest_point_approx(this.currentFromPoint);
      nearestPoint = round_to_integer(nearestPoint);
      FloatPoint addCorner =
          calculate_additional_corner(
              this.currentFromPoint, nearestPoint, true, this.angleRestriction);
      if (!shrinkedRoomShape.contains(addCorner)) {
        addCorner =
            calculate_additional_corner(
                this.currentFromPoint, nearestPoint, false, this.angleRestriction);
      }
      result.add(addCorner);
      result.add(nearestPoint);
      ++this.currentToDoorIndex;
      if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
        FRLogger.trace(
            "compare_trace_next_corners_raw net="
                + this.ctrl.netNo
                + ", mode=45, branch=TARGET_DOOR"
                + ", layer="
                + this.currentTraceLayer
                + ", from_door="
                + this.currentFromDoorIndex
                + ", to_door="
                + this.currentToDoorIndex
                + ", target_door="
                + this.currentTargetDoorIndex
                + ", result_size="
                + result.size()
                + ", nearestPoint="
                + nearestPoint
                + ", addCorner="
                + addCorner);
      }
      return result;
    }

    BacktrackElement currToInfo = this.backtrackArray[this.currentToDoorIndex];
    if (!(currToInfo.door instanceof ExpansionDoor curr_to_door)) {
      FRLogger.warn(
          "LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: ExpansionDoor expected");
      return result;
    }

    FloatPoint nearestToDoorPoint;
    if (curr_to_door.dimension == 2) {
      // May not happen in free angle routing mode because then corners are cut off.
      TileShape toDoorShape = curr_to_door.get_shape();

      TileShape shrinkedToDoorShape = (TileShape) toDoorShape.shrink(shrinkOffset);
      nearestToDoorPoint = shrinkedToDoorShape.nearest_point_approx(this.currentFromPoint);
      nearestToDoorPoint = round_to_integer(nearestToDoorPoint);
    } else {
      FloatLine[] lineSections = curr_to_door.get_section_segments(traceHalfwidth);
      if (currToInfo.sectionNoOfDoor >= lineSections.length) {
        FRLogger.warn(
            "LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: lineSections inconsistent");
        return result;
      }
      FloatLine currLineSection = lineSections[currToInfo.sectionNoOfDoor];
      nearestToDoorPoint = currLineSection.nearest_segment_point(this.currentFromPoint);

      boolean nearestToDoorPointOk = true;
      if (currToInfo.nextRoom != null) {
        Simplex nextRoomShape = currToInfo.nextRoom.get_shape().to_Simplex();
        // with IntBox or IntOctagon the next calculation will not work, because they have
        // border lines of length 0.
        FloatPoint[] nearestPoints =
            nextRoomShape.nearest_border_points_approx(nearestToDoorPoint, 2);
        if (nearestPoints.length >= 2) {
          nearestToDoorPointOk = nearestPoints[1].distance(nearestToDoorPoint) >= traceHalfwidthAdd;
        }
      }
      if (!nearestToDoorPointOk) {
        // may be the room has an acute (45 degree) angle at a corner of the door
        nearestToDoorPoint = currLineSection.a.middle_point(currLineSection.b);
      }
    }
    nearestToDoorPoint = round_to_integer(nearestToDoorPoint);
    boolean horizontalFirst =
        calc_horizontal_first_to_door(currToInfo.door, this.currentFromPoint, nearestToDoorPoint);
    result.add(
        calculate_additional_corner(
            this.currentFromPoint, nearestToDoorPoint, horizontalFirst, this.angleRestriction));
    result.add(nearestToDoorPoint);
    ++this.currentToDoorIndex;
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      FRLogger.trace(
          "compare_trace_next_corners_raw net="
              + this.ctrl.netNo
              + ", mode=45, branch=EXPANSION_DOOR"
              + ", layer="
              + this.currentTraceLayer
              + ", from_door="
              + this.currentFromDoorIndex
              + ", to_door="
              + this.currentToDoorIndex
              + ", target_door="
              + this.currentTargetDoorIndex
              + ", result_size="
              + result.size()
              + ", nearestToDoorPoint="
              + nearestToDoorPoint
              + ", horizontalFirst="
              + horizontalFirst);
    }
    return result;
  }

  /**
   * Calculates, if the 45-degree angle to the next door shape should be horizontal first when
   * coming from p_from_point.
   */
  private boolean calc_horizontal_first_to_door(
      ExpandableObject p_to_door, FloatPoint p_from_point, FloatPoint p_to_point) {
    TileShape doorShape = p_to_door.get_shape();
    IntBox fromDoorBox = doorShape.bounding_box();
    if (p_to_door.get_dimension() != 1) {
      return fromDoorBox.height() <= fromDoorBox.width();
    }
    FloatLine doorLineSegment = doorShape.diagonal_corner_segment();
    FloatPoint leftCorner;
    FloatPoint rightCorner;
    if (doorLineSegment.a.x < doorLineSegment.b.x
        || doorLineSegment.a.x == doorLineSegment.b.x
            && doorLineSegment.a.y <= doorLineSegment.b.y) {
      leftCorner = doorLineSegment.a;
      rightCorner = doorLineSegment.b;
    } else {
      leftCorner = doorLineSegment.b;
      rightCorner = doorLineSegment.a;
    }
    double doorDx = rightCorner.x - leftCorner.x;
    double doorDy = rightCorner.y - leftCorner.y;
    double absDoorDy = Math.abs(doorDy);
    double doorMaxWidth = Math.max(doorDx, absDoorDy);
    boolean result;
    double doorHalfMaxWidth = 0.5 * doorMaxWidth;
    if (fromDoorBox.width() <= doorHalfMaxWidth) {
      // door is about vertical
      result = false;
    } else if (fromDoorBox.height() <= doorHalfMaxWidth) {
      // door is about horizontal
      result = true;
    } else {
      double dx = p_to_point.x - p_from_point.x;
      double dy = p_to_point.y - p_from_point.y;
      if (leftCorner.y < rightCorner.y) {
        // door is about right diagonal
        if (Signum.of(dx) == Signum.of(dy)) {
          result = Math.abs(dx) < Math.abs(dy);
        } else {
          result = Math.abs(dx) > Math.abs(dy);
        }

      } else {
        // door is about left diagonal
        if (Signum.of(dx) == Signum.of(dy)) {
          result = Math.abs(dx) > Math.abs(dy);
        } else {
          result = Math.abs(dx) < Math.abs(dy);
        }
      }
    }
    return result;
  }
}
