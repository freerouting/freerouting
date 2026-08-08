package app.freerouting.geometry.planar;

/** Describing the functionality for the fixed directions of a RegularTileShape. */
public interface ShapeBoundingDirections {

  /** Returns the count of the fixed directions. */
  int count();

  /**
   * Calculates for an arbitrary ConvexShape a surrounding RegularTileShape with this fixed
   * directions. Is used in the implementation of the search trees.
   */
  RegularTileShape bounds(ConvexShape pShape);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntBox pBox);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntOctagon pOct);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Simplex pSimplex);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Circle pCircle);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(PolygonShape pPolygon);
}
