package app.freerouting.io.specctra;

import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/** Restores SES corners using the board's existing integer supporting-line representation. */
final class SesPathGeometry {
  private record Corner(BigFraction x, BigFraction y) {}

  private SesPathGeometry() {}

  static void check(double[] board) throws IOException {
    if (board.length < 4 || board.length % 2 != 0) {
      throw new IOException("A SES wire needs at least two complete corners");
    }
  }

  static IntPoint gridPoint(
      double x, double y, java.util.function.BiFunction<Double, Double, IntPoint> designPoint) {
    if (designPoint != null) {
      IntPoint routed = designPoint.apply(x, y);
      if (routed != null) {
        return routed;
      }
    }
    return new IntPoint((int) Math.floor(x + 0.5), (int) Math.floor(y + 0.5));
  }

  static Polyline read(double[] coordinates, double scale) throws IOException {
    if (coordinates.length < 4 || coordinates.length % 2 != 0) {
      throw new IOException("A SES wire needs at least two complete corners");
    }
    List<Corner> corners = new ArrayList<>();
    for (int index = 0; index < coordinates.length; index += 2) {
      Corner corner = corner(coordinates[index] * scale, coordinates[index + 1] * scale);
      if (!corners.isEmpty() && corners.getLast().equals(corner)) {
        if (coordinates[index - 2] != coordinates[index]
            || coordinates[index - 1] != coordinates[index + 1]) {
          throw new IOException("A nonzero SES segment collapses at board coordinate precision");
        }
      } else {
        corners.add(corner);
      }
    }
    if (corners.size() == 1) {
      Line[] crossing = crossingLines(corners.getFirst());
      return new Polyline(new Line[] {crossing[0], crossing[1], crossing[0]});
    }
    try {
      return build(corners);
    } catch (IOException failure) {
      return recoverLegacyConnector(coordinates, scale, failure);
    }
  }

  static Polyline read(BigDecimal[] coordinates, BigFraction scale) throws IOException {
    if (coordinates.length < 4 || coordinates.length % 2 != 0) {
      throw new IOException("A SES wire needs at least two complete corners");
    }
    boolean precise = false;
    for (BigDecimal coordinate : coordinates) {
      precise |= coordinate.scale() >= 24;
    }
    try {
      return readExact(coordinates, scale);
    } catch (IOException failure) {
      if (precise) {
        throw failure;
      }
      double[] legacy = new double[coordinates.length];
      for (int index = 0; index < coordinates.length; index++) {
        legacy[index] = coordinates[index].doubleValue();
      }
      return read(legacy, scale.doubleValue());
    }
  }

  static Polyline read(
      double[] board, java.util.function.BiFunction<Double, Double, IntPoint> designPoint)
      throws IOException {
    check(board);
    Polyline restored = exact(board, designPoint);
    return restored != null ? restored : grid(board, designPoint);
  }

  private static Polyline readExact(BigDecimal[] coordinates, BigFraction scale)
      throws IOException {
    List<Corner> corners = new ArrayList<>();
    for (int index = 0; index < coordinates.length; index += 2) {
      BigFraction x = SesNumbers.recover(coordinates[index], scale);
      BigFraction y = SesNumbers.recover(coordinates[index + 1], scale);
      if (x.abs().compareTo(BigFraction.of(Limits.CRIT_INT)) > 0
          || y.abs().compareTo(BigFraction.of(Limits.CRIT_INT)) > 0) {
        throw new IOException("SES wire coordinate exceeds the board range");
      }
      Corner corner = new Corner(x, y);
      if (!corners.isEmpty() && corners.getLast().equals(corner)) {
        if (coordinates[index - 2].compareTo(coordinates[index]) != 0
            || coordinates[index - 1].compareTo(coordinates[index + 1]) != 0) {
          throw new IOException("A nonzero SES segment collapses at board coordinate precision");
        }
      } else {
        corners.add(corner);
      }
    }
    return build(corners);
  }

  /**
   * Restores a path exactly, or returns null when only a grid reading can represent it. A collinear
   * reversal still fails: snapping it would drop copper.
   */
  static Polyline exact(BigDecimal[] coordinates, BigFraction scale) throws IOException {
    boolean precise = false;
    for (BigDecimal coordinate : coordinates) {
      precise |= coordinate.scale() >= 24;
    }
    try {
      return read(coordinates, scale);
    } catch (IOException failure) {
      if (precise || isCollinearRejection(failure)) {
        throw failure;
      }
      return null;
    }
  }

