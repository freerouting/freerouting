package app.freerouting.autoroute.path;

import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.autoroute.maze.MazeSearchEngine;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;

/**
 * Calculates from the backtrack list the location of the traces and vias, which realize a
 * connection found by the maze search algorithm.
 */
class FoundConnectionLocatorAnyAngle extends FoundConnectionLocator {

  private static final double cTolerance = 1.0;

  /** Creates a new instance of FoundConnectionLocatorAnyAngle. */
  protected FoundConnectionLocatorAnyAngle(
      MazeSearchEngine.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    super(mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
  }

  /**
   * Calculates the left most corner of the shape of toInfo.door seen from the center of the common
   * room with the previous door.
   */
  private static FloatPoint calcDoorLeftCorner(BacktrackElement toInfo) {
    CompleteExpansionRoom fromRoom = toInfo.door.otherRoom(toInfo.nextRoom);
    FloatPoint pole = fromRoom.getShape().centreOfGravity();
    TileShape currentToDoorShape = toInfo.door.getShape();
    int leftMostCornerNo = currentToDoorShape.indexOfLeftMostCorner(pole);
    return currentToDoorShape.cornerApprox(leftMostCornerNo);
  }

  /**
   * Calculates the right most corner of the shape of toInfo.door seen from the center of the common
   * room with the previous door.
   */
  private static FloatPoint calcDoorRightCorner(BacktrackElement toInfo) {
    CompleteExpansionRoom fromRoom = toInfo.door.otherRoom(toInfo.nextRoom);
    FloatPoint pole = fromRoom.getShape().centreOfGravity();
    TileShape currentToDoorShape = toInfo.door.getShape();
    int rightMostCornerNo = currentToDoorShape.indexOfRightMostCorner(pole);
    return currentToDoorShape.cornerApprox(rightMostCornerNo);
  }

  /**
   * Calculates a list with the next point of the trace under construction. If the trace is
   * completed, the result list will be empty.
   */
  @Override
  protected Collection<FloatPoint> calculateNextTraceCorners() {
    Collection<FloatPoint> result = new LinkedList<>();
    if (this.currentToDoorIndex >= this.currentTargetDoorIndex) {
      if (this.currentToDoorIndex == this.currentTargetDoorIndex) {
        FloatPoint nearestPoint =
            this.currentTargetShape.nearestPoint(this.currentFromPoint.round()).toFloat();
        ++this.currentToDoorIndex;
        result.add(nearestPoint);
      }
      return result;
    }

    double traceHalfwidthExact = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    double traceHalfwidthMax = traceHalfwidthExact + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    final double traceHalfwidthMiddle = traceHalfwidthExact + cTolerance;

    BacktrackElement currentToInfo = this.backtrackArray[this.currentToDoorIndex];
    FloatPoint doorLeftCorner = calcDoorLeftCorner(currentToInfo);
    FloatPoint doorRightCorner = calcDoorRightCorner(currentToInfo);
    if (this.currentFromPoint.sideOf(doorLeftCorner, doorRightCorner) != Side.ON_THE_RIGHT) {
      // the door is already crossed at this.fromPoint
      if (this.currentFromPoint.scalarProduct(this.previousFromPoint, doorLeftCorner) >= 0) {
        // Also the left corner of the door is passed.
        // That may not be the case if the door line is crossed almost parallel.
        doorLeftCorner = null;
      }
      if (this.currentFromPoint.scalarProduct(this.previousFromPoint, doorRightCorner) >= 0) {
        // Also the right corner of the door is passed.
        doorRightCorner = null;
      }
      if (doorLeftCorner == null && doorRightCorner == null) {
        // The door is completely passed.
        ++this.currentToDoorIndex;
        result.add(this.currentFromPoint);
        return result;
      }
    }

    // Calculate the visibility range for a trace line from currentFromPoint
    // through the interval from left_most_visible_point to right_most_visible_point,
    // by advancing the door index as far as possible, so that still something is visible.

    boolean endOfTrace = false;
    FloatPoint leftTangentPoint;
    FloatPoint rightTangentPoint;
    int newDoorInd = this.currentToDoorIndex;
    int leftInd = newDoorInd;
    int rightInd = newDoorInd;
    int currentDoorInd = this.currentToDoorIndex + 1;
    FloatPoint resultCorner = null;

    // construct a maximum length straight line through the doors

    for (; ; ) {
      leftTangentPoint =
          this.currentFromPoint.rightTangentialPoint(doorLeftCorner, traceHalfwidthMax);
      if (doorLeftCorner != null && leftTangentPoint == null) {
        FRLogger.trace(
            "FoundConnectionLocator.calculate_next_trace_corner: left tangent point is null");
        leftTangentPoint = doorLeftCorner;
      }
      rightTangentPoint =
          this.currentFromPoint.leftTangentialPoint(doorRightCorner, traceHalfwidthMax);
      if (doorRightCorner != null && rightTangentPoint == null) {
        FRLogger.trace(
            "FoundConnectionLocator.calculate_next_trace_corner: right tangent point is null");
        rightTangentPoint = doorRightCorner;
      }
      if (leftTangentPoint != null
          && rightTangentPoint != null
          && rightTangentPoint.sideOf(this.currentFromPoint, leftTangentPoint)
              != Side.ON_THE_RIGHT) {
        // The gap between  left_most_visible_point and right_most_visible_point is too small
        // for a trace with the current half width.

        double leftCornerDistance = doorLeftCorner.distance(this.currentFromPoint);
        double rightCornerDistance = doorRightCorner.distance(this.currentFromPoint);
        if (leftCornerDistance <= rightCornerDistance) {
          newDoorInd = leftInd;
          resultCorner =
              leftTurnNextCorner(
                  this.currentFromPoint, traceHalfwidthMax, doorLeftCorner, doorRightCorner);
        } else {
          newDoorInd = rightInd;
          resultCorner =
              rightTurnNextCorner(
                  this.currentFromPoint, traceHalfwidthMax, doorRightCorner, doorLeftCorner);
        }
        break;
      }
      if (currentDoorInd >= this.currentTargetDoorIndex) {
        endOfTrace = true;
        break;
      }
      BacktrackElement nextToInfo = this.backtrackArray[currentDoorInd];
      FloatPoint nextLeftCorner = calcDoorLeftCorner(nextToInfo);
      FloatPoint nextRightCorner = calcDoorRightCorner(nextToInfo);
      if (this.currentFromPoint.sideOf(nextLeftCorner, nextRightCorner) != Side.ON_THE_RIGHT) {
        // the door may be already crossed at this.fromPoint
        if (doorLeftCorner == null
            && this.currentFromPoint.scalarProduct(this.previousFromPoint, nextLeftCorner) >= 0) {
          // Also the left corner of the door is passed.
          // That may not be the case if the door line is crossed almost parallel.
          nextLeftCorner = null;
        }
        if (doorRightCorner == null
            && this.currentFromPoint.scalarProduct(this.previousFromPoint, nextRightCorner) >= 0) {
          // Also the right corner of the door is passed.
          nextRightCorner = null;
        }
        if (nextLeftCorner == null && nextRightCorner == null) {
          // The door is completely passed.
          // Should not happen because the previous door was not passed completely.
          FRLogger.trace(
              "FoundConnectionLocator.calculate_next_trace_corner: next door passed unexpected");
          ++this.currentToDoorIndex;
          result.add(this.currentFromPoint);
          return result;
        }
      }
      if (doorLeftCorner != null && doorRightCorner != null) {
        // otherwise the following sideOf conditions may not be correct
        // even if all parameter points are defined
        if (nextLeftCorner.sideOf(this.currentFromPoint, doorRightCorner) == Side.ON_THE_RIGHT) {
          // bend to the right
          newDoorInd = rightInd + 1;
          resultCorner =
              rightTurnNextCorner(
                  this.currentFromPoint, traceHalfwidthMax, doorRightCorner, nextLeftCorner);
          break;
        }

        if (nextRightCorner.sideOf(this.currentFromPoint, doorLeftCorner) == Side.ON_THE_LEFT) {
          // bend to the left
          newDoorInd = leftInd + 1;
          resultCorner =
              leftTurnNextCorner(
                  this.currentFromPoint, traceHalfwidthMax, doorLeftCorner, nextRightCorner);
          break;
        }
      }
      boolean visabilityRangeGetsSmallerOnTheRightSide = doorRightCorner == null;
      if (doorRightCorner != null
          && nextRightCorner.sideOf(this.currentFromPoint, doorRightCorner) != Side.ON_THE_RIGHT) {
        FloatPoint currentTangentialPoint =
            this.currentFromPoint.leftTangentialPoint(nextRightCorner, traceHalfwidthMax);
        if (currentTangentialPoint != null) {
          FloatLine checkLine = new FloatLine(this.currentFromPoint, currentTangentialPoint);
          if (checkLine.segmentDistance(doorRightCorner) >= traceHalfwidthMax) {
            visabilityRangeGetsSmallerOnTheRightSide = true;
          }
        }
      }
      if (visabilityRangeGetsSmallerOnTheRightSide) {
        // The visibility range gets smaller on the right side.
        doorRightCorner = nextRightCorner;
        rightInd = currentDoorInd;
      }
      boolean visabilityRangeGetsSmallerOnTheLeftSide = doorLeftCorner == null;
      if (doorLeftCorner != null
          && nextLeftCorner.sideOf(this.currentFromPoint, doorLeftCorner) != Side.ON_THE_LEFT) {
        FloatPoint currentTangentialPoint =
            this.currentFromPoint.rightTangentialPoint(nextLeftCorner, traceHalfwidthMax);
        if (currentTangentialPoint != null) {
          FloatLine checkLine = new FloatLine(this.currentFromPoint, currentTangentialPoint);
          if (checkLine.segmentDistance(doorLeftCorner) >= traceHalfwidthMax) {
            visabilityRangeGetsSmallerOnTheLeftSide = true;
          }
        }
      }
      if (visabilityRangeGetsSmallerOnTheLeftSide) {
        // The visibility range gets smaller on the left side.
        doorLeftCorner = nextLeftCorner;
        leftInd = currentDoorInd;
      }
      ++currentDoorInd;
    }

    if (endOfTrace) {
      FloatPoint nearestPoint =
          this.currentTargetShape.nearestPoint(this.currentFromPoint.round()).toFloat();
      resultCorner = nearestPoint;
      if (leftTangentPoint != null
          && nearestPoint.sideOf(this.currentFromPoint, leftTangentPoint) == Side.ON_THE_LEFT) {
        // The nearest target point is to the left of the visible range, add another corner
        newDoorInd = leftInd + 1;
        FloatPoint targetRightCorner =
            this.currentTargetShape.cornerApprox(
                this.currentTargetShape.indexOfRightMostCorner(this.currentFromPoint));
        FloatPoint currentCorner =
            rightLeftTangentialPoint(
                this.currentFromPoint, targetRightCorner, doorLeftCorner, traceHalfwidthMax);
        if (currentCorner != null) {
          resultCorner = currentCorner;
          endOfTrace = false;
        }
      } else if (rightTangentPoint != null
          && nearestPoint.sideOf(this.currentFromPoint, rightTangentPoint) == Side.ON_THE_RIGHT) {
        // The nearest target point is to the right of the visible range, add another corner
        FloatPoint targetLeftCorner =
            this.currentTargetShape.cornerApprox(
                this.currentTargetShape.indexOfLeftMostCorner(this.currentFromPoint));
        newDoorInd = rightInd + 1;
        FloatPoint currentCorner =
            leftRightTangentialPoint(
                this.currentFromPoint, targetLeftCorner, doorRightCorner, traceHalfwidthMax);
        if (currentCorner != null) {
          resultCorner = currentCorner;
          endOfTrace = false;
        }
      }
    }
    if (endOfTrace) {
      newDoorInd = this.currentTargetDoorIndex;
    }

    // Check clearance violation with the previous door shapes
    // and correct them in this case.

    FloatLine checkLine = new FloatLine(this.currentFromPoint, resultCorner);
    int checkFromDoorIndex = Math.max(this.currentToDoorIndex - 5, this.currentFromDoorIndex + 1);
    FloatPoint correctedResult = null;
    int correctedDoorInd = 0;
    for (int i = checkFromDoorIndex; i < newDoorInd; i++) {
      FloatPoint currentLeftCorner = calcDoorLeftCorner(this.backtrackArray[i]);
      double currentDistance = checkLine.segmentDistance(currentLeftCorner);
      if (Math.abs(currentDistance) < traceHalfwidthMiddle) {
        FloatPoint currentCorrectedResult =
            rightLeftTangentialPoint(
                checkLine.a, checkLine.b, currentLeftCorner, traceHalfwidthMax);
        if (currentCorrectedResult != null) {
          if (correctedResult == null
              || currentCorrectedResult.sideOf(this.currentFromPoint, correctedResult)
                  == Side.ON_THE_RIGHT) {
            correctedDoorInd = i;
            correctedResult = currentCorrectedResult;
          }
        }
      }
      FloatPoint currentRightCorner = calcDoorRightCorner(this.backtrackArray[i]);
      currentDistance = checkLine.segmentDistance(currentRightCorner);
      if (Math.abs(currentDistance) < traceHalfwidthMiddle) {
        FloatPoint currentCorrectedResult =
            leftRightTangentialPoint(
                checkLine.a, checkLine.b, currentRightCorner, traceHalfwidthMax);
        if (currentCorrectedResult != null) {
          if (correctedResult == null
              || currentCorrectedResult.sideOf(this.currentFromPoint, correctedResult)
                  == Side.ON_THE_LEFT) {
            correctedDoorInd = i;
            correctedResult = currentCorrectedResult;
          }
        }
      }
    }
    if (correctedResult != null) {
      resultCorner = correctedResult;
      newDoorInd = Math.max(correctedDoorInd, this.currentToDoorIndex);
    }

    this.currentToDoorIndex = newDoorInd;
    if (resultCorner != null && resultCorner != this.currentFromPoint) {
      result.add(resultCorner);
    }
    if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
      FRLogger.trace(
          "compare_trace_next_corners_raw net="
              + this.ctrl.netNumber
              + ", layer="
              + this.currentTraceLayer
              + ", from_door="
              + this.currentFromDoorIndex
              + ", to_door="
              + this.currentToDoorIndex
              + ", target_door="
              + this.currentTargetDoorIndex
              + ", endOfTrace="
              + endOfTrace
              + ", corrected="
              + (correctedResult != null)
              + ", result_size="
              + result.size()
              + ", resultCorner="
              + resultCorner
              + ", current_from="
              + this.currentFromPoint);
    }
    return result;
  }

