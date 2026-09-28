package app.freerouting.geometry.planar;

/**
 * An Area is a not necessarily simply connected Shape, which means, that it may contain holes. The
 * border and the holes of an Area are of class Shape.
 */
public interface Area {

  /** Returns true if the area is empty. */
  boolean isEmpty();

  /** Returns true if the area is contained in a sufficiently large box. */
  boolean isBounded();

  /**
   * Returns 2 if the area contains two-dimensional shapes, 1 if it contains curves, 0 if it is
   * reduced to a point, and -1 if it is empty.
   */
  int dimension();

  /** Checks, if this area is completely contained in box. */
  boolean isContainedIn(IntBox box);

  /** Returns the border shape of this area. */
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
   * Returns true, if point is contained in this area, but not inside a hole. Being on the border is
   * not defined for FloatPoints because of numerical inaccuracy.
   */
  boolean contains(FloatPoint point);

  /** Returns true, if point is inside or on the border of this area, but not inside a hole. */
  boolean contains(Point point);

  /** Calculates an approximation of the nearest point of the shape to fromPoint. */
  FloatPoint nearestPointApprox(FloatPoint fromPoint);

  /** Turns this area by factor times 90 degree around pole. */
  Area turn90Degree(int factor, IntPoint pole);

  /** Rotates the area around pole by angle. The result may be not exact. */
  Area rotateApprox(double angle, FloatPoint pole);

  /** Returns the affine translation of the area by vector. */
  Area translateBy(Vector vector);

  /** Mirrors this area at the horizontal line through pole. */
  Area mirrorHorizontal(IntPoint pole);

  /** Mirrors this area at the vertical line through pole. */
  Area mirrorVertical(IntPoint pole);

  /** Returns an approximation of the corners of this area. */
  FloatPoint[] cornerApproxArr();

  /** Returns a division of this area into convex pieces. */
  TileShape[] splitToConvex();
}
