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
  Polyline pullTight(Polyline p_polyline) {
    Polyline newResult = avoidAcidTraps(p_polyline);
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
  private Polyline trySkipSecondCorner(Polyline p_polyline) {
    if (p_polyline.arr.length < 5) {
      return p_polyline;
    }
    Line[] checkLines = new Line[4];
    checkLines[0] = p_polyline.arr[1];
    checkLines[1] = p_polyline.arr[0];
    checkLines[2] = p_polyline.arr[3];
    checkLines[3] = p_polyline.arr[4];
    Polyline checkPolyline = new Polyline(checkLines);
    if (checkPolyline.arr.length != 4
        || currClipShape != null && !currClipShape.contains(checkPolyline.cornerApprox(1))) {
      return p_polyline;
    }
    for (int i = 0; i < 2; i++) {
      TileShape shapeToCheck = checkPolyline.offsetShape(currHalfWidth, i);
      if (!board.checkTraceShape(
          shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins)) {
        return p_polyline;
      }
    }
    // now the second corner can be skipped.
    Line[] newLines = new Line[p_polyline.arr.length - 1];
    newLines[0] = p_polyline.arr[1];
    newLines[1] = p_polyline.arr[0];
    System.arraycopy(p_polyline.arr, 3, newLines, 2, newLines.length - 2);
    return new Polyline(newLines);
  }

  /**
   * Tries to reduce the amount of corners of p_polyline. Return p_polyline, if nothing was changed.
   */
  private Polyline trySkipCorners(Polyline p_polyline) {
    Line[] newLines = new Line[p_polyline.arr.length];
    newLines[0] = p_polyline.arr[0];
    newLines[1] = p_polyline.arr[1];
    int newLineIndex = 1;
    boolean polylineChanged = false;
    Line[] checkLines = new Line[4];
    boolean secondLastCornerSkipped = false;
    for (int i = 5; i <= p_polyline.arr.length; i++) {
      boolean skipLines = false;
      boolean inClipShape =
          currClipShape == null || currClipShape.contains(p_polyline.cornerApprox(i - 3));
      if (inClipShape) {
        checkLines[0] = newLines[newLineIndex - 1];
        checkLines[1] = newLines[newLineIndex];
        checkLines[2] = p_polyline.arr[i - 1];
        if (i < p_polyline.arr.length) {
          checkLines[3] = p_polyline.arr[i];
        } else {
          // use as concluding line the second last line
          checkLines[3] = p_polyline.arr[i - 2];
        }
        Polyline checkPolyline = new Polyline(checkLines);
        skipLines =
            checkPolyline.arr.length == 4
                && (currClipShape == null
                    || currClipShape.contains(checkPolyline.cornerApprox(1)));
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
        if (i == p_polyline.arr.length) {
          secondLastCornerSkipped = true;
        }
        if (board.changedArea != null) {
          FloatPoint newCorner = checkLines[1].intersectionApprox(checkLines[2]);
          board.changedArea.join(newCorner, currLayer);
          FloatPoint skippedCorner =
              p_polyline.arr[i - 2].intersectionApprox(p_polyline.arr[i - 3]);
          board.changedArea.join(skippedCorner, currLayer);
        }
        polylineChanged = true;
        ++i;
      } else {
        ++newLineIndex;
        newLines[newLineIndex] = p_polyline.arr[i - 3];
      }
    }
    if (!polylineChanged) {
      return p_polyline;
    }
    if (secondLastCornerSkipped) {
      // The second last corner of p_polyline was skipped
      ++newLineIndex;
      newLines[newLineIndex] = p_polyline.arr[p_polyline.arr.length - 1];
      ++newLineIndex;
      newLines[newLineIndex] = p_polyline.arr[p_polyline.arr.length - 2];
    } else {
      for (int i = 3; i > 0; i--) {
        ++newLineIndex;
        newLines[newLineIndex] = p_polyline.arr[p_polyline.arr.length - i];
      }
    }

    Line[] cleanedNewLines = new Line[newLineIndex + 1];
    System.arraycopy(newLines, 0, cleanedNewLines, 0, cleanedNewLines.length);
    return new Polyline(cleanedNewLines);
  }

  @Override
  Polyline smoothenStartCornerAtTrace(PolylineTrace p_trace) {
    return null;
  }

  @Override
  Polyline smoothenEndCornerAtTrace(PolylineTrace p_trace) {
    return null;
  }
}
