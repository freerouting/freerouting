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
class ShapeAndEntrySide {

  final TileShape shape;
  final ShapeEntrySide fromSide;

  /**
   * Used in the shove algorithm to calculate the fromside for pushing and to cut off dog ears of
   * the trace shape. In the check shove functions, inShoveCheck is expected to be true. In the
   * actual shove functions inShoveCheck is expected to be false.
   */
  ShapeAndEntrySide(PolylineTrace trace, int index, boolean orthogonal, boolean inShoveCheck) {
    ShapeSearchTree searchTree = trace.board.searchTreeManager.getDefaultTree();
    TileShape currentShape = trace.getTreeShape(searchTree, index);
    ShapeEntrySide currentFromSide = null;
    boolean cutOffAtStart = false;
    boolean cutOffAtEnd = false;
    if (orthogonal) {
      currentShape = currentShape.boundingBox();
    } else {
      // prevent dog ears at the start and the end of the substitute trace
      currentShape = currentShape.toSimplex();
      Line endCutline = calcCutlineAtEnd(index, trace);
      if (endCutline != null) {
        TileShape cutPlane = TileShape.getInstance(endCutline);
        TileShape tmpShape = currentShape.intersection(cutPlane);
        if (tmpShape != currentShape && !tmpShape.isEmpty()) {
          currentShape = tmpShape.toSimplex();
          cutOffAtEnd = true;
        }
      }
      Line startCutline = calcCutlineAtStart(index, trace);
      if (startCutline != null) {
        TileShape cutPlane = TileShape.getInstance(startCutline);
        TileShape tmpShape = currentShape.intersection(cutPlane);
        if (tmpShape != currentShape && !tmpShape.isEmpty()) {
          currentShape = tmpShape.toSimplex();
          cutOffAtStart = true;
        }
      }
      int fromSideIndex = -1;
      Line currentCutLine = null;
      if (cutOffAtStart) {
        currentCutLine = startCutline;
        fromSideIndex = currentShape.borderLineIndex(currentCutLine);
      }
      if (fromSideIndex < 0 && cutOffAtEnd) {
        currentCutLine = endCutline;
        fromSideIndex = currentShape.borderLineIndex(currentCutLine);
      }
      if (fromSideIndex >= 0) {
        FloatPoint borderIntersection =
            currentCutLine.intersectionApprox(currentShape.borderLine(fromSideIndex));
        currentFromSide = new ShapeEntrySide(fromSideIndex, borderIntersection);
      }
    }
    if (currentFromSide == null && !inShoveCheck) {
      // In inShoveCheck, using this calculation may produce an undesired stackLevel > 1 in
      // ShapeTraceEntries.
      currentFromSide = new ShapeEntrySide(trace.polyline(), index, currentShape);
    }
    this.shape = currentShape;
    this.fromSide = currentFromSide;
  }

  private static Line calcCutlineAtEnd(int index, PolylineTrace trace) {
    Polyline traceLines = trace.polyline();
    ShapeSearchTree searchTree = trace.board.searchTreeManager.getDefaultTree();
    if (index == traceLines.lines.length - 3
        || traceLines
                .cornerApprox(traceLines.lines.length - 2)
                .distance(traceLines.cornerApprox(index + 1))
            < trace.getCompensatedHalfWidth(searchTree)) {

      Line currentLine = traceLines.lines[traceLines.lines.length - 1];
      FloatPoint is = traceLines.cornerApprox(traceLines.lines.length - 3);
      Line cutLine;
      if (currentLine.sideOf(is) == Side.ON_THE_LEFT) {
        cutLine = currentLine.opposite();
      } else {
        cutLine = currentLine;
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
      Line currentLine = traceLines.lines[0];
      FloatPoint is = traceLines.cornerApprox(1);
      Line cutLine;
      if (currentLine.sideOf(is) == Side.ON_THE_LEFT) {
        cutLine = currentLine.opposite();
      } else {
        cutLine = currentLine;
      }
      return cutLine;
    }
    return null;
  }
}
