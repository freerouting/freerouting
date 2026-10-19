package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetMeanderConstraintTest {

  @Test
  void testDefaultValuesAndPresets() {
    double scale = 1000.0; // e.g. 1 mm = 1000 board units
    NetMeanderConstraint singleTrack =
        NetMeanderConstraint.createDefaultPreset(MeanderPatternType.SINGLE_TRACK, scale);
    assertNotNull(singleTrack);
    assertEquals(200.0, singleTrack.minAmplitude(), 1e-4);
    assertEquals(1000.0, singleTrack.maxAmplitude(), 1e-4);
    assertEquals(600.0, singleTrack.spacing(), 1e-4);
    assertEquals(CornerStyle.FILLET, singleTrack.cornerStyle());
    assertEquals(80.0, singleTrack.radiusPercentage(), 1e-4);
    assertFalse(singleTrack.singleSided());
    assertTrue(singleTrack.isConstrained());
    assertTrue(singleTrack.isValid());

    NetMeanderConstraint diffPair =
        NetMeanderConstraint.createDefaultPreset(MeanderPatternType.DIFF_PAIR, scale);
    assertEquals(1000.0, diffPair.spacing(), 1e-4);

    NetMeanderConstraint diffSkew =
        NetMeanderConstraint.createDefaultPreset(MeanderPatternType.DIFF_PAIR_SKEW, scale);
    assertEquals(600.0, diffSkew.spacing(), 1e-4);
  }

  @Test
  void testUnconstrainedSentinel() {
    NetMeanderConstraint unconstrained = NetMeanderConstraint.UNCONSTRAINED;
    assertFalse(unconstrained.isConstrained());
    assertFalse(unconstrained.hasAmplitude());
    assertFalse(unconstrained.hasSpacing());
    assertTrue(unconstrained.isValid());
  }

  @Test
  void testNegativeAndBoundClamping() {
    NetMeanderConstraint constraint =
        new NetMeanderConstraint(-5.0, -10.0, -2.0, CornerStyle.CHAMFER, 150.0, true);
    assertEquals(0.0, constraint.minAmplitude());
    assertEquals(0.0, constraint.maxAmplitude());
    assertEquals(0.0, constraint.spacing());
    assertEquals(100.0, constraint.radiusPercentage());
    assertTrue(constraint.singleSided());
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());

    NetMeanderConstraint negativeRadius =
        new NetMeanderConstraint(10.0, 20.0, 5.0, CornerStyle.FILLET, -20.0, false);
    assertEquals(0.0, negativeRadius.radiusPercentage());
  }

  @Test
  void testValidationInvariants() {
    NetMeanderConstraint valid =
        new NetMeanderConstraint(200.0, 500.0, 100.0, CornerStyle.FILLET, 80.0, false);
    assertTrue(valid.isValid());

    NetMeanderConstraint invalid =
        new NetMeanderConstraint(600.0, 500.0, 100.0, CornerStyle.FILLET, 80.0, false);
    assertFalse(invalid.isValid());
  }

  @Test
  void testMutationMethods() {
    NetMeanderConstraint original =
        NetMeanderConstraint.createDefaultPreset(MeanderPatternType.SINGLE_TRACK, 1000.0);

    NetMeanderConstraint modifiedAmp = original.withAmplitude(300.0, 1200.0);
    assertEquals(300.0, modifiedAmp.minAmplitude());
    assertEquals(1200.0, modifiedAmp.maxAmplitude());
    assertEquals(original.spacing(), modifiedAmp.spacing());

    NetMeanderConstraint modifiedSpacing = original.withSpacing(800.0);
    assertEquals(800.0, modifiedSpacing.spacing());

    NetMeanderConstraint modifiedStyle = original.withCornerStyle(CornerStyle.CHAMFER);
    assertEquals(CornerStyle.CHAMFER, modifiedStyle.cornerStyle());

    NetMeanderConstraint modifiedRadius = original.withRadiusPercentage(50.0);
    assertEquals(50.0, modifiedRadius.radiusPercentage());

    NetMeanderConstraint modifiedSingleSided = original.withSingleSided(true);
    assertTrue(modifiedSingleSided.singleSided());

    assertNotEquals(original, modifiedSingleSided);
  }

  @Test
  void testCornerStyleParsing() {
    assertEquals(CornerStyle.FILLET, CornerStyle.parse("fillet"));
    assertEquals(CornerStyle.FILLET, CornerStyle.parse("FILLET"));
    assertEquals(CornerStyle.CHAMFER, CornerStyle.parse("chamfer"));
    assertEquals(CornerStyle.CHAMFER, CornerStyle.parse("CHAMFER"));
    assertEquals(CornerStyle.FILLET, CornerStyle.parse("unknown"));
    assertEquals(CornerStyle.FILLET, CornerStyle.parse(null));
  }

  @Test
  void testMeanderPatternTypeParsing() {
    assertEquals(MeanderPatternType.SINGLE_TRACK, MeanderPatternType.parse("single_track"));
    assertEquals(MeanderPatternType.SINGLE_TRACK, MeanderPatternType.parse("single"));
    assertEquals(MeanderPatternType.DIFF_PAIR, MeanderPatternType.parse("diff_pair"));
    assertEquals(MeanderPatternType.DIFF_PAIR, MeanderPatternType.parse("differential"));
    assertEquals(MeanderPatternType.DIFF_PAIR_SKEW, MeanderPatternType.parse("diff_pair_skew"));
    assertEquals(MeanderPatternType.DIFF_PAIR_SKEW, MeanderPatternType.parse("skew"));
    assertEquals(MeanderPatternType.SINGLE_TRACK, MeanderPatternType.parse(""));
    assertEquals(MeanderPatternType.SINGLE_TRACK, MeanderPatternType.parse(null));
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
  void testBoardRulesDefaultPresets() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    NetMeanderConstraint singleTrack =
        rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK);
    assertNotNull(singleTrack);
    assertEquals(200.0, singleTrack.minAmplitude(), 1e-4);
    assertEquals(1000.0, singleTrack.maxAmplitude(), 1e-4);
    assertEquals(600.0, singleTrack.spacing(), 1e-4);
    assertEquals(CornerStyle.FILLET, singleTrack.cornerStyle());
    assertEquals(80.0, singleTrack.radiusPercentage(), 1e-4);
    assertFalse(singleTrack.singleSided());

    NetMeanderConstraint diffPair = rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR);
    assertEquals(1000.0, diffPair.spacing(), 1e-4);

    // Custom override in BoardRules
    NetMeanderConstraint custom =
        new NetMeanderConstraint(300.0, 1500.0, 700.0, CornerStyle.CHAMFER, 90.0, true);
    rules.setDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK, custom);
    assertEquals(custom, rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK));
  }

  @Test
  void testNetClassMeanderConstraint() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    NetClass netClass = board.rules.getDefaultNetClass();

    assertFalse(netClass.hasMeanderConstraint());
    assertEquals(NetMeanderConstraint.UNCONSTRAINED, netClass.getMeanderConstraint());

    NetMeanderConstraint constraint =
        new NetMeanderConstraint(250.0, 1200.0, 650.0, CornerStyle.FILLET, 75.0, false);
    netClass.setMeanderConstraint(constraint);

    assertTrue(netClass.hasMeanderConstraint());
    assertEquals(constraint, netClass.getMeanderConstraint());

    netClass.setMeanderConstraint(null);
    assertFalse(netClass.hasMeanderConstraint());
    assertEquals(NetMeanderConstraint.UNCONSTRAINED, netClass.getMeanderConstraint());
  }

  @Test
  void testNetFallbackHierarchy() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;
    NetClass netClass = rules.getDefaultNetClass();
    Net net = rules.nets.add("NET_HIERARCHY", 1, false);
    net.setClass(netClass);

    // Stage 1: Neither net nor netClass has explicit constraint -> falls back to BoardRules default
    assertFalse(net.hasExplicitMeanderConstraint());
    NetMeanderConstraint fallback = net.getMeanderConstraint();
    assertNotNull(fallback);
    assertEquals(rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK), fallback);

    // Stage 2: NetClass sets constraint -> net inherits from NetClass
    NetMeanderConstraint classConstraint =
        new NetMeanderConstraint(150.0, 800.0, 500.0, CornerStyle.CHAMFER, 80.0, false);
    netClass.setMeanderConstraint(classConstraint);
    assertFalse(net.hasExplicitMeanderConstraint());
    assertEquals(classConstraint, net.getMeanderConstraint());

    // Stage 3: Net sets explicit constraint -> overrides NetClass
    NetMeanderConstraint netConstraint =
        new NetMeanderConstraint(300.0, 1500.0, 700.0, CornerStyle.FILLET, 90.0, true);
    net.setMeanderConstraint(netConstraint);
    assertTrue(net.hasExplicitMeanderConstraint());
    assertEquals(netConstraint, net.getMeanderConstraint());
    assertEquals(netConstraint, net.getExplicitMeanderConstraint());

    // Stage 4: Reset net explicit constraint -> falls back to NetClass
    net.setMeanderConstraint(null);
    assertFalse(net.hasExplicitMeanderConstraint());
    assertEquals(classConstraint, net.getMeanderConstraint());
  }

  @Test
  void testBoardRulesMeanderConstraintResolutionAndPreservation() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    // Check defaults
    assertFalse(rules.hasExplicitMeanderConstraint(MeanderPatternType.SINGLE_TRACK));
    NetMeanderConstraint singleTrackDefault =
        rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK);
    assertNotNull(singleTrackDefault);

    // Explicitly set a constraint for DIFF_PAIR
    NetMeanderConstraint explicitDiff =
        new NetMeanderConstraint(250.0, 1200.0, 900.0, CornerStyle.CHAMFER, 75.0, false);
    rules.setDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR, explicitDiff);
    assertTrue(rules.hasExplicitMeanderConstraint(MeanderPatternType.DIFF_PAIR));

    // Re-initialize default meander constraints with a different scale (e.g. 2000.0)
    rules.initDefaultMeanderConstraints(2000.0);

    // SINGLE_TRACK was not explicit, so it got scaled (0.2 * 2000 = 400.0)
    assertEquals(
        400.0, rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK).minAmplitude());
    // DIFF_PAIR was explicit, so it is preserved
    assertEquals(explicitDiff, rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR));

    // Test rules.getMeanderConstraint(net) helper
    Net net = rules.nets.add("NET_RESOLVE", 2, false);
    net.setClass(rules.getDefaultNetClass());
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK),
        rules.getMeanderConstraint(net));
  }

  @Test
  void testDifferentialPairFallbackHierarchy() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    Net usbP = rules.nets.add("USB_P", 1, false);
    Net usbN = rules.nets.add("USB_N", 1, false);
    Net singleEnded = rules.nets.add("RESET", 1, false);

    DifferentialPair pair = new DifferentialPair(usbP, usbN, "USB");
    rules.differentialPairs.add(pair);

    // Differential pair net falls back to DIFF_PAIR preset
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR),
        rules.getMeanderConstraint(usbP));
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR),
        usbP.getMeanderConstraint());
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR),
        usbN.getMeanderConstraint());

    // Single-ended net falls back to SINGLE_TRACK preset
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK),
        rules.getMeanderConstraint(singleEnded));
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK),
        singleEnded.getMeanderConstraint());

    // Explicit constraint on diff pair net still takes precedence
    NetMeanderConstraint explicit =
        new NetMeanderConstraint(350.0, 1600.0, 800.0, CornerStyle.FILLET, 90.0, false);
    usbP.setMeanderConstraint(explicit);
    assertEquals(explicit, usbP.getMeanderConstraint());
    assertEquals(explicit, rules.getMeanderConstraint(usbP));

    // Partner net still falls back to DIFF_PAIR
    assertEquals(
        rules.getDefaultMeanderConstraint(MeanderPatternType.DIFF_PAIR),
        usbN.getMeanderConstraint());
  }
}
