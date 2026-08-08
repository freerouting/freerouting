package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
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
class LocateFoundConnectionAlgoAnyAngle extends LocateFoundConnectionAlgo {

  private static final double cTolerance = 1.0;

  /** Creates a new instance of LocateFoundConnectionAlgo */
  protected LocateFoundConnectionAlgoAnyAngle(
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

  /**
   * Calculates the left most corner of the shape of p_to_info.door seen from the center of the
   * common room with the previous door.
   */
  private static FloatPoint calcDoorLeftCorner(BacktrackElement p_to_info) {
    CompleteExpansionRoom fromRoom = p_to_info.door.otherRoom(p_to_info.nextRoom);
    FloatPoint pole = fromRoom.getShape().centreOfGravity();
    TileShape currToDoorShape = p_to_info.door.getShape();
    int leftMostCornerNo = currToDoorShape.indexOfLeftMostCorner(pole);
    return currToDoorShape.cornerApprox(leftMostCornerNo);
  }

  /**
   * Calculates the right most corner of the shape of p_to_info.door seen from the center of the
   * common room with the previous door.
   */
  private static FloatPoint calcDoorRightCorner(BacktrackElement p_to_info) {
    CompleteExpansionRoom fromRoom = p_to_info.door.otherRoom(p_to_info.nextRoom);
    FloatPoint pole = fromRoom.getShape().centreOfGravity();
    TileShape currToDoorShape = p_to_info.door.getShape();
    int rightMostCornerNo = currToDoorShape.indexOfRightMostCorner(pole);
    return currToDoorShape.cornerApprox(rightMostCornerNo);
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
    double traceHalfwidthMiddle = traceHalfwidthExact + cTolerance;

    BacktrackElement currToInfo = this.backtrackArray[this.currentToDoorIndex];
    FloatPoint doorLeftCorner = calcDoorLeftCorner(currToInfo);
    FloatPoint doorRightCorner = calcDoorRightCorner(currToInfo);
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
    int currDoorInd = this.currentToDoorIndex + 1;
    FloatPoint resultCorner = null;

    // construct a maximum length straight line through the doors

    for (; ; ) {
      leftTangentPoint =
          this.currentFromPoint.rightTangentialPoint(doorLeftCorner, traceHalfwidthMax);
      if (doorLeftCorner != null && leftTangentPoint == null) {
        FRLogger.trace(
            "LocateFoundConnectionAlgo.calculate_next_trace_corner: left tangent point is null");
        leftTangentPoint = doorLeftCorner;
      }
      rightTangentPoint =
          this.currentFromPoint.leftTangentialPoint(doorRightCorner, traceHalfwidthMax);
      if (doorRightCorner != null && rightTangentPoint == null) {
        FRLogger.trace(
            "LocateFoundConnectionAlgo.calculate_next_trace_corner: right tangent point is null");
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
      if (currDoorInd >= this.currentTargetDoorIndex) {
        endOfTrace = true;
        break;
      }
      BacktrackElement nextToInfo = this.backtrackArray[currDoorInd];
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
              "LocateFoundConnectionAlgo.calculate_next_trace_corner: next door passed unexpected");
          ++this.currentToDoorIndex;
          result.add(this.currentFromPoint);
          return result;
        }
      }
      if (doorLeftCorner != null && doorRightCorner != null)
      // otherwise the following sideOf conditions may not be correct
      // even if all parameter points are defined
      {
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
        FloatPoint currTangentialPoint =
            this.currentFromPoint.leftTangentialPoint(nextRightCorner, traceHalfwidthMax);
        if (currTangentialPoint != null) {
          FloatLine checkLine = new FloatLine(this.currentFromPoint, currTangentialPoint);
          if (checkLine.segmentDistance(doorRightCorner) >= traceHalfwidthMax) {
            visabilityRangeGetsSmallerOnTheRightSide = true;
          }
        }
      }
      if (visabilityRangeGetsSmallerOnTheRightSide) {
        // The visibility range gets smaller on the right side.
        doorRightCorner = nextRightCorner;
        rightInd = currDoorInd;
      }
      boolean visabilityRangeGetsSmallerOnTheLeftSide = doorLeftCorner == null;
      if (doorLeftCorner != null
          && nextLeftCorner.sideOf(this.currentFromPoint, doorLeftCorner) != Side.ON_THE_LEFT) {
        FloatPoint currTangentialPoint =
            this.currentFromPoint.rightTangentialPoint(nextLeftCorner, traceHalfwidthMax);
        if (currTangentialPoint != null) {
          FloatLine checkLine = new FloatLine(this.currentFromPoint, currTangentialPoint);
          if (checkLine.segmentDistance(doorLeftCorner) >= traceHalfwidthMax) {
            visabilityRangeGetsSmallerOnTheLeftSide = true;
          }
        }
      }
      if (visabilityRangeGetsSmallerOnTheLeftSide) {
        // The visibility range gets smaller on the left side.
        doorLeftCorner = nextLeftCorner;
        leftInd = currDoorInd;
      }
      ++currDoorInd;
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
        FloatPoint currCorner =
            rightLeftTangentialPoint(
                this.currentFromPoint, targetRightCorner, doorLeftCorner, traceHalfwidthMax);
        if (currCorner != null) {
          resultCorner = currCorner;
          endOfTrace = false;
        }
      } else if (rightTangentPoint != null
          && nearestPoint.sideOf(this.currentFromPoint, rightTangentPoint) == Side.ON_THE_RIGHT) {
        // The nearest target point is to the right of the visible range, add another corner
        FloatPoint targetLeftCorner =
            this.currentTargetShape.cornerApprox(
                this.currentTargetShape.indexOfLeftMostCorner(this.currentFromPoint));
        newDoorInd = rightInd + 1;
        FloatPoint currCorner =
            leftRightTangentialPoint(
                this.currentFromPoint, targetLeftCorner, doorRightCorner, traceHalfwidthMax);
        if (currCorner != null) {
          resultCorner = currCorner;
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
      FloatPoint currLeftCorner = calcDoorLeftCorner(this.backtrackArray[i]);
      double currDist = checkLine.segmentDistance(currLeftCorner);
      if (Math.abs(currDist) < traceHalfwidthMiddle) {
        FloatPoint currCorrectedResult =
            rightLeftTangentialPoint(
                checkLine.a, checkLine.b, currLeftCorner, traceHalfwidthMax);
        if (currCorrectedResult != null) {
          if (correctedResult == null
              || currCorrectedResult.sideOf(this.currentFromPoint, correctedResult)
                  == Side.ON_THE_RIGHT) {
            correctedDoorInd = i;
            correctedResult = currCorrectedResult;
          }
        }
      }
      FloatPoint currRightCorner = calcDoorRightCorner(this.backtrackArray[i]);
      currDist = checkLine.segmentDistance(currRightCorner);
      if (Math.abs(currDist) < traceHalfwidthMiddle) {
        FloatPoint currCorrectedResult =
            leftRightTangentialPoint(
                checkLine.a, checkLine.b, currRightCorner, traceHalfwidthMax);
        if (currCorrectedResult != null) {
          if (correctedResult == null
              || currCorrectedResult.sideOf(this.currentFromPoint, correctedResult)
                  == Side.ON_THE_LEFT) {
            correctedDoorInd = i;
            correctedResult = currCorrectedResult;
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
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      FRLogger.trace(
          "compare_trace_next_corners_raw net="
              + this.ctrl.netNo
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
   * Calculates as first line the left side tangent from p_from_corner to the circle with center
   * p_to_corner and radius p_dist. As second line the right side tangent from p_to_corner to the
   * circle with center p_next_corner and radius 2 * p_dist is constructed. The second line is than
   * translated by the distance p_dist to the left. Returned is the intersection of the first and
   * the second line.
   */
  private FloatPoint rightTurnNextCorner(
      FloatPoint p_from_corner, double p_dist, FloatPoint p_to_corner, FloatPoint p_next_corner) {
    FloatPoint currTangentialPoint = p_from_corner.leftTangentialPoint(p_to_corner, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo.right_turn_next_corner: left tangential point is null");
      return p_from_corner;
    }
    FloatLine firstLine = new FloatLine(p_from_corner, currTangentialPoint);
    currTangentialPoint =
        p_to_corner.rightTangentialPoint(p_next_corner, 2 * p_dist + cTolerance);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo.right_turn_next_corner: right tangential point is null");
      return p_from_corner;
    }
    FloatLine secondLine = new FloatLine(p_to_corner, currTangentialPoint);
    secondLine = secondLine.translate(p_dist);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates as first line the right side tangent from p_from_corner to the circle with center
   * p_to_corner and radius p_dist. As second line the left side tangent from p_to_corner to the
   * circle with center p_next_corner and radius 2 * p_dist is constructed. The second line is than
   * translated by the distance p_dist to the right. Returned is the intersection of the first and
   * the second line.
   */
  private FloatPoint leftTurnNextCorner(
      FloatPoint p_from_corner, double p_dist, FloatPoint p_to_corner, FloatPoint p_next_corner) {
    FloatPoint currTangentialPoint = p_from_corner.rightTangentialPoint(p_to_corner, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo.left_turn_next_corner: right tangential point is null");
      return p_from_corner;
    }
    FloatLine firstLine = new FloatLine(p_from_corner, currTangentialPoint);
    currTangentialPoint = p_to_corner.leftTangentialPoint(p_next_corner, 2 * p_dist + cTolerance);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo.left_turn_next_corner: left tangential point is null");
      return p_from_corner;
    }
    FloatLine secondLine = new FloatLine(p_to_corner, currTangentialPoint);
    secondLine = secondLine.translate(-p_dist);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates the right tangential line from p_from_point and the left tangential line from
   * p_to_point to the circle with center p_center and radius p_dist. Returns the intersection of
   * the 2 lines.
   */
  private FloatPoint rightLeftTangentialPoint(
      FloatPoint p_from_point, FloatPoint p_to_point, FloatPoint p_center, double p_dist) {
    FloatPoint currTangentialPoint = p_from_point.rightTangentialPoint(p_center, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo. right_left_tangential_point: right tangential point is null");
      return null;
    }
    FloatLine firstLine = new FloatLine(p_from_point, currTangentialPoint);
    currTangentialPoint = p_to_point.leftTangentialPoint(p_center, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo. right_left_tangential_point: left tangential point is null");
      return null;
    }
    FloatLine secondLine = new FloatLine(p_to_point, currTangentialPoint);
    return firstLine.intersection(secondLine);
  }

  /**
   * Calculates the left tangential line from p_from_point and the right tangential line from
   * p_to_point to the circle with center p_center and radius p_dist. Returns the intersection of
   * the 2 lines.
   */
  private FloatPoint leftRightTangentialPoint(
      FloatPoint p_from_point, FloatPoint p_to_point, FloatPoint p_center, double p_dist) {
    FloatPoint currTangentialPoint = p_from_point.leftTangentialPoint(p_center, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo. left_right_tangential_point: left tangential point is null");
      return null;
    }
    FloatLine firstLine = new FloatLine(p_from_point, currTangentialPoint);
    currTangentialPoint = p_to_point.rightTangentialPoint(p_center, p_dist);
    if (currTangentialPoint == null) {
      FRLogger.trace(
          "LocateFoundConnectionAlgo. left_right_tangential_point: right tangential point is null");
      return null;
    }
    FloatLine secondLine = new FloatLine(p_to_point, currTangentialPoint);
    return firstLine.intersection(secondLine);
  }
}
