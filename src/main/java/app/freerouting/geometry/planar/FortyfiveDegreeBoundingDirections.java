package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeBoundingDirections as the 8 directions, which are multiples of
 * 45 degree. The class is a singleton with the only instantiation INSTANCE.
 */
public final class FortyfiveDegreeBoundingDirections implements ShapeBoundingDirections {

  /** The one and only instantiation. */
  public static final FortyfiveDegreeBoundingDirections INSTANCE =
      new FortyfiveDegreeBoundingDirections();

  /** Prevent instantiation. */
  private FortyfiveDegreeBoundingDirections() {}

  @Override
  public int count() {
    return 8;
  }

  @Override
  public RegularTileShape bounds(ConvexShape shape) {
    return shape.boundingShape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox box) {
    return box.toIntOctagon();
  }

  @Override
  public RegularTileShape bounds(IntOctagon oct) {
    return oct;
  }

  @Override
  public RegularTileShape bounds(Simplex simplex) {
    return simplex.boundingOctagon();
  }

  @Override
  public RegularTileShape bounds(Circle circle) {
    return circle.boundingOctagon();
  }

  @Override
  public RegularTileShape bounds(PolygonShape polygon) {
    return polygon.boundingOctagon();
  }
}
