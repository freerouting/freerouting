package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Stoppable;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A PolylineArea is an Area, where the outside border curve and the hole borders consist of
 * straight lines.
 */
public class PolylineArea implements Area, Serializable {

  final PolylineShape borderShape;
  final PolylineShape[] holeArr;
  private transient TileShape[] precalculatedConvexPieces;

  /** Creates a new instance of PolylineShapeWithHoles */
  public PolylineArea(PolylineShape p_border_shape, PolylineShape[] p_hole_arr) {
    borderShape = p_border_shape;
    holeArr = p_hole_arr;
  }

  private static void cutout_hole_piece(
      TileShape p_divide_piece, TileShape p_hole_piece, Collection<TileShape> p_result_pieces) {
    TileShape[] resultPieces = p_divide_piece.cutout(p_hole_piece);
    for (int i = 0; i < resultPieces.length; i++) {
      TileShape currPiece = resultPieces[i];
      if (currPiece.dimension() == 2) {
        p_result_pieces.add(currPiece);
      }
    }
  }

  @Override
  public int dimension() {
    return borderShape.dimension();
  }

  @Override
  public boolean is_bounded() {
    return borderShape.is_bounded();
  }

  @Override
  public boolean is_empty() {
    return borderShape.is_empty();
  }

  @Override
  public boolean is_contained_in(IntBox p_box) {
    return borderShape.is_contained_in(p_box);
  }

  @Override
  public PolylineShape get_border() {
    return borderShape;
  }

  @Override
  public PolylineShape[] get_holes() {
    return holeArr;
  }

  @Override
  public IntBox bounding_box() {
    return borderShape.bounding_box();
  }

  @Override
  public IntOctagon bounding_octagon() {
    return borderShape.bounding_octagon();
  }

  @Override
  public boolean contains(FloatPoint p_point) {
    if (!borderShape.contains(p_point)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].contains(p_point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Point p_point) {
    if (!borderShape.contains(p_point)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].contains_inside(p_point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public FloatPoint nearest_point_approx(FloatPoint p_from_point) {
    double minDist = Double.MAX_VALUE;
    FloatPoint result = null;
    TileShape[] convexShapes = split_to_convex();
    for (int i = 0; i < convexShapes.length; i++) {
      FloatPoint currNearestPoint = convexShapes[i].nearest_point_approx(p_from_point);
      double currDist = currNearestPoint.distance_square(p_from_point);
      if (currDist < minDist) {
        minDist = currDist;
        result = currNearestPoint;
      }
    }
    return result;
  }

  @Override
  public PolylineArea translate_by(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    PolylineShape translatedBorder = borderShape.translate_by(p_vector);
    PolylineShape[] translatedHoles = new PolylineShape[holeArr.length];
    for (int i = 0; i < holeArr.length; i++) {
      translatedHoles[i] = holeArr[i].translate_by(p_vector);
    }
    return new PolylineArea(translatedBorder, translatedHoles);
  }

  @Override
  public FloatPoint[] corner_approx_arr() {
    int cornerCount = borderShape.border_line_count();
    for (int i = 0; i < holeArr.length; i++) {
      cornerCount += holeArr[i].border_line_count();
    }
    FloatPoint[] result = new FloatPoint[cornerCount];
    FloatPoint[] currCornerArr = borderShape.corner_approx_arr();
    System.arraycopy(currCornerArr, 0, result, 0, currCornerArr.length);
    int destPos = currCornerArr.length;
    for (int i = 0; i < holeArr.length; i++) {
      currCornerArr = holeArr[i].corner_approx_arr();
      System.arraycopy(currCornerArr, 0, result, destPos, currCornerArr.length);
      destPos += currCornerArr.length;
    }
    return result;
  }

  /**
   * Splits this polygon shape with holes into convex pieces. The result is not exact, because
   * rounded intersections of lines are used in the result pieces. It can be made exact, if
   * Polylines are returned instead of Polygons, so that no intersection points are needed in the
   * result.
   */
  @Override
  public TileShape[] split_to_convex() {
    return split_to_convex(null);
  }

  /**
   * Splits this polygon shape with holes into convex pieces. The result is not exact, because
   * rounded intersections of lines are used in the result pieces. It can be made exact, if
   * Polylines are returned instead of Polygons, so that no intersection points are needed in the
   * result. If p_stoppable_thread != null, this function can be interrupted.
   */
  public TileShape[] split_to_convex(Stoppable p_stoppable_thread) {
    if (precalculatedConvexPieces == null) {
      TileShape[] convexBorderPieces = borderShape.split_to_convex();
      if (convexBorderPieces == null) {
        // split failed
        return null;
      }
      Collection<TileShape> currPieceList = new LinkedList<>(Arrays.asList(convexBorderPieces));
      for (int i = 0; i < holeArr.length; i++) {
        if (holeArr[i].dimension() < 2) {
          FRLogger.warn("PolylineArea. split_to_convex: dimension 2 for hole expected");
          continue;
        }
        TileShape[] convexHolePieces = holeArr[i].split_to_convex();
        if (convexHolePieces == null) {
          return null;
        }
        for (int j = 0; j < convexHolePieces.length; j++) {
          TileShape currHolePiece = convexHolePieces[j];
          Collection<TileShape> newPieceList = new LinkedList<>();
          for (TileShape curr_divide_piece : currPieceList) {
            if (p_stoppable_thread != null && p_stoppable_thread.isStopRequested()) {
              return null;
            }
            cutout_hole_piece(curr_divide_piece, currHolePiece, newPieceList);
          }
          currPieceList = newPieceList;
        }
      }
      precalculatedConvexPieces = new TileShape[currPieceList.size()];
      Iterator<TileShape> it = currPieceList.iterator();
      for (int i = 0; i < precalculatedConvexPieces.length; i++) {
        precalculatedConvexPieces[i] = it.next();
      }
    }
    return precalculatedConvexPieces;
  }

  @Override
  public PolylineArea turn_90_degree(int p_factor, IntPoint p_pole) {
    PolylineShape newBorder = borderShape.turn_90_degree(p_factor, p_pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].turn_90_degree(p_factor, p_pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea rotate_approx(double p_angle, FloatPoint p_pole) {
    PolylineShape newBorder = borderShape.rotate_approx(p_angle, p_pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].rotate_approx(p_angle, p_pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirror_vertical(IntPoint p_pole) {
    PolylineShape newBorder = borderShape.mirror_vertical(p_pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirror_vertical(p_pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirror_horizontal(IntPoint p_pole) {
    PolylineShape newBorder = borderShape.mirror_horizontal(p_pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirror_horizontal(p_pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }
}
