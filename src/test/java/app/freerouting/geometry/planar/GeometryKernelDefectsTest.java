package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class GeometryKernelDefectsTest {

  @Test
  void testFr001TileShapeIndexOfNearestCorner() {
    // IntBox with corners (0,0), (100,0), (100,100), (0,100)
    IntBox box = new IntBox(0, 0, 100, 100);

    // Test a point close to corner 0 (0,0)
    assertEquals(0, box.indexOfNearestCorner(new IntPoint(-10, -10)));

    // Test a point close to corner 1 (100,0)
    assertEquals(1, box.indexOfNearestCorner(new IntPoint(110, -10)));

    // Test a point close to corner 2 (100,100)
    assertEquals(2, box.indexOfNearestCorner(new IntPoint(110, 110)));

    // Test a point close to corner 3 (0,100)
    assertEquals(3, box.indexOfNearestCorner(new IntPoint(-10, 110)));
  }

  @Test
  void testFr002PointAndVectorModularDivision() {
    // x = 6 is divisible by z = 3, but y = 5 is not divisible by z = 3
    BigInteger x = BigInteger.valueOf(6);
    BigInteger y = BigInteger.valueOf(5);
    BigInteger z = BigInteger.valueOf(3);

    Point point = Point.getInstance(x, y, z);
    assertTrue(
        point instanceof RationalPoint,
        "Point must remain a RationalPoint when y is not divisible by z");
    RationalPoint rp = (RationalPoint) point;
    assertEquals(BigInteger.valueOf(6), rp.x);
    assertEquals(BigInteger.valueOf(5), rp.y);
    assertEquals(BigInteger.valueOf(3), rp.z);

    Vector vector = Vector.getInstance(x, y, z);
    assertTrue(
        vector instanceof RationalVector,
        "Vector must remain a RationalVector when y is not divisible by z");
    RationalVector rv = (RationalVector) vector;
    assertEquals(BigInteger.valueOf(6), rv.x);
    assertEquals(BigInteger.valueOf(5), rv.y);
    assertEquals(BigInteger.valueOf(3), rv.z);
  }

  @Test
  void testFr003IntDirectionTurn45DegreeNegativeFactor() {
    IntDirection right = new IntDirection(1, 0);

    // turn 45 degrees counterclockwise (-1 means clockwise 45 degrees, which is 315 deg)
    Direction dirNeg1 = right.turn45Degree(-1);
    assertFalse(dirNeg1.getVector().equals(Vector.ZERO));
    assertEquals(right.turn45Degree(7), dirNeg1);

    // -2 is -90 degrees (270 degrees)
    Direction dirNeg2 = right.turn45Degree(-2);
    assertEquals(right.turn45Degree(6), dirNeg2);

    // -8 is a full turn (0 degrees)
    Direction dirNeg8 = right.turn45Degree(-8);
    assertEquals(right, dirNeg8);
  }

  @Test
  void testFr004LineLengthIntegerOverflow() {
    // Distance > 46340 causes (dx * dx) to overflow 32-bit int
    IntPoint a = new IntPoint(0, 0);
    IntPoint b = new IntPoint(60000, 80000);
    Line line = new Line(a, b);

    float length = line.length();
    // 3-4-5 triangle -> 60000, 80000 -> 100000
    assertEquals(100000.0f, length, 1.0f);
  }

  @Test
  void testFr005IntOctagonEmptyBounds() {
    // An octagon with inverted leftX > rightX
    IntOctagon inverted = new IntOctagon(100, 0, 0, 100, 0, 100, 0, 100);

    assertTrue(inverted.isEmpty(), "Unnormalized octagon with leftX > rightX must be empty");
    assertEquals(-1, inverted.dimension(), "Empty octagon must have dimension -1");
  }

  @Test
  void testFr008SimplexCornerApproxArrDefensiveCopy() {
    Simplex simplex = new IntBox(0, 0, 100, 100).toSimplex();
    FloatPoint[] corners1 = simplex.cornerApproxArr();
    FloatPoint original0 = corners1[0];

    // Mutate the returned array
    corners1[0] = new FloatPoint(999, 999);

    FloatPoint[] corners2 = simplex.cornerApproxArr();
    assertEquals(original0, corners2[0], "Internal corners array must not be mutated by caller");
  }

  @Test
  void testFr009AndFr010LineSegmentStairsAndFunctionInY() {
    // Line segment with steep slope (absDy > absDx) so it is a function of Y
    Line middle = new Line(new IntPoint(0, 0), new IntPoint(10, 1000));
    Line start = new Line(new IntPoint(0, 0), new IntPoint(-1000, 10)); // perpendicular
    Line end = new Line(new IntPoint(10, 1000), new IntPoint(-990, 1010)); // perpendicular
    LineSegment segment = new LineSegment(start, middle, end);

    // Small width to test stairWidth does not divide by zero
    assertDoesNotThrow(
        () -> {
          IntPoint[] stairs = segment.stairApproximation(0.0001, false);
          assertTrue(stairs.length > 0);
        });

    assertDoesNotThrow(
        () -> {
          IntPoint[] stairs45 = segment.stairApproximation45(0.0001, false);
          assertTrue(stairs45.length > 0);
        });
  }

  @Test
  void testFr011AndFr012PolygonShapeAreaAndBorderDistance() {
    Point[] squareCorners =
        new Point[] {
          new IntPoint(0, 0), new IntPoint(100, 0), new IntPoint(100, 100), new IntPoint(0, 100)
        };
    PolygonShape square = new PolygonShape(squareCorners);

    // FR-011: area must be 100 * 100 = 10000.0
    assertEquals(10000.0, square.area(), 0.001);

    // FR-012: border distance from center (50, 50) to square edge is 50.0
    assertEquals(50.0, square.borderDistance(new FloatPoint(50, 50)), 0.001);
    assertEquals(50.0, square.smallestRadius(), 0.001);
  }

  @Test
  void testFr065PolygonShapeIntersectsPolygonShape() {
    Point[] p1Corners =
        new Point[] {
          new IntPoint(0, 0), new IntPoint(100, 0), new IntPoint(100, 100), new IntPoint(0, 100)
        };
    PolygonShape p1 = new PolygonShape(p1Corners);

    Point[] p2Corners =
        new Point[] {
          new IntPoint(50, 50), new IntPoint(150, 50), new IntPoint(150, 150), new IntPoint(50, 150)
        };
    PolygonShape p2 = new PolygonShape(p2Corners);

    Point[] p3Corners =
        new Point[] {
          new IntPoint(500, 500),
          new IntPoint(600, 500),
          new IntPoint(600, 600),
          new IntPoint(500, 600)
        };
    PolygonShape p3 = new PolygonShape(p3Corners);

    // Must not throw StackOverflowError and correctly compute intersection
    assertTrue(p1.intersects((Shape) p2));
    assertFalse(p1.intersects((Shape) p3));
  }

  @Test
  void testFr013PolylineConstructorDefensiveCopy() {
    Line l1 = new Line(new IntPoint(0, 0), new IntPoint(100, 0));
    Line l2 = new Line(new IntPoint(100, 0), new IntPoint(100, 100));
    Line l3 = new Line(new IntPoint(100, 100), new IntPoint(200, 100));
    Line[] inputLines = new Line[] {l1, l2, l3};
    Line[] inputCopy = inputLines.clone();

    Polyline polyline = new Polyline(inputLines);
    assertEquals(inputCopy[0], inputLines[0], "Polyline constructor must not mutate caller array");
    assertEquals(inputCopy[1], inputLines[1], "Polyline constructor must not mutate caller array");
    assertEquals(inputCopy[2], inputLines[2], "Polyline constructor must not mutate caller array");
  }

  @Test
  void testFr098PolylineRemoveOverlapsNoUnderflow() {
    // Construct lines that overlap back-and-forth consecutively
    Line l1 = new Line(new IntPoint(0, 0), new IntPoint(100, 0));
    Line l2 = new Line(new IntPoint(100, 0), new IntPoint(0, 0)); // opposite of l1
    Line l3 = new Line(new IntPoint(0, 0), new IntPoint(100, 0)); // opposite of l2
    Line l4 = new Line(new IntPoint(100, 0), new IntPoint(100, 100));
    Line l5 = new Line(new IntPoint(100, 100), new IntPoint(200, 100));

    Line[] lines = new Line[] {l1, l2, l3, l4, l5};
    assertDoesNotThrow(() -> new Polyline(lines));
  }
}
