package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DifferentialPairDetectorTest {

  @Test
  void testBareDPlusMinusStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("D+");
    assertNotNull(pos);
    assertEquals("D", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.BARE_D_PLUS_MINUS, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("D-");
    assertNotNull(neg);
    assertEquals("D", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.BARE_D_PLUS_MINUS, neg.style());

    DifferentialPairDetector.ExtractedNetInfo hierPos = DifferentialPairDetector.extract("/USB/D+");
    assertNotNull(hierPos);
    assertEquals("/USB/D", hierPos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, hierPos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.BARE_D_PLUS_MINUS, hierPos.style());
  }

  @Test
  void testPnStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo posUnderscore =
        DifferentialPairDetector.extract("CLK_P");
    assertNotNull(posUnderscore);
    assertEquals("CLK", posUnderscore.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, posUnderscore.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.P_N, posUnderscore.style());

    DifferentialPairDetector.ExtractedNetInfo negUnderscore =
        DifferentialPairDetector.extract("clk_n");
    assertNotNull(negUnderscore);
    assertEquals("clk", negUnderscore.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, negUnderscore.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.P_N, negUnderscore.style());

    // Dot delimiter is interchangeable
    DifferentialPairDetector.ExtractedNetInfo posDot =
        DifferentialPairDetector.extract("PCIE_TX0.P");
    assertNotNull(posDot);
    assertEquals("PCIE_TX0", posDot.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, posDot.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.P_N, posDot.style());

    DifferentialPairDetector.ExtractedNetInfo negDot =
        DifferentialPairDetector.extract("PCIE_TX0.N");
    assertNotNull(negDot);
    assertEquals("PCIE_TX0", negDot.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, negDot.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.P_N, negDot.style());
  }

  @Test
  void testDpDmStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("USB0_DP");
    assertNotNull(pos);
    assertEquals("USB0", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.DP_DM, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("USB0_DM");
    assertNotNull(neg);
    assertEquals("USB0", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.DP_DM, neg.style());

    // Dot notation for DP/DM
    DifferentialPairDetector.ExtractedNetInfo dotPos = DifferentialPairDetector.extract("USB0.DP");
    assertNotNull(dotPos);
    assertEquals("USB0", dotPos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, dotPos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.DP_DM, dotPos.style());
  }

  @Test
  void testDPlusMinusStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("USB_D+");
    assertNotNull(pos);
    assertEquals("USB", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.D_PLUS_MINUS, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("USB_D-");
    assertNotNull(neg);
    assertEquals("USB", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.D_PLUS_MINUS, neg.style());

    // Dot notation for D+/D-
    DifferentialPairDetector.ExtractedNetInfo dotPos = DifferentialPairDetector.extract("USB.D+");
    assertNotNull(dotPos);
    assertEquals("USB", dotPos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, dotPos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.D_PLUS_MINUS, dotPos.style());
  }

  @Test
  void testPlusMinusStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("DIFF_+");
    assertNotNull(pos);
    assertEquals("DIFF", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.PLUS_MINUS, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("DIFF_-");
    assertNotNull(neg);
    assertEquals("DIFF", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.PLUS_MINUS, neg.style());

    // Dot notation for +/-
    DifferentialPairDetector.ExtractedNetInfo dotPos = DifferentialPairDetector.extract("DIFF.+");
    assertNotNull(dotPos);
    assertEquals("DIFF", dotPos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, dotPos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.PLUS_MINUS, dotPos.style());
  }

  @Test
  void testPosNegStyleExtraction() {
    DifferentialPairDetector.ExtractedNetInfo pos = DifferentialPairDetector.extract("DATA_POS");
    assertNotNull(pos);
    assertEquals("DATA", pos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, pos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.POS_NEG, pos.style());

    DifferentialPairDetector.ExtractedNetInfo neg = DifferentialPairDetector.extract("DATA_NEG");
    assertNotNull(neg);
    assertEquals("DATA", neg.baseName());
    assertEquals(DifferentialPairDetector.Polarity.NEGATIVE, neg.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.POS_NEG, neg.style());

    // Dot notation for POS/NEG
    DifferentialPairDetector.ExtractedNetInfo dotPos = DifferentialPairDetector.extract("DATA.POS");
    assertNotNull(dotPos);
    assertEquals("DATA", dotPos.baseName());
    assertEquals(DifferentialPairDetector.Polarity.POSITIVE, dotPos.polarity());
    assertEquals(DifferentialPairDetector.SuffixStyle.POS_NEG, dotPos.style());
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
    assertTrue(DifferentialPairDetector.isExcludedNet("BAT+"));
    assertTrue(DifferentialPairDetector.isExcludedNet("BAT-"));
    assertTrue(DifferentialPairDetector.isExcludedNet("BATT+"));
    assertTrue(DifferentialPairDetector.isExcludedNet("BATT-"));
    assertTrue(DifferentialPairDetector.isExcludedNet("V+"));
    assertTrue(DifferentialPairDetector.isExcludedNet("V-"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VS+"));
    assertTrue(DifferentialPairDetector.isExcludedNet("VS-"));
    assertTrue(DifferentialPairDetector.isExcludedNet(null));
    assertTrue(DifferentialPairDetector.isExcludedNet(""));

    assertNull(DifferentialPairDetector.extract("+5V"));
    assertNull(DifferentialPairDetector.extract("-12V"));
    assertNull(DifferentialPairDetector.extract("3V3"));
    assertNull(DifferentialPairDetector.extract("1V8"));
    assertNull(DifferentialPairDetector.extract("GND"));
    assertNull(DifferentialPairDetector.extract("AGND"));
    assertNull(DifferentialPairDetector.extract("VCC"));
    assertNull(DifferentialPairDetector.extract("BAT+"));
    assertNull(DifferentialPairDetector.extract("BAT-"));
    assertNull(DifferentialPairDetector.extract("V+"));
    assertNull(DifferentialPairDetector.extract("V-"));
  }

  @Test
  void testSingleEndedNetsIgnored() {
    assertNull(DifferentialPairDetector.extract("RESET"));
    assertNull(DifferentialPairDetector.extract("ENABLE"));
    assertNull(DifferentialPairDetector.extract("SPI_MISO"));
    assertNull(DifferentialPairDetector.extract("I2C_SDA"));
    // Words ending in letters p, n, dp, dm without underscore must not be matched
    assertNull(DifferentialPairDetector.extract("STEP"));
    assertNull(DifferentialPairDetector.extract("BUTTON"));
    assertNull(DifferentialPairDetector.extract("OPEN"));
    assertNull(DifferentialPairDetector.extract("CHIP"));
    assertNull(DifferentialPairDetector.extract("CHIN"));
    assertNull(DifferentialPairDetector.extract("MAIN"));
    assertNull(DifferentialPairDetector.extract("SCAN"));
    assertNull(DifferentialPairDetector.extract("DRAIN"));
    assertNull(DifferentialPairDetector.extract("BOOTP"));
    assertNull(DifferentialPairDetector.extract("USBDM"));
    assertNull(DifferentialPairDetector.extract("USBDP"));
    // Bare +/- on non-D nets must NOT be matched without an underscore
    assertNull(DifferentialPairDetector.extract("USB+"));
    assertNull(DifferentialPairDetector.extract("USB-"));
    assertNull(DifferentialPairDetector.extract("LED+"));
    assertNull(DifferentialPairDetector.extract("LED-"));
    assertNull(DifferentialPairDetector.extract("MOTOR+"));
    assertNull(DifferentialPairDetector.extract("MOTOR-"));
    assertNull(DifferentialPairDetector.extract("SPK+"));
    assertNull(DifferentialPairDetector.extract("SPK-"));
    assertNull(DifferentialPairDetector.extract("IN+"));
    assertNull(DifferentialPairDetector.extract("IN-"));
    assertNull(DifferentialPairDetector.extract("OUT+"));
    assertNull(DifferentialPairDetector.extract("OUT-"));
    assertNull(DifferentialPairDetector.extract("SENSE+"));
    assertNull(DifferentialPairDetector.extract("SENSE-"));
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
    Net dP = board.rules.nets.add("D+", 1, false);
    Net dN = board.rules.nets.add("D-", 1, false);
    Net clkP = board.rules.nets.add("CLK_P", 1, false);
    Net clkN = board.rules.nets.add("CLK_N", 1, false);
    board.rules.nets.add("RESET", 1, false);
    board.rules.nets.add("GND", 1, false);
    board.rules.nets.add("LED+", 1, false);
    board.rules.nets.add("LED-", 1, false);
    board.rules.nets.add("SPK+", 1, false);
    board.rules.nets.add("SPK-", 1, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);

    assertEquals(2, detected.size());
    assertEquals(2, board.rules.differentialPairs.count());

    DifferentialPair dPair = board.rules.differentialPairs.get("D");
    assertNotNull(dPair);
    assertEquals(dP, dPair.positiveNet());
    assertEquals(dN, dPair.negativeNet());

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
  void testDetectAndRegisterUnderscoreVariants() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    Net usbDp = board.rules.nets.add("USB0_DP", 1, false);
    Net usbDm = board.rules.nets.add("USB0_DM", 1, false);
    Net ethDp = board.rules.nets.add("ETH_D+", 1, false);
    Net ethDm = board.rules.nets.add("ETH_D-", 1, false);
    Net diffP = board.rules.nets.add("DIFF_+", 1, false);
    Net diffM = board.rules.nets.add("DIFF_-", 1, false);
    Net pcieP = board.rules.nets.add("PCIE.P", 1, false);
    Net pcieN = board.rules.nets.add("PCIE.N", 1, false);
    board.rules.nets.add("STEP", 1, false);
    board.rules.nets.add("BUTTON", 1, false);
    board.rules.nets.add("BAT+", 1, false);
    board.rules.nets.add("BAT-", 1, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);

    assertEquals(4, detected.size());
    assertEquals(4, board.rules.differentialPairs.count());

    DifferentialPair usb = board.rules.differentialPairs.get("USB0");
    assertNotNull(usb);
    assertEquals(usbDp, usb.positiveNet());
    assertEquals(usbDm, usb.negativeNet());

    DifferentialPair eth = board.rules.differentialPairs.get("ETH");
    assertNotNull(eth);
    assertEquals(ethDp, eth.positiveNet());
    assertEquals(ethDm, eth.negativeNet());

    DifferentialPair diff = board.rules.differentialPairs.get("DIFF");
    assertNotNull(diff);
    assertEquals(diffP, diff.positiveNet());
    assertEquals(diffM, diff.negativeNet());

    DifferentialPair pcie = board.rules.differentialPairs.get("PCIE");
    assertNotNull(pcie);
    assertEquals(pcieP, pcie.positiveNet());
    assertEquals(pcieN, pcie.negativeNet());
  }

  @Test
  void testMismatchedStylesNotPaired() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    board.rules.nets.add("DATA_D+", 1, false);
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
    // Subnet 1 and Subnet 2 for D+ and D-
    Net dP1 = board.rules.nets.add("D+", 1, false);
    Net dP2 = board.rules.nets.add("D+", 2, false);
    Net dN1 = board.rules.nets.add("D-", 1, false);
    Net dN2 = board.rules.nets.add("D-", 2, false);

    java.util.List<DifferentialPair> detected =
        DifferentialPairDetector.detectAndRegister(board.rules);
    assertEquals(1, detected.size());
    assertEquals(1, board.rules.differentialPairs.count());

    DifferentialPair pair = board.rules.differentialPairs.get("D");
    assertNotNull(pair);

    // Both subnet 1 and subnet 2 must be found for the pair
    assertEquals(pair, board.rules.differentialPairs.findForNet(dP1));
    assertEquals(pair, board.rules.differentialPairs.findForNet(dP2));
    assertEquals(pair, board.rules.differentialPairs.findForNet(dN1));
    assertEquals(pair, board.rules.differentialPairs.findForNet(dN2));

    assertTrue(pair.contains(dP1));
    assertTrue(pair.contains(dP2));
    assertTrue(pair.contains(dN1));
    assertTrue(pair.contains(dN2));
  }
}
