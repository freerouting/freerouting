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
      MazeListElement pListElement,
      ObstacleExpansionRoom pObstacleRoom,
      RoutingBoard pBoard,
      AutorouteControl pCtrl,
      boolean pShoveToTheLeft,
      Collection<DoorSection> pToDoorList) {
    if (!(pListElement.door instanceof ExpansionDoor from_door)) {
      return true;
    }
    if (!(pObstacleRoom.getItem() instanceof PolylineTrace obstacle_trace)) {
      return true;
    }
    int traceLayer = pObstacleRoom.getLayer();
    // only traces with the same halfwidth and the same clearance class can be
    // shoved.
    if (obstacle_trace.getHalfWidth() != pCtrl.traceHalfWidth[traceLayer]
        || obstacle_trace.clearanceClassNo() != pCtrl.traceClearanceClassNo) {
      return true;
    }
    double compensatedTraceHalfWidth = pCtrl.compensatedTraceHalfWidth[traceLayer];
    TileShape fromDoorShape = from_door.getShape();
    if (fromDoorShape.maxWidth() < 2 * compensatedTraceHalfWidth) {
      return true;
    }
    int traceCornerNo = pObstacleRoom.getIndexInItem();

    Polyline tracePolyline = obstacle_trace.polyline();

    // Check if traceCornerNo allows access to indices up to traceCornerNo + 2
    // (needed at lines 133-134 and 136-140)
    // Stale indices can occur when traces are modified during routing (pull-tight,
    // shoving, etc.)
    if (traceCornerNo < 0 || traceCornerNo >= tracePolyline.arr.length - 2) {
      return false;
    }
    Collection<ExpansionDoor> roomDoors = pObstacleRoom.getDoors();
    // The side of the trace line seen from the doors to expand.
    // Used to determine, if a door is on the right side to put it into the
    // p_door_list.
    LineSegment shoveLineSegment;
    if (from_door.dimension == 2) {
      // shove from a link door into the direction of the other link door.
      CompleteExpansionRoom otherRoom = from_door.otherRoom(pObstacleRoom);
      if (!(otherRoom instanceof ObstacleExpansionRoom)) {
        return false;
      }
      if (!endPointsMatching(obstacle_trace, ((ObstacleExpansionRoom) otherRoom).getItem())) {
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
      CompleteExpansionRoom fromRoom = from_door.otherRoom(pObstacleRoom);
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

      if (pShoveToTheLeft && !doorLineSwapped || !pShoveToTheLeft && doorLineSwapped) {
        sectionOk =
            pListElement.sectionNoOfDoor == pListElement.door.mazeSearchElementCount() - 1
                && (pListElement.shapeEntry.a.distanceSquare(doorLineSegment.b) <= checkDistSquare
                    || pListElement.shapeEntry.b.distanceSquare(doorLineSegment.b)
                        <= checkDistSquare);
      } else {
        sectionOk =
            pListElement.sectionNoOfDoor == 0
                && (pListElement.shapeEntry.a.distanceSquare(doorLineSegment.a) <= checkDistSquare
                    || pListElement.shapeEntry.b.distanceSquare(doorLineSegment.a)
                        <= checkDistSquare);
      }
      if (!sectionOk) {
        return false;
      }

      // create the line segment for shoving

      FloatLine shrinkedLineSegment = polarLineSegment.shrinkSegment(compensatedTraceHalfWidth);
      Direction perpendicularDirection = shoveTraceLine.direction().turn45Degree(2);
      if (sideOfTraceLine == Side.ON_THE_LEFT) {
        if (pShoveToTheLeft) {
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
        if (pShoveToTheLeft) {
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
    int traceHalfWidth = pCtrl.traceHalfWidth[traceLayer];
    int[] netNoArr = new int[1];
    netNoArr[0] = pCtrl.netNo;

    double shoveWidth =
        pBoard.checkTraceSegment(
            shoveLineSegment,
            traceLayer,
            netNoArr,
            traceHalfWidth,
            pCtrl.traceClearanceClassNo,
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
              pBoard,
              shoveLineSegment,
              pShoveToTheLeft,
              traceLayer,
              netNoArr,
              traceHalfWidth,
              pCtrl.traceClearanceClassNo,
              pCtrl.maxShoveTraceRecursionDepth,
              pCtrl.maxShoveViaRecursionDepth);

      if (shoveWidth <= 0) {
        return true;
      }
    }

    // Put the doors on this side of the room into p_to_door_list with
    if (segmentShortened) {
      shoveWidth = Math.min(shoveWidth, fromCorner.distance(toCorner));
    }

    Line shoveLine = shoveLineSegment.getLine();

    // From_door_compare_distance is used to check, that a door is between from_door
    // and the end
    // point
    // of the shove line.
    double fromDoorCompareDistance;
    if (from_door.dimension == 2 || segmentIstPoint) {
      fromDoorCompareDistance = Double.MAX_VALUE;
    } else {
      fromDoorCompareDistance = toCorner.distanceSquare(fromDoorShape.cornerApprox(0));
    }

    for (ExpansionDoor currDoor : roomDoors) {
      if (currDoor == from_door) {
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
          pToDoorList.add(new DoorSection(currDoor, 0, lineSections[0]));
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
        if (pShoveToTheLeft) {
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
              pToDoorList.add(new DoorSection(currDoor, i, currLineSection));
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
  private static boolean endPointsMatching(PolylineTrace pTrace, Item pFromItem) {
    if (pFromItem == pTrace) {
      return true;
    }
    if (!pTrace.sharesNet(pFromItem)) {
      return false;
    }
    boolean pointsMatching;
    if (pFromItem instanceof DrillItem item) {
      Point fromCenter = item.getCenter();
      pointsMatching =
          fromCenter.equals(pTrace.firstCorner()) || fromCenter.equals(pTrace.lastCorner());
    } else if (pFromItem instanceof PolylineTrace from_trace) {
      pointsMatching =
          pTrace.firstCorner().equals(from_trace.firstCorner())
              || pTrace.firstCorner().equals(from_trace.lastCorner())
              || pTrace.lastCorner().equals(from_trace.firstCorner())
              || pTrace.lastCorner().equals(from_trace.lastCorner());
    } else {
      pointsMatching = false;
    }
    return pointsMatching;
  }

  public static class DoorSection {

    final ExpansionDoor door;
    final int sectionNo;
    final FloatLine sectionLine;

    DoorSection(ExpansionDoor pDoor, int pSectionNo, FloatLine pSectionLine) {
      door = pDoor;
      sectionNo = pSectionNo;
      sectionLine = pSectionLine;
    }
  }
}
