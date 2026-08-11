package app.freerouting.geometry.planar;

/** Describing the functionality for the fixed directions of a RegularTileShape. */
public interface ShapeBoundingDirections {

  /** Returns the count of the fixed directions. */
  int count();

  /**
   * Calculates for an arbitrary ConvexShape a surrounding RegularTileShape with this fixed
   * directions. Is used in the implementation of the search trees.
   */
  RegularTileShape bounds(ConvexShape shape);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntBox box);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(IntOctagon oct);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Simplex simplex);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(Circle circle);

  /** Auxiliary function to implement the same function with parameter type ConvexShape. */
  RegularTileShape bounds(PolygonShape polygon);
}