  static Polyline exact(
      double[] board, java.util.function.BiFunction<Double, Double, IntPoint> designPoint)
      throws IOException {
    List<Corner> corners = new ArrayList<>(board.length / 2);
    for (int i = 0; i + 1 < board.length; i += 2) {
      Corner corner = null;
      if (designPoint != null) {
        IntPoint routed = designPoint.apply(board[i], board[i + 1]);
        if (routed != null) {
          corner = new Corner(BigFraction.of(routed.x), BigFraction.of(routed.y));
        }
      }
      if (corner == null) {
        try {
          corner = corner(board[i], board[i + 1]);
        } catch (IOException _) {
          return null;
        }
      }
      if (!corners.isEmpty() && corners.getLast().equals(corner)) {
        if (board[i - 2] != board[i] || board[i - 1] != board[i + 1]) {
          return null;
        }
        continue;
      }
      corners.add(corner);
    }
    try {
      Polyline polyline = build(corners);
      if (corners.size() == 1) {
        return polyline;
      }
      int count = polyline.cornerCount();
      if (count < 2
          || !near(polyline.firstCorner(), corners.getFirst())
          || !near(polyline.lastCorner(), corners.getLast())) {
        return null;
      }
      for (int i = 1; count == corners.size() && i < count - 1; i++) {
        if (!near(polyline.corner(i), corners.get(i))) {
          return null;
        }
      }
      return polyline;
    } catch (IOException failure) {
      if (isCollinearRejection(failure)) {
        throw failure;
      }
      return null;
    }
  }

  static Polyline grid(
      double[] board, java.util.function.BiFunction<Double, Double, IntPoint> designPoint)
      throws IOException {
    List<Point> points = new ArrayList<>(board.length / 2);
    for (int i = 0; i + 1 < board.length; i += 2) {
      Point point = gridPoint(board[i], board[i + 1], designPoint);
      if (points.isEmpty() || !points.getLast().equals(point)) {
        points.add(point);
      }
    }
    List<Corner> corners = new ArrayList<>(points.size());
    for (Point point : points) {
      corners.add(
          new Corner(BigFraction.of(((IntPoint) point).x), BigFraction.of(((IntPoint) point).y)));
    }
    rejectCollinearReversals(corners);
    return points.size() == 1
        ? pointCopper(corners.getFirst())
        : new Polyline(points.toArray(Point[]::new));
  }

  private static boolean isCollinearRejection(IOException failure) {
    return failure.getMessage() != null && failure.getMessage().contains("collinear");
  }

  private static boolean near(Point point, Corner corner) {
    app.freerouting.geometry.planar.FloatPoint value = point.toFloat();
    return Math.abs(value.x - corner.x.doubleValue()) <= 1e-6 * Math.max(1, Math.abs(value.x))
        && Math.abs(value.y - corner.y.doubleValue()) <= 1e-6 * Math.max(1, Math.abs(value.y));
  }

  private static Polyline pointCopper(Corner corner) throws IOException {
    Line[] crossing = crossingLines(corner);
    return new Polyline(new Line[] {crossing[0], crossing[1], crossing[0]});
  }

  static int halfWidth(double width, double scale) throws IOException {
    double value = width * scale / 2;
    double rounded = Math.rint(value);
    if (!Double.isFinite(value)
        || value <= 0
        || rounded < 1
        || rounded > Limits.CRIT_INT
        || Math.abs(value - rounded) > 4 * Math.ulp(value)) {
      throw new IOException("The SES wire width cannot be represented on this board");
    }
    return (int) rounded;
  }

  static int halfWidth(BigDecimal width, BigFraction scale) throws IOException {
    BigFraction half = SesNumbers.recoverInteger(width, scale).divide(2);
    if (half.signum() <= 0
        || !half.getDenominator().equals(BigInteger.ONE)
        || half.getNumerator().compareTo(Limits.CRIT_INT_BIG) > 0) {
      throw new IOException("The SES wire width cannot be represented on this board");
    }
    return half.getNumerator().intValueExact();
  }

