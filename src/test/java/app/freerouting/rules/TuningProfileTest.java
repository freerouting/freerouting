package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for TuningProfile domain models, registries, and edge-case handling. */
class TuningProfileTest {

  @Test
  void testTuningProfileTypeParse() {
    assertEquals(TuningProfileType.SINGLE, TuningProfileType.parse(null));
    assertEquals(TuningProfileType.SINGLE, TuningProfileType.parse(""));
    assertEquals(TuningProfileType.SINGLE, TuningProfileType.parse("single"));
    assertEquals(TuningProfileType.SINGLE, TuningProfileType.parse("SINGLE"));
    assertEquals(TuningProfileType.DIFFERENTIAL, TuningProfileType.parse("differential"));
    assertEquals(TuningProfileType.DIFFERENTIAL, TuningProfileType.parse("DIFFERENTIAL"));
    assertEquals(TuningProfileType.DIFFERENTIAL, TuningProfileType.parse("diff_pair"));
    assertEquals(TuningProfileType.SINGLE, TuningProfileType.parse("unrecognized"));
  }

  @Test
  void testTrackPropagationValidationAndNormalization() {
    TrackPropagation prop = new TrackPropagation("  F.Cu  ", " <None> ", "In1.Cu", 250.0, 60.5);
    assertEquals("F.Cu", prop.signalLayer());
    assertNull(prop.topReference(), "Reference '<None>' should normalize to null");
    assertEquals("In1.Cu", prop.bottomReference());
    assertEquals(250.0, prop.trackWidth(), 1e-4);
    assertEquals(60.5, prop.timeLengthFactor(), 1e-4);
    assertFalse(prop.hasTopReference());
    assertTrue(prop.hasBottomReference());

    // Self-referencing return plane should be normalized to null
    TrackPropagation selfRef = new TrackPropagation("In1.Cu", "In1.Cu", "In1.Cu", -10.0, -5.0);
    assertEquals("In1.Cu", selfRef.signalLayer());
    assertNull(selfRef.topReference(), "Self-referencing top plane must be null");
    assertNull(selfRef.bottomReference(), "Self-referencing bottom plane must be null");
    assertEquals(0.0, selfRef.trackWidth(), "Negative width should clamp to 0.0");
    assertEquals(0.0, selfRef.timeLengthFactor(), "Negative unit delay should clamp to 0.0");

    assertThrows(
        NullPointerException.class, () -> new TrackPropagation(null, null, null, 100.0, 50.0));
    assertThrows(
        IllegalArgumentException.class, () -> new TrackPropagation("   ", null, null, 100.0, 50.0));

    // NaN and Infinite handling
    TrackPropagation nanProp =
        new TrackPropagation("F.Cu", null, null, Double.NaN, Double.POSITIVE_INFINITY);
    assertEquals(0.0, nanProp.trackWidth(), "NaN width should clamp to 0.0");
    assertEquals(0.0, nanProp.timeLengthFactor(), "Infinite delay should clamp to 0.0");
  }

