package app.freerouting.geometry.planar;

/**
 * A shape is defined as convex, if for each line segment with both endpoints contained in the shape
 * the whole segment is contained completely in the shape.
 */
public interface ConvexShape extends Shape {

  /**
   * Calculates the offset shape by distance. If distance {@literal >} 0, the shape will be
   * enlarged, else the result shape will be smaller.
   */
  ConvexShape offset(double distance);

  /** Shrinks the shape by offset. The result shape will not be empty. */
  ConvexShape shrink(double offset);

  /** Returns the maximum diameter of the shape. */
  double maxWidth();

  /** Returns the minimum diameter of the shape. */
  double minWidth();
}
