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
    assertTrue(DifferentialPairDetector.isExcludedNet("AGND"));
    assertTrue(DifferentialPairDetector.isExcludedNet("DGND"));
    assertTrue(DifferentialPairDetector.isExcludedNet("PGND"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VCC"));
    assertTrue(DifferentialPairDetector.isExcludedNet("AVCC"));
    assertTrue(DifferentialPairDetector.isExcludedNet("+5V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("-12V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("3.3V"));
    assertTrue(DifferentialPairDetector.isExcludedNet("3V3"));
    assertTrue(DifferentialPairDetector.isExcludedNet("1V8"));
    assertTrue(DifferentialPairDetector.isExcludedNet("5V0"));
    assertTrue(DifferentialPairDetector.isExcludedNet("+3V3"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VDD"));
    assertTrue(DifferentialPairDetector.isExcludedNet("AVDD"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VSS"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VBUS"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VBAT"));
    assertTrue(DifferentialPairDetector.isExcludedNet(null));
    assertTrue(DifferentialPairDetector.isExcludedNet(""));

    assertNull(DifferentialPairDetector.extract("+5V"));
    assertNull(DifferentialPairDetector.extract("-12V"));
    assertNull(DifferentialPairDetector.extract("3V3"));
    assertNull(DifferentialPairDetector.extract("1V8"));
    assertNull(DifferentialPairDetector.extract("GND"));
    assertNull(DifferentialPairDetector.extract("AGND"));
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

  private app.freerouting.board.facade.RoutingBoard createTestBoard() {
    app.freerouting.board.model.structure.Layer layer1 =
        new app.freerouting.board.model.structure.Layer("Top", true);
    app.freerouting.board.model.structure.Layer[] layers =
        new app.freerouting.board.model.structure.Layer[] {layer1};
    app.freerouting.board.model.structure.LayerStructure layerStructure =
        new app.freerouting.board.model.structure.LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    app.freerouting.board.state.Communication communication =
        new app.freerouting.board.state.Communication();

    return new app.freerouting.board.facade.RoutingBoard(
        new app.freerouting.geometry.planar.IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new app.freerouting.geometry.planar.PolylineShape[] {
          app.freerouting.geometry.planar.TileShape.getInstance(0, 0, 2000000, 2000000)
        },
        0,
        boardRules,
        communication);
  }

  @Test
  void testDetectAndRegisterBasicPairs() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    Net usbP = board.rules.nets.add("USB+", 1, false);
    Net usbN = board.rules.nets.add("USB-", 1, false);
    Net clkP = board.rules.nets.add("CLK_P", 1, false);
    Net clkN = board.rules.nets.add("CLK_N", 1, false);
    board.rules.nets.add("RESET", 1, false);
    board.rules.nets.add("GND", 1, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);

    assertEquals(2, detected.size());
    assertEquals(2, board.rules.differentialPairs.count());

    DifferentialPair usb = board.rules.differentialPairs.get("USB");
    assertNotNull(usb);
    assertEquals(usbP, usb.positiveNet());
    assertEquals(usbN, usb.negativeNet());

    DifferentialPair clk = board.rules.differentialPairs.get("CLK");
    assertNotNull(clk);
    assertEquals(clkP, clk.positiveNet());
    assertEquals(clkN, clk.negativeNet());

    // Second call is idempotent and returns empty list
    java.util.List<DifferentialPair> secondPass =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertTrue(secondPass.isEmpty());
    assertEquals(2, board.rules.differentialPairs.count());
  }

  @Test
  void testMismatchedStylesNotPaired() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    board.rules.nets.add("DATA+", 1, false);
    board.rules.nets.add("DATA_N", 1, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertTrue(detected.isEmpty());
    assertEquals(0, board.rules.differentialPairs.count());
  }

  @Test
  void testAmbiguousPairsSkipped() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    board.rules.nets.add("SIG_P", 1, false);
    board.rules.nets.add("sig_p", 1, false);
    board.rules.nets.add("SIG_N", 1, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertTrue(detected.isEmpty());
    assertEquals(0, board.rules.differentialPairs.count());
  }

  @Test
  void testPreExistingPairPreserved() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    Net usbP = board.rules.nets.add("USB_P", 1, false);
    Net usbN = board.rules.nets.add("USB_N", 1, false);

    DifferentialPair explicitPair = new DifferentialPair(usbP, usbN, "USB", 200.0, 30.0);
    board.rules.differentialPairs.add(explicitPair);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertTrue(detected.isEmpty());
    assertEquals(1, board.rules.differentialPairs.count());
    assertEquals(200.0, board.rules.differentialPairs.get("USB").coupledSpacing());
  }

  @Test
  void testSubnetsWithIdenticalNamePairedWithoutAmbiguity() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    // Subnet 1 and Subnet 2 for USB+ and USB-
    Net usbP1 = board.rules.nets.add("USB+", 1, false);
    Net usbP2 = board.rules.nets.add("USB+", 2, false);
    Net usbN1 = board.rules.nets.add("USB-", 1, false);
    Net usbN2 = board.rules.nets.add("USB-", 2, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertEquals(1, detected.size());
    assertEquals(1, board.rules.differentialPairs.count());

    DifferentialPair pair = board.rules.differentialPairs.get("USB");
    assertNotNull(pair);

    // Both subnet 1 and subnet 2 must be found for the pair
    assertEquals(pair, board.rules.differentialPairs.findForNet(usbP1));
    assertEquals(pair, board.rules.differentialPairs.findForNet(usbP2));
    assertEquals(pair, board.rules.differentialPairs.findForNet(usbN1));
    assertEquals(pair, board.rules.differentialPairs.findForNet(usbN2));

    assertTrue(pair.contains(usbP1));
    assertTrue(pair.contains(usbP2));
    assertTrue(pair.contains(usbN1));
    assertTrue(pair.contains(usbN2));
  }
}
