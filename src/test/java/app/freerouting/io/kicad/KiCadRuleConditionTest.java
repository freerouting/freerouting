package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.io.kicad.KiCadRuleCondition.Item;
import app.freerouting.io.kicad.KiCadRuleCondition.ItemType;
import app.freerouting.io.kicad.KiCadRuleCondition.Tri;
import org.junit.jupiter.api.Test;

/** The KiCad condition subset: KiCad 10 semantics, three-valued where Freerouting cannot tell. */
class KiCadRuleConditionTest {

  private static final Item MAINS_TRACK =
      new Item(ItemType.TRACK, "/Relay1/COM", "MAINS,Default", null, null, false);
  private static final Item SIGNAL_TRACK =
      new Item(ItemType.TRACK, "/MCU/TX", "Default", null, null, false);
  private static final Item COIL_PAD =
      new Item(ItemType.PAD, "/Relay1/COIL", "Default", "K1", "1", false);
  private static final Item CONTACT_PAD =
      new Item(ItemType.PAD, "/Relay1/COM", "MAINS,Default", "K1", "3", false);
  private static final Item SMD_PAD = new Item(ItemType.PAD, "GND", "Default", "U4", "2", true);

  private static Tri eval(String condition, Item a, Item b) {
    return KiCadRuleCondition.parse(condition).evaluate(a, b);
  }

  @Test
  void compositeNetClassMatchesItsConstituents() {
    String condition = "A.NetClass == 'MAINS' && B.NetClass != 'MAINS'";
    assertEquals(Tri.TRUE, eval(condition, MAINS_TRACK, SIGNAL_TRACK));
    // conditions are commutative, as in KiCad
    assertEquals(Tri.TRUE, eval(condition, SIGNAL_TRACK, MAINS_TRACK));
    assertEquals(Tri.FALSE, eval(condition, MAINS_TRACK, CONTACT_PAD));
    assertEquals(Tri.TRUE, eval("A.NetClass == 'MAINS,Default'", MAINS_TRACK, SIGNAL_TRACK));
    assertEquals(Tri.TRUE, eval("A.NetClass == 'mains'", MAINS_TRACK, MAINS_TRACK));
    assertEquals(Tri.TRUE, eval("A.hasNetclass('MAINS')", MAINS_TRACK, MAINS_TRACK));
  }

  @Test
  void wildcardsAndFootprints() {
    String sameRelay = "A.NetName == '/Relay1/*' && B.memberOfFootprint('K1') && A.Type == 'Track'";
    assertEquals(Tri.TRUE, eval(sameRelay, MAINS_TRACK, COIL_PAD));
    assertEquals(Tri.FALSE, eval(sameRelay, MAINS_TRACK, SMD_PAD));
    assertEquals(Tri.TRUE, eval("A.memberOfFootprint('K?')", COIL_PAD, COIL_PAD));
    assertEquals(Tri.TRUE, eval("A.Pad_Type == 'SMD' && !A.isPlated()", SMD_PAD, SMD_PAD));
    assertEquals(Tri.TRUE, eval("A.Pad_Type == 'Through-hole'", COIL_PAD, COIL_PAD));
  }

  @Test
  void propertiesAnItemDoesNotHaveAreNeitherEqualNorUnequal() {
    // A.Reference resolves to the footprint for a pad, and is undefined for a track
    assertEquals(Tri.TRUE, eval("A.Reference == 'K1'", COIL_PAD, COIL_PAD));
    assertEquals(Tri.FALSE, eval("A.Reference == 'K1'", MAINS_TRACK, MAINS_TRACK));
    assertEquals(Tri.FALSE, eval("A.Reference != 'K1'", MAINS_TRACK, MAINS_TRACK));
    assertEquals(Tri.FALSE, eval("A.Pad_Type != 'SMD'", MAINS_TRACK, MAINS_TRACK));
  }

  @Test
  void whatFreeroutingCannotEvaluateIsUnknownAndReported() {
    KiCadRuleCondition geometric =
        KiCadRuleCondition.parse("A.NetClass == 'MAINS' && A.intersectsArea('zone')");
    assertEquals(Tri.UNKNOWN, geometric.evaluate(MAINS_TRACK, SIGNAL_TRACK));
    assertEquals(Tri.FALSE, geometric.evaluate(SIGNAL_TRACK, SIGNAL_TRACK));
    assertTrue(geometric.unsupportedParts().getFirst().contains("intersectsArea"));

    KiCadRuleCondition numeric = KiCadRuleCondition.parse("A.Track_Width > 0.3mm");
    assertEquals(Tri.UNKNOWN, numeric.evaluate(MAINS_TRACK, SIGNAL_TRACK));
    assertEquals(1, numeric.unsupportedParts().size());

    KiCadRuleCondition broken = KiCadRuleCondition.parse("A.NetClass == 'MAINS");
    assertEquals(Tri.UNKNOWN, broken.evaluate(MAINS_TRACK, SIGNAL_TRACK));
    assertTrue(broken.unsupportedParts().getFirst().startsWith("unparsable"));

    assertTrue(KiCadRuleCondition.parse("A.NetName == B.NetName").unsupportedParts().isEmpty());
  }

  @Test
  void signatureSeparatesWhatTheConditionTellsApart() {
    KiCadRuleCondition condition =
        KiCadRuleCondition.parse(
            "A.NetClass == 'MAINS' && B.NetClass != 'MAINS' && A.memberOfFootprint('K1')");
    Item otherMainsTrack =
        new Item(ItemType.TRACK, "/Relay2/COM", "MAINS,Default", null, null, false);
    assertEquals(condition.signature(MAINS_TRACK), condition.signature(otherMainsTrack));
    assertNotEquals(condition.signature(MAINS_TRACK), condition.signature(CONTACT_PAD));
    assertNotEquals(condition.signature(SIGNAL_TRACK), condition.signature(MAINS_TRACK));
  }
}
