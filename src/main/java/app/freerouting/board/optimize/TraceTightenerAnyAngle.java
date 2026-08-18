package app.freerouting.board.optimize;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.trace.PolylineTrace;
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
class TraceTightenerAnyAngle extends TraceTightener {

  private static final double SKIP_LENGTH = 10.0;

  TraceTightenerAnyAngle(
      RoutingBoard board,
      int[] onlyNetNoArr,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    super(board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
  }

  @Override
  public Polyline pullTight(Polyline polyline) {
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
    if (polyline.lines.length < 4) {
      return polyline;
    }
    int lastIndex = polyline.lines.length - 4;

    Line[] newLines = new Line[polyline.lines.length];
    newLines[0] = polyline.lines[0];
    newLines[1] = polyline.lines[1];

    int newLineIndex = 1;

    boolean polylineChanged = false;

    Line[] currentLines = new Line[3];

    for (int i = 0; i <= lastIndex; i++) {
      boolean skipLine = false;
      FloatPoint newA = newLines[newLineIndex - 1].intersectionApprox(newLines[newLineIndex]);
      FloatPoint newB = polyline.cornerApprox(i + 2);
      boolean inClipShape =
          currentClipShape == null
              || currentClipShape.contains(newA)
                  && currentClipShape.contains(newB)
                  && currentClipShape.contains(polyline.cornerApprox(newLineIndex));

      if (inClipShape) {
        currentLines[1] = new Line(newA.round(), newB.round());
        boolean ok = true;
        if (newLineIndex == 1) {
          if (!(polyline.firstCorner() instanceof IntPoint)) {
            // first corner must not be changed
            ok = false;
          } else {
            Direction dir = currentLines[1].direction();
            currentLines[0] = Line.getInstance(polyline.firstCorner(), dir.turn45Degree(2));
          }
        } else {
          currentLines[0] = newLines[newLineIndex - 1];
        }
        if (i == lastIndex) {
          if (!(polyline.lastCorner() instanceof IntPoint)) {
            // last corner must not be changed
            ok = false;
          } else {
            Direction dir = currentLines[1].direction();
            currentLines[2] = Line.getInstance(polyline.lastCorner(), dir.turn45Degree(2));
          }
        } else {
          currentLines[2] = polyline.lines[i + 3];
        }

        // check, if the intersection of currentLines[0] and currentLines[1]
        // is near newA and the intersection of currentLines[0] and
        // currentLines[1] and currentLines[2] is near newB.
        // There may be numerical stability problems with
        // near parallel lines.

        final double checkDist = 100;
        if (ok) {
          FloatPoint checkIs = currentLines[0].intersectionApprox(currentLines[1]);
          double dist = checkIs.distanceSquare(newA);

          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok) {
          FloatPoint checkIs = currentLines[1].intersectionApprox(currentLines[2]);
          double dist = checkIs.distanceSquare(newB);
          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok && i == 1 && !(polyline.firstCorner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second corner of the new polyline
          // is on the same side of the trace as the third corner. (There may be splitting problems)
          Point newCorner = currentLines[0].intersection(currentLines[1]);
          if (newCorner.sideOf(newLines[0]) != polyline.corner(1).sideOf(newLines[0])) {
            ok = false;
          }
        }
        if (ok && i == lastIndex - 1 && !(polyline.lastCorner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second last corner of the new polyline
          // is on the same side of the trace as the third last corner (There may be splitting
          // problems)
          Point newCorner = currentLines[1].intersection(currentLines[2]);
          if (newCorner.sideOf(newLines[0])
              != polyline.corner(polyline.cornerCount() - 2).sideOf(newLines[0])) {
            ok = false;
          }
        }
        Polyline currentPolyline = null;
        if (ok) {
          FloatPoint skipCorner = newLines[newLineIndex].intersectionApprox(polyline.lines[i + 2]);
          currentPolyline = new Polyline(currentLines);
          if (currentPolyline.lines.length != 3) {
            ok = false;
          }
          double lengthBefore = skipCorner.distance(newA) + skipCorner.distance(newB);
          double lengthAfter = currentPolyline.lengthApprox() + 1.5;
          // 1.5 added because of possible inaccuracy SQRT_2
          // by twice rounding.
          if (lengthAfter >= lengthBefore) {
            // May happen from rounding to integer.
            // Prevent infinite loop.
            ok = false;
          }
        }

        if (ok) {
          TileShape shapeToCheck = currentPolyline.offsetShape(currentHalfWidth, 0);
          skipLine =
              board.checkTraceShape(
                  shapeToCheck,
                  currentLayer,
                  currentNetNumbers,
                  currentClearanceClassIndex,
                  this.contactPins);
        }
      }
      if (skipLine) {
        polylineChanged = true;
        newLines[newLineIndex] = currentLines[1];
        if (newLineIndex == 1) {
          // make the first line perpendicular to the current line
          newLines[0] = currentLines[0];
        }
        if (i == lastIndex) {
          // make the last line perpendicular to the current line
          ++newLineIndex;
          newLines[newLineIndex] = currentLines[2];
        }
        if (board.changedArea != null) {
          board.changedArea.join(newA, currentLayer);
          board.changedArea.join(newB, currentLayer);
        }
      } else {
        ++newLineIndex;
        newLines[newLineIndex] = polyline.lines[i + 2];
        if (i == lastIndex) {
          ++newLineIndex;
          newLines[newLineIndex] = polyline.lines[i + 3];
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
    if (polyline.lines.length < 4) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lines = new Line[polyline.lines.length];
    System.arraycopy(polyline.lines, 0, lines, 0, lines.length);

    for (int i = 0; i < lines.length - 3; i++) {
      Line newLine = smoothenCorner(lines, i);
      if (newLine != null) {
        polylineChanged = true;
        // add the new line into the line array
        Line[] tmpLines = new Line[lines.length + 1];
        System.arraycopy(lines, 0, tmpLines, 0, i + 2);
        tmpLines[i + 2] = newLine;
        System.arraycopy(lines, i + 2, tmpLines, i + 3, tmpLines.length - (i + 3));
        lines = tmpLines;
        ++i;
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lines);
  }

  /** Tries to shorten polyline by relocating its lines. */
  @Override
  Polyline repositionLines(Polyline polyline) {
    if (polyline.lines.length < 5) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lines = new Line[polyline.lines.length];
    System.arraycopy(polyline.lines, 0, lines, 0, lines.length);
    for (int i = 0; i < lines.length - 4; i++) {
      Line newLine = repositionLine(lines, i);
      if (newLine != null) {
        polylineChanged = true;
        lines[i + 2] = newLine;
        if (lines[i + 2].isParallel(lines[i + 1]) || lines[i + 2].isParallel(lines[i + 3])) {
          // calculation of corners not possible before skipping
          // parallel lines
          break;
        }
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lines);
  }

  /**
   * Tries to reduce the number of lines of polyline by moving lines parallel beyond the
   * intersection of the next or previous lines.
   */
  private Polyline reduceLines(Polyline polyline) {
    if (polyline.lines.length < 6) {
      return polyline;
    }
    boolean polylineChanged = false;
    Line[] lines = polyline.lines;
    for (int i = 2; i < lines.length - 2; i++) {
      FloatPoint prevCorner = lines[i - 2].intersectionApprox(lines[i - 1]);
      FloatPoint nextCorner = lines[i + 1].intersectionApprox(lines[i + 2]);
      boolean inClipShape =
          currentClipShape == null
              || currentClipShape.contains(prevCorner) && currentClipShape.contains(nextCorner);
      if (!inClipShape) {
        continue;
      }
      Line translateLine = lines[i];
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
        FloatPoint prevPrevCorner = lines[i - 3].intersectionApprox(lines[i - 2]);
        if (newLine.sideOf(prevPrevCorner) != lineSide) {
          continue;
        }
      }
      if (crossedCornersAfterCount > 0) {
        if (i >= lines.length - 3) {
          continue;
        }
        FloatPoint nextNextCorner = lines[i + 2].intersectionApprox(lines[i + 3]);
        if (newLine.sideOf(nextNextCorner) != lineSide) {
          continue;
        }
      }
      Line[] currentLines =
          new Line[lines.length - crossedCornersBeforeCount - crossedCornersAfterCount];
      int keepBeforeInd = i - crossedCornersBeforeCount;
      System.arraycopy(lines, 0, currentLines, 0, keepBeforeInd);
      currentLines[keepBeforeInd] = newLine;
      System.arraycopy(
          lines,
          i + 1 + crossedCornersAfterCount,
          currentLines,
          keepBeforeInd + 1,
          currentLines.length - (keepBeforeInd + 1));
      Polyline tmp = new Polyline(currentLines);
      boolean checkOk = false;
      if (tmp.lines.length == currentLines.length) {
        TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, keepBeforeInd - 1);
        checkOk =
            board.checkTraceShape(
                shapeToCheck,
                currentLayer,
                currentNetNumbers,
                currentClearanceClassIndex,
                this.contactPins);
      }
      if (checkOk) {
        if (board.changedArea != null) {
          board.changedArea.join(prevCorner, currentLayer);
          board.changedArea.join(nextCorner, currentLayer);
        }
        polylineChanged = true;
        lines = currentLines;
        --i;
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return new Polyline(lines);
  }

  private Line smoothenCorner(Line[] lines, int startNo) {
    if (lines.length - startNo < 4) {
      return null;
    }
    FloatPoint currentCorner = lines[startNo + 1].intersectionApprox(lines[startNo + 2]);
    if (currentClipShape != null && !currentClipShape.contains(currentCorner)) {
      return null;
    }
    double cosinusAngle = lines[startNo + 1].cosAngle(lines[startNo + 2]);
    if (cosinusAngle > c_max_cos_angle) {
      // lines are already nearly parallel, don't divide angle any further
      // because of problems with numerical stability
      return null;
    }
    FloatPoint prevCorner = lines[startNo].intersectionApprox(lines[startNo + 1]);
    FloatPoint nextCorner = lines[startNo + 2].intersectionApprox(lines[startNo + 3]);

    // create a line approximately through currentCorner, whose
    // direction is about the middle of the directions of the
    // previous and the next line.
    // Translations of this line are used to cut off the corner.
    Direction prevDir = lines[startNo + 1].direction();
    Direction nextDir = lines[startNo + 2].direction();
    Direction middleDir = prevDir.middleApprox(nextDir);
    Line translateLine = Line.getInstance(currentCorner.round(), middleDir);
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
    Line[] currentLines = new Line[lines.length + 1];
    System.arraycopy(lines, 0, currentLines, 0, startNo + 2);
    System.arraycopy(
        lines, startNo + 2, currentLines, startNo + 3, currentLines.length - startNo - 3);
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
        currentLines[startNo + 2] = newLine;
        Polyline tmp = new Polyline(currentLines);

        if (tmp.lines.length == currentLines.length) {
          TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, startNo + 1);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck,
                  currentLayer,
                  currentNetNumbers,
                  currentClearanceClassIndex,
                  this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currentLines[startNo + 2];
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
      FloatPoint newPrevCorner =
          currentLines[startNo].intersectionApprox(currentLines[startNo + 1]);
      FloatPoint newNextCorner =
          currentLines[startNo + 3].intersectionApprox(currentLines[startNo + 4]);
      board.changedArea.join(newPrevCorner, currentLayer);
      board.changedArea.join(newNextCorner, currentLayer);
    }
    return result;
  }

  @Override
  protected Line repositionLine(Line[] lines, int startNo) {
    if (lines.length - startNo < 5) {
      return null;
    }
    if (currentClipShape != null) {
      // check, that the corners of the line to translate are inside
      // the clip shape
      for (int i = 1; i < 3; i++) {
        FloatPoint currentCorner = lines[startNo + i].intersectionApprox(lines[startNo + i + 1]);
        if (!currentClipShape.contains(currentCorner)) {
          return null;
        }
      }
    }
    Line translateLine = lines[startNo + 2];
    FloatPoint prevCorner = lines[startNo].intersectionApprox(lines[startNo + 1]);
    FloatPoint nextCorner = lines[startNo + 3].intersectionApprox(lines[startNo + 4]);
    double prevDist = translateLine.signedDistance(prevCorner);
    int cornersSkippedBefore = 0;
    int cornersSkippedAfter = 0;
    final double epsilon = 0.001;
    while (Math.abs(prevDist) < epsilon) {
      // move also all lines through the start corner of the line to translate
      ++cornersSkippedBefore;
      int currentNo = startNo - cornersSkippedBefore;
      if (currentNo < 0) {
        // the first corner is on the line to translate
        return null;
      }
      prevCorner = lines[currentNo].intersectionApprox(lines[currentNo + 1]);
      prevDist = translateLine.signedDistance(prevCorner);
    }
    double nextDist = translateLine.signedDistance(nextCorner);
    while (Math.abs(nextDist) < epsilon) {
      // move also all lines through the end corner of the line to translate
      ++cornersSkippedAfter;
      int currentNo = startNo + 3 + cornersSkippedAfter;
      if (currentNo >= lines.length - 2) {
        // the last corner is on the line to translate
        return null;
      }
      nextCorner = lines[currentNo].intersectionApprox(lines[currentNo + 1]);
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
    Line[] currentLines = new Line[lines.length];
    System.arraycopy(lines, 0, currentLines, 0, startNo + 2);
    System.arraycopy(
        lines, startNo + 3, currentLines, startNo + 3, currentLines.length - startNo - 3);
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
        currentLines[startNo + 2] = newLine;
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
          FloatPoint currentPrevCorner =
              prevTranslatedLine.intersectionApprox(currentLines[prevLineNo]);
          Line currentTranslateLine = lines[startNo + 1 - i];
          double currentTranslateDist = currentTranslateLine.signedDistance(currentPrevCorner);
          prevTranslatedLine = currentTranslateLine.translate(-currentTranslateDist);
          currentLines[startNo + 1 - i] = prevTranslatedLine;
        }
        prevTranslatedLine = newLine;
        for (int i = 0; i < cornersSkippedAfter; i++) {
          // Translate the next lines onto or past the
          // intersection of newLine with the first untranslated line.
          int nextLineNo = startNo + 3 + cornersSkippedAfter;
          FloatPoint currentNextCorner =
              prevTranslatedLine.intersectionApprox(currentLines[nextLineNo]);
          Line currentTranslateLine = lines[startNo + 3 + i];
          double currentTranslateDist = currentTranslateLine.signedDistance(currentNextCorner);
          prevTranslatedLine = currentTranslateLine.translate(-currentTranslateDist);
          currentLines[startNo + 3 + i] = prevTranslatedLine;
        }
        Polyline tmp = new Polyline(currentLines);

        if (tmp.lines.length == currentLines.length) {
          TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, startNo + 1);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck,
                  currentLayer,
                  currentNetNumbers,
                  currentClearanceClassIndex,
                  this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currentLines[startNo + 2];
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
      FloatPoint newPrevCorner =
          currentLines[startNo].intersectionApprox(currentLines[startNo + 1]);
      FloatPoint newNextCorner =
          currentLines[startNo + 3].intersectionApprox(currentLines[startNo + 4]);
      board.changedArea.join(newPrevCorner, currentLayer);
      board.changedArea.join(newNextCorner, currentLayer);
    }
    return result;
  }

  private Polyline skipLines(Polyline polyline) {
    for (int i = 1; i < polyline.lines.length - 3; i++) {
      for (int j = 0; j <= 1; j++) {
        FloatPoint corner1;
        FloatPoint corner2;
        Line currentLine;
        if (j == 0) { // try to skip the line before the i+2-th line
          currentLine = polyline.lines[i + 2];
          corner1 = polyline.cornerApprox(i);
          corner2 = polyline.cornerApprox(i - 1);
        } else { // try to skip the line after i-th line
          currentLine = polyline.lines[i];
          corner1 = polyline.cornerApprox(i + 1);
          corner2 = polyline.cornerApprox(i + 2);
        }
        boolean inClipShape =
            currentClipShape == null
                || currentClipShape.contains(corner1) && currentClipShape.contains(corner2);
        if (!inClipShape) {
          continue;
        }

        Side side1 = currentLine.sideOf(corner1);
        Side side2 = currentLine.sideOf(corner2);
        if (side1 != side2) {
          // the two corners are on different sides of the line
          Polyline reducedPolyline = polyline.skipLines(i + 1, i + 1);
          if (reducedPolyline.lines.length == polyline.lines.length - 1) {
            int shapeIndex = i - 1;
            if (j == 0) {
              ++shapeIndex;
            }
            TileShape shapeToCheck = reducedPolyline.offsetShape(currentHalfWidth, shapeIndex);
            if (board.checkTraceShape(
                shapeToCheck,
                currentLayer,
                currentNetNumbers,
                currentClearanceClassIndex,
                this.contactPins)) {
              if (board.changedArea != null) {
                board.changedArea.join(corner1, currentLayer);
                board.changedArea.join(corner2, currentLayer);
              }
              return reducedPolyline;
            }
          }
        }
        // now try skipping 2 lines
        if (i >= polyline.lines.length - 4) {
          break;
        }
        FloatPoint corner3;
        if (j == 1) {
          corner3 = polyline.cornerApprox(i + 3);
        } else {
          corner3 = polyline.cornerApprox(i + 1);
        }
        if (currentClipShape != null && !currentClipShape.contains(corner3)) {
          continue;
        }
        if (j == 0) {
          // currentLine is 1 line later than in the case skipping 1 line
          // when coming from behind
          currentLine = polyline.lines[i + 3];
          side1 = currentLine.sideOf(corner1);
          side2 = currentLine.sideOf(corner2);
        } else {
          side1 = currentLine.sideOf(corner3);
        }
        if (side1 != side2) {
          // the two corners are on different sides of the line
          Polyline reducedPolyline = polyline.skipLines(i + 1, i + 2);
          if (reducedPolyline.lines.length == polyline.lines.length - 2) {
            int shapeIndex = i - 1;
            if (j == 0) {
              ++shapeIndex;
            }
            TileShape shapeToCheck = reducedPolyline.offsetShape(currentHalfWidth, shapeIndex);
            if (board.checkTraceShape(
                shapeToCheck,
                currentLayer,
                currentNetNumbers,
                currentClearanceClassIndex,
                this.contactPins)) {
              if (board.changedArea != null) {
                board.changedArea.join(corner1, currentLayer);
                board.changedArea.join(corner2, currentLayer);
                board.changedArea.join(corner3, currentLayer);
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
  public Polyline smoothenStartCornerAtTrace(PolylineTrace trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = trace.polyline();
    Point currentEndCorner = tracePolyline.corner(0);

    if (this.currentClipShape != null && this.currentClipShape.isOutside(currentEndCorner)) {
      return null;
    }

    Point currentPrevEndCorner = tracePolyline.corner(1);
    boolean skipShortSegment =
        !(currentEndCorner instanceof IntPoint)
            && currentEndCorner.toFloat().distanceSquare(currentPrevEndCorner.toFloat())
                < SKIP_LENGTH;
    int startLineNo = 1;
    if (skipShortSegment) {
      if (tracePolyline.cornerCount() < 3) {
        return null;
      }
      currentPrevEndCorner = tracePolyline.corner(2);
      ++startLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.lines[startLineNo].direction();
    Direction prevLineDirection = tracePolyline.lines[startLineNo + 1].direction();

    Collection<Item> contactList = trace.getStartContacts();
    for (Item currentContact : contactList) {
      if (currentContact instanceof PolylineTrace contactTrace && !currentContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        FloatPoint currentOtherTraceCornerApprox;
        Line currentOtherTraceLine;
        Line currentOtherPrevTraceLine;
        if (contactTracePolyline.firstCorner().equals(currentEndCorner)) {
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
          currentOtherTraceLine = contactTracePolyline.lines[1];
          currentOtherPrevTraceLine = contactTracePolyline.lines[2];
        } else {
          int currentCornerNo = contactTracePolyline.cornerCount() - 2;
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currentCornerNo);
          currentOtherTraceLine = contactTracePolyline.lines[currentCornerNo + 1].opposite();
          currentOtherPrevTraceLine = contactTracePolyline.lines[currentCornerNo];
        }
        Side currentPrevCornerSide = currentPrevEndCorner.sideOf(currentOtherTraceLine);
        Signum currentProjection = lineDirection.projection(currentOtherTraceLine.direction());
        boolean otherTraceFound = false;
        if (currentProjection == Signum.POSITIVE && currentPrevCornerSide != Side.COLLINEAR) {
          acuteAngle = true;
          otherTraceFound = true;

        } else if (currentProjection == Signum.ZERO && tracePolyline.cornerCount() > 2) {
          if (prevLineDirection.projection(currentOtherTraceLine.direction()) == Signum.POSITIVE) {
            bend = true;
            otherTraceFound = true;
          }
        }
        if (otherTraceFound) {
          otherTraceCornerApprox = currentOtherTraceCornerApprox;
          otherTraceLine = currentOtherTraceLine;
          prevCornerSide = currentPrevCornerSide;
          otherPrevTraceLine = currentOtherPrevTraceLine;
        }
      } else {
        return null;
      }
    }
    int newLineCount = tracePolyline.lines.length + 1;
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
      Line translateLine = Line.getInstance(currentEndCorner.toFloat().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currentHalfWidth;
      double prevCornerDist =
          Math.abs(translateLine.signedDistance(currentPrevEndCorner.toFloat()));
      double otherDist = Math.abs(translateLine.signedDistance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.sideOf(currentPrevEndCorner) == Side.ON_THE_LEFT) {
          translateDist = -translateDist;
        }
        Line addLine = translateLine.translate(translateDist);
        // construct the new trace polyline.
        Line[] newLines = new Line[newLineCount];
        newLines[0] = otherTraceLine;
        newLines[1] = addLine;
        System.arraycopy(tracePolyline.lines, 2 - diff, newLines, 2, newLines.length - 2);
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[newLineCount];
      checkLineArr[0] = otherPrevTraceLine;
      checkLineArr[1] = otherTraceLine;
      System.arraycopy(tracePolyline.lines, 2 - diff, checkLineArr, 2, checkLineArr.length - 2);
      Line newLine = repositionLine(checkLineArr, 0);
      if (newLine != null) {
        Line[] newLines = new Line[tracePolyline.lines.length];
        newLines[0] = otherTraceLine;
        newLines[1] = newLine;
        System.arraycopy(tracePolyline.lines, 2, newLines, 2, newLines.length - 2);
        return new Polyline(newLines);
      }
    }
    return null;
  }

  @Override
  public Polyline smoothenEndCornerAtTrace(PolylineTrace trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = trace.polyline();
    Point currentEndCorner = tracePolyline.lastCorner();

    if (this.currentClipShape != null && this.currentClipShape.isOutside(currentEndCorner)) {
      return null;
    }

    Point currentPrevEndCorner = tracePolyline.corner(tracePolyline.cornerCount() - 2);
    boolean skipShortSegment =
        !(currentEndCorner instanceof IntPoint)
            && currentEndCorner.toFloat().distanceSquare(currentPrevEndCorner.toFloat())
                < SKIP_LENGTH;
    int endLineNo = tracePolyline.lines.length - 2;
    if (skipShortSegment) {
      if (tracePolyline.cornerCount() < 3) {
        return null;
      }
      currentPrevEndCorner = tracePolyline.corner(tracePolyline.cornerCount() - 3);
      --endLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.lines[endLineNo].direction().opposite();
    Direction prevLineDirection = tracePolyline.lines[endLineNo].direction().opposite();

    Collection<Item> contactList = trace.getEndContacts();
    for (Item currentContact : contactList) {
      if (currentContact instanceof PolylineTrace contactTrace && !currentContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        if (contactTracePolyline.cornerCount() > 2) {
          FloatPoint currentOtherTraceCornerApprox;
          Line currentOtherTraceLine;
          Line currentOtherPrevTraceLine;
          if (contactTracePolyline.firstCorner().equals(currentEndCorner)) {
            currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
            currentOtherTraceLine = contactTracePolyline.lines[1];
            currentOtherPrevTraceLine = contactTracePolyline.lines[2];
          } else {
            int currentCornerNo = contactTracePolyline.cornerCount() - 2;
            currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currentCornerNo);
            currentOtherTraceLine = contactTracePolyline.lines[currentCornerNo + 1].opposite();
            currentOtherPrevTraceLine = contactTracePolyline.lines[currentCornerNo];
          }
          Side currentPrevCornerSide = currentPrevEndCorner.sideOf(currentOtherTraceLine);
          Signum currentProjection = lineDirection.projection(currentOtherTraceLine.direction());
          boolean otherTraceFound = false;
          if (currentProjection == Signum.POSITIVE && currentPrevCornerSide != Side.COLLINEAR) {
            acuteAngle = true;
            otherTraceFound = true;
          } else if (currentProjection == Signum.ZERO && tracePolyline.cornerCount() > 2) {
            if (prevLineDirection.projection(currentOtherTraceLine.direction())
                == Signum.POSITIVE) {
              bend = true;
              otherTraceFound = true;
            }
          }
          if (otherTraceFound) {
            otherTraceCornerApprox = currentOtherTraceCornerApprox;
            otherTraceLine = currentOtherTraceLine;
            prevCornerSide = currentPrevCornerSide;
            otherPrevTraceLine = currentOtherPrevTraceLine;
          }
        }
      } else {
        return null;
      }
    }

    int newLineCount = tracePolyline.lines.length + 1;
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
      Line translateLine = Line.getInstance(currentEndCorner.toFloat().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currentHalfWidth;
      double prevCornerDist =
          Math.abs(translateLine.signedDistance(currentPrevEndCorner.toFloat()));
      double otherDist = Math.abs(translateLine.signedDistance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.sideOf(currentPrevEndCorner) == Side.ON_THE_LEFT) {
          translateDist = -translateDist;
        }
        Line addLine = translateLine.translate(translateDist);
        // construct the new trace polyline.
        Line[] newLines = new Line[newLineCount];
        System.arraycopy(tracePolyline.lines, 0, newLines, 0, tracePolyline.lines.length - 1);
        newLines[newLines.length - 2] = addLine;
        newLines[newLines.length - 1] = otherTraceLine;
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[newLineCount];
      System.arraycopy(tracePolyline.lines, diff, checkLineArr, 0, checkLineArr.length - 2);
      checkLineArr[checkLineArr.length - 2] = otherTraceLine;
      checkLineArr[checkLineArr.length - 1] = otherPrevTraceLine;
      Line newLine = repositionLine(checkLineArr, checkLineArr.length - 5);
      if (newLine != null) {
        Line[] newLines = new Line[tracePolyline.lines.length];
        System.arraycopy(tracePolyline.lines, 0, newLines, 0, newLines.length - 2);
        newLines[newLines.length - 2] = newLine;
        newLines[newLines.length - 1] = otherTraceLine;
        return new Polyline(newLines);
      }
    }
    return null;
  }
}
