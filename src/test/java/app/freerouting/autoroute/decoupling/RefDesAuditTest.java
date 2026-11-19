package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Components;
import app.freerouting.geometry.planar.IntPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefDesAuditTest {

  @Test
  void testAuditClassification() {
    Components components = new Components();
    components.add("C12", new IntPoint(0, 0), 0, true, null, null, false, null); // id 1
    components.add("R5", new IntPoint(0, 0), 0, true, null, null, false, null); // id 2
    components.add("D1", new IntPoint(0, 0), 0, true, null, null, false, null); // id 3
    components.add("REF**", new IntPoint(0, 0), 0, true, null, null, false, null); // id 4
    components.add("SW1", new IntPoint(0, 0), 0, true, null, null, false, null); // id 5
    components.add("S2", new IntPoint(0, 0), 0, true, null, null, false, null); // id 6

    Pin pinMock = mock(Pin.class);

    CapacitorScorer.EvaluationState s1 =
        new CapacitorScorer.EvaluationState(1, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s1.verdict = Verdict.DECOUPLING;

    CapacitorScorer.EvaluationState s2 =
        new CapacitorScorer.EvaluationState(2, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s2.verdict = Verdict.DECOUPLING;

    CapacitorScorer.EvaluationState s3 =
        new CapacitorScorer.EvaluationState(3, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s3.verdict = Verdict.DECOUPLING;

    CapacitorScorer.EvaluationState s4 =
        new CapacitorScorer.EvaluationState(4, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s4.verdict = Verdict.DECOUPLING;

    CapacitorScorer.EvaluationState s5 =
        new CapacitorScorer.EvaluationState(1, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s5.verdict = Verdict.REVIEW;

    CapacitorScorer.EvaluationState s6 =
        new CapacitorScorer.EvaluationState(5, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s6.verdict = Verdict.DECOUPLING;

    CapacitorScorer.EvaluationState s7 =
        new CapacitorScorer.EvaluationState(6, pinMock, pinMock, 1, 2, 0, 100L, 100L);
    s7.verdict = Verdict.DECOUPLING;

    IdentifierConfig config = IdentifierConfig.defaultConfig();
    List<CapacitorScorer.EvaluationState> states = List.of(s1, s2, s3, s4, s5, s6, s7);

    int conflicts = RefDesAudit.audit(states, components, config);

    assertEquals(4, conflicts); // R5, D1, SW1, S2
    assertEquals(AuditResult.AGREE, s1.audit);
    assertEquals(AuditResult.CONFLICT, s2.audit);
    assertEquals(AuditResult.CONFLICT, s3.audit);
    assertEquals(AuditResult.UNKNOWN, s4.audit);
    assertEquals(AuditResult.NOT_RUN, s5.audit);
    assertEquals(AuditResult.CONFLICT, s6.audit);
    assertEquals(AuditResult.CONFLICT, s7.audit);
    // Non-capacitor refdes is hard rejected
    assertEquals(Verdict.REJECTED, s2.verdict);
    assertEquals(Verdict.REJECTED, s6.verdict);
    assertEquals(Verdict.REJECTED, s7.verdict);

    // Test with auditDemotesToReview = false
    IdentifierConfig noDemoteConfig =
        new IdentifierConfig(
            config.maxOwnerRadiusMm(),
            config.minPitchMm(),
            config.maxPitchMm(),
            config.minPadAreaRatio(),
            config.decouplingThreshold(),
            config.reviewThreshold(),
            config.ownerTopK(),
            config.runRefDesAudit(),
            false,
            config.groundNameHints(),
            config.forcedGroundNets(),
            config.forcedRejectComponents());

    s2.verdict = Verdict.DECOUPLING;
    RefDesAudit.audit(List.of(s2), components, noDemoteConfig);
    assertEquals(Verdict.REJECTED, s2.verdict);
  }
}
