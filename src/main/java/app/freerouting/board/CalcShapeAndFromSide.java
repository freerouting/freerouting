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
  CalcShapeAndFromSide(
      PolylineTrace p_trace, int p_index, boolean p_orthogonal, boolean p_in_shove_check) {
    ShapeSearchTree searchTree = p_trace.board.searchTreeManager.get_default_tree();
    TileShape currShape = p_trace.get_tree_shape(searchTree, p_index);
    CalcFromSide currFromSide = null;
    boolean cutOffAtStart = false;
    boolean cutOffAtEnd = false;
    if (p_orthogonal) {
      currShape = currShape.bounding_box();
    } else {
      // prevent dog ears at the start and the end of the substitute trace
      currShape = currShape.to_Simplex();
      Line endCutline = calc_cutline_at_end(p_index, p_trace);
      if (endCutline != null) {
        TileShape cutPlane = TileShape.get_instance(endCutline);
        TileShape tmpShape = currShape.intersection(cutPlane);
        if (tmpShape != currShape && !tmpShape.is_empty()) {
          currShape = tmpShape.to_Simplex();
          cutOffAtEnd = true;
        }
      }
      Line startCutline = calc_cutline_at_start(p_index, p_trace);
      if (startCutline != null) {
        TileShape cutPlane = TileShape.get_instance(startCutline);
        TileShape tmpShape = currShape.intersection(cutPlane);
        if (tmpShape != currShape && !tmpShape.is_empty()) {
          currShape = tmpShape.to_Simplex();
          cutOffAtStart = true;
        }
      }
      int fromSideNo = -1;
      Line currCutLine = null;
      if (cutOffAtStart) {
        currCutLine = startCutline;
        fromSideNo = currShape.border_line_index(currCutLine);
      }
      if (fromSideNo < 0 && cutOffAtEnd) {
        currCutLine = endCutline;
        fromSideNo = currShape.border_line_index(currCutLine);
      }
      if (fromSideNo >= 0) {
        FloatPoint borderIntersection =
            currCutLine.intersection_approx(currShape.border_line(fromSideNo));
        currFromSide = new CalcFromSide(fromSideNo, borderIntersection);
      }
    }
    if (currFromSide == null && !p_in_shove_check) {
      // In p_in_shove_check, using this calculation may produce an undesired stackLevel > 1 in
      // ShapeTraceEntries.
      currFromSide = new CalcFromSide(p_trace.polyline(), p_index, currShape);
    }
    this.shape = currShape;
    this.fromSide = currFromSide;
  }

  private static Line calc_cutline_at_end(int p_index, PolylineTrace p_trace) {
    Polyline traceLines = p_trace.polyline();
    ShapeSearchTree searchTree = p_trace.board.searchTreeManager.get_default_tree();
    if (p_index == traceLines.arr.length - 3
        || traceLines
                .corner_approx(traceLines.arr.length - 2)
                .distance(traceLines.corner_approx(p_index + 1))
            < p_trace.get_compensated_half_width(searchTree)) {

      Line currLine = traceLines.arr[traceLines.arr.length - 1];
      FloatPoint is = traceLines.corner_approx(traceLines.arr.length - 3);
      Line cutLine;
      if (currLine.side_of(is) == Side.ON_THE_LEFT) {
        cutLine = currLine.opposite();
      } else {
        cutLine = currLine;
      }
      return cutLine;
    }
    return null;
  }

  private static Line calc_cutline_at_start(int p_index, PolylineTrace p_trace) {
    Polyline traceLines = p_trace.polyline();
    ShapeSearchTree searchTree = p_trace.board.searchTreeManager.get_default_tree();
    if (p_index == 0
        || traceLines.corner_approx(0).distance(traceLines.corner_approx(p_index))
            < p_trace.get_compensated_half_width(searchTree)) {
      Line currLine = traceLines.arr[0];
      FloatPoint is = traceLines.corner_approx(1);
      Line cutLine;
      if (currLine.side_of(is) == Side.ON_THE_LEFT) {
        cutLine = currLine.opposite();
      } else {
        cutLine = currLine;
      }
      return cutLine;
    }
    return null;
  }
}
