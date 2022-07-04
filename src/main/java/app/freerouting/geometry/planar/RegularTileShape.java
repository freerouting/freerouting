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
  public abstract Side compare(RegularTileShape p_other, int p_edge_no);

  /** calculates the smallest RegularTileShape containing this shape and p_other. */
  public abstract RegularTileShape union(RegularTileShape p_other);

  /** returns true, if p_other is completely contained in this shape */
  public abstract boolean contains(RegularTileShape p_other);

  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntBox p_other, int p_edge_no);
  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract Side compare(IntOctagon p_other, int p_edge_no);
  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntBox p_other);
  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract RegularTileShape union(IntOctagon p_other);
  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  public abstract boolean is_contained_in(IntBox p_other);
  /** Auxiliary function to implement the same function with parameter type RegularTileShape. */
  abstract boolean is_contained_in(IntOctagon p_other);
}
