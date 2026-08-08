package app.freerouting.geometry.planar;

/**
 * An Area is a not necessarily simply connected Shape, which means, that it may contain holes. The
 * border and the holes of an Area are of class Shape.
 */
public interface Area {

  /** returns true, if the area is empty */
  boolean isEmpty();

  /** returns true, if the area is contained in a sufficiently large box */
  boolean isBounded();

  /**
   * returns 2, if the area contains 2 dimensional shapes , 1, if it contains curves, 0, if it is
   * reduced to a points and -1, if it is empty.
   */
  int dimension();

  /** Checks, if this area is completely contained in p_box. */
  boolean isContainedIn(IntBox p_box);

  /** returns the border shape of this area */
  Shape getBorder();

  /** Returns the array of holes, of this area. */
  Shape[] getHoles();

  /**
   * Returns the smallest surrounding box of the area. If the area is not bounded, some coordinates
   * of the resulting box may be equal Integer.MAX_VALUE
   */
  IntBox boundingBox();

  /**
   * Returns the smallest surrounding octagon of the area. If the area is not bounded, some
   * coordinates of the resulting octagon may be equal Integer.MAX_VALUE
   */
  IntOctagon boundingOctagon();

  /**
   * Returns true, if p_point is contained in this area, but not inside a hole. Being on the border
   * is not defined for FloatPoints because of numerical inaccuracy.
   */
  boolean contains(FloatPoint p_point);

  /** Returns true, if p_point is inside or on the border of this area, but not inside a hole. */
  boolean contains(Point p_point);

  /** Calculates an approximation of the nearest point of the shape to p_from_point */
  FloatPoint nearestPointApprox(FloatPoint p_from_point);

  /** Turns this area by p_factor times 90 degree around p_pole. */
  Area turn90Degree(int p_factor, IntPoint p_pole);

  /** Rotates the area around p_pole by p_angle. The result may be not exact. */
  Area rotateApprox(double p_angle, FloatPoint p_pole);

  /** Returns the affine translation of the area by p_vector */
  Area translateBy(Vector p_vector);

  /** Mirrors this area at the horizontal line through p_pole. */
  Area mirrorHorizontal(IntPoint p_pole);

  /** Mirrors this area at the vertical line through p_pole. */
  Area mirrorVertical(IntPoint p_pole);

  /** Returns an approximation of the corners of this area. */
  FloatPoint[] cornerApproxArr();

  /** Returns a division of this area into convex pieces. */
  TileShape[] splitToConvex();
}
