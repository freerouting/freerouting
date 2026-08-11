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
  public static boolean checkShoveTraceLine(
      MazeListElement listElement,
      ObstacleExpansionRoom obstacleRoom,
      RoutingBoard board,
      AutorouteControl ctrl,
      boolean shoveToTheLeft,
      Collection<DoorSection> toDoorList) {
    if (!(listElement.door instanceof ExpansionDoor fromDoor)) {
      return true;
    }
    if (!(obstacleRoom.getItem() instanceof PolylineTrace obstacleTrace)) {
      return true;
    }
    int traceLayer = obstacleRoom.getLayer();
    // only traces with the same halfwidth and the same clearance class can be
    // shoved.
    if (obstacleTrace.getHalfWidth() != ctrl.traceHalfWidth[traceLayer]
        || obstacleTrace.clearanceClassNo() != ctrl.traceClearanceClassNo) {
      return true;
    }
    double compensatedTraceHalfWidth = ctrl.compensatedTraceHalfWidth[traceLayer];
    TileShape fromDoorShape = fromDoor.getShape();
    if (fromDoorShape.maxWidth() < 2 * compensatedTraceHalfWidth) {
      return true;
    }
    int traceCornerNo = obstacleRoom.getIndexInItem();

    Polyline tracePolyline = obstacleTrace.polyline();

    // Check if traceCornerNo allows access to indices up to traceCornerNo + 2
    // (needed at lines 133-134 and 136-140)
    // Stale indices can occur when traces are modified during routing (pull-tight,
    // shoving, etc.)
    if (traceCornerNo < 0 || traceCornerNo >= tracePolyline.arr.length - 2) {
      return false;
    }
    final Collection<ExpansionDoor> roomDoors = obstacleRoom.getDoors();
    // The side of the trace line seen from the doors to expand.
    // Used to determine, if a door is on the right side to put it into the
    // p_door_list.
    LineSegment shoveLineSegment;
    if (fromDoor.dimension == 2) {
      // shove from a link door into the direction of the other link door.
      CompleteExpansionRoom otherRoom = fromDoor.otherRoom(obstacleRoom);
      if (!(otherRoom instanceof ObstacleExpansionRoom)) {
        return false;
      }
      if (!endPointsMatching(obstacleTrace, ((ObstacleExpansionRoom) otherRoom).getItem())) {
        return false;
      }
      FloatPoint doorCenter = fromDoorShape.centreOfGravity();
      FloatPoint corner1 = tracePolyline.cornerApprox(traceCornerNo);
      FloatPoint corner2 = tracePolyline.cornerApprox(traceCornerNo + 1);
      if (corner1.distanceSquare(corner2) < 1) {
        // shoveLineSegment may be reduced to a point
        return false;
      }
      boolean shoveIntoDirectionOfTraceStart =
          doorCenter.distanceSquare(corner2) < doorCenter.distanceSquare(corner1);
      shoveLineSegment = new LineSegment(tracePolyline, traceCornerNo + 1);
      if (shoveIntoDirectionOfTraceStart) {

        // shove from the endpoint to the start point of the line segment
        shoveLineSegment = shoveLineSegment.opposite();
      }
    } else {
      CompleteExpansionRoom fromRoom = fromDoor.otherRoom(obstacleRoom);
      FloatPoint fromPoint = fromRoom.getShape().centreOfGravity();
      Line shoveTraceLine = tracePolyline.arr[traceCornerNo + 1];
      FloatLine doorLineSegment = fromDoorShape.diagonalCornerSegment();
      Side sideOfTraceLine = shoveTraceLine.sideOf(doorLineSegment.a, 0);

      FloatLine polarLineSegment = fromDoorShape.polarLineSegment(fromPoint);

      boolean doorLineSwapped =
          polarLineSegment.b.distanceSquare(doorLineSegment.a)
              < polarLineSegment.a.distanceSquare(doorLineSegment.a);

      boolean sectionOk;
      // shove only from the right most section to the right or from the left most
      // section to the
      // left.

      double shapeEntryCheckDistance = compensatedTraceHalfWidth + 5;
      double checkDistSquare = shapeEntryCheckDistance * shapeEntryCheckDistance;

      if (shoveToTheLeft && !doorLineSwapped || !shoveToTheLeft && doorLineSwapped) {
        sectionOk =
            listElement.sectionNoOfDoor == listElement.door.mazeSearchElementCount() - 1
                && (listElement.shapeEntry.a.distanceSquare(doorLineSegment.b) <= checkDistSquare
                    || listElement.shapeEntry.b.distanceSquare(doorLineSegment.b)
                        <= checkDistSquare);
      } else {
        sectionOk =
            listElement.sectionNoOfDoor == 0
                && (listElement.shapeEntry.a.distanceSquare(doorLineSegment.a) <= checkDistSquare
                    || listElement.shapeEntry.b.distanceSquare(doorLineSegment.a)
                        <= checkDistSquare);
      }
      if (!sectionOk) {
        return false;
      }

      // create the line segment for shoving

      FloatLine shrinkedLineSegment = polarLineSegment.shrinkSegment(compensatedTraceHalfWidth);
      Direction perpendicularDirection = shoveTraceLine.direction().turn45Degree(2);
      if (sideOfTraceLine == Side.ON_THE_LEFT) {
        if (shoveToTheLeft) {
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
        if (shoveToTheLeft) {
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
    int traceHalfWidth = ctrl.traceHalfWidth[traceLayer];
    int[] netNoArr = new int[1];
    netNoArr[0] = ctrl.netNo;

    double shoveWidth =
        board.checkTraceSegment(
            shoveLineSegment,
            traceLayer,
            netNoArr,
            traceHalfWidth,
            ctrl.traceClearanceClassNo,
            true);
    boolean segmentShortened = false;
    if (shoveWidth < Integer.MAX_VALUE) {
      // shorten shoveLineSegment
      shoveWidth = shoveWidth - 1;
      if (shoveWidth <= 0) {
        return true;
      }
      shoveLineSegment = shoveLineSegment.changeLengthApprox(shoveWidth);
      segmentShortened = true;
    }

    FloatPoint fromCorner = shoveLineSegment.startPointApprox();
    FloatPoint toCorner = shoveLineSegment.endPointApprox();
    boolean segmentIstPoint = fromCorner.distanceSquare(toCorner) < 0.1;

    if (!segmentIstPoint) {
      shoveWidth =
          ShoveTraceAlgo.check(
              board,
              shoveLineSegment,
              shoveToTheLeft,
              traceLayer,
              netNoArr,
              traceHalfWidth,
              ctrl.traceClearanceClassNo,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth);

      if (shoveWidth <= 0) {
        return true;
      }
    }

    // Put the doors on this side of the room into p_to_door_list with
    if (segmentShortened) {
      shoveWidth = Math.min(shoveWidth, fromCorner.distance(toCorner));
    }

    Line shoveLine = shoveLineSegment.getLine();

    // From_door_compare_distance is used to check, that a door is between fromDoor
    // and the end
    // point
    // of the shove line.
    double fromDoorCompareDistance;
    if (fromDoor.dimension == 2 || segmentIstPoint) {
      fromDoorCompareDistance = Double.MAX_VALUE;
    } else {
      fromDoorCompareDistance = toCorner.distanceSquare(fromDoorShape.cornerApprox(0));
    }

    for (ExpansionDoor currDoor : roomDoors) {
      if (currDoor == fromDoor) {
        continue;
      }
      if (currDoor.firstRoom instanceof ObstacleExpansionRoom room
          && currDoor.secondRoom instanceof ObstacleExpansionRoom room1) {
        Item firstRoomItem = room.getItem();
        Item secondRoomItem = room1.getItem();
        if (firstRoomItem != secondRoomItem) {
          // there may be topological problems at a trace fork
          continue;
        }
      }
      TileShape currDoorShape = currDoor.getShape();
      if (currDoor.dimension == 2 && shoveWidth >= Integer.MAX_VALUE) {
        boolean addLinkDoor = currDoorShape.contains(toCorner);

        if (addLinkDoor) {
          FloatLine[] lineSections = currDoor.getSectionSegments(compensatedTraceHalfWidth);
          toDoorList.add(new DoorSection(currDoor, 0, lineSections[0]));
        }
      } else if (!segmentIstPoint) {
        // now currDoor is 1-dimensional

        // check, that currDoor is on the same borderLine as p_from_door.
        FloatLine currDoorSegment = currDoorShape.diagonalCornerSegment();
        if (currDoorSegment == null) {
          FRLogger.trace("MazeShoveTraceAlgo.check_shove_trace_line: door shape is empty");
          continue;
        }
        Side startCornerSideOfTraceLine = shoveLine.sideOf(currDoorSegment.a, 0);
        Side endCornerSideOfTraceLine = shoveLine.sideOf(currDoorSegment.b, 0);
        if (shoveToTheLeft) {
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
        FloatLine currDoorLine = currDoorShape.polarLineSegment(fromCorner);
        FloatPoint currDoorNearestCorner;
        if (currDoorLine.a.distanceSquare(fromCorner)
            <= currDoorLine.b.distanceSquare(fromCorner)) {
          currDoorNearestCorner = currDoorLine.a;
        } else {
          currDoorNearestCorner = currDoorLine.b;
        }
        if (toCorner.distanceSquare(currDoorNearestCorner) >= fromDoorCompareDistance) {
          // currDoor is not located into the direction of toCorner.
          continue;
        }
        FloatPoint currDoorProjection = currDoorNearestCorner.projectionApprox(shoveLine);

        if (currDoorProjection.distance(fromCorner) + compensatedTraceHalfWidth <= shoveWidth) {
          FloatLine[] lineSections = currDoor.getSectionSegments(compensatedTraceHalfWidth);
          for (int i = 0; i < lineSections.length; i++) {
            FloatLine currLineSection = lineSections[i];
            FloatPoint currSectionNearestCorner;
            if (currLineSection.a.distanceSquare(fromCorner)
                <= currLineSection.b.distanceSquare(fromCorner)) {
              currSectionNearestCorner = currLineSection.a;
            } else {
              currSectionNearestCorner = currLineSection.b;
            }
            FloatPoint currSectionProjection = currSectionNearestCorner.projectionApprox(shoveLine);
            if (currSectionProjection.distance(fromCorner) <= shoveWidth) {
              toDoorList.add(new DoorSection(currDoor, i, currLineSection));
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
  private static boolean endPointsMatching(PolylineTrace trace, Item fromItem) {
    if (fromItem == trace) {
      return true;
    }
    if (!trace.sharesNet(fromItem)) {
      return false;
    }
    boolean pointsMatching;
    if (fromItem instanceof DrillItem item) {
      Point fromCenter = item.getCenter();
      pointsMatching =
          fromCenter.equals(trace.firstCorner()) || fromCenter.equals(trace.lastCorner());
    } else if (fromItem instanceof PolylineTrace fromTrace) {
      pointsMatching =
          trace.firstCorner().equals(fromTrace.firstCorner())
              || trace.firstCorner().equals(fromTrace.lastCorner())
              || trace.lastCorner().equals(fromTrace.firstCorner())
              || trace.lastCorner().equals(fromTrace.lastCorner());
    } else {
      pointsMatching = false;
    }
    return pointsMatching;
  }

  /** DoorSection. */
  public static class DoorSection {

    final ExpansionDoor door;
    final int sectionNo;
    final FloatLine sectionLine;

    DoorSection(ExpansionDoor door, int sectionNo, FloatLine sectionLine) {
      this.door = door;
      this.sectionNo = sectionNo;
      this.sectionLine = sectionLine;
    }
  }
}
