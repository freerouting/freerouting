package app.freerouting.geometry.planar;

/** Describing the functionality for the fixed directions of a RegularTileShape. */
public interface ShapeBoundingDirections {
  /** Retuns the count of the fixed directions. */
  int count();

  /**
   * Calculates for an arbitrary ConvexShape a surrounding RegularTileShape with this fixed
   * directions. Is used in the implementation of the seach trees.
   */
  RegularTileShape bounds(ConvexShape p_shape);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntBox p_box);
  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntOctagon p_oct);
  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Simplex p_simplex);
  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Circle p_circle);
  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(PolygonShape p_polygon);
}
