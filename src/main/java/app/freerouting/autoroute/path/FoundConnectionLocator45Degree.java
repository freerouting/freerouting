package app.freerouting.autoroute.path;

import app.freerouting.autoroute.expansion.ExpandableObject;
import app.freerouting.autoroute.expansion.ExpansionDoor;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.autoroute.maze.MazeSearchEngine;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.searchtree.ShapeSearchTree;
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

/**
 * Locates and constructs 45-degree trace connection geometries from maze search backtrack paths.
 */
public class FoundConnectionLocator45Degree extends FoundConnectionLocator {

  /** Creates a new instance of FoundConnectionLocator45Degree. */
  public FoundConnectionLocator45Degree(
      MazeSearchEngine.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    super(mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
  }

  private static FloatPoint roundToInteger(FloatPoint point) {
    return point.round().toFloat();
  }

  /**
   * Calculates if the next 45-degree angle should be horizontal first when coming from fromPoint on
   * fromDoor.
   */
  private static boolean calcHorizontalFirstFromDoor(
      ExpandableObject fromDoor, FloatPoint fromPoint, FloatPoint toPoint) {
    TileShape doorShape = fromDoor.getShape();
    IntBox fromDoorBox = doorShape.boundingBox();
    if (fromDoor.getDimension() != 1) {
      return fromDoorBox.height() >= fromDoorBox.width();
    }

    FloatLine doorLineSegment = doorShape.diagonalCornerSegment();
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
      double dx = toPoint.x - fromPoint.x;
      double dy = toPoint.y - fromPoint.y;
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
  protected Collection<FloatPoint> calculateNextTraceCorners() {
    Collection<FloatPoint> result = new LinkedList<>();

    if (this.currentToDoorIndex > this.currentTargetDoorIndex) {
      if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
        FRLogger.trace(
            "compare_trace_next_corners_raw net="
                + this.ctrl.netNumber
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

    BacktrackElement currentFromInfo = this.backtrackArray[this.currentToDoorIndex - 1];

    if (currentFromInfo.nextRoom == null) {
      FRLogger.warn(
          "FoundConnectionLocator45Degree.calculate_next_trace_corners: nextRoom is null");
      return result;
    }

    TileShape roomShape = currentFromInfo.nextRoom.getShape();

    int traceHalfwidth = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    int traceHalfwidthAdd =
        traceHalfwidth
            + AutorouteEngine
                .TRACE_WIDTH_TOLERANCE; // add some tolerance for free space expansion rooms.
    int shrinkOffset;
    if (currentFromInfo.nextRoom instanceof ObstacleExpansionRoom) {

      shrinkOffset = traceHalfwidth;
    } else {
      shrinkOffset = traceHalfwidthAdd;
    }

    TileShape shrinkedRoomShape = (TileShape) roomShape.offset(-shrinkOffset);
    if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
      FRLogger.trace(
          "compare_trace_room_shrink_raw net="
              + this.ctrl.netNumber
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
              + currentFromInfo.nextRoom.getClass().getSimpleName()
              + ", shrinkOffset="
              + shrinkOffset
              + ", room_empty="
              + roomShape.isEmpty()
              + ", shrinked_empty="
              + shrinkedRoomShape.isEmpty()
              + ", current_from="
              + this.currentFromPoint);
    }
    if (!shrinkedRoomShape.isEmpty()) {
      // enter the shrunk room shape by a 45-degree angle first
      FloatPoint nearestRoomPoint = shrinkedRoomShape.nearestPointApprox(this.currentFromPoint);
      boolean horizontalFirst =
          calcHorizontalFirstFromDoor(
              currentFromInfo.door, this.currentFromPoint, nearestRoomPoint);
      nearestRoomPoint = roundToInteger(nearestRoomPoint);
      result.add(
          calculateAdditionalCorner(
              this.currentFromPoint, nearestRoomPoint, horizontalFirst, this.angleRestriction));
      result.add(nearestRoomPoint);
      this.currentFromPoint = nearestRoomPoint;
    } else {
      shrinkedRoomShape = roomShape;
    }

    if (this.currentToDoorIndex == this.currentTargetDoorIndex) {
      FloatPoint nearestPoint = this.currentTargetShape.nearestPointApprox(this.currentFromPoint);
      nearestPoint = roundToInteger(nearestPoint);
      FloatPoint addCorner =
          calculateAdditionalCorner(
              this.currentFromPoint, nearestPoint, true, this.angleRestriction);
      if (!shrinkedRoomShape.contains(addCorner)) {
        addCorner =
            calculateAdditionalCorner(
                this.currentFromPoint, nearestPoint, false, this.angleRestriction);
      }
      result.add(addCorner);
      result.add(nearestPoint);
      ++this.currentToDoorIndex;
      if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
        FRLogger.trace(
            "compare_trace_next_corners_raw net="
                + this.ctrl.netNumber
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

    BacktrackElement currentToInfo = this.backtrackArray[this.currentToDoorIndex];
    if (!(currentToInfo.door instanceof ExpansionDoor currentToDoor)) {
      FRLogger.warn(
          "FoundConnectionLocator45Degree.calculate_next_trace_corners: ExpansionDoor expected");
      return result;
    }

    FloatPoint nearestToDoorPoint;
    if (currentToDoor.dimension == 2) {
      // May not happen in free angle routing mode because then corners are cut off.
      TileShape toDoorShape = currentToDoor.getShape();

      TileShape shrinkedToDoorShape = (TileShape) toDoorShape.shrink(shrinkOffset);
      nearestToDoorPoint = shrinkedToDoorShape.nearestPointApprox(this.currentFromPoint);
      nearestToDoorPoint = roundToInteger(nearestToDoorPoint);
    } else {
      FloatLine[] lineSections = currentToDoor.getSectionSegments(traceHalfwidth);
      if (currentToInfo.sectionNoOfDoor >= lineSections.length) {
        FRLogger.warn(
            "FoundConnectionLocator45Degree.calculate_next_trace_corners: "
                + "lineSections inconsistent");
        return result;
      }
      FloatLine currentLineSection = lineSections[currentToInfo.sectionNoOfDoor];
      nearestToDoorPoint = currentLineSection.nearestSegmentPoint(this.currentFromPoint);

      boolean nearestToDoorPointOk = true;
      if (currentToInfo.nextRoom != null) {
        Simplex nextRoomShape = currentToInfo.nextRoom.getShape().toSimplex();
        // with IntBox or IntOctagon the next calculation will not work, because they have
        // border lines of length 0.
        FloatPoint[] nearestPoints = nextRoomShape.nearestBorderPointsApprox(nearestToDoorPoint, 2);
        if (nearestPoints.length >= 2) {
          nearestToDoorPointOk = nearestPoints[1].distance(nearestToDoorPoint) >= traceHalfwidthAdd;
        }
      }
      if (!nearestToDoorPointOk) {
        // may be the room has an acute (45 degree) angle at a corner of the door
        nearestToDoorPoint = currentLineSection.a.middlePoint(currentLineSection.b);
      }
    }
    nearestToDoorPoint = roundToInteger(nearestToDoorPoint);
    boolean horizontalFirst =
        calcHorizontalFirstToDoor(currentToInfo.door, this.currentFromPoint, nearestToDoorPoint);
    result.add(
        calculateAdditionalCorner(
            this.currentFromPoint, nearestToDoorPoint, horizontalFirst, this.angleRestriction));
    result.add(nearestToDoorPoint);
    ++this.currentToDoorIndex;
    if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
      FRLogger.trace(
          "compare_trace_next_corners_raw net="
              + this.ctrl.netNumber
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
   * coming from fromPoint.
   */
  private boolean calcHorizontalFirstToDoor(
      ExpandableObject toDoor, FloatPoint fromPoint, FloatPoint toPoint) {
    TileShape doorShape = toDoor.getShape();
    IntBox fromDoorBox = doorShape.boundingBox();
    if (toDoor.getDimension() != 1) {
      return fromDoorBox.height() <= fromDoorBox.width();
    }
    FloatLine doorLineSegment = doorShape.diagonalCornerSegment();
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
      double dx = toPoint.x - fromPoint.x;
      double dy = toPoint.y - fromPoint.y;
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
