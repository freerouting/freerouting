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

  /** Tries to reduce the amount of corners of polyline. Return polyline, if nothing was changed. */
  private Polyline reduceCorners(Polyline polyline) {
    if (polyline.arr.length <= 4) {
      return polyline;
    }
    Point[] currentCorner = new Point[4];
    for (int i = 0; i < 4; i++) {
      currentCorner[i] = polyline.corner(i);
      if (!(currentCorner[i] instanceof IntPoint)) {
        return polyline;
      }
    }
    boolean[] currentCornerInClipShape = new boolean[4];

    for (int i = 0; i < 4; i++) {
      if (currentClipShape == null) {
        currentCornerInClipShape[i] = true;
      } else {
        currentCornerInClipShape[i] = !currentClipShape.isOutside(currentCorner[i]);
      }
    }

    boolean polylineChanged = false;
    int newCornerCount = 1;
    Point[] newCorners = new Point[polyline.arr.length - 3];
    newCorners[0] = currentCorner[0];
    Point[] currentCheckPoints = new Point[2];
    Point newCorner = null;
    int cornerNo = 3;
    while (cornerNo < polyline.arr.length - 1) {
      currentCorner[3] = polyline.corner(cornerNo);
      if (!(currentCorner[3] instanceof IntPoint)) {
        return polyline;
      }
      if (currentCorner[1].equals(currentCorner[2])
          || cornerNo < polyline.arr.length - 2
              && currentCorner[3].sideOf(currentCorner[1], currentCorner[2]) == Side.COLLINEAR) {
        // corners in the middle af a line can be skipped
        ++cornerNo;
        currentCorner[2] = currentCorner[3];
        currentCornerInClipShape[2] = currentCornerInClipShape[3];
        if (cornerNo < polyline.arr.length - 1) {
          currentCorner[3] = polyline.corner(cornerNo);
          if (!(currentCorner[3] instanceof IntPoint)) {
            return polyline;
          }
        }
        polylineChanged = true;
      }
      currentCornerInClipShape[3] =
          currentClipShape == null || !currentClipShape.isOutside(currentCorner[3]);
      boolean cornerRemoved = false;
      if (currentCornerInClipShape[1]
          && currentCornerInClipShape[2]
          && currentCornerInClipShape[3]) {
        // translate the line from currentCorner[2] to currentCorner[1] to currentCorner[3]
        Vector delta = currentCorner[3].differenceBy(currentCorner[2]);
        newCorner = currentCorner[1].translateBy(delta);
        if (currentCorner[3].equals(currentCorner[2])) {
          // just remove multiple corner
          cornerRemoved = true;
        } else if (newCorner.sideOf(currentCorner[0], currentCorner[1]) == Side.COLLINEAR) {
          currentCheckPoints[0] = newCorner;
          currentCheckPoints[1] = currentCorner[1];
          Polyline checkPolyline = new Polyline(currentCheckPoints);
          if (checkPolyline.arr.length == 3) {
            TileShape shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 0);
            if (board.checkTraceShape(
                shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins)) {
              currentCheckPoints[1] = currentCorner[3];
              if (currentCheckPoints[0].equals(currentCheckPoints[1])) {
                cornerRemoved = true;
              } else {
                checkPolyline = new Polyline(currentCheckPoints);
                if (checkPolyline.arr.length == 3) {
                  shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 0);
                  cornerRemoved =
                      board.checkTraceShape(
                          shapeToCheck,
                          currentLayer,
                          currentNetNoArr,
                          currentClType,
                          this.contactPins);
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
          && currentCornerInClipShape[0]
          && currentCornerInClipShape[1]
          && currentCornerInClipShape[2]) {
        // the first try has failed. Try to translate the line from
        // corner2 to corner1 to corner_0
        Vector delta = currentCorner[0].differenceBy(currentCorner[1]);
        newCorner = currentCorner[2].translateBy(delta);
        if (currentCorner[0].equals(currentCorner[1])) {
          // just remove multiple corner
          cornerRemoved = true;
        } else if (newCorner.sideOf(currentCorner[2], currentCorner[3]) == Side.COLLINEAR) {
          currentCheckPoints[0] = newCorner;
          currentCheckPoints[1] = currentCorner[0];
          Polyline checkPolyline = new Polyline(currentCheckPoints);
          if (checkPolyline.arr.length == 3) {
            TileShape shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 0);
            if (board.checkTraceShape(
                shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins)) {
              currentCheckPoints[1] = currentCorner[2];
              checkPolyline = new Polyline(currentCheckPoints);
              if (checkPolyline.arr.length == 3) {
                shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 0);
                cornerRemoved =
                    board.checkTraceShape(
                        shapeToCheck,
                        currentLayer,
                        currentNetNoArr,
                        currentClType,
                        this.contactPins);
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
        currentCorner[1] = newCorner;
        currentCornerInClipShape[1] =
            currentClipShape == null || !currentClipShape.isOutside(currentCorner[1]);
        if (board.changedArea != null) {
          board.changedArea.join(newCorner.toFloat(), currentLayer);
          board.changedArea.join(currentCorner[1].toFloat(), currentLayer);
          board.changedArea.join(currentCorner[2].toFloat(), currentLayer);
        }
      } else {
        newCorners[newCornerCount] = currentCorner[1];
        ++newCornerCount;
        currentCorner[0] = currentCorner[1];
        currentCorner[1] = currentCorner[2];
        currentCornerInClipShape[0] = currentCornerInClipShape[1];
        currentCornerInClipShape[1] = currentCornerInClipShape[2];
      }
      currentCorner[2] = currentCorner[3];
      currentCornerInClipShape[2] = currentCornerInClipShape[3];
      ++cornerNo;
    }
    if (!polylineChanged) {
      return polyline;
    }
    Point[] adjustedCorners = new Point[newCornerCount + 2];
    System.arraycopy(newCorners, 0, adjustedCorners, 0, newCornerCount);
    adjustedCorners[newCornerCount] = currentCorner[1];
    adjustedCorners[newCornerCount + 1] = currentCorner[2];
    return new Polyline(adjustedCorners);
  }

  /**
   * Smoothens the 90 degree corners of polyline to 45 degree by cutting of the 90 degree corner.
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
   * Adds a line at no to smoothen a 90 degree corner between line1 and line2 to 45 degree.
   *
   * <p>The distance of the new line to the corner will be so small that no clearance check is
   * necessary.
   */
  private Line smoothenSharpCorner(Line[] lineArr, int no) {
    FloatPoint currentCorner = lineArr[no].intersectionApprox(lineArr[no + 1]);
    if (currentCorner.x != (int) currentCorner.x) {
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
    Line translateLine = Line.getInstance(currentCorner.round(), newLineDir);
    double translateDist = (Limits.sqrt2 - 1) * this.currentHalfWidth;
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
      board.changedArea.join(currentCorner, currentLayer);
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
    FloatPoint currentCorner = prevLine.intersectionApprox(nextLine);
    FloatPoint prevCorner = prevLine.intersectionApprox(lineArr[no - 1]);
    FloatPoint nextCorner = nextLine.intersectionApprox(lineArr[no + 2]);
    int newX = 0;
    int newY = 0;
    boolean newLineIsVertical = false;
    boolean newLineIsHorizontal = false;
    if (prevCorner.x > currentCorner.x && nextCorner.x > currentCorner.x) {
      newX = (int) Math.ceil(currentCorner.x);
      newY = (int) Math.ceil(currentCorner.y);
      newLineIsVertical = true;
    } else if (prevCorner.x < currentCorner.x && nextCorner.x < currentCorner.x) {
      newX = (int) Math.floor(currentCorner.x);
      newY = (int) Math.floor(currentCorner.y);
      newLineIsVertical = true;
    } else if (prevCorner.y > currentCorner.y && nextCorner.y > currentCorner.y) {
      newX = (int) Math.ceil(currentCorner.x);
      newY = (int) Math.ceil(currentCorner.y);
      newLineIsHorizontal = true;
    } else if (prevCorner.y < currentCorner.y && nextCorner.y < currentCorner.y) {
      newX = (int) Math.floor(currentCorner.x);
      newY = (int) Math.floor(currentCorner.y);
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
   * Adds a line at no to smoothen a 90 degree corner between line1 and line2 to 45 degree.
   *
   * <p>The distance of the new line to the corner will be so big that a clearance check is
   * necessary.
   */
  private Line smoothenCorner(Line[] lineArr, int no) {
    FloatPoint prevCorner = lineArr[no].intersectionApprox(lineArr[no - 1]);
    FloatPoint currentCorner = lineArr[no].intersectionApprox(lineArr[no + 1]);
    FloatPoint nextCorner = lineArr[no + 1].intersectionApprox(lineArr[no + 2]);

    Direction prevDir = lineArr[no].direction();
    Direction nextDir = lineArr[no + 1].direction();
    Direction newLineDir = Direction.getInstance(prevDir.getVector().add(nextDir.getVector()));
    Line translateLine = Line.getInstance(currentCorner.round(), newLineDir);
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
          TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, 0);
          checkOk =
              board.checkTraceShape(
                  shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins);
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
      board.changedArea.join(newPrevCorner, currentLayer);
      board.changedArea.join(newNextCorner, currentLayer);
      board.changedArea.join(currentCorner, currentLayer);
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
    Point currentEndCorner = tracePolyline.corner(0);

    if (this.currentClipShape != null && this.currentClipShape.isOutside(currentEndCorner)) {
      return null;
    }

    Point currentPrevEndCorner = tracePolyline.corner(1);
    Side prevCornerSide = null;
    Direction lineDirection = tracePolyline.arr[1].direction();
    Direction prevLineDirection = tracePolyline.arr[2].direction();

    Collection<Item> contactList = trace.getStartContacts();
    for (Item currentContact : contactList) {
      if (currentContact instanceof PolylineTrace contactTrace && !currentContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        FloatPoint currentOtherTraceCornerApprox;
        Line currentOtherTraceLine;
        Line currentOtherPrevTraceLine;
        if (contactTracePolyline.firstCorner().equals(currentEndCorner)) {
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
          currentOtherTraceLine = contactTracePolyline.arr[1];
          currentOtherPrevTraceLine = contactTracePolyline.arr[2];
        } else {
          int currentCornerNo = contactTracePolyline.cornerCount() - 2;
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currentCornerNo);
          currentOtherTraceLine = contactTracePolyline.arr[currentCornerNo + 1].opposite();
          currentOtherPrevTraceLine = contactTracePolyline.arr[currentCornerNo];
        }
        Side currentPrevCornerSide = currentPrevEndCorner.sideOf(currentOtherTraceLine);
        Signum currentProjection = lineDirection.projection(currentOtherTraceLine.direction());
        boolean otherTraceFound = false;
        if (currentProjection == Signum.POSITIVE && currentPrevCornerSide != Side.COLLINEAR) {
          if (currentOtherTraceLine.direction().isOrthogonal()) {
            acuteAngle = true;
            otherTraceFound = true;
          }
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
    Point currentEndCorner = tracePolyline.lastCorner();

    if (this.currentClipShape != null && this.currentClipShape.isOutside(currentEndCorner)) {
      return null;
    }

    Point currentPrevEndCorner = tracePolyline.corner(tracePolyline.cornerCount() - 2);
    Side prevCornerSide = null;
    Direction lineDirection =
        tracePolyline.arr[tracePolyline.arr.length - 2].direction().opposite();
    Direction prevLineDirection =
        tracePolyline.arr[tracePolyline.arr.length - 3].direction().opposite();

    Collection<Item> contactList = trace.getEndContacts();
    for (Item currentContact : contactList) {
      if (currentContact instanceof PolylineTrace contactTrace && !currentContact.isShoveFixed()) {
        Polyline contactTracePolyline = contactTrace.polyline();
        FloatPoint currentOtherTraceCornerApprox;
        Line currentOtherTraceLine;
        Line currentOtherPrevTraceLine;
        if (contactTracePolyline.firstCorner().equals(currentEndCorner)) {
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(1);
          currentOtherTraceLine = contactTracePolyline.arr[1];
          currentOtherPrevTraceLine = contactTracePolyline.arr[2];
        } else {
          int currentCornerNo = contactTracePolyline.cornerCount() - 2;
          currentOtherTraceCornerApprox = contactTracePolyline.cornerApprox(currentCornerNo);
          currentOtherTraceLine = contactTracePolyline.arr[currentCornerNo + 1].opposite();
          currentOtherPrevTraceLine = contactTracePolyline.arr[currentCornerNo];
        }
        Side currentPrevCornerSide = currentPrevEndCorner.sideOf(currentOtherTraceLine);
        Signum currentProjection = lineDirection.projection(currentOtherTraceLine.direction());
        boolean otherTraceFound = false;
        if (currentProjection == Signum.POSITIVE && currentPrevCornerSide != Side.COLLINEAR) {
          if (currentOtherTraceLine.direction().isOrthogonal()) {
            acuteAngle = true;
            otherTraceFound = true;
          }
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
