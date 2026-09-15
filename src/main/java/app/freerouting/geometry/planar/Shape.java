package app.freerouting.geometry.planar;

/**
 * Interface describing functionality for connected 2-dimensional shapes in the plane. A Shape
 * object is expected to be simply connected, that means, it may not contain holes.
 */
public interface Shape extends Area {

  /**
   * Returns the length of the border of this shape. If the shape is unbounded, Integer.MAX_VALUE is
   * returned.
   */
  double circumference();

  /**
   * Returns the content of the area of the shape. If the shape is unbounded, Double.MAX_VALUE is
   * returned.
   */
  double area();

  /** Returns the gravity point of this shape. */
  FloatPoint centreOfGravity();

  /** Returns true, if point is not contained in the inside or the boundary of the shape. */
  boolean isOutside(Point point);

  /** Returns true, if point is contained in this shape, but not on the border. */
  boolean containsInside(Point point);

  /** Returns true, if point lies exact on the boundary of the shape. */
  boolean containsOnBorder(Point point);

  /**
   * Returns the distance between point and its nearest point on the shape. 0, if point is contained
   * in this shape
   */
  double distance(FloatPoint point);

  /** Return a bounding TileShape of this shape. */
  TileShape boundingTile();

  /** Returns the bounding RegularTileShape with the fixed directions dirs. */
  RegularTileShape boundingShape(ShapeBoundingDirections dirs);

  /** Returns the distance between point and its nearest point on the border of the shape. */
  double borderDistance(FloatPoint point);

  /** Returns the smallest distance from the centre of gravity to the border of the shape. */
  double smallestRadius();

  /**
   * Returns the offset shape of this shape by offsetting the boundary by distance to the outside.
   * The result instance may be of a different class than this instance. (For example an enlarged
   * IntBox is an IntOctagon).
   */
  Shape enlarge(double offset);

  /** Checks, if this shape and other have a nonempty intersection. */
  boolean intersects(Shape other);

  /** Auxiliary function to implement the same function with parameter type Shape. */
  boolean intersects(IntBox other);

  /** Auxiliary function to implement the same function with parameter type Shape. */
  boolean intersects(IntOctagon other);

  /** Auxiliary function to implement the same function with parameter type Shape. */
  boolean intersects(Simplex other);

  /** Auxiliary function to implement the same function with parameter type Shape. */
  boolean intersects(Circle other);

  /**
   * Cuts out the parts of polyline in the interior of this shape and returns a list of the
   * remaining pieces of polyline. Pieces completely contained in the border of this shape are not
   * returned.
   */
  Polyline[] cutout(Polyline polyline);
}
