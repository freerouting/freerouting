package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeDirections as the 4 orthogonal directions. The class is a
 * singleton with the only instantiation INSTANCE.
 */
public final class OrthogonalBoundingDirections implements ShapeBoundingDirections {

  /** The one and only instantiation. */
  public static final OrthogonalBoundingDirections INSTANCE = new OrthogonalBoundingDirections();

  /** Prevent instantiation. */
  private OrthogonalBoundingDirections() {}

  @Override
  public int count() {
    return 4;
  }

  @Override
  public RegularTileShape bounds(ConvexShape shape) {
    return shape.boundingShape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox box) {
    return box;
  }

  @Override
  public RegularTileShape bounds(IntOctagon oct) {
    return oct.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Simplex simplex) {
    return simplex.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Circle circle) {
    return circle.boundingBox();
  }

  @Override
  public RegularTileShape bounds(PolygonShape polygon) {
    return polygon.boundingBox();
  }
}
