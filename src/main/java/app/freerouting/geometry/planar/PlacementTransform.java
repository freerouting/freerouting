package app.freerouting.geometry.planar;

/** Affine package placement; quantization happens only after local and world transforms. */
public final class PlacementTransform {
  private final FloatPoint location;
  private final double rotation;
  private final boolean mirrored;
  private final boolean rotateFirst;

  /** Creates a placement using the board's mirror/rotation convention. */
  public PlacementTransform(
      FloatPoint location, double rotation, boolean mirrored, boolean rotateFirst) {
    this.location = location;
    this.rotation = rotation;
    this.mirrored = mirrored;
    this.rotateFirst = rotateFirst;
  }

  /** Applies only the linear part, for offsets and directions. */
  public FloatPoint direction(FloatPoint point) {
    FloatPoint result = point;
    if (mirrored && !rotateFirst) {
      result = new FloatPoint(-result.x, result.y);
    }
    result = rotate(result, rotation);
    if (mirrored && rotateFirst) {
      result = new FloatPoint(-result.x, result.y);
    }
    return result;
  }

  /** Places a package-local point without rounding intermediate coordinates. */
  public FloatPoint point(FloatPoint point) {
    FloatPoint offset = direction(point);
    return new FloatPoint(location.x + offset.x, location.y + offset.y);
  }

  /** Exact permutations avoid trigonometric noise at quarter turns. */
  public static FloatPoint rotate(FloatPoint point, double degrees) {
    return degrees % 90 == 0
        ? point.turn90Degree(Math.floorMod((int) (degrees / 90), 4), FloatPoint.ZERO)
        : point.rotate(Math.toRadians(degrees), FloatPoint.ZERO);
  }

  /** Places pad geometry, including asymmetric local offsets, on the board grid. */
  public Shape shape(Shape shape, FloatPoint offset, double pinRotation) {
    if (shape.isEmpty()) {
      return shape;
    }
    if (shape instanceof Circle circle) {
      return new Circle(
          padPoint(circle.center.toFloat(), offset, pinRotation).round(), circle.radius);
    }
    FloatPoint[] corners = shape.cornerApproxArr();
    Point[] placed = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      placed[mirrored ? corners.length - 1 - i : i] =
          padPoint(corners[i], offset, pinRotation).round();
    }
    if (shape instanceof TileShape) {
      Point[] unique = java.util.Arrays.stream(placed).distinct().toArray(Point[]::new);
      Point[] normalized = unique.length < 3 ? unique : new PolygonShape(unique).corners;
      return switch (normalized.length) {
        case 0 -> IntBox.EMPTY;
        case 1 -> TileShape.getInstance(normalized[0]);
        case 2 -> new LineSegment(new Polyline(normalized), 1).toSimplex();
        default -> TileShape.getInstance(normalized);
      };
    }
    return new PolygonShape(placed);
  }

  private FloatPoint padPoint(FloatPoint point, FloatPoint offset, double pinRotation) {
    FloatPoint rotated = rotate(point, pinRotation);
    return point(new FloatPoint(offset.x + rotated.x, offset.y + rotated.y));
  }

  /** Places an outline or keepout while retaining its holes. */
  public Area area(Area area) {
    Shape border = shape(area.getBorder(), FloatPoint.ZERO, 0);
    Shape[] holes = area.getHoles();
    if (holes.length == 0) {
      return border;
    }
    PolylineShape[] placedHoles = new PolylineShape[holes.length];
    for (int i = 0; i < holes.length; i++) {
      placedHoles[i] = (PolylineShape) shape(holes[i], FloatPoint.ZERO, 0);
    }
    return new PolylineArea((PolylineShape) border, placedHoles);
  }
}