  private static Polyline build(List<Corner> corners) throws IOException {
    if (corners.size() == 1) {
      Line[] crossing = crossingLines(corners.getFirst());
      return new Polyline(new Line[] {crossing[0], crossing[1], crossing[0]});
    }
    rejectCollinearReversals(corners);
    Line[] lines = new Line[corners.size() + 1];
    for (int index = 1; index < corners.size(); index++) {
      lines[index] = supportingLine(corners.get(index - 1), corners.get(index));
    }
    lines[0] = endpointLine(corners.getFirst(), lines[1]);
    lines[lines.length - 1] = endpointLine(corners.getLast(), lines[lines.length - 2]);
    Polyline polyline = new Polyline(lines);
    if (polyline.cornerCount() < 2) {
      throw new IOException("The SES path cannot be represented on this board");
    }
    return polyline;
  }

  private static Polyline recoverLegacyConnector(
      double[] coordinates, double scale, IOException failure) throws IOException {
    if (coordinates.length != 4) {
      throw failure;
    }
    for (boolean reverse : new boolean[] {false, true}) {
      int first = reverse ? 2 : 0;
      int last = reverse ? 0 : 2;
      SesNumbers.Interval ax = SesNumbers.legacyInterval(coordinates[first] * scale);
      SesNumbers.Interval ay = SesNumbers.legacyInterval(coordinates[first + 1] * scale);
      BigFraction anchorX = BigFraction.of(Math.round(coordinates[first] * scale));
      BigFraction anchorY = BigFraction.of(Math.round(coordinates[first + 1] * scale));
      if (!ax.contains(anchorX) || !ay.contains(anchorY)) {
        continue;
      }
      SesNumbers.Interval bx = SesNumbers.legacyInterval(coordinates[last] * scale);
      SesNumbers.Interval by = SesNumbers.legacyInterval(coordinates[last + 1] * scale);
      SesNumbers.Interval dx =
          new SesNumbers.Interval(bx.low().subtract(anchorX), bx.high().subtract(anchorX));
      SesNumbers.Interval dy =
          new SesNumbers.Interval(by.low().subtract(anchorY), by.high().subtract(anchorY));
      BigInteger vx;
      BigInteger vy;
      if (dx.contains(BigFraction.ZERO)) {
        if (dy.contains(BigFraction.ZERO)) {
          continue;
        }
        vx = BigInteger.ZERO;
        vy = BigInteger.valueOf(dy.low().signum());
      } else if (dy.contains(BigFraction.ZERO)) {
        vx = BigInteger.valueOf(dx.low().signum());
        vy = BigInteger.ZERO;
      } else {
        BigFraction[] ratios = {
          dy.low().divide(dx.low()),
          dy.low().divide(dx.high()),
          dy.high().divide(dx.low()),
          dy.high().divide(dx.high())
        };
        java.util.Arrays.sort(ratios);
        BigFraction slope = SesNumbers.simplest(new SesNumbers.Interval(ratios[0], ratios[3]));
        vx = slope.getDenominator().multiply(BigInteger.valueOf(dx.low().signum()));
        vy = slope.getNumerator().multiply(BigInteger.valueOf(dx.low().signum()));
      }
      try {
        IntPoint anchor = integerPoint(anchorX.getNumerator(), anchorY.getNumerator());
        final Line support =
            new Line(
                anchor,
                integerPoint(anchorX.getNumerator().add(vx), anchorY.getNumerator().add(vy)));
        SesNumbers.Interval parameter = null;
        for (int axis = 0; axis < 2; axis++) {
          BigInteger direction = axis == 0 ? vx : vy;
          SesNumbers.Interval delta = axis == 0 ? dx : dy;
          if (direction.signum() == 0) {
            if (!delta.contains(BigFraction.ZERO)) {
              throw failure;
            }
            continue;
          }
          BigFraction low = delta.low().divide(direction);
          BigFraction high = delta.high().divide(direction);
          SesNumbers.Interval constraint =
              direction.signum() > 0
                  ? new SesNumbers.Interval(low, high)
                  : new SesNumbers.Interval(high, low);
          parameter = parameter == null ? constraint : parameter.intersect(constraint);
        }
        BigFraction t = SesNumbers.simplest(parameter);
        if (t.signum() <= 0) {
          throw failure;
        }
        BigFraction endX = anchorX.add(t.multiply(vx));
        BigFraction endY = anchorY.add(t.multiply(vy));
        if (!bx.contains(endX) || !by.contains(endY)) {
          throw failure;
        }
        Corner end = new Corner(endX, endY);
        Line cap = endpointLine(end, support);
        Polyline recovered = new Polyline(new Line[] {support.capAt(anchor), support, cap});
        if (recovered.cornerCount() != 2) {
          throw failure;
        }
        return reverse ? recovered.reverse() : recovered;
      } catch (IOException | IllegalArgumentException unsupported) {
        // A legacy decimal carries no exact provenance. Refuse rather than expand its envelope.
      }
    }
    throw failure;
  }

