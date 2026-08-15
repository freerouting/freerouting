package app.freerouting.geometry.planar;

/**
 * TileShapes whose border lines may have only directions out of a fixed set, as for example
 * orthogonal directions, which define axis parallel box shapes.
 */
public abstract class RegularTileShape extends TileShape {

  /**
   * Compares the edgelines of index edgeNo of this regular TileShape and other. returns
   * Side.ON_THE_LEFT, if the edgeline of this simplex is to the left of the edgeline of other;
   * Side.COLLINEAR, if the edlines are equal, and Side.ON_THE_RIGHT, if this edgeline is to the
   * right of the edgeline of other.
   */
  public abstract Side compare(RegularTileShape other, int edgeNo);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntBox other, int edgeNo);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntOctagon other, int edgeNo);

  /** Calculates the smallest RegularTileShape containing this shape and other. */
  public abstract RegularTileShape union(RegularTileShape other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntBox other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntOctagon other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  public abstract boolean contains(RegularTileShape other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  @Override
  public abstract boolean isContainedIn(IntBox other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract boolean isContainedIn(IntOctagon other);
}
