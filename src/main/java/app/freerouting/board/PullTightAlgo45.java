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
import app.freerouting.geometry.planar.Vector;
import java.util.Collection;

class PullTightAlgo45 extends PullTightAlgo {

  /** Creates a new instance of PullTight90. */
  public PullTightAlgo45(
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
    while (newResult != prevResult && !this.isStopRequested()) {
      prevResult = newResult;
      Polyline tmp1 = reduceCorners(prevResult);
      Polyline tmp2 = smoothenCorners(tmp1);
      newResult = repositionLines(tmp2);
    }
    return newResult;
  }

  AngleRestriction getAngleRestriction() {
    return AngleRestriction.FORTYFIVE_DEGREE;
  }

  /**
   * Tries to reduce the amount of corners of p_polyline. Return p_polyline, if nothing was changed.
   */
  private Polyline reduceCorners(Polyline polyline) {
    if (polyline.arr.length <= 4) {
      return polyline;
    }
    int newCornerCount = 1;
    Point[] currCorner = new Point[4];
    for (int i = 0; i < 4; i++) {
      currCorner[i] = polyline.corner(i);
      if (!(currCorner[i] instanceof IntPoint)) {
        return polyline;
      }
    }
    boolean[] currCornerInClipShape = new boolean[4];

    for (int i = 0; i < 4; i++) {
      if (currClipShape == null) {
        currCornerInClipShape[i] = true;
      } else {
        currCornerInClipShape[i] = !currClipShape.isOutside(currCorner[i]);
      }
    }

    boolean polylineChanged = false;
    Point[] newCorners = new Point[polyline.arr.length - 3];
    newCorners[0] = currCorner[0];
    Point[] currCheckPoints = new Point[2];
    Point newCorner = null;
    int cornerNo = 3;
    while (cornerNo < polyline.arr.length - 1) {
      boolean cornerRemoved = false;
      currCorner[3] = polyline.corner(cornerNo);
      if (!(currCorner[3] instanceof IntPoint)) {
        return polyline;
      }
      if (currCorner[1].equals(currCorner[2])
          || cornerNo < polyline.arr.length - 2
              && currCorner[3].sideOf(currCorner[1], currCorner[2]) == Side.COLLINEAR) {
        // corners in the middle af a line can be skipped
        ++cornerNo;
        currCorner[2] = currCorner[3];
        currCornerInClipShape[2] = currCornerInClipShape[3];
        if (cornerNo < polyline.arr.length - 1) {
          currCorner[3] = polyline.corner(cornerNo);
          if (!(currCorner[3] instanceof IntPoint)) {
            return polyline;
          }
        }
        polylineChanged = true;
      }
      currCornerInClipShape[3] = currClipShape == null || !currClipShape.isOutside(currCorner[3]);
      if (currCornerInClipShape[1] && currCornerInClipShape[2] && currCornerInClipShape[3]) {
        // translate the line from currCorner[2] to currCorner[1] to currCorner[3]
        Vector delta = currCorner[3].differenceBy(currCorner[2]);
        newCorner = currCorner[1].translateBy(delta);
        if (currCorner[3].equals(currCorner[2])) {
          // just remove multiple corner
          cornerRemoved = true;
        } else if (newCorner.sideOf(currCorner[0], currCorner[1]) == Side.COLLINEAR) {
          currCheckPoints[0] = newCorner;
          currCheckPoints[1] = currCorner[1];
          Polyline checkPolyline = new Polyline(currCheckPoints);
          if (checkPolyline.arr.length == 3) {
            TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 0);
            if (board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
              currCheckPoints[1] = currCorner[3];
              if (currCheckPoints[0].equals(currCheckPoints[1])) {
                cornerRemoved = true;
              } else {
                checkPolyline = new Polyline(currCheckPoints);
                if (checkPolyline.arr.length == 3) {
                  shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 0);
                  cornerRemoved =
                      board.checkTraceShape(
                          shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
                } else {
                  cornerRemoved = true;
                }
              }
            }
          } else {
            cornerRemoved = true;
          }
        }
      }
      if (!cornerRemoved
          && currCornerInClipShape[0]
          && currCornerInClipShape[1]
          && currCornerInClipShape[2]) {
        // the first try has failed. Try to translate the line from
        // corner2 to corner1 to corner_0
        Vector delta = currCorner[0].differenceBy(currCorner[1]);
        newCorner = currCorner[2].translateBy(delta);
        if (currCorner[0].equals(currCorner[1])) {
          // just remove multiple corner
          cornerRemoved = true;
        } else if (newCorner.sideOf(currCorner[2], currCorner[3]) == Side.COLLINEAR) {
          currCheckPoints[0] = newCorner;
          currCheckPoints[1] = currCorner[0];
          Polyline checkPolyline = new Polyline(currCheckPoints);
          if (checkPolyline.arr.length == 3) {
            TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 0);
            if (board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
              currCheckPoints[1] = currCorner[2];
              checkPolyline = new Polyline(currCheckPoints);
              if (checkPolyline.arr.length == 3) {
                shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 0);
                cornerRemoved =
                    board.checkTraceShape(
                        shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
              } else {
                cornerRemoved = true;
              }
            }
          } else {
            cornerRemoved = true;
          }
        }
      }
      if (cornerRemoved) {
        polylineChanged = true;
        currCorner[1] = newCorner;
        currCornerInClipShape[1] = currClipShape == null || !currClipShape.isOutside(currCorner[1]);
        if (board.changedArea != null) {
          board.changedArea.join(newCorner.toFloat(), currLayer);
          board.changedArea.join(currCorner[1].toFloat(), currLayer);
          board.changedArea.join(currCorner[2].toFloat(), currLayer);
        }
      } else {
        newCorners[newCornerCount] = currCorner[1];
        ++newCornerCount;
        currCorner[0] = currCorner[1];
        currCorner[1] = currCorner[2];
        currCornerInClipShape[0] = currCornerInClipShape[1];
        currCornerInClipShape[1] = currCornerInClipShape[2];
      }
      currCorner[2] = currCorner[3];
      currCornerInClipShape[2] = currCornerInClipShape[3];
      ++cornerNo;
    }
    if (!polylineChanged) {
      return polyline;
    }
    Point[] adjustedCorners = new Point[newCornerCount + 2];
    System.arraycopy(newCorners, 0, adjustedCorners, 0, newCornerCount);
    adjustedCorners[newCornerCount] = currCorner[1];
    adjustedCorners[newCornerCount + 1] = currCorner[2];
    return new Polyline(adjustedCorners);
  }

  /**
   * Smoothens the 90 degree corners of p_polyline to 45 degree by cutting of the 90 degree corner.
   * The cutting of is so small, that no check is needed
   */
  private Polyline smoothenCorners(Polyline polyline) {
    Polyline result = polyline;
    boolean polylineChanged = true;
    while (polylineChanged) {
      if (result.arr.length < 4) {
        return result;
      }
      polylineChanged = false;
      Line[] lineArr = new Line[result.arr.length];
      System.arraycopy(result.arr, 0, lineArr, 0, lineArr.length);

      for (int i = 1; i < lineArr.length - 2; i++) {
        Direction d1 = lineArr[i].direction();
        Direction d2 = lineArr[i + 1].direction();
        if (d1.isMultipleOf45Degree()
            && d2.isMultipleOf45Degree()
            && d1.projection(d2) != Signum.POSITIVE) {
          // there is a 90 degree or sharper angle
          Line newLine = smoothenCorner(lineArr, i);
          if (newLine == null) {
            // the greedy smoothening couldn't change the polyline
            newLine = smoothenSharpCorner(lineArr, i);
          }
          if (newLine != null) {
            polylineChanged = true;
            // add the new line into the line array
            Line[] tmpLines = new Line[lineArr.length + 1];
            System.arraycopy(lineArr, 0, tmpLines, 0, i + 1);
            tmpLines[i + 1] = newLine;
            System.arraycopy(lineArr, i + 1, tmpLines, i + 2, tmpLines.length - (i + 2));
            lineArr = tmpLines;
            ++i;
          }
        }
      }
      if (polylineChanged) {
        result = new Polyline(lineArr);
      }
    }
    return result;
  }

  /**
   * adds a line between at p_no to smoothen a 90 degree corner between p_line_1 and p_line_2 to 45.
   * degree. The distance of the new line to the corner will be so small that no clearance check is
   * necessary.
   */
  private Line smoothenSharpCorner(Line[] lineArr, int no) {
    FloatPoint currCorner = lineArr[no].intersectionApprox(lineArr[no + 1]);
    if (currCorner.x != (int) currCorner.x) {
      // intersection of 2 diagonal lines is not integer
      Line result = smoothenNonIntegerCorner(lineArr, no);
      {
        if (result != null) {
          return result;
        }
      }
    }
    FloatPoint prevCorner = lineArr[no].intersectionApprox(lineArr[no - 1]);
    FloatPoint nextCorner = lineArr[no + 1].intersectionApprox(lineArr[no + 2]);

    Direction prevDir = lineArr[no].direction();
    Direction nextDir = lineArr[no + 1].direction();
    Direction newLineDir = Direction.getInstance(prevDir.getVector().add(nextDir.getVector()));
    Line translateLine = Line.getInstance(currCorner.round(), newLineDir);
    double translateDist = (Limits.sqrt2 - 1) * this.currHalfWidth;
    double prevDist = Math.abs(translateLine.signedDistance(prevCorner));
    double nextDist = Math.abs(translateLine.signedDistance(nextCorner));
    translateDist = Math.min(translateDist, prevDist);
    translateDist = Math.min(translateDist, nextDist);
    if (translateDist < 0.99) {
      return null;
    }
    translateDist = Math.max(translateDist - 1, 1);
    if (translateLine.sideOf(nextCorner) == Side.ON_THE_LEFT) {
      translateDist = -translateDist;
    }
    Line result = translateLine.translate(translateDist);
    if (board.changedArea != null) {
      board.changedArea.join(currCorner, currLayer);
    }
    return result;
  }

  /**
   * Smoothens with a short axis parallel line to remove a non integer corner of two intersecting
   * diagonal lines. Returns null, if that is not possible.
   */
  private Line smoothenNonIntegerCorner(Line[] lineArr, int no) {
    Line prevLine = lineArr[no];
    Line nextLine = lineArr[no + 1];
    if (prevLine.isEqualOrOpposite(nextLine)) {
      return null;
    }
    if (!(prevLine.isDiagonal() && nextLine.isDiagonal())) {
      return null;
    }
    FloatPoint currCorner = prevLine.intersectionApprox(nextLine);
    FloatPoint prevCorner = prevLine.intersectionApprox(lineArr[no - 1]);
    FloatPoint nextCorner = nextLine.intersectionApprox(lineArr[no + 2]);
    int newX = 0;
    int newY = 0;
    boolean newLineIsVertical = false;
    boolean newLineIsHorizontal = false;
    if (prevCorner.x > currCorner.x && nextCorner.x > currCorner.x) {
      newX = (int) Math.ceil(currCorner.x);
      newY = (int) Math.ceil(currCorner.y);
      newLineIsVertical = true;
    } else if (prevCorner.x < currCorner.x && nextCorner.x < currCorner.x) {
      newX = (int) Math.floor(currCorner.x);
      newY = (int) Math.floor(currCorner.y);
      newLineIsVertical = true;
    } else if (prevCorner.y > currCorner.y && nextCorner.y > currCorner.y) {
      newX = (int) Math.ceil(currCorner.x);
      newY = (int) Math.ceil(currCorner.y);
      newLineIsHorizontal = true;
    } else if (prevCorner.y < currCorner.y && nextCorner.y < currCorner.y) {
      newX = (int) Math.floor(currCorner.x);
      newY = (int) Math.floor(currCorner.y);
      newLineIsHorizontal = true;
    }
    Direction newLineDir;
    if (newLineIsVertical) {
      if (prevCorner.y < nextCorner.y) {
        newLineDir = Direction.UP;
      } else {
        newLineDir = Direction.DOWN;
      }
    } else if (newLineIsHorizontal) {
      if (prevCorner.x < nextCorner.x) {
        newLineDir = Direction.RIGHT;
      } else {
        newLineDir = Direction.LEFT;
      }
    } else {
      return null;
    }

    Point lineA = new IntPoint(newX, newY);
    return new Line(lineA, newLineDir);
  }

  /**
   * adds a line between at p_no to smoothen a 90 degree corner between p_line_1 and p_line_2 to 45.
   * degree. The distance of the new line to the corner will be so big that a clearance check is
   * necessary.
   */
  private Line smoothenCorner(Line[] lineArr, int no) {
    FloatPoint prevCorner = lineArr[no].intersectionApprox(lineArr[no - 1]);
    FloatPoint currCorner = lineArr[no].intersectionApprox(lineArr[no + 1]);
    FloatPoint nextCorner = lineArr[no + 1].intersectionApprox(lineArr[no + 2]);

    Direction prevDir = lineArr[no].direction();
    Direction nextDir = lineArr[no + 1].direction();
    Direction newLineDir = Direction.getInstance(prevDir.getVector().add(nextDir.getVector()));
    Line translateLine = Line.getInstance(currCorner.round(), newLineDir);
    double prevDist = Math.abs(translateLine.signedDistance(prevCorner));
    double nextDist = Math.abs(translateLine.signedDistance(nextCorner));
    if (prevDist == 0 || nextDist == 0) {
      return null;
    }
    double maxTranslateDist;
    FloatPoint nearestCorner;
    if (prevDist <= nextDist) {
      maxTranslateDist = prevDist;
      nearestCorner = prevCorner;
    } else {
      maxTranslateDist = nextDist;
      nearestCorner = nextCorner;
    }
    if (maxTranslateDist < 1) {
      return null;
    }
    maxTranslateDist = Math.max(maxTranslateDist - 1, 1);
    if (translateLine.sideOf(nextCorner) == Side.ON_THE_LEFT) {
      maxTranslateDist = -maxTranslateDist;
    }
    Line[] checkLines = new Line[3];
    checkLines[0] = lineArr[no];
    checkLines[2] = lineArr[no + 1];
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestCorner = translateLine.sideOf(nearestCorner);
    int sign = Signum.asInt(maxTranslateDist);
    Line result = null;
    while (Math.abs(deltaDist) > this.minTranslateDist) {
      boolean checkOk = false;
      Line newLine = translateLine.translate(translateDist);
      Side newLineSideOfNearestCorner = newLine.sideOf(nearestCorner);
      if (newLineSideOfNearestCorner == sideOfNearestCorner
          || newLineSideOfNearestCorner == Side.COLLINEAR) {
        checkLines[1] = newLine;
        Polyline tmp = new Polyline(checkLines);

        if (tmp.arr.length == 3) {
          TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, 0);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        deltaDist /= 2;
        if (checkOk) {
          result = checkLines[1];
          if (translateDist == maxTranslateDist) {
            // biggest possible change
            break;
          }
          translateDist += deltaDist;
        } else {
          translateDist -= deltaDist;
        }
      } else { // moved a little bit to far at the first time because of numerical inaccuracy
        double shortenValue = sign * 0.5;
        maxTranslateDist -= shortenValue;
        translateDist -= shortenValue;
        deltaDist -= shortenValue;
      }
    }
    if (result != null && board.changedArea != null) {
      FloatPoint newPrevCorner = checkLines[0].intersectionApprox(result);
      FloatPoint newNextCorner = checkLines[2].intersectionApprox(result);
      board.changedArea.join(newPrevCorner, currLayer);
      board.changedArea.join(newNextCorner, currLayer);
      board.changedArea.join(currCorner, currLayer);
    }
    return result;
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
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[1].direction();
    Direction prevLineDirection = tracePolyline.arr[2].direction();

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
          if (currOtherTraceLine.direction().isOrthogonal()) {
            acuteAngle = true;
            otherTraceFound = true;
          }
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
        Line[] newLines = new Line[tracePolyline.arr.length + 1];
        newLines[0] = otherTraceLine;
        newLines[1] = addLine;
        System.arraycopy(tracePolyline.arr, 1, newLines, 2, newLines.length - 2);
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[tracePolyline.arr.length + 1];
      checkLineArr[0] = otherPrevTraceLine;
      checkLineArr[1] = otherTraceLine;
      System.arraycopy(tracePolyline.arr, 1, checkLineArr, 2, checkLineArr.length - 2);
      Line newLine = repositionLine(checkLineArr, 2);
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
    Side prevCornerSide = null;
    Direction lineDirection =
        tracePolyline.arr[tracePolyline.arr.length - 2].direction().opposite();
    Direction prevLineDirection =
        tracePolyline.arr[tracePolyline.arr.length - 3].direction().opposite();

    Collection<Item> contactList = trace.getEndContacts();
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
          if (currOtherTraceLine.direction().isOrthogonal()) {
            acuteAngle = true;
            otherTraceFound = true;
          }
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
        Line[] newLines = new Line[tracePolyline.arr.length + 1];
        System.arraycopy(tracePolyline.arr, 0, newLines, 0, tracePolyline.arr.length - 1);
        newLines[newLines.length - 2] = addLine;
        newLines[newLines.length - 1] = otherTraceLine;
        return new Polyline(newLines);
      }
    } else if (bend) {
      Line[] checkLineArr = new Line[tracePolyline.arr.length + 1];
      System.arraycopy(tracePolyline.arr, 0, checkLineArr, 0, tracePolyline.arr.length - 1);
      checkLineArr[checkLineArr.length - 2] = otherTraceLine;
      checkLineArr[checkLineArr.length - 1] = otherPrevTraceLine;
      Line newLine = repositionLine(checkLineArr, tracePolyline.arr.length - 2);
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
