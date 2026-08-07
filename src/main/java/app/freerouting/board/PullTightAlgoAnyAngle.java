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
      RoutingBoard p_board,
      int[] p_only_net_no_arr,
      Stoppable p_stoppable_thread,
      int p_time_limit,
      Point p_keep_point,
      int p_keep_point_layer) {
    super(
        p_board,
        p_only_net_no_arr,
        p_stoppable_thread,
        p_time_limit,
        p_keep_point,
        p_keep_point_layer);
  }

  @Override
  Polyline pull_tight(Polyline p_polyline) {
    Polyline newResult = avoid_acid_traps(p_polyline);
    Polyline prevResult = null;
    while (newResult != prevResult && !is_stop_requested()) {
      prevResult = newResult;
      Polyline tmp = skip_segments_of_length_0(prevResult);
      Polyline tmp0 = reduce_lines(tmp);
      Polyline tmp1 = skip_lines(tmp0);

      // I intended to replace reduce_corners by the previous 2
      // functions, because with consecutive corners closer than
      // 1 grid point reduce_corners may loop with smoothen_corners
      // because of changing directions heavily.
      // Unlike reduce_corners, the above 2 functions do not
      // introduce new directions

      Polyline tmp2 = reduce_corners(tmp1);
      Polyline tmp3 = reposition_lines(tmp2);
      newResult = smoothen_corners(tmp3);
    }
    return newResult;
  }

  // tries to reduce the corner count of p_polyline by replacing two consecutive
  // lines by a line through IntPoints near the previous corner and the next
  // corner, if that is possible without clearance violation.
  private Polyline reduce_corners(Polyline p_polyline) {
    if (p_polyline.arr.length < 4) {
      return p_polyline;
    }
    int lastIndex = p_polyline.arr.length - 4;

    Line[] newLines = new Line[p_polyline.arr.length];
    newLines[0] = p_polyline.arr[0];
    newLines[1] = p_polyline.arr[1];

    int newLineIndex = 1;

    boolean polylineChanged = false;

    Line[] currLines = new Line[3];

    for (int i = 0; i <= lastIndex; i++) {
      boolean skipLine = false;
      FloatPoint newA = newLines[newLineIndex - 1].intersection_approx(newLines[newLineIndex]);
      FloatPoint newB = p_polyline.corner_approx(i + 2);
      boolean inClipShape =
          currClipShape == null
              || currClipShape.contains(newA)
                  && currClipShape.contains(newB)
                  && currClipShape.contains(p_polyline.corner_approx(newLineIndex));

      if (inClipShape) {
        FloatPoint skipCorner = newLines[newLineIndex].intersection_approx(p_polyline.arr[i + 2]);
        currLines[1] = new Line(newA.round(), newB.round());
        boolean ok = true;
        if (newLineIndex == 1) {
          if (!(p_polyline.first_corner() instanceof IntPoint)) {
            // first corner must not be changed
            ok = false;
          } else {
            Direction dir = currLines[1].direction();
            currLines[0] = Line.get_instance(p_polyline.first_corner(), dir.turn_45_degree(2));
          }
        } else {
          currLines[0] = newLines[newLineIndex - 1];
        }
        if (i == lastIndex) {
          if (!(p_polyline.last_corner() instanceof IntPoint)) {
            // last corner must not be changed
            ok = false;
          } else {
            Direction dir = currLines[1].direction();
            currLines[2] = Line.get_instance(p_polyline.last_corner(), dir.turn_45_degree(2));
          }
        } else {
          currLines[2] = p_polyline.arr[i + 3];
        }

        // check, if the intersection of currLines[0] and currLines[1]
        // is near newA and the intersection of currLines[0] and
        // currLines[1] and currLines[2] is near newB.
        // There may be numerical stability problems with
        // near parallel lines.

        final double checkDist = 100;
        if (ok) {
          FloatPoint checkIs = currLines[0].intersection_approx(currLines[1]);
          double dist = checkIs.distance_square(newA);

          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok) {
          FloatPoint checkIs = currLines[1].intersection_approx(currLines[2]);
          double dist = checkIs.distance_square(newB);
          if (dist > checkDist) {
            ok = false;
          }
        }
        if (ok && i == 1 && !(p_polyline.first_corner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second corner of the new polyline
          // is on the same side of the trace as the third corner. (There may be splitting problems)
          Point newCorner = currLines[0].intersection(currLines[1]);
          if (newCorner.side_of(newLines[0]) != p_polyline.corner(1).side_of(newLines[0])) {
            ok = false;
          }
        }
        if (ok && i == lastIndex - 1 && !(p_polyline.last_corner() instanceof IntPoint)) {
          // There may be a connection to a trace.
          // make sure that the second last corner of the new polyline
          // is on the same side of the trace as the third last corner (There may be splitting
          // problems)
          Point newCorner = currLines[1].intersection(currLines[2]);
          if (newCorner.side_of(newLines[0])
              != p_polyline.corner(p_polyline.corner_count() - 2).side_of(newLines[0])) {
            ok = false;
          }
        }
        Polyline currPolyline = null;
        if (ok) {
          currPolyline = new Polyline(currLines);
          if (currPolyline.arr.length != 3) {
            ok = false;
          }
          double lengthBefore = skipCorner.distance(newA) + skipCorner.distance(newB);
          double lengthAfter = currPolyline.length_approx() + 1.5;
          // 1.5 added because of possible inaccuracy SQRT_2
          // by twice rounding.
          if (lengthAfter >= lengthBefore)
          // May happen from rounding to integer.
          // Prevent infinite loop.
          {
            ok = false;
          }
        }

        if (ok) {
          TileShape shapeToCheck = currPolyline.offset_shape(currHalfWidth, 0);
          skipLine =
              board.check_trace_shape(
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
        newLines[newLineIndex] = p_polyline.arr[i + 2];
        if (i == lastIndex) {
          ++newLineIndex;
          newLines[newLineIndex] = p_polyline.arr[i + 3];
        }
      }
      if (newLines[newLineIndex].is_parallel(newLines[newLineIndex - 1])) {
        // skip line, if it is parallel to the previous one
        --newLineIndex;
      }
    }
    if (!polylineChanged) {
      return p_polyline;
    }
    Line[] cleanedNewLines = new Line[newLineIndex + 1];
    System.arraycopy(newLines, 0, cleanedNewLines, 0, cleanedNewLines.length);
    return new Polyline(cleanedNewLines);
  }

  /** tries to smoothen p_polyline by cutting of corners, if possible */
  private Polyline smoothen_corners(Polyline p_polyline) {
    if (p_polyline.arr.length < 4) {
      return p_polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = new Line[p_polyline.arr.length];
    System.arraycopy(p_polyline.arr, 0, lineArr, 0, lineArr.length);

    for (int i = 0; i < lineArr.length - 3; i++) {
      Line newLine = smoothen_corner(lineArr, i);
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
      return p_polyline;
    }
    return new Polyline(lineArr);
  }

  /** tries to shorten p_polyline by relocating its lines */
  @Override
  Polyline reposition_lines(Polyline p_polyline) {
    if (p_polyline.arr.length < 5) {
      return p_polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = new Line[p_polyline.arr.length];
    System.arraycopy(p_polyline.arr, 0, lineArr, 0, lineArr.length);
    for (int i = 0; i < lineArr.length - 4; i++) {
      Line newLine = reposition_line(lineArr, i);
      if (newLine != null) {
        polylineChanged = true;
        lineArr[i + 2] = newLine;
        if (lineArr[i + 2].is_parallel(lineArr[i + 1])
            || lineArr[i + 2].is_parallel(lineArr[i + 3])) {
          // calculation of corners not possible before skipping
          // parallel lines
          break;
        }
      }
    }
    if (!polylineChanged) {
      return p_polyline;
    }
    return new Polyline(lineArr);
  }

  /**
   * tries to reduce te number of lines of p_polyline by moving lines parallel beyond the
   * intersection of the next or previous lines.
   */
  private Polyline reduce_lines(Polyline p_polyline) {
    if (p_polyline.arr.length < 6) {
      return p_polyline;
    }
    boolean polylineChanged = false;
    Line[] lineArr = p_polyline.arr;
    for (int i = 2; i < lineArr.length - 2; i++) {
      FloatPoint prevCorner = lineArr[i - 2].intersection_approx(lineArr[i - 1]);
      FloatPoint nextCorner = lineArr[i + 1].intersection_approx(lineArr[i + 2]);
      boolean inClipShape =
          currClipShape == null
              || currClipShape.contains(prevCorner) && currClipShape.contains(nextCorner);
      if (!inClipShape) {
        continue;
      }
      Line translateLine = lineArr[i];
      double prevDist = translateLine.signed_distance(prevCorner);
      double nextDist = translateLine.signed_distance(nextCorner);
      if (Signum.of(prevDist) != Signum.of(nextDist))
      // the 2 corners are on different sides of the translateLine
      {
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
      Side lineSide = translateLine.side_of(prevCorner);
      Line newLine = translateLine.translate(-translateDist);
      // make sure, we have crossed the nearestCorner;
      int sign = Signum.as_int(translateDist);
      Side newLineSideOfPrevCorner = newLine.side_of(prevCorner);
      Side newLineSideOfNextCorner = newLine.side_of(nextCorner);
      while (newLineSideOfPrevCorner == lineSide && newLineSideOfNextCorner == lineSide) {
        translateDist += sign * 0.5;
        newLine = translateLine.translate(-translateDist);
        newLineSideOfPrevCorner = newLine.side_of(prevCorner);
        newLineSideOfNextCorner = newLine.side_of(nextCorner);
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
        FloatPoint prevPrevCorner = lineArr[i - 3].intersection_approx(lineArr[i - 2]);
        if (newLine.side_of(prevPrevCorner) != lineSide) {
          continue;
        }
      }
      if (crossedCornersAfterCount > 0) {
        if (i >= lineArr.length - 3) {
          continue;
        }
        FloatPoint nextNextCorner = lineArr[i + 2].intersection_approx(lineArr[i + 3]);
        if (newLine.side_of(nextNextCorner) != lineSide) {
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
        TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, keepBeforeInd - 1);
        checkOk =
            board.check_trace_shape(
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
      return p_polyline;
    }
    return new Polyline(lineArr);
  }

  private Line smoothen_corner(Line[] p_line_arr, int p_start_no) {
    if (p_line_arr.length - p_start_no < 4) {
      return null;
    }
    FloatPoint currCorner =
        p_line_arr[p_start_no + 1].intersection_approx(p_line_arr[p_start_no + 2]);
    if (currClipShape != null && !currClipShape.contains(currCorner)) {
      return null;
    }
    double cosinusAngle = p_line_arr[p_start_no + 1].cos_angle(p_line_arr[p_start_no + 2]);
    if (cosinusAngle > c_max_cos_angle)
    // lines are already nearly parallel, don't divide angle any further
    // because of problems with numerical stability
    {
      return null;
    }
    FloatPoint prevCorner = p_line_arr[p_start_no].intersection_approx(p_line_arr[p_start_no + 1]);
    FloatPoint nextCorner =
        p_line_arr[p_start_no + 2].intersection_approx(p_line_arr[p_start_no + 3]);

    // create a line approximately through currCorner, whose
    // direction is about the middle of the directions of the
    // previous and the next line.
    // Translations of this line are used to cut off the corner.
    Direction prevDir = p_line_arr[p_start_no + 1].direction();
    Direction nextDir = p_line_arr[p_start_no + 2].direction();
    Direction middleDir = prevDir.middle_approx(nextDir);
    Line translateLine = Line.get_instance(currCorner.round(), middleDir);
    double prevDist = translateLine.signed_distance(prevCorner);
    double nextDist = translateLine.signed_distance(nextCorner);
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
    Line[] currLines = new Line[p_line_arr.length + 1];
    System.arraycopy(p_line_arr, 0, currLines, 0, p_start_no + 2);
    System.arraycopy(
        p_line_arr, p_start_no + 2, currLines, p_start_no + 3, currLines.length - p_start_no - 3);
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestPoint = translateLine.side_of(nearestPoint);
    int sign = Signum.as_int(maxTranslateDist);
    Line result = null;
    while (Math.abs(deltaDist) > this.minTranslateDist) {
      boolean checkOk = false;
      Line newLine = translateLine.translate(-translateDist);
      Side newLineSideOfNearestPoint = newLine.side_of(nearestPoint);
      if (newLineSideOfNearestPoint == sideOfNearestPoint
          || newLineSideOfNearestPoint == Side.COLLINEAR) {
        currLines[p_start_no + 2] = newLine;
        Polyline tmp = new Polyline(currLines);

        if (tmp.arr.length == currLines.length) {
          TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, p_start_no + 1);
          checkOk =
              board.check_trace_shape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currLines[p_start_no + 2];
          if (translateDist == maxTranslateDist) {
            // biggest possible change
            break;
          }
          translateDist += deltaDist;
        } else {
          translateDist -= deltaDist;
        }
      } else
      // moved a little bit to far at the first time
      // because of numerical inaccuracy
      {
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
          currLines[p_start_no].intersection_approx(currLines[p_start_no + 1]);
      FloatPoint newNextCorner =
          currLines[p_start_no + 3].intersection_approx(currLines[p_start_no + 4]);
      board.changedArea.join(newPrevCorner, currLayer);
      board.changedArea.join(newNextCorner, currLayer);
    }
    return result;
  }

  @Override
  protected Line reposition_line(Line[] p_line_arr, int p_start_no) {
    if (p_line_arr.length - p_start_no < 5) {
      return null;
    }
    if (currClipShape != null)
    // check, that the corners of the line to translate are inside
    // the clip shape
    {
      for (int i = 1; i < 3; i++) {
        FloatPoint currCorner =
            p_line_arr[p_start_no + i].intersection_approx(p_line_arr[p_start_no + i + 1]);
        if (!currClipShape.contains(currCorner)) {
          return null;
        }
      }
    }
    Line translateLine = p_line_arr[p_start_no + 2];
    FloatPoint prevCorner = p_line_arr[p_start_no].intersection_approx(p_line_arr[p_start_no + 1]);
    FloatPoint nextCorner =
        p_line_arr[p_start_no + 3].intersection_approx(p_line_arr[p_start_no + 4]);
    double prevDist = translateLine.signed_distance(prevCorner);
    int cornersSkippedBefore = 0;
    int cornersSkippedAfter = 0;
    final double cEpsilon = 0.001;
    while (Math.abs(prevDist) < cEpsilon)
    // move also all lines through the start corner of the line to translate
    {
      ++cornersSkippedBefore;
      int currNo = p_start_no - cornersSkippedBefore;
      if (currNo < 0)
      // the first corner is on the line to translate
      {
        return null;
      }
      prevCorner = p_line_arr[currNo].intersection_approx(p_line_arr[currNo + 1]);
      prevDist = translateLine.signed_distance(prevCorner);
    }
    double nextDist = translateLine.signed_distance(nextCorner);
    while (Math.abs(nextDist) < cEpsilon)
    // move also all lines through the end corner of the line to translate
    {
      ++cornersSkippedAfter;
      int currNo = p_start_no + 3 + cornersSkippedAfter;
      if (currNo >= p_line_arr.length - 2)
      // the last corner is on the line to translate
      {
        return null;
      }
      nextCorner = p_line_arr[currNo].intersection_approx(p_line_arr[currNo + 1]);
      nextDist = translateLine.signed_distance(nextCorner);
    }
    if (Signum.of(prevDist) != Signum.of(nextDist))
    // the 2 corners are at different sides of translateLine
    {
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
    Line[] currLines = new Line[p_line_arr.length];
    System.arraycopy(p_line_arr, 0, currLines, 0, p_start_no + 2);
    System.arraycopy(
        p_line_arr, p_start_no + 3, currLines, p_start_no + 3, currLines.length - p_start_no - 3);
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestPoint = translateLine.side_of(nearestPoint);
    int sign = Signum.as_int(maxTranslateDist);
    Line result = null;
    boolean firstTime = true;
    while (firstTime || Math.abs(deltaDist) > this.minTranslateDist) {
      boolean checkOk = false;
      Line newLine = translateLine.translate(-translateDist);
      if (firstTime && Math.abs(translateDist) < 1) {
        if (newLine.equals(translateLine)) {
          // try the parallel line through the nearestPoint
          IntPoint roundedNearestPoint = nearestPoint.round();
          if (nearestPoint.distance(roundedNearestPoint.to_float()) < Math.abs(translateDist)) {
            newLine = Line.get_instance(roundedNearestPoint, translateLine.direction());
          }
          firstTime = false;
        }
        if (newLine.equals(translateLine)) {
          return null;
        }
      }
      Side newLineSideOfNearestPoint = newLine.side_of(nearestPoint);
      if (newLineSideOfNearestPoint == sideOfNearestPoint
          || newLineSideOfNearestPoint == Side.COLLINEAR) {
        firstTime = false;
        currLines[p_start_no + 2] = newLine;
        // cornersSkippedBefore > 0 or cornersSkippedAfter > 0
        // happens very rarely. But this handling seems to be
        // important because there are situations which no other
        // tightening function can solve. For example when 3 or more
        // consecutive corners are equal.
        Line prevTranslatedLine = newLine;
        for (int i = 0; i < cornersSkippedBefore; i++)
        // Translate the previous lines onto or past the
        // intersection of newLine with the first untranslated line.
        {
          int prevLineNo = p_start_no + 1 - cornersSkippedBefore;
          FloatPoint currPrevCorner = prevTranslatedLine.intersection_approx(currLines[prevLineNo]);
          Line currTranslateLine = p_line_arr[p_start_no + 1 - i];
          double currTranslateDist = currTranslateLine.signed_distance(currPrevCorner);
          prevTranslatedLine = currTranslateLine.translate(-currTranslateDist);
          currLines[p_start_no + 1 - i] = prevTranslatedLine;
        }
        prevTranslatedLine = newLine;
        for (int i = 0; i < cornersSkippedAfter; i++)
        // Translate the next lines onto or past the
        // intersection of newLine with the first untranslated line.
        {
          int nextLineNo = p_start_no + 3 + cornersSkippedAfter;
          FloatPoint currNextCorner = prevTranslatedLine.intersection_approx(currLines[nextLineNo]);
          Line currTranslateLine = p_line_arr[p_start_no + 3 + i];
          double currTranslateDist = currTranslateLine.signed_distance(currNextCorner);
          prevTranslatedLine = currTranslateLine.translate(-currTranslateDist);
          currLines[p_start_no + 3 + i] = prevTranslatedLine;
        }
        Polyline tmp = new Polyline(currLines);

        if (tmp.arr.length == currLines.length) {
          TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, p_start_no + 1);
          checkOk =
              board.check_trace_shape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = currLines[p_start_no + 2];
          if (translateDist == maxTranslateDist) {
            // biggest possible change
            break;
          }
          translateDist += deltaDist;
        } else {
          translateDist -= deltaDist;
        }
      } else
      // moved a little bit to far at the first time
      // because of numerical inaccuracy
      {
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
          currLines[p_start_no].intersection_approx(currLines[p_start_no + 1]);
      FloatPoint newNextCorner =
          currLines[p_start_no + 3].intersection_approx(currLines[p_start_no + 4]);
      board.changedArea.join(newPrevCorner, currLayer);
      board.changedArea.join(newNextCorner, currLayer);
    }
    return result;
  }

  private Polyline skip_lines(Polyline p_polyline) {
    for (int i = 1; i < p_polyline.arr.length - 3; i++) {
      for (int j = 0; j <= 1; j++) {
        FloatPoint corner1;
        FloatPoint corner2;
        Line currLine;
        if (j == 0) // try to skip the line before the i+2-th line
        {
          currLine = p_polyline.arr[i + 2];
          corner1 = p_polyline.corner_approx(i);
          corner2 = p_polyline.corner_approx(i - 1);
        } else // try to skip the line after i-th line
        {
          currLine = p_polyline.arr[i];
          corner1 = p_polyline.corner_approx(i + 1);
          corner2 = p_polyline.corner_approx(i + 2);
        }
        boolean inClipShape =
            currClipShape == null
                || currClipShape.contains(corner1) && currClipShape.contains(corner2);
        if (!inClipShape) {
          continue;
        }

        Side side1 = currLine.side_of(corner1);
        Side side2 = currLine.side_of(corner2);
        if (side1 != side2)
        // the two corners are on different sides of the line
        {
          Polyline reducedPolyline = p_polyline.skip_lines(i + 1, i + 1);
          if (reducedPolyline.arr.length == p_polyline.arr.length - 1) {
            int shapeNo = i - 1;
            if (j == 0) {
              ++shapeNo;
            }
            TileShape shapeToCheck = reducedPolyline.offset_shape(currHalfWidth, shapeNo);
            if (board.check_trace_shape(
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
        if (i >= p_polyline.arr.length - 4) {
          break;
        }
        FloatPoint corner3;
        if (j == 1) {
          corner3 = p_polyline.corner_approx(i + 3);
        } else {
          corner3 = p_polyline.corner_approx(i + 1);
        }
        if (currClipShape != null && !currClipShape.contains(corner3)) {
          continue;
        }
        if (j == 0)
        // currLine is 1 line later than in the case skipping 1 line
        // when coming from behind
        {
          currLine = p_polyline.arr[i + 3];
          side1 = currLine.side_of(corner1);
          side2 = currLine.side_of(corner2);
        } else {
          side1 = currLine.side_of(corner3);
        }
        if (side1 != side2)
        // the two corners are on different sides of the line
        {
          Polyline reducedPolyline = p_polyline.skip_lines(i + 1, i + 2);
          if (reducedPolyline.arr.length == p_polyline.arr.length - 2) {
            int shapeNo = i - 1;
            if (j == 0) {
              ++shapeNo;
            }
            TileShape shapeToCheck = reducedPolyline.offset_shape(currHalfWidth, shapeNo);
            if (board.check_trace_shape(
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
    return p_polyline;
  }

  @Override
  Polyline smoothen_start_corner_at_trace(PolylineTrace p_trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = p_trace.polyline();
    Point currEndCorner = tracePolyline.corner(0);

    if (this.currClipShape != null && this.currClipShape.is_outside(currEndCorner)) {
      return null;
    }

    Point currPrevEndCorner = tracePolyline.corner(1);
    boolean skipShortSegment =
        !(currEndCorner instanceof IntPoint)
            && currEndCorner.to_float().distance_square(currPrevEndCorner.to_float()) < SKIP_LENGTH;
    int startLineNo = 1;
    if (skipShortSegment) {
      if (tracePolyline.corner_count() < 3) {
        return null;
      }
      currPrevEndCorner = tracePolyline.corner(2);
      ++startLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[startLineNo].direction();
    Direction prevLineDirection = tracePolyline.arr[startLineNo + 1].direction();

    Collection<Item> contactList = p_trace.get_start_contacts();
    for (Item currContact : contactList) {
      if (currContact instanceof PolylineTrace trace && !currContact.is_shove_fixed()) {
        Polyline contactTracePolyline = trace.polyline();
        FloatPoint currOtherTraceCornerApprox;
        Line currOtherTraceLine;
        Line currOtherPrevTraceLine;
        if (contactTracePolyline.first_corner().equals(currEndCorner)) {
          currOtherTraceCornerApprox = contactTracePolyline.corner_approx(1);
          currOtherTraceLine = contactTracePolyline.arr[1];
          currOtherPrevTraceLine = contactTracePolyline.arr[2];
        } else {
          int currCornerNo = contactTracePolyline.corner_count() - 2;
          currOtherTraceCornerApprox = contactTracePolyline.corner_approx(currCornerNo);
          currOtherTraceLine = contactTracePolyline.arr[currCornerNo + 1].opposite();
          currOtherPrevTraceLine = contactTracePolyline.arr[currCornerNo];
        }
        Side currPrevCornerSide = currPrevEndCorner.side_of(currOtherTraceLine);
        Signum currProjection = lineDirection.projection(currOtherTraceLine.direction());
        boolean otherTraceFound = false;
        if (currProjection == Signum.POSITIVE && currPrevCornerSide != Side.COLLINEAR) {
          acuteAngle = true;
          otherTraceFound = true;

        } else if (currProjection == Signum.ZERO && tracePolyline.corner_count() > 2) {
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
        newLineDir = otherTraceLine.direction().turn_45_degree(2);
      } else {
        newLineDir = otherTraceLine.direction().turn_45_degree(6);
      }
      Line translateLine = Line.get_instance(currEndCorner.to_float().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currHalfWidth;
      double prevCornerDist = Math.abs(translateLine.signed_distance(currPrevEndCorner.to_float()));
      double otherDist = Math.abs(translateLine.signed_distance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.side_of(currPrevEndCorner) == Side.ON_THE_LEFT) {
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
      Line newLine = reposition_line(checkLineArr, 0);
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
  Polyline smoothen_end_corner_at_trace(PolylineTrace p_trace) {
    boolean acuteAngle = false;
    boolean bend = false;
    FloatPoint otherTraceCornerApprox = null;
    Line otherTraceLine = null;
    Line otherPrevTraceLine = null;
    Polyline tracePolyline = p_trace.polyline();
    Point currEndCorner = tracePolyline.last_corner();

    if (this.currClipShape != null && this.currClipShape.is_outside(currEndCorner)) {
      return null;
    }

    Point currPrevEndCorner = tracePolyline.corner(tracePolyline.corner_count() - 2);
    boolean skipShortSegment =
        !(currEndCorner instanceof IntPoint)
            && currEndCorner.to_float().distance_square(currPrevEndCorner.to_float()) < SKIP_LENGTH;
    int endLineNo = tracePolyline.arr.length - 2;
    if (skipShortSegment) {
      if (tracePolyline.corner_count() < 3) {
        return null;
      }
      currPrevEndCorner = tracePolyline.corner(tracePolyline.corner_count() - 3);
      --endLineNo;
    }
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[endLineNo].direction().opposite();
    Direction prevLineDirection = tracePolyline.arr[endLineNo].direction().opposite();

    Collection<Item> contactList = p_trace.get_end_contacts();
    for (Item currContact : contactList) {
      if (currContact instanceof PolylineTrace trace && !currContact.is_shove_fixed()) {
        Polyline contactTracePolyline = trace.polyline();
        if (contactTracePolyline.corner_count() > 2) {
          FloatPoint currOtherTraceCornerApprox;
          Line currOtherTraceLine;
          Line currOtherPrevTraceLine;
          if (contactTracePolyline.first_corner().equals(currEndCorner)) {
            currOtherTraceCornerApprox = contactTracePolyline.corner_approx(1);
            currOtherTraceLine = contactTracePolyline.arr[1];
            currOtherPrevTraceLine = contactTracePolyline.arr[2];
          } else {
            int currCornerNo = contactTracePolyline.corner_count() - 2;
            currOtherTraceCornerApprox = contactTracePolyline.corner_approx(currCornerNo);
            currOtherTraceLine = contactTracePolyline.arr[currCornerNo + 1].opposite();
            currOtherPrevTraceLine = contactTracePolyline.arr[currCornerNo];
          }
          Side currPrevCornerSide = currPrevEndCorner.side_of(currOtherTraceLine);
          Signum currProjection = lineDirection.projection(currOtherTraceLine.direction());
          boolean otherTraceFound = false;
          if (currProjection == Signum.POSITIVE && currPrevCornerSide != Side.COLLINEAR) {
            acuteAngle = true;
            otherTraceFound = true;
          } else if (currProjection == Signum.ZERO && tracePolyline.corner_count() > 2) {
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
        newLineDir = otherTraceLine.direction().turn_45_degree(6);
      } else {
        newLineDir = otherTraceLine.direction().turn_45_degree(2);
      }
      Line translateLine = Line.get_instance(currEndCorner.to_float().round(), newLineDir);
      double translateDist = (Limits.sqrt2 - 1) * this.currHalfWidth;
      double prevCornerDist = Math.abs(translateLine.signed_distance(currPrevEndCorner.to_float()));
      double otherDist = Math.abs(translateLine.signed_distance(otherTraceCornerApprox));
      translateDist = Math.min(translateDist, prevCornerDist);
      translateDist = Math.min(translateDist, otherDist);
      if (translateDist >= 0.99) {

        translateDist = Math.max(translateDist - 1, 1);
        if (translateLine.side_of(currPrevEndCorner) == Side.ON_THE_LEFT) {
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
      Line newLine = reposition_line(checkLineArr, checkLineArr.length - 5);
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
