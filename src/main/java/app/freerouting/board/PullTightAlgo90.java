package app.freerouting.board;

import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;

class PullTightAlgo90 extends PullTightAlgo {

  /** Creates a new instance of PullTight90. */
  public PullTightAlgo90(
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
      Polyline tmp1 = trySkipSecondCorner(prevResult);
      Polyline tmp2 = trySkipCorners(tmp1);
      newResult = repositionLines(tmp2);
    }
    return newResult;
  }

  /** Tries to skip the second corner of p_polyline. Return p_polyline, if nothing was changed. */
  private Polyline trySkipSecondCorner(Polyline polyline) {
    if (polyline.arr.length < 5) {
      return polyline;
    }
    Line[] checkLines = new Line[4];
    checkLines[0] = polyline.arr[1];
    checkLines[1] = polyline.arr[0];
    checkLines[2] = polyline.arr[3];
    checkLines[3] = polyline.arr[4];
    Polyline checkPolyline = new Polyline(checkLines);
    if (checkPolyline.arr.length != 4
        || currentClipShape != null && !currentClipShape.contains(checkPolyline.cornerApprox(1))) {
      return polyline;
    }
    for (int i = 0; i < 2; i++) {
      TileShape shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, i);
      if (!board.checkTraceShape(
          shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins)) {
        return polyline;
      }
    }
    // now the second corner can be skipped.
    Line[] newLines = new Line[polyline.arr.length - 1];
    newLines[0] = polyline.arr[1];
    newLines[1] = polyline.arr[0];
    System.arraycopy(polyline.arr, 3, newLines, 2, newLines.length - 2);
    return new Polyline(newLines);
  }

  /**
   * Tries to reduce the amount of corners of p_polyline. Return p_polyline, if nothing was changed.
   */
  private Polyline trySkipCorners(Polyline polyline) {
    Line[] newLines = new Line[polyline.arr.length];
    newLines[0] = polyline.arr[0];
    newLines[1] = polyline.arr[1];
    int newLineIndex = 1;
    boolean polylineChanged = false;
    Line[] checkLines = new Line[4];
    boolean secondLastCornerSkipped = false;
    for (int i = 5; i <= polyline.arr.length; i++) {
      boolean skipLines = false;
      boolean inClipShape =
          currentClipShape == null || currentClipShape.contains(polyline.cornerApprox(i - 3));
      if (inClipShape) {
        checkLines[0] = newLines[newLineIndex - 1];
        checkLines[1] = newLines[newLineIndex];
        checkLines[2] = polyline.arr[i - 1];
        if (i < polyline.arr.length) {
          checkLines[3] = polyline.arr[i];
        } else {
          // use as concluding line the second last line
          checkLines[3] = polyline.arr[i - 2];
        }
        Polyline checkPolyline = new Polyline(checkLines);
        skipLines =
            checkPolyline.arr.length == 4
                && (currentClipShape == null
                    || currentClipShape.contains(checkPolyline.cornerApprox(1)));
        if (skipLines) {
          TileShape shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 0);
          skipLines =
              board.checkTraceShape(
                  shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins);
        }
        if (skipLines) {
          TileShape shapeToCheck = checkPolyline.offsetShape(currentHalfWidth, 1);
          skipLines =
              board.checkTraceShape(
                  shapeToCheck, currentLayer, currentNetNoArr, currentClType, this.contactPins);
        }
      }
      if (skipLines) {
        if (i == polyline.arr.length) {
          secondLastCornerSkipped = true;
        }
        if (board.changedArea != null) {
          FloatPoint newCorner = checkLines[1].intersectionApprox(checkLines[2]);
          board.changedArea.join(newCorner, currentLayer);
          FloatPoint skippedCorner = polyline.arr[i - 2].intersectionApprox(polyline.arr[i - 3]);
          board.changedArea.join(skippedCorner, currentLayer);
        }
        polylineChanged = true;
        ++i;
      } else {
        ++newLineIndex;
        newLines[newLineIndex] = polyline.arr[i - 3];
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    if (secondLastCornerSkipped) {
      // The second last corner of p_polyline was skipped
      ++newLineIndex;
      newLines[newLineIndex] = polyline.arr[polyline.arr.length - 1];
      ++newLineIndex;
      newLines[newLineIndex] = polyline.arr[polyline.arr.length - 2];
    } else {
      for (int i = 3; i > 0; i--) {
        ++newLineIndex;
        newLines[newLineIndex] = polyline.arr[polyline.arr.length - i];
      }
    }

    Line[] cleanedNewLines = new Line[newLineIndex + 1];
    System.arraycopy(newLines, 0, cleanedNewLines, 0, cleanedNewLines.length);
    return new Polyline(cleanedNewLines);
  }

  @Override
  Polyline smoothenStartCornerAtTrace(PolylineTrace trace) {
    return null;
  }

  @Override
  Polyline smoothenEndCornerAtTrace(PolylineTrace trace) {
    return null;
  }
}
