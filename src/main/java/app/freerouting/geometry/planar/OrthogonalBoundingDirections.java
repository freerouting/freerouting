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
  public RegularTileShape bounds(ConvexShape pShape) {
    return pShape.boundingShape(this);
  }

  @Override
  public RegularTileShape bounds(IntBox pBox) {
    return pBox;
  }

  @Override
  public RegularTileShape bounds(IntOctagon pOct) {
    return pOct.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Simplex pSimplex) {
    return pSimplex.boundingBox();
  }

  @Override
  public RegularTileShape bounds(Circle pCircle) {
    return pCircle.boundingBox();
  }

  @Override
  public RegularTileShape bounds(PolygonShape pPolygon) {
    return pPolygon.boundingBox();
  }
}
