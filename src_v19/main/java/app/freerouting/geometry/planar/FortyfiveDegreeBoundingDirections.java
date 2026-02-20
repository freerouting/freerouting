package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeBoundingDirections as the 8 directions, which are multiples of
 * 45 degree. The class is a singleton with the only instantiation INSTANCE.
 */
public class FortyfiveDegreeBoundingDirections implements ShapeBoundingDirections {
  /** the one and only instantiation */
  public static final FortyfiveDegreeBoundingDirections INSTANCE =
      new FortyfiveDegreeBoundingDirections();

  /** prevent instantiation */
  private FortyfiveDegreeBoundingDirections() {}

  @Override
  public int count() {
    return 8;
  }

  @Override
  public RegularTileShape bounds(ConvexShape p_shape) {
    return p_shape.bounding_shape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox p_box) {
    return p_box.to_IntOctagon();
  }

  @Override
  public RegularTileShape bounds(IntOctagon p_oct) {
    return p_oct;
  }

  @Override
  public RegularTileShape bounds(Simplex p_simplex) {
    return p_simplex.bounding_octagon();
  }

  @Override
  public RegularTileShape bounds(Circle p_circle) {
    return p_circle.bounding_octagon();
  }

  @Override
  public RegularTileShape bounds(PolygonShape p_polygon) {
    return p_polygon.bounding_octagon();
  }
}
