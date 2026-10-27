package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Point;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SesNumericFidelityTest {
  @ParameterizedTest
  @CsvSource({
    "1,3",
    "-1,97",
    "357180298144,178933",
    "-124230707150,178933",
    "3333333333333333,10000000000000000",
    "1,1000000000000000000000000000000"
  })
  void decimalOutputRetainsExactRationalValue(String numerator, String denominator)
      throws Exception {
    BigFraction original = BigFraction.of(new BigInteger(numerator), new BigInteger(denominator));
    String output = SesNumbers.format(original);
    assertFalse(output.contains("/"));
    assertFalse(output.contains("E"));
    assertEquals(original, SesNumbers.recover(new BigDecimal(output), BigFraction.ONE));
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void legacySmallConnectorRetainsExactIntegerLineIntersection(boolean reverse) throws Exception {
    double[] path =
        reverse
            ? new double[] {1996167.8289862685, -694286.1694041904, 1996168, -694286}
            : new double[] {1996168, -694286, 1996167.8289862685, -694286.1694041904};
    var restored = SesPathGeometry.read(path, 1.0);
    Point fractional =
        Point.getInstance(
            BigInteger.valueOf(357180298144L),
            BigInteger.valueOf(-124230707150L),
            BigInteger.valueOf(178933));
    assertEquals(
        new IntPoint(1996168, -694286), reverse ? restored.lastCorner() : restored.firstCorner());
    assertEquals(fractional, reverse ? restored.firstCorner() : restored.lastCorner());
  }

  @Test
  void legacyFallbackAlsoCoversCoordinateRecoveryFailures() throws Exception {
    String[] tokens = {
      "1760000",
      "-991250",
      "1764864",
      "-991250",
      "1842257",
      "-1068643",
      "1842257",
      "-1216995",
      "1842257.2276604224",
      "-1216995.3943403747"
    };
    BigDecimal[] exact =
        java.util.Arrays.stream(tokens).map(BigDecimal::new).toArray(BigDecimal[]::new);
    double[] legacy = java.util.Arrays.stream(tokens).mapToDouble(Double::parseDouble).toArray();
    // The final corner projects (1842257, -1216995) onto the captured C4 trunk from
    // (1827966, -1225246) to (1853380, -1210574). Independent exact dot-product arithmetic
    // gives (23111116921, -15267207222) / 12545; the decoder is not the expected-value oracle.
    Point[] expected = {
      new IntPoint(1760000, -991250),
      new IntPoint(1764864, -991250),
      new IntPoint(1842257, -1068643),
      new IntPoint(1842257, -1216995),
      Point.getInstance(
          BigInteger.valueOf(23111116921L),
          BigInteger.valueOf(-15267207222L),
          BigInteger.valueOf(12545))
    };
    var legacyRestored = SesPathGeometry.read(legacy, 1.0);
    var restored = SesPathGeometry.read(exact, BigFraction.ONE);
    assertArrayEquals(expected, legacyRestored.corners());
    assertArrayEquals(expected, restored.corners());
    assertNotNull(SesPathGeometry.exact(exact, BigFraction.ONE));
  }

  @Test
  void outOfRangeCoordinatesAreNotRoundedOntoTheGrid() throws Exception {
    BigDecimal beyond = BigDecimal.valueOf(Limits.CRIT_INT + 1L);
    assertThrows(
        IOException.class,
        () ->
            SesPathGeometry.exact(
                new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, beyond, BigDecimal.ONE},
                BigFraction.ONE));
    assertThrows(
        IOException.class,
        () -> SesPathGeometry.read(new double[] {0, 0, Limits.CRIT_INT + 1.0, 1}, 1));
  }

  @Test
  void exactScaleAndLargeIntegerTokensDoNotPassThroughDouble() throws Exception {
    BigDecimal input = new BigDecimal("9007199254740993");
    assertEquals(BigFraction.of(new BigInteger("9007199254740993")), SesNumbers.exact(input));
    assertEquals(
        BigFraction.of(1, 3),
        SesNumbers.recover(
            new BigDecimal(SesNumbers.format(BigFraction.of(1, 9))), BigFraction.of(3)));
  }

  @Test
  void fractionRecoveryPrecedesArbitrarySessionScaling() throws Exception {
    for (BigFraction external :
        new BigFraction[] {
          BigFraction.of(1, 3), BigFraction.of(-17, 97), BigFraction.of(357180298144L, 178933)
        }) {
      for (BigFraction scale :
          new BigFraction[] {
            BigFraction.of(7, 13), BigFraction.of(1000000007, 11), BigFraction.of(1, 999983)
          }) {
        assertEquals(
            external.multiply(scale),
            SesNumbers.recover(new BigDecimal(SesNumbers.format(external)), scale));
      }
    }
  }

  @Test
  void legacyIntegerRecoveryStaysInsideOriginalFloatingEnvelope() throws Exception {
    BigDecimal legacy = new BigDecimal("0.6666666666666666");
    assertEquals(BigFraction.of(2), SesNumbers.recoverInteger(legacy, BigFraction.of(3)));
    assertEquals(1, SesPathGeometry.halfWidth(legacy, BigFraction.of(3)));
    assertThrows(
        IOException.class,
        () -> SesNumbers.recoverInteger(new BigDecimal("0.66666666666666"), BigFraction.of(3)));
    assertThrows(
        IOException.class,
        () ->
            SesNumbers.recoverInteger(
                new BigDecimal(
                    SesNumbers.format(BigFraction.of(2, 3).subtract(BigFraction.of(1, 1000000)))),
                BigFraction.of(3)));
  }

  @Test
  void numericRecoveryRefusesAmbiguousAndExcessiveInputs() {
    assertThrows(
        IOException.class,
        () -> SesNumbers.simplest(new SesNumbers.Interval(BigFraction.ZERO, BigFraction.ONE)));
    assertThrows(IOException.class, () -> SesNumbers.exact(new BigDecimal("1e-5000")));
    assertThrows(
        IOException.class,
        () -> SesPathGeometry.read(new double[] {100, 100, 100.00000000000001, 100}, 1));
  }
}
