package app.freerouting.board;

import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;

class PullTightAlgo90 extends PullTightAlgo {

  /** Creates a new instance of PullTight90 */
  public PullTightAlgo90(
      RoutingBoard pBoard,
      int[] pOnlyNetNoArr,
      Stoppable pStoppableThread,
      int pTimeLimit,
      Point pKeepPoint,
      int pKeepPointLayer) {
    super(pBoard, pOnlyNetNoArr, pStoppableThread, pTimeLimit, pKeepPoint, pKeepPointLayer);
  }

  @Override
  Polyline pullTight(Polyline pPolyline) {
    Polyline newResult = avoidAcidTraps(pPolyline);
    Polyline prevResult = null;
    while (newResult != prevResult && !this.isStopRequested()) {
      prevResult = newResult;
      Polyline tmp1 = trySkipSecondCorner(prevResult);
      Polyline tmp2 = trySkipCorners(tmp1);
      newResult = repositionLines(tmp2);
    }
    return newResult;
  }

  /** Tries to skip the second corner of p_polyline. Return p_polyline, if nothing was changed. */
  private Polyline trySkipSecondCorner(Polyline pPolyline) {
    if (pPolyline.arr.length < 5) {
      return pPolyline;
    }
    Line[] checkLines = new Line[4];
    checkLines[0] = pPolyline.arr[1];
    checkLines[1] = pPolyline.arr[0];
    checkLines[2] = pPolyline.arr[3];
    checkLines[3] = pPolyline.arr[4];
    Polyline checkPolyline = new Polyline(checkLines);
    if (checkPolyline.arr.length != 4
        || currClipShape != null && !currClipShape.contains(checkPolyline.cornerApprox(1))) {
      return pPolyline;
    }
    for (int i = 0; i < 2; i++) {
      TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, i);
      if (!board.checkTraceShape(
          shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
        return pPolyline;
      }
    }
    // now the second corner can be skipped.
    Line[] newLines = new Line[pPolyline.arr.length - 1];
    newLines[0] = pPolyline.arr[1];
    newLines[1] = pPolyline.arr[0];
    System.arraycopy(pPolyline.arr, 3, newLines, 2, newLines.length - 2);
    return new Polyline(newLines);
  }

  /**
   * Tries to reduce the amount of corners of p_polyline. Return p_polyline, if nothing was changed.
   */
  private Polyline trySkipCorners(Polyline pPolyline) {
    Line[] newLines = new Line[pPolyline.arr.length];
    newLines[0] = pPolyline.arr[0];
    newLines[1] = pPolyline.arr[1];
    int newLineIndex = 1;
    boolean polylineChanged = false;
    Line[] checkLines = new Line[4];
    boolean secondLastCornerSkipped = false;
    for (int i = 5; i <= pPolyline.arr.length; i++) {
      boolean skipLines = false;
      boolean inClipShape =
          currClipShape == null || currClipShape.contains(pPolyline.cornerApprox(i - 3));
      if (inClipShape) {
        checkLines[0] = newLines[newLineIndex - 1];
        checkLines[1] = newLines[newLineIndex];
        checkLines[2] = pPolyline.arr[i - 1];
        if (i < pPolyline.arr.length) {
          checkLines[3] = pPolyline.arr[i];
        } else {
          // use as concluding line the second last line
          checkLines[3] = pPolyline.arr[i - 2];
        }
        Polyline checkPolyline = new Polyline(checkLines);
        skipLines =
            checkPolyline.arr.length == 4
                && (currClipShape == null || currClipShape.contains(checkPolyline.cornerApprox(1)));
        if (skipLines) {
          TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 0);
          skipLines =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
        if (skipLines) {
          TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, 1);
          skipLines =
              board.checkTraceShape(
                  shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
        }
      }
      if (skipLines) {
        if (i == pPolyline.arr.length) {
          secondLastCornerSkipped = true;
        }
        if (board.changedArea != null) {
          FloatPoint newCorner = checkLines[1].intersectionApprox(checkLines[2]);
          board.changedArea.join(newCorner, currLayer);
          FloatPoint skippedCorner = pPolyline.arr[i - 2].intersectionApprox(pPolyline.arr[i - 3]);
          board.changedArea.join(skippedCorner, currLayer);
        }
        polylineChanged = true;
        ++i;
      } else {
        ++newLineIndex;
        newLines[newLineIndex] = pPolyline.arr[i - 3];
      }
    }
    if (!polylineChanged) {
      return pPolyline;
    }
    if (secondLastCornerSkipped) {
      // The second last corner of p_polyline was skipped
      ++newLineIndex;
      newLines[newLineIndex] = pPolyline.arr[pPolyline.arr.length - 1];
      ++newLineIndex;
      newLines[newLineIndex] = pPolyline.arr[pPolyline.arr.length - 2];
    } else {
      for (int i = 3; i > 0; i--) {
        ++newLineIndex;
        newLines[newLineIndex] = pPolyline.arr[pPolyline.arr.length - i];
      }
    }

    Line[] cleanedNewLines = new Line[newLineIndex + 1];
    System.arraycopy(newLines, 0, cleanedNewLines, 0, cleanedNewLines.length);
    return new Polyline(cleanedNewLines);
  }

  @Override
  Polyline smoothenStartCornerAtTrace(PolylineTrace pTrace) {
    return null;
  }

  @Override
  Polyline smoothenEndCornerAtTrace(PolylineTrace pTrace) {
    return null;
  }
}