  @Test
  void testViaDelayOverrideValidationAndMatching() {
    ViaDelayOverride override = new ViaDelayOverride(" F.Cu ", " B.Cu ", " F.Cu ", " B.Cu ", 4.5);
    assertEquals("F.Cu", override.signalLayerFrom());
    assertEquals("B.Cu", override.signalLayerTo());
    assertEquals("F.Cu", override.viaLayerFrom());
    assertEquals("B.Cu", override.viaLayerTo());
    assertEquals(4.5, override.delayPs(), 1e-4);

    // Direction-agnostic signal transition matching
    assertTrue(override.matchesSignalTransition("F.Cu", "B.Cu"));
    assertTrue(override.matchesSignalTransition("b.cu", "f.cu"));
    assertFalse(override.matchesSignalTransition("F.Cu", "In1.Cu"));
    assertFalse(override.matchesSignalTransition(null, "B.Cu"));

    // Clamps negative delay
    ViaDelayOverride clamped = new ViaDelayOverride("F.Cu", "B.Cu", "F.Cu", "B.Cu", -2.5);
    assertEquals(0.0, clamped.delayPs(), 1e-4);

    ViaDelayOverride nanOverride = new ViaDelayOverride("F.Cu", "B.Cu", "F.Cu", "B.Cu", Double.NaN);
    assertEquals(0.0, nanOverride.delayPs(), 1e-4);

    assertThrows(
        NullPointerException.class, () -> new ViaDelayOverride(null, "B.Cu", "F.Cu", "B.Cu", 1.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ViaDelayOverride("", "B.Cu", "F.Cu", "B.Cu", 1.0));
  }

  @Test
  void testTuningProfileCreationAndLookups() {
    TrackPropagation prop1 = new TrackPropagation("F.Cu", null, "In1.Cu", 200.0, 60.5);
    TrackPropagation prop2 = new TrackPropagation("B.Cu", "In2.Cu", null, 220.0, 62.0);
    ViaDelayOverride viaOv1 = new ViaDelayOverride("F.Cu", "B.Cu", "F.Cu", "B.Cu", 4.5);

    TuningProfile profile =
        new TuningProfile(
            "test2",
            TuningProfileType.SINGLE,
            50.0,
            true,
            List.of(prop1, prop2),
            10.0,
            List.of(viaOv1));

    assertEquals("test2", profile.name());
    assertEquals(TuningProfileType.SINGLE, profile.type());
    assertEquals(50.0, profile.targetImpedanceOhms(), 1e-4);
    assertTrue(profile.enableTimeDomainTuning());
    assertEquals(10.0, profile.globalViaUnitDelay(), 1e-4);
    assertEquals(2, profile.trackPropagations().size());
    assertEquals(1, profile.viaDelayOverrides().size());

    // Layer lookup
    assertEquals(prop1, profile.getTrackPropagation("f.cu"));
    assertEquals(prop2, profile.getTrackPropagation("B.CU"));
    assertNull(profile.getTrackPropagation("NonExistent"));
    assertNull(profile.getTrackPropagation(null));

    // Via override lookup
    assertEquals(viaOv1, profile.getViaDelayOverride("f.cu", "b.cu"));
    assertEquals(viaOv1, profile.getViaDelayOverride("b.cu", "f.cu"));
    assertNull(profile.getViaDelayOverride("f.cu", "in1.cu"));
    assertNull(profile.getViaDelayOverride(null, "b.cu"));

    // NaN / Infinity check
    TuningProfile nanProfile =
        new TuningProfile(
            "nanProfile",
            TuningProfileType.SINGLE,
            Double.NaN,
            true,
            List.of(),
            Double.POSITIVE_INFINITY,
            List.of());
    assertEquals(0.0, nanProfile.targetImpedanceOhms(), 1e-4);
    assertEquals(0.0, nanProfile.globalViaUnitDelay(), 1e-4);
  }

  @Test
  void testTuningProfilesRegistry() {
    TuningProfiles registry = new TuningProfiles();
    assertEquals(0, registry.count());

    TuningProfile p1 = new TuningProfile("test");
    TuningProfile p2 = new TuningProfile("test2");

    registry.add(p1);
    registry.add(p2);
    assertEquals(2, registry.count());

    assertTrue(registry.contains("TEST"));
    assertTrue(registry.contains("test2"));
    assertFalse(registry.contains("missing"));
    assertFalse(registry.contains(null));

    assertEquals(p1, registry.get("Test"));
    assertEquals(p2, registry.get("TEST2"));
    assertNull(registry.get("missing"));
    assertNull(registry.get(null));

    assertEquals(2, registry.getAll().size());

    assertEquals(p1, registry.remove("test"));
    assertEquals(1, registry.count());
    assertFalse(registry.contains("test"));

    registry.clear();
    assertEquals(0, registry.count());
  }

  @Test
  void testNetClassTuningProfileAssignment() {
    app.freerouting.board.model.structure.Layer[] layers =
        new app.freerouting.board.model.structure.Layer[] {
          new app.freerouting.board.model.structure.Layer("Top", true),
          new app.freerouting.board.model.structure.Layer("Bottom", true)
        };
    app.freerouting.board.model.structure.LayerStructure layerStructure =
        new app.freerouting.board.model.structure.LayerStructure(layers);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);

    NetClass netClass = new NetClass("HighSpeed", layerStructure, clearanceMatrix, false);
    assertNull(netClass.getTuningProfileName());

    netClass.setTuningProfileName("test2");
    assertEquals("test2", netClass.getTuningProfileName());

    BoardRules rules = new BoardRules(layerStructure, clearanceMatrix);
    assertNull(netClass.getTuningProfile(rules), "Profile not yet in rules should return null");

    TuningProfile profile = new TuningProfile("test2");
    rules.tuningProfiles.add(profile);

    assertEquals(profile, netClass.getTuningProfile(rules), "Should resolve registered profile");

    netClass.setTuningProfileName(null);
    assertNull(netClass.getTuningProfileName());
    assertNull(netClass.getTuningProfile(rules));
  }
}
