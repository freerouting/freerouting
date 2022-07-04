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

  public int count() {
    return 8;
  }

  public RegularTileShape bounds(ConvexShape p_shape) {
    return p_shape.bounding_shape(this);
  }

  public RegularTileShape bounds(IntBox p_box) {
    return p_box.to_IntOctagon();
  }

  public RegularTileShape bounds(IntOctagon p_oct) {
    return p_oct;
  }

  public RegularTileShape bounds(Simplex p_simplex) {
    return p_simplex.bounding_octagon();
  }

  public RegularTileShape bounds(Circle p_circle) {
    return p_circle.bounding_octagon();
  }

  public RegularTileShape bounds(PolygonShape p_polygon) {
    return p_polygon.bounding_octagon();
  }
}
