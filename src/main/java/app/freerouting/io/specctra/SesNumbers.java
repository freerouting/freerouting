package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.Unit;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * Exact decimal interchange and bounded rational recovery; never an electrical contact tolerance.
 */
final class SesNumbers {
  record Interval(BigFraction low, BigFraction high) {
    boolean contains(BigFraction value) {
      return value.compareTo(low) >= 0 && value.compareTo(high) <= 0;
    }

    Interval intersect(Interval other) throws IOException {
      BigFraction lower = low.compareTo(other.low) > 0 ? low : other.low;
      BigFraction upper = high.compareTo(other.high) < 0 ? high : other.high;
      if (lower.compareTo(upper) > 0) {
        throw new IOException("SES numeric recovery intervals do not intersect");
      }
      return new Interval(lower, upper);
    }
  }

  private SesNumbers() {}

  static BigFraction sessionScale(BasicBoard board, Unit unit, int resolution) throws IOException {
    BigFraction base =
        board.communication.coordinateTransform == null
            ? BigFraction.of(board.communication.resolution)
            : fromDouble(board.communication.coordinateTransform.dsnToBoard(1));
    return base.multiply(micrometers(unit))
        .divide(micrometers(board.communication.unit))
        .divide(resolution);
  }

  private static BigFraction micrometers(Unit unit) {
    return switch (unit) {
      case UM -> BigFraction.ONE;
      case MM -> BigFraction.of(1000);
      case MIL -> BigFraction.of(254, 10);
      case INCH -> BigFraction.of(25400);
    };
  }

  static BigFraction exact(BigDecimal value) throws IOException {
    int scale = value.scale();
    if (Math.abs((long) scale) > 4096 || value.precision() > 4096) {
      throw new IOException("SES number exceeds the supported decimal precision");
    }
    return scale >= 0
        ? BigFraction.of(value.unscaledValue(), BigInteger.TEN.pow(scale))
        : BigFraction.of(value.unscaledValue().multiply(BigInteger.TEN.pow(-scale)));
  }

  static BigFraction fromDouble(double value) throws IOException {
    if (!Double.isFinite(value)) {
      throw new IOException("Non-finite SES number");
    }
    return exact(BigDecimal.valueOf(value));
  }

  static Interval legacyInterval(double value) throws IOException {
    if (!Double.isFinite(value)) {
      throw new IOException("Non-finite SES coordinate");
    }
    BigFraction center = BigFraction.from(value);
    BigFraction tolerance = BigFraction.from(4 * Math.ulp(value));
    return new Interval(center.subtract(tolerance), center.add(tolerance));
  }

  static BigFraction recover(BigDecimal token, BigFraction scale) throws IOException {
    BigFraction external = exact(token);
    BigFraction recovered = external;
    if (token.scale() > 0) {
      BigFraction halfPlace = exact(token.ulp()).divide(2);
      recovered = simplest(new Interval(external.subtract(halfPlace), external.add(halfPlace)));
    }
    BigFraction value = recovered.multiply(scale);
    if (!legacyInterval(token.doubleValue() * scale.doubleValue()).contains(value)) {
      throw new IOException("SES rational recovery exceeds the numeric fidelity envelope");
    }
    return value;
  }

  static BigFraction recoverInteger(BigDecimal token, BigFraction scale) throws IOException {
    // Old writers did not certify their decimal rounding interval. Preserve only integer values
    // within the original floating-point envelope; never round a genuinely fractional position.
    if (token.scale() < 24) {
      double approximate = token.doubleValue() * scale.doubleValue();
      BigFraction integer = BigFraction.of(Math.round(approximate));
      if (legacyInterval(approximate).contains(integer)) {
        return integer;
      }
    }
    BigFraction value = recover(token, scale);
    if (!value.getDenominator().equals(BigInteger.ONE)) {
      throw new IOException("The SES value is not an integer board position");
    }
    return value;
  }

  static BigFraction simplest(Interval interval) throws IOException {
    return simplest(interval.low, interval.high, 0);
  }

  private static BigFraction simplest(BigFraction low, BigFraction high, int depth)
      throws IOException {
    if (depth > 512 || low.compareTo(high) > 0) {
      throw new IOException("SES rational interval cannot be reconstructed");
    }
    if (low.equals(high)) {
      return low;
    }
    if (low.signum() <= 0) {
      if (high.signum() >= 0) {
        if (low.compareTo(BigFraction.of(-1)) <= 0 || high.compareTo(BigFraction.ONE) >= 0) {
          throw new IOException("Ambiguous SES integer recovery");
        }
        return BigFraction.ZERO;
      }
      return simplest(high.negate(), low.negate(), depth + 1).negate();
    }
    BigInteger integer = low.getNumerator().divide(low.getDenominator());
    BigFraction whole = BigFraction.of(integer);
    if (low.equals(whole)) {
      if (high.compareTo(whole.add(1)) >= 0) {
        throw new IOException("Ambiguous SES integer recovery");
      }
      return low;
    }
    BigFraction next = whole.add(1);
    if (high.compareTo(next) >= 0) {
      if (high.compareTo(next.add(1)) >= 0) {
        throw new IOException("Ambiguous SES integer recovery");
      }
      return next;
    }
    return whole.add(
        simplest(high.subtract(whole).reciprocal(), low.subtract(whole).reciprocal(), depth + 1)
            .reciprocal());
  }

  static String format(BigFraction value) {
    if (value.getDenominator().equals(BigInteger.ONE)) {
      return value.getNumerator().toString();
    }
    // Fixed scale certifies a rounding interval narrow enough to identify the source denominator.
    // Retain trailing zeros even for terminating fractions; stripping them destroys that interval.
    int scale = Math.max(24, 2 * value.getDenominator().toString().length() + 8);
    return new BigDecimal(value.getNumerator())
        .divide(new BigDecimal(value.getDenominator()), scale, RoundingMode.HALF_EVEN)
        .toPlainString();
  }
}