  private static Corner corner(double x, double y) throws IOException {
    BigFraction fx = coordinate(x);
    BigFraction fy = coordinate(y);
    // Intersections share a projective denominator. Independent convergents can choose different
    // denominators near an integer; prefer a shared one only when both original values still fit
    // the serialization tolerance. This restores their correlation without broadening tolerance.
    for (BigInteger denominator : new BigInteger[] {fx.getDenominator(), fy.getDenominator()}) {
      BigFraction sharedX = atDenominator(x, denominator);
      BigFraction sharedY = atDenominator(y, denominator);
      if (Math.abs(sharedX.doubleValue() - x) <= 4 * Math.ulp(x)
          && Math.abs(sharedY.doubleValue() - y) <= 4 * Math.ulp(y)) {
        return new Corner(sharedX, sharedY);
      }
    }
    return new Corner(fx, fy);
  }

  private static BigFraction atDenominator(double value, BigInteger denominator) {
    BigInteger numerator =
        BigFraction.from(value)
            .multiply(denominator)
            .bigDecimalValue(0, RoundingMode.HALF_EVEN)
            .toBigInteger();
    return BigFraction.of(numerator, denominator);
  }

  private static BigFraction coordinate(double value) throws IOException {
    if (!Double.isFinite(value) || Math.abs(value) > Limits.CRIT_INT) {
      throw new IOException("Non-finite or out-of-range SES wire coordinate");
    }
    // Recover rational intersections within serialization noise, never round to the board grid.
    // Approximate only the fractional part: the library's convergents have a 32-bit limit,
    // whereas legitimate board coordinates can have much larger full numerators.
    double tolerance = 4 * Math.ulp(value);
    double nearestInteger = Math.rint(value);
    if (Math.abs(value - nearestInteger) <= tolerance) {
      return BigFraction.of((long) nearestInteger);
    }
    // Truncate towards zero to avoid cancellation when a negative value is close to zero.
    long whole = (long) value;
    try {
      BigFraction restored = BigFraction.from(value - whole, tolerance, 100).add(whole);
      if (Math.abs(restored.doubleValue() - value) > tolerance) {
        throw new IOException("SES rational recovery exceeds the serialization tolerance");
      }
      return restored;
    } catch (ArithmeticException failure) {
      throw new IOException(
          "The SES corner cannot be restored at board coordinate precision", failure);
    }
  }

  private static void rejectCollinearReversals(List<Corner> corners) throws IOException {
    for (int index = 1; index < corners.size() - 1; index++) {
      Corner before = corners.get(index - 1);
      Corner current = corners.get(index);
      Corner after = corners.get(index + 1);
      BigFraction dx = current.x.subtract(before.x);
      BigFraction dy = current.y.subtract(before.y);
      BigFraction nextX = after.x.subtract(current.x);
      BigFraction nextY = after.y.subtract(current.y);
      if (dx.multiply(nextY).equals(dy.multiply(nextX))
          && dx.multiply(nextX).add(dy.multiply(nextY)).signum() < 0) {
        throw new IOException(
            "A collinear SES reversal cannot be represented without losing copper");
      }
    }
  }

  private static Line supportingLine(Corner from, Corner to) throws IOException {
    BigFraction deltaX = to.x.subtract(from.x);
    BigFraction deltaY = to.y.subtract(from.y);
    BigInteger denominator = commonDenominator(deltaX, deltaY);
    BigInteger dx = deltaX.multiply(denominator).getNumerator();
    BigInteger dy = deltaY.multiply(denominator).getNumerator();
    return integerLine(from, dx, dy);
  }

  private static Line integerLine(Corner from, BigInteger dx, BigInteger dy) throws IOException {
    try {
      Corner next = new Corner(from.x.add(dx), from.y.add(dy));
      return new Line(point(from), point(next));
    } catch (IllegalArgumentException unsupported) {
      throw new IOException("The SES segment has no bounded integer supporting line", unsupported);
    }
  }

  private static app.freerouting.geometry.planar.Point point(Corner corner) {
    BigInteger denominator = commonDenominator(corner.x, corner.y);
    return app.freerouting.geometry.planar.Point.getInstance(
        corner.x.multiply(denominator).getNumerator(),
        corner.y.multiply(denominator).getNumerator(),
        denominator);
  }

