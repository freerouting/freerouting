package app.freerouting.geometry.planar;

/**
 * Implements the abstract class ShapeBoundingDirections as the 8 directions, which are multiples of
 * 45 degree. The class is a singleton with the only instantiation INSTANCE.
 */
public final class FortyfiveDegreeBoundingDirections implements ShapeBoundingDirections {

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
  public RegularTileShape bounds(ConvexShape pShape) {
    return pShape.boundingShape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox pBox) {
    return pBox.toIntOctagon();
  }

  @Override
  public RegularTileShape bounds(IntOctagon pOct) {
    return pOct;
  }

  @Override
  public RegularTileShape bounds(Simplex pSimplex) {
    return pSimplex.boundingOctagon();
  }

  @Override
  public RegularTileShape bounds(Circle pCircle) {
    return pCircle.boundingOctagon();
  }

  @Override
  public RegularTileShape bounds(PolygonShape pPolygon) {
    return pPolygon.boundingOctagon();
  }
}
