package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapacitorScorerTest {

  private Pin createMockPin(
      int id, int compId, int layer, boolean isSmd, int net, int x, int y, int width, int height) {
    Pin pin = mock(Pin.class);
    when(pin.getId()).thenReturn(id);
    when(pin.getComponentId()).thenReturn(compId);
    when(pin.firstLayer()).thenReturn(layer);
    when(pin.lastLayer()).thenReturn(isSmd ? layer : layer + 1);
    when(pin.netCount()).thenReturn(net > 0 ? 1 : 0);
    if (net > 0) {
      when(pin.getNetNumber(0)).thenReturn(net);
    }
    when(pin.getCenter()).thenReturn(new IntPoint(x, y));
    when(pin.boundingBox())
        .thenReturn(new IntBox(x - width / 2, y - height / 2, x + width / 2, y + height / 2));
    return pin;
  }

  @Test
  void testHardGates() {
    final BoardIndex index = mock(BoardIndex.class);
    final IdentifierConfig config = IdentifierConfig.defaultConfig();
    final double resolutionMm = 1000.0; // 1000 board units per mm

    // Case 1: Asymmetric pads
    Pin p1 = createMockPin(1, 10, 0, true, 1, 0, 0, 100, 100); // area 10,000
    Pin p2 = createMockPin(2, 10, 0, true, 2, 1000, 0, 50, 50); // area 2,500 (ratio 0.25 < 0.85)
    when(index.getTwoPinComponentIds()).thenReturn(List.of(10));
    when(index.getPinsOfComponent(10)).thenReturn(List.of(p1, p2));

    List<CapacitorScorer.EvaluationState> states =
        CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertEquals(1, states.size());
    assertFalse(states.get(0).gatePassed);
    assertEquals(Verdict.REJECTED, states.get(0).verdict);
    assertTrue(ReasonCode.ASYMMETRIC_PADS.isSet(states.get(0).reasonMask));

    // Case 2: Same net
    Pin p3 = createMockPin(3, 11, 0, true, 1, 0, 0, 100, 100);
    Pin p4 = createMockPin(4, 11, 0, true, 1, 1000, 0, 100, 100);
    when(index.getTwoPinComponentIds()).thenReturn(List.of(11));
    when(index.getPinsOfComponent(11)).thenReturn(List.of(p3, p4));

    states = CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertFalse(states.get(0).gatePassed);
    assertTrue(ReasonCode.SAME_OR_NO_NET.isSet(states.get(0).reasonMask));

    // Case 3: Pitch out of range (pitch 15.0 mm > maxPitch 12.0 mm)
    Pin p5 = createMockPin(5, 12, 0, true, 1, 0, 0, 100, 100);
    Pin p6 = createMockPin(6, 12, 0, true, 2, 15000, 0, 100, 100); // 15000 units = 15 mm
    when(index.getTwoPinComponentIds()).thenReturn(List.of(12));
    when(index.getPinsOfComponent(12)).thenReturn(List.of(p5, p6));

    states = CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertFalse(states.get(0).gatePassed);
    assertTrue(ReasonCode.PITCH_OUT_OF_RANGE.isSet(states.get(0).reasonMask));

    // Case 4: Forced user exclusion
    IdentifierConfig configWithExclusion =
        new IdentifierConfig(
            config.maxOwnerRadiusMm(),
            config.minPitchMm(),
            config.maxPitchMm(),
            config.minPadAreaRatio(),
            config.decouplingThreshold(),
            config.reviewThreshold(),
            config.ownerTopK(),
            config.runRefDesAudit(),
            config.auditDemotesToReview(),
            config.groundNameHints(),
            config.forcedGroundNets(),
            Set.of(13));

    Pin p7 = createMockPin(7, 13, 0, true, 1, 0, 0, 100, 100);
    Pin p8 = createMockPin(8, 13, 0, true, 2, 1000, 0, 100, 100);
    when(index.getTwoPinComponentIds()).thenReturn(List.of(13));
    when(index.getPinsOfComponent(13)).thenReturn(List.of(p7, p8));

    states = CapacitorScorer.evaluatePassA(index, configWithExclusion, resolutionMm);
    assertFalse(states.get(0).gatePassed);
    assertTrue(ReasonCode.USER_EXCLUDED.isSet(states.get(0).reasonMask));

    // Case 5: Explicit non-capacitor reference designators (e.g. SW1, S2, R1)
    Pin p9 = createMockPin(9, 14, 0, true, 1, 0, 0, 100, 100);
    Pin p10 = createMockPin(10, 14, 0, true, 2, 1000, 0, 100, 100);
    when(index.getTwoPinComponentIds()).thenReturn(List.of(14));
    when(index.getPinsOfComponent(14)).thenReturn(List.of(p9, p10));
    when(index.getComponentName(14)).thenReturn("SW1");

    states = CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertFalse(states.get(0).gatePassed);
    assertTrue(ReasonCode.USER_EXCLUDED.isSet(states.get(0).reasonMask));

    when(index.getComponentName(14)).thenReturn("S2");
    states = CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertFalse(states.get(0).gatePassed);

    when(index.getComponentName(14)).thenReturn("R5");
    states = CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertFalse(states.get(0).gatePassed);
  }

  @Test
  void testScoringAndGroundResolution() {
    BoardIndex index = mock(BoardIndex.class);
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    double resolutionMm = 1000.0;

    // Normal decoupling capacitor: pitch 1.2 mm, symmetric pads
    Pin p1 = createMockPin(1, 20, 0, true, 1, 0, 0, 100, 100); // net 1 (GND)
    Pin p2 = createMockPin(2, 20, 0, true, 2, 1200, 0, 100, 100); // net 2 (VCC)
    when(index.getTwoPinComponentIds()).thenReturn(List.of(20));
    when(index.getPinsOfComponent(20)).thenReturn(List.of(p1, p2));

    // Net 1 is strong ground (80.0), Net 2 is power rail (10.0) -> margin 70 >= 15
    when(index.getGroundScore(1)).thenReturn(80.0);
    when(index.getGroundScore(2)).thenReturn(10.0);

    BoardIndex.NetEvidence vccEvidence =
        new BoardIndex.NetEvidence(2, "VCC", 20, 5, 3, 4, true, false, 0, 0, 10.0);
    when(index.getNetEvidence(2)).thenReturn(vccEvidence);

    List<CapacitorScorer.EvaluationState> states =
        CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertEquals(1, states.size());
    CapacitorScorer.EvaluationState state = states.get(0);

    assertTrue(state.gatePassed);
    assertEquals(1, state.groundNet);
    assertEquals(2, state.railNet);
    assertTrue(ReasonCode.STRONG_GROUND.isSet(state.reasonMask));
    assertTrue(ReasonCode.MULTI_IC_RAIL.isSet(state.reasonMask));
    assertTrue(ReasonCode.SHUNT_CAP_DEGREE.isSet(state.reasonMask));
    // Score: 30 (ground) + 15 (multi IC / plane) + 10 (shunt cap degree) = 55
    assertTrue(state.score >= 55);
  }

  @Test
  void testAmbiguousGroundDemotesToReview() {
    BoardIndex index = mock(BoardIndex.class);
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    double resolutionMm = 1000.0;

    Pin p1 = createMockPin(1, 30, 0, true, 1, 0, 0, 100, 100);
    Pin p2 = createMockPin(2, 30, 0, true, 2, 1200, 0, 100, 100);
    when(index.getTwoPinComponentIds()).thenReturn(List.of(30));
    when(index.getPinsOfComponent(30)).thenReturn(List.of(p1, p2));

    // Both nets have similar ground score (margin 5.0 < 15.0)
    when(index.getGroundScore(1)).thenReturn(25.0);
    when(index.getGroundScore(2)).thenReturn(20.0);

    List<CapacitorScorer.EvaluationState> states =
        CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertEquals(1, states.size());
    CapacitorScorer.EvaluationState state = states.get(0);

    assertTrue(state.gatePassed);
    assertTrue(ReasonCode.AMBIGUOUS_GROUND.isSet(state.reasonMask));
    assertEquals(-1, state.groundNet);
    assertEquals(-1, state.railNet);
    // Ambiguous ground cannot achieve DECOUPLING
    assertFalse(state.verdict == Verdict.DECOUPLING);
  }

  @Test
  void testPassBFilteredIslandPromotion() {
    BoardIndex index = mock(BoardIndex.class);
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    double resolutionMm = 1000.0;

    // Strong rail cap on VCC (net 2) with parallel sibling:
    Pin p1 = createMockPin(1, 40, 0, true, 1, 0, 0, 100, 100); // GND
    Pin p2 = createMockPin(2, 40, 0, true, 2, 1000, 0, 100, 100); // VCC
    when(index.getPinsOfComponent(40)).thenReturn(List.of(p1, p2));

    Pin p1b = createMockPin(7, 43, 0, true, 1, 500, 0, 100, 100); // GND
    Pin p2b = createMockPin(8, 43, 0, true, 2, 1500, 0, 100, 100); // VCC
    when(index.getPinsOfComponent(43)).thenReturn(List.of(p1b, p2b));

    when(index.getGroundScore(1)).thenReturn(90.0);
    when(index.getGroundScore(2)).thenReturn(10.0);
    BoardIndex.NetEvidence vccEvidence =
        new BoardIndex.NetEvidence(2, "VCC", 50, 10, 4, 5, true, false, 0, 0, 10.0);
    when(index.getNetEvidence(2)).thenReturn(vccEvidence);

    // Island cap on AVDD (net 3):
    Pin p3 = createMockPin(3, 41, 0, true, 1, 2000, 0, 100, 100); // GND
    Pin p4 = createMockPin(4, 41, 0, true, 3, 3000, 0, 100, 100); // AVDD
    when(index.getPinsOfComponent(41)).thenReturn(List.of(p3, p4));
    when(index.getGroundScore(3)).thenReturn(5.0);
    // AVDD net has single load, no plane, so receives few-pins penalty in Pass A
    BoardIndex.NetEvidence avddEvidence =
        new BoardIndex.NetEvidence(3, "AVDD", 2, 1, 1, 1, false, false, 0, 0, 5.0);
    when(index.getNetEvidence(3)).thenReturn(avddEvidence);

    when(index.getTwoPinComponentIds()).thenReturn(List.of(40, 43, 41));

    List<CapacitorScorer.EvaluationState> states =
        CapacitorScorer.evaluatePassA(index, config, resolutionMm);
    assertEquals(3, states.size());

    CapacitorScorer.EvaluationState strongState = states.get(0);
    CapacitorScorer.EvaluationState islandState = states.get(2);

    assertEquals(Verdict.DECOUPLING, strongState.verdict);
    // Island state has 30 (ground) - 25 (few pins penalty) = 5 -> REJECTED
    assertEquals(Verdict.REJECTED, islandState.verdict);

    // Setup Pass B series passive (ferrite L1, component 42) connecting net 2 (VCC) and net 3
    // (AVDD)
    Pin pL1_a = createMockPin(5, 42, 0, true, 3, 2500, 500, 100, 100); // on AVDD
    Pin pL1_b = createMockPin(6, 42, 0, true, 2, 2500, 1500, 100, 100); // on VCC
    when(index.getPinsOfNet(3)).thenReturn(List.of(p4, pL1_a));
    when(index.getComponentType(42)).thenReturn(ComponentType.TWO_PIN_SMD);
    when(index.getComponentName(42)).thenReturn("L1");
    when(index.getPinsOfComponent(42)).thenReturn(List.of(pL1_a, pL1_b));

    CapacitorScorer.applyPassB(states, index, config);

    // Island candidate receives +25 points and FILTERED_ISLAND reason code
    assertTrue(ReasonCode.FILTERED_ISLAND.isSet(islandState.reasonMask));
    assertEquals(30, islandState.score); // 5 + 25 = 30
  }

  @Test
  @DisplayName("isPowerNetName recognises EDA numeric voltages, sub-domains and prefixes")
  void testIsPowerNetNamePatterns() {
    // Standard positive voltages
    assertTrue(CapacitorScorer.isPowerNetName("+1V0"));
    assertTrue(CapacitorScorer.isPowerNetName("+1V8"));
    assertTrue(CapacitorScorer.isPowerNetName("+3V3"));
    assertTrue(CapacitorScorer.isPowerNetName("+3.3V"));
    assertTrue(CapacitorScorer.isPowerNetName("+5V"));
    assertTrue(CapacitorScorer.isPowerNetName("+12V"));
    assertTrue(CapacitorScorer.isPowerNetName("+24V"));
    assertTrue(CapacitorScorer.isPowerNetName("+48V"));

    // Standard negative voltages
    assertTrue(CapacitorScorer.isPowerNetName("-5V"));
    assertTrue(CapacitorScorer.isPowerNetName("-12V"));
    assertTrue(CapacitorScorer.isPowerNetName("-15V"));

    // Sub-domain rails
    assertTrue(CapacitorScorer.isPowerNetName("+3.3VA"));
    assertTrue(CapacitorScorer.isPowerNetName("+3.3VADC"));
    assertTrue(CapacitorScorer.isPowerNetName("+3.3VDAC"));
    assertTrue(CapacitorScorer.isPowerNetName("+3.3VP"));
    assertTrue(CapacitorScorer.isPowerNetName("+1V35"));
    assertTrue(CapacitorScorer.isPowerNetName("+5C"));
    assertTrue(CapacitorScorer.isPowerNetName("+5F"));
    assertTrue(CapacitorScorer.isPowerNetName("+5P"));
    assertTrue(CapacitorScorer.isPowerNetName("+5VD"));
    assertTrue(CapacitorScorer.isPowerNetName("+5VL"));
    assertTrue(CapacitorScorer.isPowerNetName("+5VP"));
    assertTrue(CapacitorScorer.isPowerNetName("+7.5V"));
    assertTrue(CapacitorScorer.isPowerNetName("+9VA"));
    assertTrue(CapacitorScorer.isPowerNetName("-2V5"));
    assertTrue(CapacitorScorer.isPowerNetName("-3V3"));
    assertTrue(CapacitorScorer.isPowerNetName("-5VA"));
    assertTrue(CapacitorScorer.isPowerNetName("-12VA"));
    assertTrue(CapacitorScorer.isPowerNetName("PWR_3V3"));
    assertTrue(CapacitorScorer.isPowerNetName("PWR_5V"));

    // Traditional EDA power rails
    assertTrue(CapacitorScorer.isPowerNetName("VAA"));
    assertTrue(CapacitorScorer.isPowerNetName("VAC"));
    assertTrue(CapacitorScorer.isPowerNetName("VCC"));
    assertTrue(CapacitorScorer.isPowerNetName("VDD"));
    assertTrue(CapacitorScorer.isPowerNetName("VDDA"));
    assertTrue(CapacitorScorer.isPowerNetName("VDDF"));
    assertTrue(CapacitorScorer.isPowerNetName("VCCQ"));
    assertTrue(CapacitorScorer.isPowerNetName("VCOM"));
    assertTrue(CapacitorScorer.isPowerNetName("VBUS"));
    assertTrue(CapacitorScorer.isPowerNetName("VMEM"));
    assertTrue(CapacitorScorer.isPowerNetName("VPP"));
    assertTrue(CapacitorScorer.isPowerNetName("VEE"));
    assertTrue(CapacitorScorer.isPowerNetName("+BATT"));
    assertTrue(CapacitorScorer.isPowerNetName("-BATT"));
    assertTrue(CapacitorScorer.isPowerNetName("VDC"));
    assertTrue(CapacitorScorer.isPowerNetName("+VDC"));
    assertTrue(CapacitorScorer.isPowerNetName("-VDC"));
    assertTrue(CapacitorScorer.isPowerNetName("Vdrive"));
    assertTrue(CapacitorScorer.isPowerNetName("HT"));
    assertTrue(CapacitorScorer.isPowerNetName("VD"));
    assertTrue(CapacitorScorer.isPowerNetName("VS"));
    assertTrue(CapacitorScorer.isPowerNetName("V-+"));
    assertTrue(CapacitorScorer.isPowerNetName("V--"));

    // Hierarchical net paths
    assertTrue(CapacitorScorer.isPowerNetName("/MCU/+3V3"));
    assertTrue(CapacitorScorer.isPowerNetName("/POWER/VDD"));

    // Non-power / Excluded nets
    assertFalse(CapacitorScorer.isPowerNetName("GND"));
    assertFalse(CapacitorScorer.isPowerNetName("GND1"));
    assertFalse(CapacitorScorer.isPowerNetName("GND2"));
    assertFalse(CapacitorScorer.isPowerNetName("GND3"));
    assertFalse(CapacitorScorer.isPowerNetName("PWR_GND"));
    assertFalse(CapacitorScorer.isPowerNetName("GNDPWR"));
    assertFalse(CapacitorScorer.isPowerNetName("GNDREF"));
    assertFalse(CapacitorScorer.isPowerNetName("GNDS"));
    assertFalse(CapacitorScorer.isPowerNetName("VSS"));
    assertFalse(CapacitorScorer.isPowerNetName("VSSA"));
    assertFalse(CapacitorScorer.isPowerNetName("V-0"));
    assertFalse(CapacitorScorer.isPowerNetName("V-GND"));
    assertFalse(CapacitorScorer.isPowerNetName("V-PE"));
    assertFalse(CapacitorScorer.isPowerNetName("V-L"));
    assertFalse(CapacitorScorer.isPowerNetName("V-N"));
    assertFalse(CapacitorScorer.isPowerNetName("AC"));
    assertFalse(CapacitorScorer.isPowerNetName("Earth"));
    assertFalse(CapacitorScorer.isPowerNetName("Earth_Clean"));
    assertFalse(CapacitorScorer.isPowerNetName("Earth_Protective"));
    assertFalse(CapacitorScorer.isPowerNetName("CLK"));
    assertFalse(CapacitorScorer.isPowerNetName("RESET"));
    assertFalse(CapacitorScorer.isPowerNetName("DATA0"));
    assertFalse(CapacitorScorer.isPowerNetName("SPI_MOSI"));
    assertFalse(CapacitorScorer.isPowerNetName("USB_D+"));
    assertFalse(CapacitorScorer.isPowerNetName("USB_D-"));
    assertFalse(CapacitorScorer.isPowerNetName("PWR_FLAG"));
    assertFalse(CapacitorScorer.isPowerNetName("+VSW"));
    assertFalse(CapacitorScorer.isPowerNetName("-VSW"));
    assertFalse(CapacitorScorer.isPowerNetName("LINE"));
    assertFalse(CapacitorScorer.isPowerNetName("NEUT"));
    assertFalse(CapacitorScorer.isPowerNetName("PRI_HI"));
    assertFalse(CapacitorScorer.isPowerNetName("PRI_LO"));
    assertFalse(CapacitorScorer.isPowerNetName("PRI_MID"));
  }
}
