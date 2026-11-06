package app.freerouting.geometry.planar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.util.GeometricShapeFactory;

/**
 * Pure-Java 2D planar polygon area supporting constructive solid geometry (CSG) boolean operations
 * (union, difference, intersection) and island decomposition.
 *
 * <p>Backed by JTS Core, completely eliminating dependencies on {@code java.desktop} ({@code
 * java.awt.geom.Area}) from the core routing engine, DRC, and native CLI executables.
 */
public final class PlanarArea {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
  private static final PlanarArea EMPTY = new PlanarArea(GEOMETRY_FACTORY.createPolygon());

  private final Geometry geometry;

  /**
   * Constructs a PlanarArea wrapping the specified JTS Geometry.
   *
   * @param geometry the underlying JTS geometry
   */
  public PlanarArea(Geometry geometry) {
    this.geometry = (geometry != null) ? geometry : GEOMETRY_FACTORY.createPolygon();
  }

  /** Returns an empty planar area. */
  public static PlanarArea empty() {
    return EMPTY;
  }

  /**
   * Creates a PlanarArea from an {@link IntBox}.
   *
   * @param box the bounding box
   * @return the resulting planar area
   */
  public static PlanarArea fromBox(IntBox box) {
    if (box == null || box.width() <= 0 || box.height() <= 0) {
      return empty();
    }
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(box.ll.x, box.ll.y),
          new Coordinate(box.ur.x, box.ll.y),
          new Coordinate(box.ur.x, box.ur.y),
          new Coordinate(box.ll.x, box.ur.y),
          new Coordinate(box.ll.x, box.ll.y)
        };
    return new PlanarArea(GEOMETRY_FACTORY.createPolygon(coords));
  }

  /**
   * Creates a PlanarArea approximating a {@link Circle}.
   *
   * @param circle the circle shape
   * @param numPoints number of vertices for polygon approximation (minimum 16)
   * @return the resulting planar area
   */
  public static PlanarArea fromCircle(Circle circle, int numPoints) {
    if (circle == null || circle.radius <= 0) {
      return empty();
    }
    GeometricShapeFactory gsf = new GeometricShapeFactory(GEOMETRY_FACTORY);
    gsf.setCentre(new Coordinate(circle.center.x, circle.center.y));
    gsf.setSize(2.0 * circle.radius);
    gsf.setNumPoints(Math.max(numPoints, 16));
    return new PlanarArea(gsf.createCircle());
  }

  /**
   * Creates a PlanarArea from a {@link PolylineShape}.
   *
   * @param poly the bounded polyline shape
   * @return the resulting planar area
   */
  public static PlanarArea fromPolylineShape(PolylineShape poly) {
    if (poly == null || !poly.isBounded()) {
      return empty();
    }
    int count = poly.borderLineCount();
    if (count < 3) {
      return empty();
    }
    Coordinate[] coords = new Coordinate[count + 1];
    for (int i = 0; i < count; i++) {
      FloatPoint p = poly.cornerApprox(i);
      coords[i] = new Coordinate(p.x, p.y);
    }
    coords[count] = new Coordinate(coords[0].x, coords[0].y);
    try {
      LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coords);
      return new PlanarArea(GEOMETRY_FACTORY.createPolygon(shell));
    } catch (Exception _) {
      return empty();
    }
  }

  /**
   * Creates a PlanarArea from a generic {@link Shape}.
   *
   * @param shape the geometric shape
   * @return the resulting planar area
   */
  public static PlanarArea fromShape(Shape shape) {
    if (shape == null) {
      return empty();
    }
    if (shape instanceof Circle circle) {
      return fromCircle(circle, 32);
    }
    if (shape instanceof IntBox box) {
      return fromBox(box);
    }
    if (shape instanceof PolylineShape poly) {
      return fromPolylineShape(poly);
    }
    return empty();
  }

  /**
   * Creates a PlanarArea from a Freerouting domain {@link Area}.
   *
   * @param area the domain area with border and optional holes
   * @return the resulting planar area
   */
  public static PlanarArea fromArea(Area area) {
    if (area == null || area.isEmpty()) {
      return empty();
    }
    if (area instanceof Circle circle) {
      return fromCircle(circle, 32);
    }
    Shape borderShape = area.getBorder();
    if (!(borderShape instanceof PolylineShape border) || !border.isBounded()) {
      return empty();
    }
    PlanarArea result = fromPolylineShape(border);
    Shape[] holes = area.getHoles();
    if (holes != null && holes.length > 0) {
      List<PlanarArea> holeAreas = new ArrayList<>(holes.length);
      for (Shape hole : holes) {
        PlanarArea ha = fromShape(hole);
        if (ha != null && !ha.isEmpty()) {
          holeAreas.add(ha);
        }
      }
      if (!holeAreas.isEmpty()) {
        result = result.subtractAll(holeAreas);
      }
    }
    return result;
  }

  /**
   * Creates a rotated spoke rectangle centered at {@code (centerX, centerY)}.
   *
   * @param centerX center x coordinate
   * @param centerY center y coordinate
   * @param spokeWidth width of the spoke
   * @param length total length of the spoke
   * @param angleRadians rotation angle in radians
   * @return the resulting rotated rectangle planar area
   */
  public static PlanarArea createRotatedSpoke(
      double centerX, double centerY, double spokeWidth, double length, double angleRadians) {
    double halfW = spokeWidth / 2.0;
    double halfL = length / 2.0;
    double cos = Math.cos(angleRadians);
    double sin = Math.sin(angleRadians);

    double[][] localPts = {
      {-halfW, -halfL},
      {halfW, -halfL},
      {halfW, halfL},
      {-halfW, halfL}
    };

    Coordinate[] coords = new Coordinate[5];
    for (int i = 0; i < 4; i++) {
      double rx = localPts[i][0] * cos - localPts[i][1] * sin;
      double ry = localPts[i][0] * sin + localPts[i][1] * cos;
      coords[i] = new Coordinate(centerX + rx, centerY + ry);
    }
    coords[4] = new Coordinate(coords[0].x, coords[0].y);

    try {
      LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coords);
      return new PlanarArea(GEOMETRY_FACTORY.createPolygon(shell));
    } catch (Exception _) {
      return empty();
    }
  }

  /** Returns true if this area is empty or has zero area. */
  public boolean isEmpty() {
    return geometry == null || geometry.isEmpty() || geometry.getArea() <= 0.0;
  }

  /**
   * Computes the geometric difference (this minus other).
   *
   * @param other the area to subtract
   * @return the resulting planar area
   */
  public PlanarArea difference(PlanarArea other) {
    if (isEmpty()) {
      return empty();
    }
    if (other == null || other.isEmpty()) {
      return this;
    }
    try {
      return new PlanarArea(this.geometry.difference(other.geometry));
    } catch (Exception _) {
      return this;
    }
  }

  /**
   * Computes the geometric union of this and other.
   *
   * @param other the area to union with
   * @return the resulting planar area
   */
  public PlanarArea union(PlanarArea other) {
    if (isEmpty()) {
      return other != null ? other : empty();
    }
    if (other == null || other.isEmpty()) {
      return this;
    }
    try {
      return new PlanarArea(this.geometry.union(other.geometry));
    } catch (Exception _) {
      return this;
    }
  }

  /**
   * Computes the geometric intersection of this and other.
   *
   * @param other the area to intersect with
   * @return the resulting planar area
   */
  public PlanarArea intersection(PlanarArea other) {
    if (isEmpty() || other == null || other.isEmpty()) {
      return empty();
    }
    try {
      return new PlanarArea(this.geometry.intersection(other.geometry));
    } catch (Exception _) {
      return empty();
    }
  }

  /**
   * Computes the union of a collection of planar areas using an optimized spatial union.
   *
   * @param areas collection of planar areas
   * @return the merged planar area
   */
  public static PlanarArea unionAll(List<PlanarArea> areas) {
    if (areas == null || areas.isEmpty()) {
      return empty();
    }
    List<Geometry> geoms = new ArrayList<>(areas.size());
    for (PlanarArea a : areas) {
      if (a != null && !a.isEmpty()) {
        geoms.add(a.geometry);
      }
    }
    if (geoms.isEmpty()) {
      return empty();
    }
    try {
      return new PlanarArea(UnaryUnionOp.union(geoms));
    } catch (Exception _) {
      Geometry result = geoms.get(0);
      for (int i = 1; i < geoms.size(); i++) {
        try {
          result = result.union(geoms.get(i));
        } catch (Exception _) {
          // Continue best-effort merge
        }
      }
      return new PlanarArea(result);
    }
  }

  /**
   * Subtracts all given cutout areas from this area.
   *
   * @param cutouts collection of areas to subtract
   * @return the resulting planar area
   */
  public PlanarArea subtractAll(List<PlanarArea> cutouts) {
    if (isEmpty() || cutouts == null || cutouts.isEmpty()) {
      return this;
    }
    PlanarArea combined = unionAll(cutouts);
    return difference(combined);
  }

  /**
   * Decomposes this planar area into contiguous, disjoint island polygons.
   *
   * @return list of contiguous island planar areas
   */
  public List<PlanarArea> decomposeIntoIslands() {
    if (isEmpty()) {
      return Collections.emptyList();
    }
    List<PlanarArea> islands = new ArrayList<>();
    collectPolygons(this.geometry, islands);
    return islands;
  }

  private static void collectPolygons(Geometry geom, List<PlanarArea> list) {
    if (geom == null || geom.isEmpty()) {
      return;
    }
    if (geom instanceof Polygon poly) {
      if (!poly.isEmpty() && poly.getArea() > 0.0) {
        list.add(new PlanarArea(poly));
      }
    } else if (geom instanceof GeometryCollection col) {
      for (int i = 0; i < col.getNumGeometries(); i++) {
        collectPolygons(col.getGeometryN(i), list);
      }
    }
  }

  /**
   * Tests if the specified point {@code (x, y)} is contained within this area.
   *
   * @param x point x coordinate
   * @param y point y coordinate
   * @return true if the point is strictly inside this area
   */
  public boolean contains(double x, double y) {
    if (isEmpty()) {
      return false;
    }
    try {
      Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
      return this.geometry.contains(pt);
    } catch (Exception _) {
      return false;
    }
  }

  /** Returns the total area in square board units. */
  public double getArea() {
    return isEmpty() ? 0.0 : this.geometry.getArea();
  }

  /** Returns the center coordinate of this area's bounding envelope. */
  public double[] getCenter() {
    if (isEmpty()) {
      return new double[] {0.0, 0.0};
    }
    var env = this.geometry.getEnvelopeInternal();
    return new double[] {env.centre().x, env.centre().y};
  }

  /** Returns bounding box as {@code [minX, minY, width, height]}. */
  public double[] getBoundingBox() {
    if (isEmpty()) {
      return new double[] {0.0, 0.0, 0.0, 0.0};
    }
    var env = this.geometry.getEnvelopeInternal();
    return new double[] {env.getMinX(), env.getMinY(), env.getWidth(), env.getHeight()};
  }

  /** Represents a closed polygon contour with an outer boundary and optional hole rings. */
  public record Contour(List<FloatPoint> outer, List<List<FloatPoint>> holes) {}

  /**
   * Converts all disjoint islands and holes into lists of {@link FloatPoint} contours for rendering
   * or export.
   *
   * @return list of island contours
   */
  public List<Contour> toContours() {
    if (isEmpty()) {
      return Collections.emptyList();
    }
    List<Contour> contours = new ArrayList<>();
    for (PlanarArea island : decomposeIntoIslands()) {
      if (island.geometry instanceof Polygon poly) {
        List<FloatPoint> outer = ringToPoints(poly.getExteriorRing());
        List<List<FloatPoint>> holes = new ArrayList<>();
        for (int i = 0; i < poly.getNumInteriorRing(); i++) {
          holes.add(ringToPoints(poly.getInteriorRingN(i)));
        }
        contours.add(new Contour(outer, holes));
      }
    }
    return contours;
  }

  private static List<FloatPoint> ringToPoints(LinearRing ring) {
    if (ring == null || ring.isEmpty()) {
      return Collections.emptyList();
    }
    Coordinate[] coords = ring.getCoordinates();
    List<FloatPoint> points = new ArrayList<>(coords.length);
    for (Coordinate c : coords) {
      points.add(new FloatPoint(c.x, c.y));
    }
    return points;
  }
}