  private static Line endpointLine(Corner corner, Line segment) throws IOException {
    if (corner.x.getDenominator().equals(BigInteger.ONE)
        && corner.y.getDenominator().equals(BigInteger.ONE)) {
      IntPoint point = integerPoint(corner.x.getNumerator(), corner.y.getNumerator());
      return Line.getInstance(point, segment.direction().turn45Degree(2));
    }
    Line[] crossing = crossingLines(corner);
    return crossing[0].isParallel(segment) ? crossing[1] : crossing[0];
  }

  private record LatticeVector(BigInteger x, BigInteger y) {
    BigInteger dot(LatticeVector other) {
      return x.multiply(other.x).add(y.multiply(other.y));
    }

    LatticeVector subtract(LatticeVector other, BigInteger multiple) {
      return new LatticeVector(
          x.subtract(other.x.multiply(multiple)), y.subtract(other.y.multiply(multiple)));
    }
  }

  private static Line[] crossingLines(Corner corner) throws IOException {
    BigInteger denominator = commonDenominator(corner.x, corner.y);
    BigInteger x = corner.x.multiply(denominator).getNumerator();
    BigInteger y = corner.y.multiply(denominator).getNumerator();
    if (denominator.equals(BigInteger.ONE)) {
      IntPoint point = integerPoint(x, y);
      return new Line[] {
        Line.getInstance(point, Direction.RIGHT), Line.getInstance(point, Direction.UP)
      };
    }
    // Integer supporting directions satisfy y*dx - x*dy = 0 (mod denominator).
    // A basis of that congruence lattice avoids treating the intersection denominator as a
    // coordinate bound. Reduce only this SES representation, never the router's geometry.
    BigInteger divisor = y.gcd(denominator);
    BigInteger horizontal = denominator.divide(divisor);
    BigInteger residue =
        x.multiply(y.divide(divisor).mod(horizontal).modInverse(horizontal)).mod(horizontal);
    LatticeVector first = new LatticeVector(horizontal, BigInteger.ZERO);
    LatticeVector second = new LatticeVector(residue, divisor);
    boolean reduced = false;
    for (int iteration = 0; iteration < 128; iteration++) {
      if (first.dot(first).compareTo(second.dot(second)) > 0) {
        LatticeVector swap = first;
        first = second;
        second = swap;
      }
      BigInteger multiple =
          BigFraction.of(first.dot(second), first.dot(first))
              .bigDecimalValue(0, RoundingMode.HALF_EVEN)
              .toBigInteger();
      if (multiple.signum() == 0) {
        reduced = true;
        break;
      }
      second = second.subtract(first, multiple);
    }
    if (!reduced) {
      throw new IOException("SES supporting-line reduction did not converge");
    }
    Line firstLine = null;
    IOException lastFailure = null;
    // A reduced direction need not have an integer point on the corresponding line (e.g. the
    // vertical line through x=1/3). Small basis combinations restore independent integer lines.
    for (LatticeVector direction :
        new LatticeVector[] {
          first,
          second,
          first.subtract(second, BigInteger.ONE),
          first.subtract(second, BigInteger.ONE.negate())
        }) {
      try {
        Line candidate = integerLine(corner, direction.x, direction.y);
        if (firstLine == null) {
          firstLine = candidate;
        } else if (!firstLine.isParallel(candidate)) {
          return new Line[] {firstLine, candidate};
        }
      } catch (IOException unsupportedDirection) {
        lastFailure = unsupportedDirection;
      }
    }
    throw new IOException("The SES corner has no supported pair of integer lines", lastFailure);
  }

  private static BigInteger commonDenominator(BigFraction x, BigFraction y) {
    BigInteger dx = x.getDenominator();
    BigInteger dy = y.getDenominator();
    return dx.divide(dx.gcd(dy)).multiply(dy);
  }

  private static IntPoint integerPoint(BigInteger x, BigInteger y) throws IOException {
    if (x.abs().compareTo(Limits.CRIT_INT_BIG) > 0 || y.abs().compareTo(Limits.CRIT_INT_BIG) > 0) {
      throw new IOException(
          "SES supporting-line coordinates exceed the board's exact integer range");
    }
    return new IntPoint(x.intValueExact(), y.intValueExact());
  }
}
