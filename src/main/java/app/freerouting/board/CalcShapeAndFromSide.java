package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;

/**
 * Used in the shove algorithm to calculate the fromside for pushing and to cut off dog ears of the
 * trace shape.
 */
class CalcShapeAndFromSide {

  final TileShape shape;
  final CalcFromSide fromSide;

  /**
   * Used in the shove algorithm to calculate the fromside for pushing and to cut off dog ears of
   * the trace shape. In the check shove functions, p_in_shove_check is expected to be true. In the
   * actual shove functions p_in_shove_check is expected to be false.
   */
  CalcShapeAndFromSide(PolylineTrace trace, int index, boolean orthogonal, boolean inShoveCheck) {
    ShapeSearchTree searchTree = trace.board.searchTreeManager.getDefaultTree();
    TileShape currShape = trace.getTreeShape(searchTree, index);
    CalcFromSide currFromSide = null;
    boolean cutOffAtStart = false;
    boolean cutOffAtEnd = false;
    if (orthogonal) {
      currShape = currShape.boundingBox();
    } else {
      // prevent dog ears at the start and the end of the substitute trace
      currShape = currShape.toSimplex();
      Line endCutline = calcCutlineAtEnd(index, trace);
      if (endCutline != null) {
        TileShape cutPlane = TileShape.getInstance(endCutline);
        TileShape tmpShape = currShape.intersection(cutPlane);
        if (tmpShape != currShape && !tmpShape.isEmpty()) {
          currShape = tmpShape.toSimplex();
          cutOffAtEnd = true;
        }
      }
      Line startCutline = calcCutlineAtStart(index, trace);
      if (startCutline != null) {
        TileShape cutPlane = TileShape.getInstance(startCutline);
        TileShape tmpShape = currShape.intersection(cutPlane);
        if (tmpShape != currShape && !tmpShape.isEmpty()) {
          currShape = tmpShape.toSimplex();
          cutOffAtStart = true;
        }
      }
      int fromSideNo = -1;
      Line currCutLine = null;
      if (cutOffAtStart) {
        currCutLine = startCutline;
        fromSideNo = currShape.borderLineIndex(currCutLine);
      }
      if (fromSideNo < 0 && cutOffAtEnd) {
        currCutLine = endCutline;
        fromSideNo = currShape.borderLineIndex(currCutLine);
      }
      if (fromSideNo >= 0) {
        FloatPoint borderIntersection =
            currCutLine.intersectionApprox(currShape.borderLine(fromSideNo));
        currFromSide = new CalcFromSide(fromSideNo, borderIntersection);
      }
    }
    if (currFromSide == null && !inShoveCheck) {
      // In p_in_shove_check, using this calculation may produce an undesired stackLevel > 1 in
      // ShapeTraceEntries.
      currFromSide = new CalcFromSide(trace.polyline(), index, currShape);
    }
    this.shape = currShape;
    this.fromSide = currFromSide;
  }

  private static Line calcCutlineAtEnd(int index, PolylineTrace trace) {
    Polyline traceLines = trace.polyline();
    ShapeSearchTree searchTree = trace.board.searchTreeManager.getDefaultTree();
    if (index == traceLines.arr.length - 3
        || traceLines
                .cornerApprox(traceLines.arr.length - 2)
                .distance(traceLines.cornerApprox(index + 1))
            < trace.getCompensatedHalfWidth(searchTree)) {

      Line currLine = traceLines.arr[traceLines.arr.length - 1];
      FloatPoint is = traceLines.cornerApprox(traceLines.arr.length - 3);
      Line cutLine;
      if (currLine.sideOf(is) == Side.ON_THE_LEFT) {
        cutLine = currLine.opposite();
      } else {
        cutLine = currLine;
      }
      return cutLine;
    }
    return null;
  }

  private static Line calcCutlineAtStart(int index, PolylineTrace trace) {
    Polyline traceLines = trace.polyline();
    ShapeSearchTree searchTree = trace.board.searchTreeManager.getDefaultTree();
    if (index == 0
        || traceLines.cornerApprox(0).distance(traceLines.cornerApprox(index))
            < trace.getCompensatedHalfWidth(searchTree)) {
      Line currLine = traceLines.arr[0];
      FloatPoint is = traceLines.cornerApprox(1);
      Line cutLine;
      if (currLine.sideOf(is) == Side.ON_THE_LEFT) {
        cutLine = currLine.opposite();
      } else {
        cutLine = currLine;
      }
      return cutLine;
    }
    return null;
  }
}
