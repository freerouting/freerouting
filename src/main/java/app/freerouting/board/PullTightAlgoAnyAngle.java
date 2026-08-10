package app.freerouting.board;

import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;

/** Auxiliary class containing internal functions for pulling any angle traces tight. */
class PullTightAlgoAnyAngle extends PullTightAlgo {

  private static final double SKIP_LENGTH = 10.0;

  PullTightAlgoAnyAngle(
      RoutingBoard board,
      int[] onlyNetNoArr,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    super(board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
  }

  @Override
  Polyline pullTight(Polyline polyline) {
    Polyline newResult = avoidAcidTraps(polyline);
    Polyline prevResult = null;
    while (newResult != prevResult && !isStopRequested()) {
      prevResult = newResult;
      Polyline tmp = skipSegmentsOfLength0(prevResult);
      Polyline tmp0 = reduceLines(tmp);
      Polyline tmp1 = skipLines(tmp0);

      // I intended to replace reduce_corners by the previous 2
      // functions, because with consecutive corners closer than
      // 1 grid point reduce_corners may loop with smoothen_corners
      // because of changing directions heavily.
      // Unlike reduce_corners, the above 2 functions do not
      // introduce new directions

      Polyline tmp2 = reduceCorners(tmp1);
      Polyline tmp3 = repositionLines(tmp2);
      newResult = smoothenCorners(tmp3);
    }
    return newResult;
  }

  // tries to reduce the corner count of polyline by replacing two consecutive
  // lines by a line through IntPoints near the previous corner and the next
  // corner, if that is possible without clearance violation.
  private Polyline reduceCorners(Polyline polyline) {
    if (polyline.arr.length < 4) {
      return polyline;
    }
    int lastIndex = polyline.arr.length - 4;

    Line[] newLines = new Line[polyline.arr.length];
    newLines[0] = polyline.arr[0];
    newLines[1] = polyline.arr[1];

    int newLineIndex = 1;

    boolean polylineChanged = false;

    Line[] currLines = new Line[3];

    for (int i = 0; i <= lastIndex; i++) {
      boolean skipLine = false;
      FloatPoint newA = newLines[newLineIndex - 1].intersectionApprox(newLines[newLineIndex]);
      FloatPoint newB = polyline.cornerApprox(i + 2);
      boolean inClipShape =
          currClipShape == null
              || currClipShape.contains(newA)
                  && currClipShape.contains(newB)
                  && currClipShape.contains(polyline.cornerApprox(newLineIndex));

      if (inClipShape) {
        currLines[1] = new Line(newA.round(), newB.round());
        boolean ok = true;
        if (newLineIndex == 1) {
          if (!(polyline.firstCorner() instanceof IntPoint)) {
            // first corner must not be changed
            ok = false;
          } else {
            Direction dir = currLines[1].direction();
            currLines[0] = Line.getInstance(polyline.firstCorner(), dir.turn45Degree(2));
          }
        } else {
          currLines[0] = newLines[newLineIndex - 1];
        }
        if (i == lastIndex) {
          if (!(polyline.lastCorner() instanceof IntPoint)) {
            // last corner must not be changed
            ok = false;
          } else {
            Direction dir = currLines[1].direction();
            currLines[2] = Line.getInstance(polyline.lastCorner(), dir.turn45Degree(2));
          }
        } else {
          currLines[2] = polyline.arr[i + 3];
        }

        // check, if the intersection of currLines[0] and currLines[1]
        // is near newA and the intersection of currLines[0] and
        // currLines[1] and currLines[2] is near newB.
        // There may be numerical stability problems with
        // near parallel lines.

        final double checkDist = 100;
        if (ok) {
          FloatPoint checkIs = currLines[0].intersectionApprox(currLines[1]);
          double dist = checkIs.distanceSquare(newA);

          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok) {
          FloatPoint checkIs = currLines[1].intersectionApprox(currLines[2]);
          double dist = checkIs.distanceSquare(newB);
          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok && i == 1 && !(polyline.firstCorner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second corner of the new polyline
          // is on the same side of the trace as the third corner. (There may be splitting problems)
          Point newCorner = currLines[0].intersection(currLines[1]);
          if (newCorner.sideOf(newLines[0]) != polyline.corner(1).sideOf(newLines[0])) {
            ok = false;
          }
        }
        if (ok && i == lastIndex - 1 && !(polyline.lastCorner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second last corner of the new polyline
          // is on the same side of the trace as the third last corner (There may be splitting
          // problems)
          Point newCorner = currLines[1].intersection(currLines[2]);
          if (newCorner.sideOf(newLines[0])
              != polyline.corner(polyline.cornerCount() - 2).sideOf(newLines[0])) {
            ok = false;
          }
        }
        Polyline currPolyline = null;
        if (ok) {
          FloatPoint skipCorner = newLines[newLineIndex].intersectionApprox(polyline.arr[i + 2]);
          currPolyline = new Polyline(currLines);
          if (currPolyline.arr.length != 3) {
            ok = false;
          }
          double lengthBefore = skipCorner.distance(newA) + skipCorner.distance(newB);
          double lengthAfter = currPolyline.lengthApprox() + 1.5;
          // 1.5 added because of possible inaccuracy SQRT_2
          // by twice rounding.
          if (lengthAfter >= lengthBefore) {
            // May happen from rounding to integer.
            // Prevent infinite loop.
            ok = false;
          }
        }

        if (ok) {
          TileShape shapeToCheck = currPolyline.offsetShape(currHalfWidth, 0);
          skipLine =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
      }
      if (skipLine) {
        polylineChanged = true;
        newLines[newLineIndex] = currLines[1];
        if (newLineIndex == 1) {
          // make the first line perpendicular to the current line
          newLines[0] = currLines[0];
        }
        if (i == lastIndex) {
          // make the last line perpendicular to the current line
          ++newLineIndex;
          newLines[newLineIndex] = currLines[2];
        }
        if (board.changedArea != null) {
          board.changedArea.join(newA, currLayer);
          board.changedArea.join(newB, currLayer);
        }
      } else {
        ++newLineIndex;
        newLines[newLineIndex] = polyline.arr[i + 2];
        if (i == lastIndex) {
          ++newLineIndex;
          newLines[newLineIndex] = polyline.arr[i + 3];
        }
      }
      if (newLines[newLineIndex].isParallel(newLines[newLineIndex - 1])) {
        // skip line, if it is parallel to the previous one
        --newLineIndex;
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    Line[] cleanedNewLines = new Line[newLineIndex + 1];
    System.arraycopy(newLines, 0, cleanedNewLines, 0, cleanedNewLines.length);
    return new Polyline(cleanedNewLines);
  }

  /** Tries to smoothen polyline by cutting of corners, if possible. */
  private Polyline smoothenCorners(Polyline polyline) {
    if (polyline.arr.length < 4) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = new Line[polyline.arr.length];
    System.arraycopy(polyline.arr, 0, lineArr, 0, lineArr.length);

    for (int i = 0; i < lineArr.length - 3; i++) {
      Line newLine = smoothenCorner(lineArr, i);
      if (newLine != null) {
        polylineChanged = true;
        // add the new line into the line array
        Line[] tmpLines = new Line[lineArr.length + 1];
        System.arraycopy(lineArr, 0, tmpLines, 0, i + 2);
        tmpLines[i + 2] = newLine;
        System.arraycopy(lineArr, i + 2, tmpLines, i + 3, tmpLines.length - (i + 3));
        lineArr = tmpLines;
        ++i;
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lineArr);
  }

  /** Tries to shorten polyline by relocating its lines. */
  @Override
  Polyline repositionLines(Polyline polyline) {
    if (polyline.arr.length < 5) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = new Line[polyline.arr.length];
    System.arraycopy(polyline.arr, 0, lineArr, 0, lineArr.length);
    for (int i = 0; i < lineArr.length - 4; i++) {
      Line newLine = repositionLine(lineArr, i);
      if (newLine != null) {
        polylineChanged = true;
        lineArr[i + 2] = newLine;
        if (lineArr[i + 2].isParallel(lineArr[i + 1])
            || lineArr[i + 2].isParallel(lineArr[i + 3])) {
          // calculation of corners not possible before skipping
          // parallel lines
          break;
        }
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lineArr);
  }

  /**
   * Tries to reduce the number of lines of polyline by moving lines parallel beyond the
   * intersection of the next or previous lines.
   */
  private Polyline reduceLines(Polyline polyline) {
    if (polyline.arr.length < 6) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = polyline.arr;
    for (int i = 2; i < lineArr.length - 2; i++) {
      FloatPoint prevCorner = lineArr[i - 2].intersectionApprox(lineArr[i - 1]);
      FloatPoint nextCorner = lineArr[i + 1].intersectionApprox(lineArr[i + 2]);
      boolean inClipShape =
          currClipShape == null
              || currClipShape.contains(prevCorner) && currClipShape.contains(nextCorner);
      if (!inClipShape) {
        continue;
      }
      Line translateLine = lineArr[i];
      double prevDist = translateLine.signedDistance(prevCorner);
      double nextDist = translateLine.signedDistance(nextCorner);
      if (Signum.of(prevDist) != Signum.of(nextDist)) {
        // the 2 corners are on different sides of the translateLine
        continue;
      }
      double translateDist;
      if (Math.abs(prevDist) < Math.abs(nextDist)) {

        translateDist = prevDist;
      } else {
        translateDist = nextDist;
      }
      if (translateDist == 0) {
        // line segment may have length 0
        continue;
      }
      Side lineSide = translateLine.sideOf(prevCorner);
      Line newLine = translateLine.translate(-translateDist);
      // make sure, we have crossed the nearestCorner;
      int sign = Signum.asInt(translateDist);
      Side newLineSideOfPrevCorner = newLine.sideOf(prevCorner);
      Side newLineSideOfNextCorner = newLine.sideOf(nextCorner);
      while (newLineSideOfPrevCorner == lineSide && newLineSideOfNextCorner == lineSide) {
        translateDist += sign * 0.5;
        newLine = translateLine.translate(-translateDist);
        newLineSideOfPrevCorner = newLine.sideOf(prevCorner);
        newLineSideOfNextCorner = newLine.sideOf(nextCorner);
      }
      int crossedCornersBeforeCount = 0;
      int crossedCornersAfterCount = 0;
      if (newLineSideOfPrevCorner != lineSide) {
        ++crossedCornersBeforeCount;
      }
      if (newLineSideOfNextCorner != lineSide) {
        ++crossedCornersAfterCount;
      }
      // check, that we haven't crossed both corners
      if (crossedCornersBeforeCount > 1 || crossedCornersAfterCount > 1) {
        continue;
      }
      // check, that next_nearest_corner and nearestCorner are on
      // different sides of newLine;
      if (crossedCornersBeforeCount > 0) {
        if (i < 3) {
          continue;
        }
        FloatPoint prevPrevCorner = lineArr[i - 3].intersectionApprox(lineArr[i - 2]);
        if (newLine.sideOf(prevPrevCorner) != lineSide) {
          continue;
        }
      }
      if (crossedCornersAfterCount > 0) {
        if (i >= lineArr.length - 3) {
          continue;
        }
        FloatPoint nextNextCorner = lineArr[i + 2].intersectionApprox(lineArr[i + 3]);
        if (newLine.sideOf(nextNextCorner) != lineSide) {
          continue;
        }
      }
      Line[] currLines =
          new Line[lineArr.length - crossedCornersBeforeCount - crossedCornersAfterCount];
      int keepBeforeInd = i - crossedCornersBeforeCount;
      System.arraycopy(lineArr, 0, currLines, 0, keepBeforeInd);
      currLines[keepBeforeInd] = newLine;
      System.arraycopy(
          lineArr,
          i + 1 + crossedCornersAfterCount,
          currLines,
          keepBeforeInd + 1,
          currLines.length - (keepBeforeInd + 1));
      Polyline tmp = new Polyline(currLines);
      boolean checkOk = false;
      if (tmp.arr.length == currLines.length) {
        TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, keepBeforeInd - 1);
        checkOk =
            board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
      }
      if (checkOk) {
        if (board.changedArea != null) {
          board.changedArea.join(prevCorner, currLayer);
          board.changedArea.join(nextCorner, currLayer);
        }
        polylineChanged = true;
        lineArr = currLines;
        --i;
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lineArr);
  }

  private Line smoothenCorner(Line[] lineArr, int startNo) {
    if (lineArr.length - startNo < 4) {
      return null;
    }
    FloatPoint currCorner = lineArr[startNo + 1].intersectionApprox(lineArr[startNo + 2]);
    if (currClipShape != null && !currClipShape.contains(currCorner)) {
      return null;
    }
    double cosinusAngle = lineArr[startNo + 1].cosAngle(lineArr[startNo + 2]);
    if (cosinusAngle > c_max_cos_angle) {
      // lines are already nearly parallel, don't divide angle any further
      // because of problems with numerical stability
      return null;
    }
    FloatPoint prevCorner = lineArr[startNo].intersectionApprox(lineArr[startNo + 1]);
    FloatPoint nextCorner = lineArr[startNo + 2].intersectionApprox(lineArr[startNo + 3]);

    // create a line approximately through currCorner, whose
    // direction is about the middle of the directions of the
    // previous and the next line.
    // Translations of this line are used to cut off the corner.
    Direction prevDir = lineArr[startNo + 1].direction();
    Direction nextDir = lineArr[startNo + 2].direction();
    Direction middleDir = prevDir.middleApprox(nextDir);
    Line translateLine = Line.getInstance(currCorner.round(), middleDir);
    double prevDist = translateLine.signedDistance(prevCorner);
    double nextDist = translateLine.signedDistance(nextCorner);
    FloatPoint nearestPoint;
    double maxTranslateDist;
    if (Math.abs(prevDist) < Math.abs(nextDist)) {
      nearestPoint = prevCorner;
      maxTranslateDist = prevDist;
    } else {
      nearestPoint = nextCorner;
      maxTranslateDist = nextDist;
    }
    if (Math.abs(maxTranslateDist) < 1) {
      return null;
    }
    Line[] currLines = new Line[lineArr.length + 1];
    System.arraycopy(lineArr, 0, currLines, 0, startNo + 2);
    System.arraycopy(lineArr, startNo + 2, currLines, startNo + 3, currLines.length - startNo - 3);
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestPoint = translateLine.sideOf(nearestPoint);
    int sign = Signum.asInt(maxTranslateDist);
    Line result = null;
    while (Math.abs(deltaDist) > this.minTranslateDist) {
      boolean checkOk = false;
      Line newLine = translateLine.translate(-translateDist);
      Side newLineSideOfNearestPoint = newLine.sideOf(nearestPoint);
      if (newLineSideOfNearestPoint == sideOfNearestPoint
          || newLineSideOfNearestPoint == Side.COLLINEAR) {
        currLines[startNo + 2] = newLine;
        Polyline tmp = new Polyline(currLines);

        if (tmp.arr.length == currLines.length) {
          TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, startNo + 1);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currLines[startNo + 2];
          if (translateDist == maxTranslateDist) {
            // biggest possible change
            break;
          }
          translateDist += deltaDist;
        } else {
          translateDist -= deltaDist;
        }
      } else { // moved a little bit too far at the first time because of numerical inaccuracy
        double shortenValue = sign * 0.5;
        maxTranslateDist -= shortenValue;
        translateDist -= shortenValue;
        deltaDist -= shortenValue;
      }
    }
    if (result == null) {
      return null;
    }

    if (board.changedArea != null) {
      FloatPoint newPrevCorner = currLines[startNo].intersectionApprox(currLines[startNo + 1]);
      FloatPoint newNextCorner = currLines[startNo + 3].intersectionApprox(currLines[startNo + 4]);
      board.changedArea.join(newPrevCorner, currLayer);
      board.changedArea.join(newNextCorner, currLayer);
    }
    return result;
  }

  @Override
  protected Line repositionLine(Line[] lineArr, int startNo) {
    if (lineArr.length - startNo < 5) {
      return null;
    }
    if (currClipShape != null) {
      // check, that the corners of the line to translate are inside
      // the clip shape
      for (int i = 1; i < 3; i++) {
        FloatPoint currCorner = lineArr[startNo + i].intersectionApprox(lineArr[startNo + i + 1]);
        if (!currClipShape.contains(currCorner)) {
          return null;
        }
      }
    }
    Line translateLine = lineArr[startNo + 2];
    FloatPoint prevCorner = lineArr[startNo].intersectionApprox(lineArr[startNo + 1]);
    FloatPoint nextCorner = lineArr[startNo + 3].intersectionApprox(lineArr[startNo + 4]);
    double prevDist = translateLine.signedDistance(prevCorner);
    int cornersSkippedBefore = 0;
    int cornersSkippedAfter = 0;
    final double epsilon = 0.001;
    while (Math.abs(prevDist) < epsilon) {
      // move also all lines through the start corner of the line to translate
      ++cornersSkippedBefore;
      int currNo = startNo - cornersSkippedBefore;
      if (currNo < 0) {
        // the first corner is on the line to translate
        return null;
      }
      prevCorner = lineArr[currNo].intersectionApprox(lineArr[currNo + 1]);
      prevDist = translateLine.signedDistance(prevCorner);
    }
    double nextDist = translateLine.signedDistance(nextCorner);
    while (Math.abs(nextDist) < epsilon) {
      // move also all lines through the end corner of the line to translate
      ++cornersSkippedAfter;
      int currNo = startNo + 3 + cornersSkippedAfter;
      if (currNo >= lineArr.length - 2) {
        // the last corner is on the line to translate
        return null;
      }
      nextCorner = lineArr[currNo].intersectionApprox(lineArr[currNo + 1]);
      nextDist = translateLine.signedDistance(nextCorner);
    }
    if (Signum.of(prevDist) != Signum.of(nextDist)) {
      // the 2 corners are at different sides of translateLine
      return null;
    }
    FloatPoint nearestPoint;
    double maxTranslateDist;
    if (Math.abs(prevDist) < Math.abs(nextDist)) {
      nearestPoint = prevCorner;
      maxTranslateDist = prevDist;
    } else {
      nearestPoint = nextCorner;
      maxTranslateDist = nextDist;
    }
    Line[] currLines = new Line[lineArr.length];
    System.arraycopy(lineArr, 0, currLines, 0, startNo + 2);
    System.arraycopy(lineArr, startNo + 3, currLines, startNo + 3, currLines.length - startNo - 3);
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestPoint = translateLine.sideOf(nearestPoint);
    int sign = Signum.asInt(maxTranslateDist);
    Line result = null;
    boolean firstTime = true;
    while (firstTime || Math.abs(deltaDist) > this.minTranslateDist) {
      boolean checkOk = false;
      Line newLine = translateLine.translate(-translateDist);
      if (firstTime && Math.abs(translateDist) < 1) {
        if (newLine.equals(translateLine)) {
          // try the parallel line through the nearestPoint
          IntPoint roundedNearestPoint = nearestPoint.round();
          if (nearestPoint.distance(roundedNearestPoint.toFloat()) < Math.abs(translateDist)) {
            newLine = Line.getInstance(roundedNearestPoint, translateLine.direction());
          }
          firstTime = false;
        }
        if (newLine.equals(translateLine)) {
          return null;
        }
      }
      Side newLineSideOfNearestPoint = newLine.sideOf(nearestPoint);
      if (newLineSideOfNearestPoint == sideOfNearestPoint
          || newLineSideOfNearestPoint == Side.COLLINEAR) {
        firstTime = false;
        currLines[startNo + 2] = newLine;
        // cornersSkippedBefore > 0 or cornersSkippedAfter > 0
        // happens very rarely. But this handling seems to be
        // important because there are situations which no other
        // tightening function can solve. For example when 3 or more
        // consecutive corners are equal.
        Line prevTranslatedLine = newLine;
        for (int i = 0; i < cornersSkippedBefore; i++) {
          // Translate the previous lines onto or past the
          // intersection of newLine with the first untranslated line.
          int prevLineNo = startNo + 1 - cornersSkippedBefore;
          FloatPoint currPrevCorner = prevTranslatedLine.intersectionApprox(currLines[prevLineNo]);
          Line currTranslateLine = lineArr[startNo + 1 - i];
          double currTranslateDist = currTranslateLine.signedDistance(currPrevCorner);
          prevTranslatedLine = currTranslateLine.translate(-currTranslateDist);
          currLines[startNo + 1 - i] = prevTranslatedLine;
        }
        prevTranslatedLine = newLine;
        for (int i = 0; i < cornersSkippedAfter; i++) {
          // Translate the next lines onto or past the
          // intersection of newLine with the first untranslated line.
          int nextLineNo = startNo + 3 + cornersSkippedAfter;
          FloatPoint currNextCorner = prevTranslatedLine.intersectionApprox(currLines[nextLineNo]);
          Line currTranslateLine = lineArr[startNo + 3 + i];
          double currTranslateDist = currTranslateLine.signedDistance(currNextCorner);
          prevTranslatedLine = currTranslateLine.translate(-currTranslateDist);
          currLines[startNo + 3 + i] = prevTranslatedLine;
        }
        Polyline tmp = new Polyline(currLines);

        if (tmp.arr.length == currLines.length) {
          TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, startNo + 1);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currLines[startNo + 2];
          if (translateDist == maxTranslateDist) {
            // biggest possible change
            break;
          }
          translateDist += deltaDist;
        } else {
          translateDist -= deltaDist;
        }
      } else { // moved a little bit too far at the first time because of numerical inaccuracy
        double shortenValue = sign * 0.5;
        maxTranslateDist -= shortenValue;
        translateDist -= shortenValue;
        deltaDist -= shortenValue;
      }
    }
    if (result == null) {
      return null;
    }

    if (board.changedArea != null) {
      FloatPoint newPrevCorner = currLines[startNo].intersectionApprox(currLines[startNo + 1]);
      FloatPoint newNextCorner = currLines[startNo + 3].intersectionApprox(currLines[startNo + 4]);
      board.changedArea.join(newPrevCorner, currLayer);
      board.changedArea.join(newNextCorner, currLayer);
    }
    return result;
  }

  private Polyline skipLines(Polyline polyline) {
    for (int i = 1; i < polyline.arr.length - 3; i++) {
      for (int j = 0; j <= 1; j++) {
        FloatPoint corner1;
        FloatPoint corner2;
        Line currLine;
        if (j == 0) { // try to skip the line before the i+2-th line
          currLine = polyline.arr[i + 2];
          corner1 = polyline.cornerApprox(i);
          corner2 = polyline.cornerApprox(i - 1);
        } else { // try to skip the line after i-th line
          currLine = polyline.arr[i];
          corner1 = polyline.cornerApprox(i + 1);
          corner2 = polyline.cornerApprox(i + 2);
        }
        boolean inClipShape =
            currClipShape == null
                || currClipShape.contains(corner1) && currClipShape.contains(corner2);
        if (!inClipShape) {
          continue;
        }

        Side side1 = currLine.sideOf(corner1);
        Side side2 = currLine.sideOf(corner2);
        if (side1 != side2) {
          // the two corners are on different sides of the line
          Polyline reducedPolyline = polyline.skipLines(i + 1, i + 1);
          if (reducedPolyline.arr.length == polyline.arr.length - 1) {
            int shapeNo = i - 1;
            if (j == 0) {
              ++shapeNo;
            }
            TileShape shapeToCheck = reducedPolyline.offsetShape(currHalfWidth, shapeNo);
            if (board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
              if (board.changedArea != null) {
                board.changedArea.join(corner1, currLayer);
                board.changedArea.join(corner2, currLayer);
              }
              return reducedPolyline;
            }
          }
        }
        // now try skipping 2 lines
        if (i >= polyline.arr.length - 4) {
          break;
        }
        FloatPoint corner3;
        if (j == 1) {
          corner3 = polyline.cornerApprox(i + 3);
        } else {
          corner3 = polyline.cornerApprox(i + 1);
        }
        if (currClipShape != null && !currClipShape.contains(corner3)) {
          continue;
        }
        if (j == 0) {
          // currLine is 1 line later than in the case skipping 1 line
          // when coming from behind
          currLine = polyline.arr[i + 3];
          side1 = currLine.sideOf(corner1);
          side2 = currLine.sideOf(corner2);
        } else {
          side1 = currLine.sideOf(corner3);
        }
        if (side1 != side2) {
          // the two corners are on different sides of the line
          Polyline reducedPolyline = polyline.skipLines(i + 1, i + 2);
          if (reducedPolyline.arr.length == polyline.arr.length - 2) {
            int shapeNo = i - 1;
            if (j == 0) {
              ++shapeNo;
            }
            TileShape shapeToCheck = reducedPolyline.offsetShape(currHalfWidth, shapeNo);
            if (board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
              if (board.changedArea != null) {
                board.changedArea.join(corner1, currLayer);
                board.changedArea.join(corner2, currLayer);
                board.changedArea.join(corner3, currLayer);
              }
              return reducedPolyline;
            }
          }
        }
      }
    }
    return polyline;
  }

  @Override
  Polyline smoothenStartCornerAtTrace(PolylineTrace trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = trace.polyline();
    Point currEndCorner = tracePolyline.corner(0);

    if (this.currClipShape != null && this.currClipShape.isOutside(currEndCorner)) {
      return null;
    }

    Point currPrevEndCorner = tracePolyline.corner(1);
    boolean skipShortSegment =
        !(currEndCorner instanceof IntPoint)
            && currEndCorner.toFloat().distanceSquare(currPrevEndCorner.toFloat()) < SKIP_LENGTH;
    int startLineNo = 1;
    if (skipShortSegment) {
      if (tracePolyline.cornerCount() < 3) {
        return null;
      }
      currPrevEndCorner = tracePolyline.corner(2);
      ++startLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[startLineNo].direction();
    Direction prevLineDirection = tracePolyline.arr[startLineNo + 1].direction();

    Collection<Item> contactList = trace.getStartContacts();
    for (Item currContact : contactList) {
      if (currContact instanceof PolylineTrace contactTrace && !currContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        FloatPoint currOtherTraceCornerApprox;
        Line currOtherTraceLine;
        Line currOtherPrevTraceLine;
        if (contactTracePolyline.firstCorner().equals(currEndCorner)) {
          currOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
          currOtherTraceLine = contactTracePolyline.arr[1];
          currOtherPrevTraceLine = contactTracePolyline.arr[2];
        } else {
          int currCornerNo = contactTracePolyline.cornerCount() - 2;
          currOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currCornerNo);
          currOtherTraceLine = contactTracePolyline.arr[currCornerNo + 1].opposite();
          currOtherPrevTraceLine = contactTracePolyline.arr[currCornerNo];
        }
        Side currPrevCornerSide = currPrevEndCorner.sideOf(currOtherTraceLine);
        Signum currProjection = lineDirection.projection(currOtherTraceLine.direction());
        boolean otherTraceFound = false;
        if (currProjection == Signum.POSITIVE && currPrevCornerSide != Side.COLLINEAR) {
          acuteAngle = true;
          otherTraceFound = true;

        } else if (currProjection == Signum.ZERO && tracePolyline.cornerCount() > 2) {
          if (prevLineDirection.projection(currOtherTraceLine.direction()) == Signum.POSITIVE) {
            bend = true;
            otherTraceFound = true;
          }
        }
        if (otherTraceFound) {
          otherTraceCornerApprox = currOtherTraceCornerApprox;
          otherTraceLine = currOtherTraceLine;
          prevCornerSide = currPrevCornerSide;
          otherPrevTraceLine = currOtherPrevTraceLine;
        }
      } else {
        return null;
      }
    }
    int newLineCount = tracePolyline.arr.length + 1;
    int diff = 1;
    if (skipShortSegment) {
      --newLineCount;
      --diff;
    }
    if (acuteAngle) {
      Direction newLineDir;
      if (prevCornerSide == Side.ON_THE_LEFT) {
        newLineDir = otherTraceLine.direction().turn45Degree(2);
      } else {
        newLineDir = otherTraceLine.direction().turn45Degree(6);
      }
      Line translateLine = Line.getInstance(currEndCorner.toFloat().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currHalfWidth;
      double prevCornerDist = Math.abs(translateLine.signedDistance(currPrevEndCorner.toFloat()));
      double otherDist = Math.abs(translateLine.signedDistance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.sideOf(currPrevEndCorner) == Side.ON_THE_LEFT) {
          translateDist = -translateDist;
        }
        Line addLine = translateLine.translate(translateDist);
        // construct the new trace polyline.
        Line[] newLines = new Line[newLineCount];
        newLines[0] = otherTraceLine;
        newLines[1] = addLine;
        System.arraycopy(tracePolyline.arr, 2 - diff, newLines, 2, newLines.length - 2);
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[newLineCount];
      checkLineArr[0] = otherPrevTraceLine;
      checkLineArr[1] = otherTraceLine;
      System.arraycopy(tracePolyline.arr, 2 - diff, checkLineArr, 2, checkLineArr.length - 2);
      Line newLine = repositionLine(checkLineArr, 0);
      if (newLine != null) {
        Line[] newLines = new Line[tracePolyline.arr.length];
        newLines[0] = otherTraceLine;
        newLines[1] = newLine;
        System.arraycopy(tracePolyline.arr, 2, newLines, 2, newLines.length - 2);
        return new Polyline(newLines);
      }
    }
    return null;
  }

  @Override
  Polyline smoothenEndCornerAtTrace(PolylineTrace trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = trace.polyline();
    Point currEndCorner = tracePolyline.lastCorner();

    if (this.currClipShape != null && this.currClipShape.isOutside(currEndCorner)) {
      return null;
    }

    Point currPrevEndCorner = tracePolyline.corner(tracePolyline.cornerCount() - 2);
    boolean skipShortSegment =
        !(currEndCorner instanceof IntPoint)
            && currEndCorner.toFloat().distanceSquare(currPrevEndCorner.toFloat()) < SKIP_LENGTH;
    int endLineNo = tracePolyline.arr.length - 2;
    if (skipShortSegment) {
      if (tracePolyline.cornerCount() < 3) {
        return null;
      }
      currPrevEndCorner = tracePolyline.corner(tracePolyline.cornerCount() - 3);
      --endLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[endLineNo].direction().opposite();
    Direction prevLineDirection = tracePolyline.arr[endLineNo].direction().opposite();

    Collection<Item> contactList = trace.getEndContacts();
    for (Item currContact : contactList) {
      if (currContact instanceof PolylineTrace contactTrace && !currContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        if (contactTracePolyline.cornerCount() > 2) {
          FloatPoint currOtherTraceCornerApprox;
          Line currOtherTraceLine;
          Line currOtherPrevTraceLine;
          if (contactTracePolyline.firstCorner().equals(currEndCorner)) {
            currOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
            currOtherTraceLine = contactTracePolyline.arr[1];
            currOtherPrevTraceLine = contactTracePolyline.arr[2];
          } else {
            int currCornerNo = contactTracePolyline.cornerCount() - 2;
            currOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currCornerNo);
            currOtherTraceLine = contactTracePolyline.arr[currCornerNo + 1].opposite();
            currOtherPrevTraceLine = contactTracePolyline.arr[currCornerNo];
          }
          Side currPrevCornerSide = currPrevEndCorner.sideOf(currOtherTraceLine);
          Signum currProjection = lineDirection.projection(currOtherTraceLine.direction());
          boolean otherTraceFound = false;
          if (currProjection == Signum.POSITIVE && currPrevCornerSide != Side.COLLINEAR) {
            acuteAngle = true;
            otherTraceFound = true;
          } else if (currProjection == Signum.ZERO && tracePolyline.cornerCount() > 2) {
            if (prevLineDirection.projection(currOtherTraceLine.direction()) == Signum.POSITIVE) {
              bend = true;
              otherTraceFound = true;
            }
          }
          if (otherTraceFound) {
            otherTraceCornerApprox = currOtherTraceCornerApprox;
            otherTraceLine = currOtherTraceLine;
            prevCornerSide = currPrevCornerSide;
            otherPrevTraceLine = currOtherPrevTraceLine;
          }
        }
      } else {
        return null;
      }
    }

    int newLineCount = tracePolyline.arr.length + 1;
    int diff = 0;
    if (skipShortSegment) {
      --newLineCount;
      ++diff;
    }

    if (acuteAngle) {
      Direction newLineDir;
      if (prevCornerSide == Side.ON_THE_LEFT) {
        newLineDir = otherTraceLine.direction().turn45Degree(6);
      } else {
        newLineDir = otherTraceLine.direction().turn45Degree(2);
      }
      Line translateLine = Line.getInstance(currEndCorner.toFloat().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currHalfWidth;
      double prevCornerDist = Math.abs(translateLine.signedDistance(currPrevEndCorner.toFloat()));
      double otherDist = Math.abs(translateLine.signedDistance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.sideOf(currPrevEndCorner) == Side.ON_THE_LEFT) {
          translateDist = -translateDist;
        }
        Line addLine = translateLine.translate(translateDist);
        // construct the new trace polyline.
        Line[] newLines = new Line[newLineCount];
        System.arraycopy(tracePolyline.arr, 0, newLines, 0, tracePolyline.arr.length - 1);
        newLines[newLines.length - 2] = addLine;
        newLines[newLines.length - 1] = otherTraceLine;
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[newLineCount];
      System.arraycopy(tracePolyline.arr, diff, checkLineArr, 0, checkLineArr.length - 2);
      checkLineArr[checkLineArr.length - 2] = otherTraceLine;
      checkLineArr[checkLineArr.length - 1] = otherPrevTraceLine;
      Line newLine = repositionLine(checkLineArr, checkLineArr.length - 5);
      if (newLine != null) {
        Line[] newLines = new Line[tracePolyline.arr.length];
        System.arraycopy(tracePolyline.arr, 0, newLines, 0, newLines.length - 2);
        newLines[newLines.length - 2] = newLine;
        newLines[newLines.length - 1] = otherTraceLine;
        return new Polyline(newLines);
      }
    }
    return null;
  }
}
