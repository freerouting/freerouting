package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeDirections as the 4 orthogonal directions. The class is a
 * singleton with the only instantiation INSTANCE.
 */
public final class OrthogonalBoundingDirections implements ShapeBoundingDirections {

  /** the one and only instantiation */
  public static final OrthogonalBoundingDirections INSTANCE = new OrthogonalBoundingDirections();

  /** prevent instantiation */
  private OrthogonalBoundingDirections() {}

  @Override
  public int count() {
    return 4;
  }

  @Override
  public RegularTileShape bounds(ConvexShape p_shape) {
    return p_shape.boundingShape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox p_box) {
    return p_box;
  }

  @Override
  public RegularTileShape bounds(IntOctagon p_oct) {
    return p_oct.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Simplex p_simplex) {
    return p_simplex.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Circle p_circle) {
    return p_circle.boundingBox();
  }

  @Override
  public RegularTileShape bounds(PolygonShape p_polygon) {
    return p_polygon.boundingBox();
  }
}
