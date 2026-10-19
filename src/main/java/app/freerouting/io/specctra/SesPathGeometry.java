package app.freerouting.io.specctra;

import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/** Restores SES corners using the board's existing integer supporting-line representation. */
final class SesPathGeometry {
  private record Corner(BigFraction x, BigFraction y) {}

  private SesPathGeometry() {}

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
    BigInteger divisor = dx.gcd(dy);
    dx = dx.divide(divisor);
    dy = dy.divide(divisor);
    BigFraction intercept = from.x.multiply(dy).subtract(from.y.multiply(dx));
    if (!intercept.getDenominator().equals(BigInteger.ONE)) {
      throw new IOException(
          "The SES segment has no integer supporting line; refusing to move copper");
    }
    BigInteger constant = intercept.getNumerator();
    BigInteger x;
    BigInteger y;
    if (dx.signum() == 0) {
      x = constant.divide(dy);
      y = from.y.bigDecimalValue(0, RoundingMode.HALF_UP).toBigInteger();
    } else if (dy.signum() == 0) {
      x = from.x.bigDecimalValue(0, RoundingMode.HALF_UP).toBigInteger();
      y = constant.negate().divide(dx);
    } else {
      // Coprime direction components define the existing line's integer lattice; BigInteger's
      // modular inverse finds one point without implementing new routing or geometry algorithms.
      x = constant.multiply(dy.mod(dx.abs()).modInverse(dx.abs())).mod(dx.abs());
      y = dy.multiply(x).subtract(constant).divide(dx);
      BigInteger steps =
          from.x.subtract(x).divide(dx).bigDecimalValue(0, RoundingMode.HALF_UP).toBigInteger();
      x = x.add(steps.multiply(dx));
      y = y.add(steps.multiply(dy));
    }
    return new Line(integerPoint(x, y), integerPoint(x.add(dx), y.add(dy)));
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
