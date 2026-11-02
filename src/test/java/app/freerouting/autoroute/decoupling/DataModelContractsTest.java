package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataModelContractsTest {

  @Test
  void testRecordsAreImmutable() {
    List<OwnerCandidate> owners = new ArrayList<>();
    owners.add(new OwnerCandidate(10, 2, 8, 1000L));

    CapacitorCandidate candidate =
        new CapacitorCandidate(
            1,
            101,
            102,
            1,
            2,
            1,
            2,
            0,
            500L,
            500L,
            85,
            Verdict.DECOUPLING,
            ReasonCode.STRONG_GROUND.mask() | ReasonCode.OWNER_IN_RADIUS.mask(),
            AuditResult.AGREE,
            owners);

    owners.clear();
    assertEquals(1, candidate.owners().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> candidate.owners().add(new OwnerCandidate(11, 2, 8, 2000L)));

    List<CapacitorCandidate> candidateList = new ArrayList<>();
    candidateList.add(candidate);
    IdentificationResult result =
        new IdentificationResult(candidateList, new DiagnosticsSummary(10, 5, 1, 0, 4, 0));

    candidateList.clear();
    assertEquals(1, result.candidates().size());
    assertThrows(UnsupportedOperationException.class, () -> result.candidates().add(candidate));
  }

  @Test
  void testReasonCodeBitmask() {
    long mask = ReasonCode.STRONG_GROUND.mask() | ReasonCode.MULTI_IC_RAIL.mask();
    assertTrue(ReasonCode.STRONG_GROUND.isSet(mask));
    assertTrue(ReasonCode.MULTI_IC_RAIL.isSet(mask));
    assertFalse(ReasonCode.XTAL_PENALTY.isSet(mask));
    assertFalse(ReasonCode.NOT_TWO_PIN_SMD.isSet(mask));
  }

  @Test
  void testDefaultConfig() {
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    assertEquals(7.5, config.maxOwnerRadiusMm());
    assertEquals(0.20, config.minPitchMm());
    assertEquals(3.50, config.maxPitchMm());
    assertEquals(0.85, config.minPadAreaRatio());
    assertEquals(60, config.decouplingThreshold());
    assertEquals(40, config.reviewThreshold());
    assertEquals(4, config.ownerTopK());
    assertTrue(config.runRefDesAudit());
    assertFalse(config.auditDemotesToReview());
    assertFalse(config.groundNameHints().isEmpty());
  }
}