  /**
   * Calculates as first line the left side tangent from fromCorner to the circle with center
   * toCorner and radius dist. As second line the right side tangent from toCorner to the circle
   * with center nextCorner and radius 2 * dist is constructed. The second line is than translated
   * by the distance dist to the left. Returned is the intersection of the first and the second
   * line.
   */
  private FloatPoint rightTurnNextCorner(
      FloatPoint fromCorner, double dist, FloatPoint toCorner, FloatPoint nextCorner) {
    FloatPoint currentTangentialPoint = fromCorner.leftTangentialPoint(toCorner, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator.right_turn_next_corner: left tangential point is null");
      return fromCorner;
    }
    final FloatLine firstLine = new FloatLine(fromCorner, currentTangentialPoint);
    currentTangentialPoint = toCorner.rightTangentialPoint(nextCorner, 2 * dist + cTolerance);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator.right_turn_next_corner: right tangential point is null");
      return fromCorner;
    }
    FloatLine secondLine = new FloatLine(toCorner, currentTangentialPoint);
    secondLine = secondLine.translate(dist);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates as first line the right side tangent from fromCorner to the circle with center
   * toCorner and radius dist. As second line the left side tangent from toCorner to the circle with
   * center nextCorner and radius 2 * dist is constructed. The second line is than translated by the
   * distance dist to the right. Returned is the intersection of the first and the second line.
   */
  private FloatPoint leftTurnNextCorner(
      FloatPoint fromCorner, double dist, FloatPoint toCorner, FloatPoint nextCorner) {
    FloatPoint currentTangentialPoint = fromCorner.rightTangentialPoint(toCorner, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator.left_turn_next_corner: right tangential point is null");
      return fromCorner;
    }
    final FloatLine firstLine = new FloatLine(fromCorner, currentTangentialPoint);
    currentTangentialPoint = toCorner.leftTangentialPoint(nextCorner, 2 * dist + cTolerance);
    if (currentTangentialPoint == null) {
      FRLogger.trace("FoundConnectionLocator.left_turn_next_corner: left tangential point is null");
      return fromCorner;
    }
    FloatLine secondLine = new FloatLine(toCorner, currentTangentialPoint);
    secondLine = secondLine.translate(-dist);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates the right tangential line from fromPoint and the left tangential line from toPoint
   * to the circle with center center and radius dist. Returns the intersection of the 2 lines.
   */
  private FloatPoint rightLeftTangentialPoint(
      FloatPoint fromPoint, FloatPoint toPoint, FloatPoint center, double dist) {
    FloatPoint currentTangentialPoint = fromPoint.rightTangentialPoint(center, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator. right_left_tangential_point: right tangential point is null");
      return null;
    }
    FloatLine firstLine = new FloatLine(fromPoint, currentTangentialPoint);
    currentTangentialPoint = toPoint.leftTangentialPoint(center, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator. right_left_tangential_point: left tangential point is null");
      return null;
    }
    FloatLine secondLine = new FloatLine(toPoint, currentTangentialPoint);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates the left tangential line from fromPoint and the right tangential line from toPoint
   * to the circle with center center and radius dist. Returns the intersection of the 2 lines.
   */
  private FloatPoint leftRightTangentialPoint(
      FloatPoint fromPoint, FloatPoint toPoint, FloatPoint center, double dist) {
    FloatPoint currentTangentialPoint = fromPoint.leftTangentialPoint(center, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator. left_right_tangential_point: left tangential point is null");
      return null;
    }
    FloatLine firstLine = new FloatLine(fromPoint, currentTangentialPoint);
    currentTangentialPoint = toPoint.rightTangentialPoint(center, dist);
    if (currentTangentialPoint == null) {
      FRLogger.trace(
          "FoundConnectionLocator. left_right_tangential_point: right tangential point is null");
      return null;
    }
    FloatLine secondLine = new FloatLine(toPoint, currentTangentialPoint);
    return firstLine.intersection(secondLine);
  }
}
