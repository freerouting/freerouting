package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeDirections as the 4 orthogonal directions. The class is a
 * singleton with the only instanciation INSTANCE.
 */
public class OrthogonalBoundingDirections implements ShapeBoundingDirections {
  /** the one and only instantiation */
  public static final OrthogonalBoundingDirections INSTANCE = new OrthogonalBoundingDirections();

  /** prevent instantiation */
  private OrthogonalBoundingDirections() {}

  public int count() {
    return 4;
  }

  public RegularTileShape bounds(ConvexShape p_shape) {
    return p_shape.bounding_shape(this);
  }

  public RegularTileShape bounds(IntBox p_box) {
    return p_box;
  }

  public RegularTileShape bounds(IntOctagon p_oct) {
    return p_oct.bounding_box();
  }

  public RegularTileShape bounds(Simplex p_simplex) {
    return p_simplex.bounding_box();
  }

  public RegularTileShape bounds(Circle p_circle) {
    return p_circle.bounding_box();
  }

  public RegularTileShape bounds(PolygonShape p_polygon) {
    return p_polygon.bounding_box();
  }
}
