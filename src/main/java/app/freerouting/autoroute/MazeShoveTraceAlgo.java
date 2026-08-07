package app.freerouting.autoroute;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.ShoveTraceAlgo;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;

/** Auxiliary functions used in MazeSearchAlgo. */
public final class MazeShoveTraceAlgo {

  private MazeShoveTraceAlgo() {}

  /**
   * Returns false, if the algorithm did not succeed and trying to shove from another door section
   * may be more successful.
   */
  public static boolean check_shove_trace_line(
      MazeListElement p_list_element,
      ObstacleExpansionRoom p_obstacle_room,
      RoutingBoard p_board,
      AutorouteControl p_ctrl,
      boolean p_shove_to_the_left,
      Collection<DoorSection> p_to_door_list) {
    if (!(p_list_element.door instanceof ExpansionDoor from_door)) {
      return true;
    }
    if (!(p_obstacle_room.get_item() instanceof PolylineTrace obstacle_trace)) {
      return true;
    }
    int traceLayer = p_obstacle_room.get_layer();
    // only traces with the same halfwidth and the same clearance class can be
    // shoved.
    if (obstacle_trace.get_half_width() != p_ctrl.traceHalfWidth[traceLayer]
        || obstacle_trace.clearance_class_no() != p_ctrl.traceClearanceClassNo) {
      return true;
    }
    double compensatedTraceHalfWidth = p_ctrl.compensatedTraceHalfWidth[traceLayer];
    TileShape fromDoorShape = from_door.get_shape();
    if (fromDoorShape.max_width() < 2 * compensatedTraceHalfWidth) {
      return true;
    }
    int traceCornerNo = p_obstacle_room.get_index_in_item();

    Polyline tracePolyline = obstacle_trace.polyline();

    // Check if traceCornerNo allows access to indices up to traceCornerNo + 2
    // (needed at lines 133-134 and 136-140)
    // Stale indices can occur when traces are modified during routing (pull-tight,
    // shoving, etc.)
    if (traceCornerNo < 0 || traceCornerNo >= tracePolyline.arr.length - 2) {
      return false;
    }
    Collection<ExpansionDoor> roomDoors = p_obstacle_room.get_doors();
    // The side of the trace line seen from the doors to expand.
    // Used to determine, if a door is on the right side to put it into the
    // p_door_list.
    LineSegment shoveLineSegment;
    if (from_door.dimension == 2) {
      // shove from a link door into the direction of the other link door.
      CompleteExpansionRoom otherRoom = from_door.other_room(p_obstacle_room);
      if (!(otherRoom instanceof ObstacleExpansionRoom)) {
        return false;
      }
      if (!end_points_matching(obstacle_trace, ((ObstacleExpansionRoom) otherRoom).get_item())) {
        return false;
      }
      FloatPoint doorCenter = fromDoorShape.centre_of_gravity();
      FloatPoint corner1 = tracePolyline.corner_approx(traceCornerNo);
      FloatPoint corner2 = tracePolyline.corner_approx(traceCornerNo + 1);
      if (corner1.distance_square(corner2) < 1) {
        // shoveLineSegment may be reduced to a point
        return false;
      }
      boolean shoveIntoDirectionOfTraceStart =
          doorCenter.distance_square(corner2) < doorCenter.distance_square(corner1);
      shoveLineSegment = new LineSegment(tracePolyline, traceCornerNo + 1);
      if (shoveIntoDirectionOfTraceStart) {

        // shove from the endpoint to the start point of the line segment
        shoveLineSegment = shoveLineSegment.opposite();
      }
    } else {
      CompleteExpansionRoom fromRoom = from_door.other_room(p_obstacle_room);
      FloatPoint fromPoint = fromRoom.get_shape().centre_of_gravity();
      Line shoveTraceLine = tracePolyline.arr[traceCornerNo + 1];
      FloatLine doorLineSegment = fromDoorShape.diagonal_corner_segment();
      Side sideOfTraceLine = shoveTraceLine.side_of(doorLineSegment.a, 0);

      FloatLine polarLineSegment = fromDoorShape.polar_line_segment(fromPoint);

      boolean doorLineSwapped =
          polarLineSegment.b.distance_square(doorLineSegment.a)
              < polarLineSegment.a.distance_square(doorLineSegment.a);

      boolean sectionOk;
      // shove only from the right most section to the right or from the left most
      // section to the
      // left.

      double shapeEntryCheckDistance = compensatedTraceHalfWidth + 5;
      double checkDistSquare = shapeEntryCheckDistance * shapeEntryCheckDistance;

      if (p_shove_to_the_left && !doorLineSwapped || !p_shove_to_the_left && doorLineSwapped) {
        sectionOk =
            p_list_element.sectionNoOfDoor == p_list_element.door.maze_search_element_count() - 1
                && (p_list_element.shapeEntry.a.distance_square(doorLineSegment.b)
                        <= checkDistSquare
                    || p_list_element.shapeEntry.b.distance_square(doorLineSegment.b)
                        <= checkDistSquare);
      } else {
        sectionOk =
            p_list_element.sectionNoOfDoor == 0
                && (p_list_element.shapeEntry.a.distance_square(doorLineSegment.a)
                        <= checkDistSquare
                    || p_list_element.shapeEntry.b.distance_square(doorLineSegment.a)
                        <= checkDistSquare);
      }
      if (!sectionOk) {
        return false;
      }

      // create the line segment for shoving

      FloatLine shrinkedLineSegment = polarLineSegment.shrink_segment(compensatedTraceHalfWidth);
      Direction perpendicularDirection = shoveTraceLine.direction().turn_45_degree(2);
      if (sideOfTraceLine == Side.ON_THE_LEFT) {
        if (p_shove_to_the_left) {
          Line startClosingLine = new Line(shrinkedLineSegment.b.round(), perpendicularDirection);
          shoveLineSegment =
              new LineSegment(
                  startClosingLine,
                  tracePolyline.arr[traceCornerNo + 1],
                  tracePolyline.arr[traceCornerNo + 2]);
        } else {
          Line startClosingLine = new Line(shrinkedLineSegment.a.round(), perpendicularDirection);
          shoveLineSegment =
              new LineSegment(
                  startClosingLine,
                  tracePolyline.arr[traceCornerNo + 1].opposite(),
                  tracePolyline.arr[traceCornerNo].opposite());
        }
      } else {
        if (p_shove_to_the_left) {
          Line startClosingLine = new Line(shrinkedLineSegment.b.round(), perpendicularDirection);
          shoveLineSegment =
              new LineSegment(
                  startClosingLine,
                  tracePolyline.arr[traceCornerNo + 1].opposite(),
                  tracePolyline.arr[traceCornerNo].opposite());
        } else {
          Line startClosingLine = new Line(shrinkedLineSegment.a.round(), perpendicularDirection);
          shoveLineSegment =
              new LineSegment(
                  startClosingLine,
                  tracePolyline.arr[traceCornerNo + 1],
                  tracePolyline.arr[traceCornerNo + 2]);
        }
      }
    }
    int traceHalfWidth = p_ctrl.traceHalfWidth[traceLayer];
    int[] netNoArr = new int[1];
    netNoArr[0] = p_ctrl.netNo;

    double shoveWidth =
        p_board.check_trace_segment(
            shoveLineSegment,
            traceLayer,
            netNoArr,
            traceHalfWidth,
            p_ctrl.traceClearanceClassNo,
            true);
    boolean segmentShortened = false;
    if (shoveWidth < Integer.MAX_VALUE) {
      // shorten shoveLineSegment
      shoveWidth = shoveWidth - 1;
      if (shoveWidth <= 0) {
        return true;
      }
      shoveLineSegment = shoveLineSegment.change_length_approx(shoveWidth);
      segmentShortened = true;
    }

    FloatPoint fromCorner = shoveLineSegment.start_point_approx();
    FloatPoint toCorner = shoveLineSegment.end_point_approx();
    boolean segmentIstPoint = fromCorner.distance_square(toCorner) < 0.1;

    if (!segmentIstPoint) {
      shoveWidth =
          ShoveTraceAlgo.check(
              p_board,
              shoveLineSegment,
              p_shove_to_the_left,
              traceLayer,
              netNoArr,
              traceHalfWidth,
              p_ctrl.traceClearanceClassNo,
              p_ctrl.maxShoveTraceRecursionDepth,
              p_ctrl.maxShoveViaRecursionDepth);

      if (shoveWidth <= 0) {
        return true;
      }
    }

    // Put the doors on this side of the room into p_to_door_list with
    if (segmentShortened) {
      shoveWidth = Math.min(shoveWidth, fromCorner.distance(toCorner));
    }

    Line shoveLine = shoveLineSegment.get_line();

    // From_door_compare_distance is used to check, that a door is between from_door
    // and the end
    // point
    // of the shove line.
    double fromDoorCompareDistance;
    if (from_door.dimension == 2 || segmentIstPoint) {
      fromDoorCompareDistance = Double.MAX_VALUE;
    } else {
      fromDoorCompareDistance = toCorner.distance_square(fromDoorShape.corner_approx(0));
    }

    for (ExpansionDoor currDoor : roomDoors) {
      if (currDoor == from_door) {
        continue;
      }
      if (currDoor.firstRoom instanceof ObstacleExpansionRoom room
          && currDoor.secondRoom instanceof ObstacleExpansionRoom room1) {
        Item firstRoomItem = room.get_item();
        Item secondRoomItem = room1.get_item();
        if (firstRoomItem != secondRoomItem) {
          // there may be topological problems at a trace fork
          continue;
        }
      }
      TileShape currDoorShape = currDoor.get_shape();
      if (currDoor.dimension == 2 && shoveWidth >= Integer.MAX_VALUE) {
        boolean addLinkDoor = currDoorShape.contains(toCorner);

        if (addLinkDoor) {
          FloatLine[] lineSections = currDoor.get_section_segments(compensatedTraceHalfWidth);
          p_to_door_list.add(new DoorSection(currDoor, 0, lineSections[0]));
        }
      } else if (!segmentIstPoint) {
        // now currDoor is 1-dimensional

        // check, that currDoor is on the same borderLine as p_from_door.
        FloatLine currDoorSegment = currDoorShape.diagonal_corner_segment();
        if (currDoorSegment == null) {
          FRLogger.trace("MazeShoveTraceAlgo.check_shove_trace_line: door shape is empty");
          continue;
        }
        Side startCornerSideOfTraceLine = shoveLine.side_of(currDoorSegment.a, 0);
        Side endCornerSideOfTraceLine = shoveLine.side_of(currDoorSegment.b, 0);
        if (p_shove_to_the_left) {
          if (startCornerSideOfTraceLine != Side.ON_THE_LEFT
              || endCornerSideOfTraceLine != Side.ON_THE_LEFT) {
            continue;
          }
        } else {
          if (startCornerSideOfTraceLine != Side.ON_THE_RIGHT
              || endCornerSideOfTraceLine != Side.ON_THE_RIGHT) {
            continue;
          }
        }
        FloatLine currDoorLine = currDoorShape.polar_line_segment(fromCorner);
        FloatPoint currDoorNearestCorner;
        if (currDoorLine.a.distance_square(fromCorner)
            <= currDoorLine.b.distance_square(fromCorner)) {
          currDoorNearestCorner = currDoorLine.a;
        } else {
          currDoorNearestCorner = currDoorLine.b;
        }
        if (toCorner.distance_square(currDoorNearestCorner) >= fromDoorCompareDistance) {
          // currDoor is not located into the direction of toCorner.
          continue;
        }
        FloatPoint currDoorProjection = currDoorNearestCorner.projection_approx(shoveLine);

        if (currDoorProjection.distance(fromCorner) + compensatedTraceHalfWidth <= shoveWidth) {
          FloatLine[] lineSections = currDoor.get_section_segments(compensatedTraceHalfWidth);
          for (int i = 0; i < lineSections.length; i++) {
            FloatLine currLineSection = lineSections[i];
            FloatPoint currSectionNearestCorner;
            if (currLineSection.a.distance_square(fromCorner)
                <= currLineSection.b.distance_square(fromCorner)) {
              currSectionNearestCorner = currLineSection.a;
            } else {
              currSectionNearestCorner = currLineSection.b;
            }
            FloatPoint currSectionProjection =
                currSectionNearestCorner.projection_approx(shoveLine);
            if (currSectionProjection.distance(fromCorner) <= shoveWidth) {
              p_to_door_list.add(new DoorSection(currDoor, i, currLineSection));
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Check if the endpoints of p_trace and p_from_item are matching, so that the shove can continue
   * through a link door.
   */
  private static boolean end_points_matching(PolylineTrace p_trace, Item p_from_item) {
    if (p_from_item == p_trace) {
      return true;
    }
    if (!p_trace.shares_net(p_from_item)) {
      return false;
    }
    boolean pointsMatching;
    if (p_from_item instanceof DrillItem item) {
      Point fromCenter = item.get_center();
      pointsMatching =
          fromCenter.equals(p_trace.first_corner()) || fromCenter.equals(p_trace.last_corner());
    } else if (p_from_item instanceof PolylineTrace from_trace) {
      pointsMatching =
          p_trace.first_corner().equals(from_trace.first_corner())
              || p_trace.first_corner().equals(from_trace.last_corner())
              || p_trace.last_corner().equals(from_trace.first_corner())
              || p_trace.last_corner().equals(from_trace.last_corner());
    } else {
      pointsMatching = false;
    }
    return pointsMatching;
  }

  public static class DoorSection {

    final ExpansionDoor door;
    final int sectionNo;
    final FloatLine sectionLine;

    DoorSection(ExpansionDoor p_door, int p_section_no, FloatLine p_section_line) {
      door = p_door;
      sectionNo = p_section_no;
      sectionLine = p_section_line;
    }
  }
}
