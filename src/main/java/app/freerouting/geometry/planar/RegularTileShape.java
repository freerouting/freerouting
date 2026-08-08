package app.freerouting.geometry.planar;

/**
 * TileShapes whose border lines may have only directions out of a fixed set, as for example
 * orthogonal directions, which define axis parallel box shapes.
 */
public abstract class RegularTileShape extends TileShape {

  /**
   * Compares the edgelines of index p_edge_no of this regular TileShape and p_other. returns
   * Side.ON_THE_LEFT, if the edgeline of this simplex is to the left of the edgeline of p_other;
   * Side.COLLINEAR, if the edlines are equal, and Side.ON_THE_RIGHT, if this edgeline is to the
   * right of the edgeline of p_other.
   */
  public abstract Side compare(RegularTileShape pOther, int pEdgeNo);

  /** calculates the smallest RegularTileShape containing this shape and p_other. */
  public abstract RegularTileShape union(RegularTileShape pOther);

  /** returns true, if p_other is completely contained in this shape */
  public abstract boolean contains(RegularTileShape pOther);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntBox pOther, int pEdgeNo);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntOctagon pOther, int pEdgeNo);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntBox pOther);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntOctagon pOther);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  @Override
  public abstract boolean isContainedIn(IntBox pOther);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract boolean isContainedIn(IntOctagon pOther);
}
