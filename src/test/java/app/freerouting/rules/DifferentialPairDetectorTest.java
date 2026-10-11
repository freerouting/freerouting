package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DifferentialPairDetectorTest {

  @Test
  void testPlusMinusStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("USB+");
    assertNotNull(pos);
    assertEquals("USB", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.PLUS_MINUS, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("USB-");
    assertNotNull(neg);
    assertEquals("USB", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.PLUS_MINUS, neg.style());
  }

  @Test
  void testUnderscorePnStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("CLK_P");
    assertNotNull(pos);
    assertEquals("CLK", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.UNDERSCORE_P_N, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("clk_n");
    assertNotNull(neg);
    assertEquals("clk", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.UNDERSCORE_P_N, neg.style());
  }

  @Test
  void testDotPnStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("PCIE_TX0.P");
    assertNotNull(pos);
    assertEquals("PCIE_TX0", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.DOT_P_N, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("PCIE_TX0.N");
    assertNotNull(neg);
    assertEquals("PCIE_TX0", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.DOT_P_N, neg.style());
  }

  @Test
  void testUnderscorePosNegStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("DATA_POS");
    assertNotNull(pos);
    assertEquals("DATA", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.UNDERSCORE_POS_NEG, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("DATA_NEG");
    assertNotNull(neg);
    assertEquals("DATA", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.UNDERSCORE_POS_NEG, neg.style());
  }

  @Test
  void testHierarchicalNetNames() {
    DifferentialPairDetector.ExtractedNetInfo pos =
        DifferentialPairDetector.extract("/Sheet1/USB_P");
    assertNotNull(pos);
    assertEquals("/Sheet1/USB", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());

    DifferentialPairDetector.ExtractedNetInfo neg =
        DifferentialPairDetector.extract("/Sheet1/USB_N");
    assertNotNull(neg);
    assertEquals("/Sheet1/USB", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
  }

  @Test
  void testPowerAndGroundExclusions() {
    assertTrue(DifferentialPairDetector.isExcludedNet("GND"));
    assertTrue(DifferentialPairDetector.isExcludedNet("GND_EARTH"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VCC"));
    assertTrue(DifferentialPairDetector.isExcludedNet("+5V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("-12V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("3.3V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VDD"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VSS"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VBUS"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VBAT"));
    assertTrue(DifferentialPairDetector.isExcludedNet(null));
    assertTrue(DifferentialPairDetector.isExcludedNet(""));

    assertNull(DifferentialPairDetector.extract("+5V"));
    assertNull(DifferentialPairDetector.extract("-12V"));
    assertNull(DifferentialPairDetector.extract("GND"));
    assertNull(DifferentialPairDetector.extract("VCC"));
  }

  @Test
  void testSingleEndedNetsIgnored() {
    assertNull(DifferentialPairDetector.extract("RESET"));
    assertNull(DifferentialPairDetector.extract("ENABLE"));
    assertNull(DifferentialPairDetector.extract("SPI_MISO"));
    assertNull(DifferentialPairDetector.extract("I2C_SDA"));
    assertNull(DifferentialPairDetector.extract(""));
    assertNull(DifferentialPairDetector.extract(null));
  }

  @Test
  void testSuffixOnlyNetsIgnored() {
    assertNull(DifferentialPairDetector.extract("+"));
    assertNull(DifferentialPairDetector.extract("-"));
    assertNull(DifferentialPairDetector.extract("_P"));
    assertNull(DifferentialPairDetector.extract("_N"));
  }
}
