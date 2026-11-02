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

    // Case 3: Pitch out of range (pitch 5.0 mm > maxPitch 3.5 mm)
    Pin p5 = createMockPin(5, 12, 0, true, 1, 0, 0, 100, 100);
    Pin p6 = createMockPin(6, 12, 0, true, 2, 5000, 0, 100, 100); // 5000 units = 5 mm
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
        new BoardIndex.NetEvidence(2, 20, 5, 3, 4, true, false, 0, 0, 10.0);
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
        new BoardIndex.NetEvidence(2, 50, 10, 4, 5, true, false, 0, 0, 10.0);
    when(index.getNetEvidence(2)).thenReturn(vccEvidence);

    // Island cap on AVDD (net 3):
    Pin p3 = createMockPin(3, 41, 0, true, 1, 2000, 0, 100, 100); // GND
    Pin p4 = createMockPin(4, 41, 0, true, 3, 3000, 0, 100, 100); // AVDD
    when(index.getPinsOfComponent(41)).thenReturn(List.of(p3, p4));
    when(index.getGroundScore(3)).thenReturn(5.0);
    // AVDD net has single load, no plane, so receives few-pins penalty in Pass A
    BoardIndex.NetEvidence avddEvidence =
        new BoardIndex.NetEvidence(3, 2, 1, 1, 1, false, false, 0, 0, 5.0);
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
    when(index.getPinsOfComponent(42)).thenReturn(List.of(pL1_a, pL1_b));

    CapacitorScorer.applyPassB(states, index, config);

    // Island candidate receives +25 points and FILTERED_ISLAND reason code
    assertTrue(ReasonCode.FILTERED_ISLAND.isSet(islandState.reasonMask));
    assertEquals(30, islandState.score); // 5 + 25 = 30
  }
}
