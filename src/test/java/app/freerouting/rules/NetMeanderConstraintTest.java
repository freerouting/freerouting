package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import org.junit.jupiter.api.Test;

class NetMeanderConstraintTest {

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer[] layers = new Layer[] {layer1};
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication = new Communication();

    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
        0,
        boardRules,
        communication);
  }

  @Test
  void standardConstructorInitializesDefaults() {
    NetMeanderConstraint constraint = new NetMeanderConstraint(1.5, 0.3, 0.8);

    assertEquals(1.5, constraint.maxAmplitude(), 1e-6);
    assertEquals(0.3, constraint.minAmplitude(), 1e-6);
    assertEquals(0.8, constraint.gap(), 1e-6);
    assertFalse(constraint.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.AUTO, constraint.cornerStyle());
    assertEquals(
        NetMeanderConstraint.DEFAULT_CORNER_RADIUS_PERCENT, constraint.cornerRadiusPercentage());

    assertTrue(constraint.isMeanderAllowed());
    assertTrue(constraint.hasMaxAmplitude());
    assertTrue(constraint.hasMinAmplitude());
    assertTrue(constraint.hasGap());
  }

  @Test
  void zeroMaxAmplitudeProhibitsMeander() {
    NetMeanderConstraint constraint = new NetMeanderConstraint(0.0, 0.0, 0.5);
    assertFalse(constraint.isMeanderAllowed());
    assertFalse(constraint.hasMaxAmplitude());
  }

  @Test
  void negativeOrUnspecifiedSentinels() {
    NetMeanderConstraint constraint = new NetMeanderConstraint(-1.0, -1.0, 0.0);
    assertTrue(constraint.isMeanderAllowed());
    assertFalse(constraint.hasMaxAmplitude());
    assertFalse(constraint.hasMinAmplitude());
    assertFalse(constraint.hasGap());
  }

  @Test
  void resolveEffectiveGapFollows3wRule() {
    double traceWidth = 100.0;
    double clearance = 50.0;

    // 1. When gap is not specified (<= 0), defaults to max(3 * width, width + clearance) = 300.0
    NetMeanderConstraint noGap = new NetMeanderConstraint(1.0, 0.2, 0.0);
    assertEquals(300.0, noGap.resolveEffectiveGap(traceWidth, clearance), 1e-6);

    // 2. When gap is smaller than 3 * width (e.g. 200.0 < 300.0), clamped up to 3W (300.0)
    NetMeanderConstraint smallGap = new NetMeanderConstraint(1.0, 0.2, 200.0);
    assertEquals(300.0, smallGap.resolveEffectiveGap(traceWidth, clearance), 1e-6);

    // 3. When gap is larger than 3 * width (e.g. 500.0 > 300.0), the explicit gap is respected
    NetMeanderConstraint largeGap = new NetMeanderConstraint(1.0, 0.2, 500.0);
    assertEquals(500.0, largeGap.resolveEffectiveGap(traceWidth, clearance), 1e-6);
  }

  @Test
  void resolveCornerStyleAdaptsToAngleRestriction() {
    NetMeanderConstraint autoConstraint = new NetMeanderConstraint(1.0, 0.2, 0.5);

    assertEquals(
        NetMeanderConstraint.CornerStyle.CHAMFERED_45,
        autoConstraint.resolveCornerStyle(AngleRestriction.FORTYFIVE_DEGREE));
    assertEquals(
        NetMeanderConstraint.CornerStyle.ORTHOGONAL_90,
        autoConstraint.resolveCornerStyle(AngleRestriction.NINETY_DEGREE));

    // Explicit corner style overrides auto resolution
    NetMeanderConstraint roundConstraint =
        new NetMeanderConstraint(
            1.0, 0.2, 0.5, false, NetMeanderConstraint.CornerStyle.FILLETED_ROUND, 80);
    assertEquals(
        NetMeanderConstraint.CornerStyle.FILLETED_ROUND,
        roundConstraint.resolveCornerStyle(AngleRestriction.FORTYFIVE_DEGREE));
  }

  @Test
  void mergeWithFallbackSupplementsMissingFields() {
    NetMeanderConstraint specific = new NetMeanderConstraint(1.2, 0.0, 0.0);
    NetMeanderConstraint fallback =
        new NetMeanderConstraint(
            2.0, 0.4, 0.9, true, NetMeanderConstraint.CornerStyle.CHAMFERED_45, 75);

    NetMeanderConstraint merged = specific.mergeWith(fallback);

    assertEquals(1.2, merged.maxAmplitude(), 1e-6); // Kept from specific
    assertEquals(0.4, merged.minAmplitude(), 1e-6); // Filled from fallback
    assertEquals(0.9, merged.gap(), 1e-6); // Filled from fallback
    assertTrue(merged.singleSided()); // Inherited from fallback
    assertEquals(NetMeanderConstraint.CornerStyle.CHAMFERED_45, merged.cornerStyle());
    assertEquals(75, merged.cornerRadiusPercentage());
  }

  @Test
  void singleNetInheritsAndOverridesClassMeanderConstraint() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    NetClass netClass = rules.getDefaultNetClass();
    assertNull(netClass.getMeanderConstraint());

    Net net = rules.nets.add("CLK_SINGLE", 1, false);
    assertNull(net.getMeanderConstraint());
    assertFalse(net.hasExplicitMeanderConstraint());

    // 1. Set meander constraint on NetClass
    NetMeanderConstraint classConstraint = new NetMeanderConstraint(2.0, 0.5, 0.8);
    netClass.setMeanderConstraint(classConstraint);

    assertNotNull(net.getMeanderConstraint());
    assertEquals(2.0, net.getMeanderConstraint().maxAmplitude(), 1e-6);
    assertEquals(0.8, net.getMeanderConstraint().gap(), 1e-6);
    assertFalse(net.hasExplicitMeanderConstraint());

    // 2. Set explicit override on the single net (e.g. tighter amplitude)
    NetMeanderConstraint netOverride = new NetMeanderConstraint(1.2, 0.2, 0.0);
    net.setMeanderConstraint(netOverride);

    assertTrue(net.hasExplicitMeanderConstraint());
    assertEquals(1.2, net.getMeanderConstraint().maxAmplitude(), 1e-6);
    assertEquals(0.2, net.getMeanderConstraint().minAmplitude(), 1e-6);
    // Gap was unspecified at net level, merged transparently from NetClass!
    assertEquals(0.8, net.getMeanderConstraint().gap(), 1e-6);

    // 3. Clear explicit override -> reverts cleanly to NetClass
    net.setMeanderConstraint(null);
    assertFalse(net.hasExplicitMeanderConstraint());
    assertEquals(2.0, net.getMeanderConstraint().maxAmplitude(), 1e-6);
  }

  @Test
  void boardRulesDefaultMeanderConstraintResolution() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    NetMeanderConstraint boardDefault = new NetMeanderConstraint(3.0, 0.5, 1.0);
    rules.setDefaultMeanderConstraint(boardDefault);

    assertEquals(boardDefault, rules.getDefaultMeanderConstraint());

    Net net = rules.nets.add("DATA_0", 1, false);
    // Net has no explicit constraint and its class has none -> resolves to board default
    NetMeanderConstraint resolved = rules.resolveMeanderConstraint(net);
    assertNotNull(resolved);
    assertEquals(3.0, resolved.maxAmplitude(), 1e-6);
  }

  @Test
  void cornerStyleParseAndToDsn() {
    assertEquals(
        NetMeanderConstraint.CornerStyle.CHAMFERED_45,
        NetMeanderConstraint.CornerStyle.parse("chamfered"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.CHAMFERED_45,
        NetMeanderConstraint.CornerStyle.parse("45"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.FILLETED_ROUND,
        NetMeanderConstraint.CornerStyle.parse("fillet"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.FILLETED_ROUND,
        NetMeanderConstraint.CornerStyle.parse("filleted"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.FILLETED_ROUND,
        NetMeanderConstraint.CornerStyle.parse("round"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.ORTHOGONAL_90,
        NetMeanderConstraint.CornerStyle.parse("orthogonal"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.ORTHOGONAL_90,
        NetMeanderConstraint.CornerStyle.parse("90"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.AUTO, NetMeanderConstraint.CornerStyle.parse("unknown"));
    assertEquals(
        NetMeanderConstraint.CornerStyle.AUTO, NetMeanderConstraint.CornerStyle.parse(null));

    assertEquals("chamfered", NetMeanderConstraint.CornerStyle.CHAMFERED_45.toDsn());
    assertEquals("filleted", NetMeanderConstraint.CornerStyle.FILLETED_ROUND.toDsn());
    assertEquals("orthogonal", NetMeanderConstraint.CornerStyle.ORTHOGONAL_90.toDsn());
  }

  @Test
  void hasCustomCornerStyleDetection() {
    NetMeanderConstraint autoConstraint = new NetMeanderConstraint(1.0, 0.2, 0.5);
    assertFalse(autoConstraint.hasCustomCornerStyle());

    NetMeanderConstraint styled =
        autoConstraint.withCornerStyle(NetMeanderConstraint.CornerStyle.CHAMFERED_45, 80);
    assertTrue(styled.hasCustomCornerStyle());
  }

  @Test
  void staticMergeNullSafety() {
    NetMeanderConstraint a = new NetMeanderConstraint(1.0, 0.2, 0.5);
    NetMeanderConstraint b = new NetMeanderConstraint(2.0, 0.4, 0.8);

    assertNull(NetMeanderConstraint.merge(null, null));
    assertEquals(a, NetMeanderConstraint.merge(a, null));
    assertEquals(b, NetMeanderConstraint.merge(null, b));

    NetMeanderConstraint merged = NetMeanderConstraint.merge(a, b);
    assertNotNull(merged);
    assertEquals(1.0, merged.maxAmplitude(), 1e-6);
  }

  @Test
  void meanderTargetParseAndToDsn() {
    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR,
        NetMeanderConstraint.MeanderTarget.parse("diff_pair"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR,
        NetMeanderConstraint.MeanderTarget.parse("pair"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR,
        NetMeanderConstraint.MeanderTarget.parse("differential_pair"));

    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW,
        NetMeanderConstraint.MeanderTarget.parse("diff_pair_skew"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW,
        NetMeanderConstraint.MeanderTarget.parse("skew"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW,
        NetMeanderConstraint.MeanderTarget.parse("pair_skew"));

    assertEquals(
        NetMeanderConstraint.MeanderTarget.SINGLE_TRACK,
        NetMeanderConstraint.MeanderTarget.parse("single_track"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.SINGLE_TRACK,
        NetMeanderConstraint.MeanderTarget.parse("track"));
    assertEquals(
        NetMeanderConstraint.MeanderTarget.SINGLE_TRACK,
        NetMeanderConstraint.MeanderTarget.parse(null));

    assertEquals("single_track", NetMeanderConstraint.MeanderTarget.SINGLE_TRACK.toDsn());
    assertEquals("diff_pair", NetMeanderConstraint.MeanderTarget.DIFF_PAIR.toDsn());
    assertEquals("diff_pair_skew", NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW.toDsn());
  }

  @Test
  void multiTargetConstraintsOnNetClassAndNet() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;
    NetClass netClass = rules.getDefaultNetClass();

    NetMeanderConstraint singleTrack =
        new NetMeanderConstraint(1.5, 0.3, 0.6, false, NetMeanderConstraint.CornerStyle.AUTO, 80);
    NetMeanderConstraint diffPair =
        new NetMeanderConstraint(
            2.5,
            0.5,
            1.2,
            true,
            NetMeanderConstraint.CornerStyle.CHAMFERED_45,
            75,
            NetMeanderConstraint.MeanderTarget.DIFF_PAIR);
    NetMeanderConstraint skew =
        new NetMeanderConstraint(
            0.8,
            0.1,
            0.4,
            false,
            NetMeanderConstraint.CornerStyle.FILLETED_ROUND,
            60,
            NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW);

    netClass.setMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK, singleTrack);
    netClass.setMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR, diffPair);
    netClass.setMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW, skew);

    assertTrue(netClass.hasAnyMeanderConstraint());
    assertEquals(singleTrack, netClass.getMeanderConstraint());
    assertEquals(
        singleTrack,
        netClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK));
    assertEquals(
        diffPair, netClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR));
    assertEquals(
        skew, netClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));

    Net net = rules.nets.add("DP_TX_P", 1, false);
    // Net inherits all 3 targets from class
    assertEquals(
        singleTrack, net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK));
    assertEquals(diffPair, net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR));
    assertEquals(skew, net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));
    assertFalse(net.hasAnyExplicitMeanderConstraint());

    // Explicit override on diff pair only
    NetMeanderConstraint diffPairOverride =
        new NetMeanderConstraint(3.0, 0.6, 1.5, false, NetMeanderConstraint.CornerStyle.AUTO, 80);
    net.setMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR, diffPairOverride);

    assertTrue(net.hasAnyExplicitMeanderConstraint());
    assertFalse(net.hasExplicitMeanderConstraint()); // single track is not explicitly set
    assertTrue(net.hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR));

    assertEquals(
        3.0,
        net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR).maxAmplitude(),
        1e-6);
    // Single track and skew still inherit from netClass
    assertEquals(
        1.5,
        net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK).maxAmplitude(),
        1e-6);
    assertEquals(
        0.8,
        net.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW).maxAmplitude(),
        1e-6);
  }

  @Test
  void boardRulesMultiTargetResolution() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;

    NetMeanderConstraint defaultSingle = new NetMeanderConstraint(2.0, 0.4, 0.8);
    NetMeanderConstraint defaultDiffPair =
        new NetMeanderConstraint(
            3.0,
            0.6,
            1.0,
            true,
            NetMeanderConstraint.CornerStyle.AUTO,
            80,
            NetMeanderConstraint.MeanderTarget.DIFF_PAIR);

    rules.setDefaultMeanderConstraint(
        NetMeanderConstraint.MeanderTarget.SINGLE_TRACK, defaultSingle);
    rules.setDefaultMeanderConstraint(
        NetMeanderConstraint.MeanderTarget.DIFF_PAIR, defaultDiffPair);

    assertTrue(rules.hasAnyDefaultMeanderConstraint());
    assertEquals(defaultSingle, rules.getDefaultMeanderConstraint());
    assertEquals(
        defaultSingle,
        rules.getDefaultMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK));
    assertEquals(
        defaultDiffPair,
        rules.getDefaultMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR));
    assertNull(
        rules.getDefaultMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));

    Net net = rules.nets.add("BUS_D0", 1, false);
    assertEquals(
        2.0,
        rules
            .resolveMeanderConstraint(net, NetMeanderConstraint.MeanderTarget.SINGLE_TRACK)
            .maxAmplitude(),
        1e-6);
    assertEquals(
        3.0,
        rules
            .resolveMeanderConstraint(net, NetMeanderConstraint.MeanderTarget.DIFF_PAIR)
            .maxAmplitude(),
        1e-6);
    assertNull(
        rules.resolveMeanderConstraint(net, NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));
  }
}
