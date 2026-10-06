package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetMeanderConstraint;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for Specctra DSN net-level, class-level, and board default meander
 * constraints (amplitude, gap, pattern type, corner style).
 */
class DsnMeanderConstraintsTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  private static String createDsn(String structureExtra, String networkBody) {
    return """
        (pcb test_meander.dsn
          (parser
            (string_quote ")
            (space_in_quoted_tokens on)
          )
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (boundary
              (path pcb 0  0 0  20000 0  20000 20000  0 20000  0 0)
            )
            (rule (width 250) (clearance 200) """
        + structureExtra
        + """
            )
          )
          (placement
            (component PAD
              (place U1 2000 5000 front 0)
              (place U2 8000 5000 front 0)
            )
          )
          (library
            (image PAD
              (pin CirclePad 1 0 0)
            )
            (padstack CirclePad
              (shape (circle F.Cu 800))
              (attach off)
            )
          )
          (network
        """
        + networkBody
        + """
          )
        )
        """;
  }

  private static String createDsn(String networkBody) {
    return createDsn("", networkBody);
  }

  private RoutingBoard loadBoardFromString(String dsn) throws Exception {
    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result, "DSN read must succeed");
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void testSingleNetMeanderConstraintParsedFromRule() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (rule (length_amplitude 2500 500) (length_gap 1200))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertTrue(net.hasExplicitMeanderConstraint());

    NetMeanderConstraint constraint = net.getExplicitMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        2500.0,
        board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()),
        1e-4);
    assertEquals(
        500.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        1200.0, board.communication.coordinateTransform.boardToDsn(constraint.gap()), 1e-4);
    assertFalse(constraint.singleSided());
    assertTrue(constraint.isMeanderAllowed());
  }

  @Test
  void testSingleNetMeanderConstraintParsedFromCircuitWithSubscopes() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit
                (length_amplitude 1800 300 (type trombone) (corner filleted) (radius 75))
                (length_gap 900)
              )
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertTrue(net.hasExplicitMeanderConstraint());

    NetMeanderConstraint constraint = net.getExplicitMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        1800.0,
        board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()),
        1e-4);
    assertEquals(
        300.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(900.0, board.communication.coordinateTransform.boardToDsn(constraint.gap()), 1e-4);
    assertTrue(constraint.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.FILLETED_ROUND, constraint.cornerStyle());
    assertEquals(75, constraint.cornerRadiusPercentage());
  }

  @Test
  void testNetClassMeanderConstraintParsedFromCircuit() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (circuit (length_amplitude 3000 1000) (length_gap 1500))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertFalse(net.hasExplicitMeanderConstraint());

    NetMeanderConstraint resolved = board.rules.resolveMeanderConstraint(net);
    assertNotNull(resolved);
    assertEquals(
        3000.0, board.communication.coordinateTransform.boardToDsn(resolved.maxAmplitude()), 1e-4);
    assertEquals(
        1000.0, board.communication.coordinateTransform.boardToDsn(resolved.minAmplitude()), 1e-4);
    assertEquals(1500.0, board.communication.coordinateTransform.boardToDsn(resolved.gap()), 1e-4);
  }

  @Test
  void testNetClassMeanderConstraintParsedFromRule() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (rule (length_amplitude 3500 1200 (type trombone)) (length_gap 1600))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertFalse(net.hasExplicitMeanderConstraint());

    NetMeanderConstraint resolved = board.rules.resolveMeanderConstraint(net);
    assertNotNull(resolved);
    assertEquals(
        3500.0, board.communication.coordinateTransform.boardToDsn(resolved.maxAmplitude()), 1e-4);
    assertEquals(
        1200.0, board.communication.coordinateTransform.boardToDsn(resolved.minAmplitude()), 1e-4);
    assertEquals(1600.0, board.communication.coordinateTransform.boardToDsn(resolved.gap()), 1e-4);
    assertTrue(resolved.singleSided());
  }

  @Test
  void testStructureDefaultMeanderConstraintParsed() throws Exception {
    String dsn =
        createDsn(
            "(length_amplitude 4000 1000) (length_gap 2000)",
            """
            (net N1 (pins U1-1 U2-1))
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertFalse(net.hasExplicitMeanderConstraint());

    assertNotNull(board.rules.getDefaultMeanderConstraint());
    NetMeanderConstraint resolved = board.rules.resolveMeanderConstraint(net);
    assertNotNull(resolved);
    assertEquals(
        4000.0, board.communication.coordinateTransform.boardToDsn(resolved.maxAmplitude()), 1e-4);
    assertEquals(
        1000.0, board.communication.coordinateTransform.boardToDsn(resolved.minAmplitude()), 1e-4);
    assertEquals(2000.0, board.communication.coordinateTransform.boardToDsn(resolved.gap()), 1e-4);
  }

  @Test
  void testForbiddenMeanderWithZeroAmplitude() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (rule (length_amplitude 0.0))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertTrue(net.hasExplicitMeanderConstraint());
    assertFalse(net.getExplicitMeanderConstraint().isMeanderAllowed());
  }

  @Test
  void testRoundTripSingleNetMeanderExportAndReload() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit
                (length_amplitude 2500 500 (type trombone) (corner chamfered))
                (length_gap 1200)
              )
            )
            (net N2 (pins U1-1 U2-1))
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net origNet1 = board.rules.nets.get("N1", 1);
    assertTrue(origNet1.hasExplicitMeanderConstraint());

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    // Verify circuit scope was written inside N1 and contains meander rules
    assertTrue(
        exportedDsn.contains("(length_amplitude 2500.0 500.0 (type trombone) (corner chamfered))"),
        "Exported DSN must serialize length_amplitude");
    assertTrue(
        exportedDsn.contains("(length_gap 1200.0)"), "Exported DSN must serialize length_gap");

    // Read back exported DSN
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    Net reloadedNet1 = reloadedBoard.rules.nets.get("N1", 1);
    assertNotNull(reloadedNet1);
    assertTrue(reloadedNet1.hasExplicitMeanderConstraint());

    NetMeanderConstraint reloadedConstraint = reloadedNet1.getExplicitMeanderConstraint();
    assertEquals(
        2500.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.maxAmplitude()),
        1e-4);
    assertEquals(
        500.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.minAmplitude()),
        1e-4);
    assertEquals(
        1200.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(reloadedConstraint.gap()),
        1e-4);
    assertTrue(reloadedConstraint.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.CHAMFERED_45, reloadedConstraint.cornerStyle());

    Net reloadedNet2 = reloadedBoard.rules.nets.get("N2", 1);
    assertNotNull(reloadedNet2);
    assertFalse(reloadedNet2.hasExplicitMeanderConstraint());
  }

  @Test
  void testRoundTripNetClassMeanderExportAndReload() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (circuit (length_amplitude 3000 1000) (length_gap 1500))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_class", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    assertTrue(
        exportedDsn.contains("(length_amplitude 3000.0 1000.0)"),
        "Exported DSN must contain class length_amplitude");
    assertTrue(
        exportedDsn.contains("(length_gap 1500.0)"), "Exported DSN must contain class length_gap");

    // Reload
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    Net reloadedNet = reloadedBoard.rules.nets.get("N1", 1);
    assertNotNull(reloadedNet);
    NetMeanderConstraint resolved = reloadedBoard.rules.resolveMeanderConstraint(reloadedNet);
    assertNotNull(resolved);
    assertEquals(
        3000.0, board.communication.coordinateTransform.boardToDsn(resolved.maxAmplitude()), 1e-4);
    assertEquals(
        1000.0, board.communication.coordinateTransform.boardToDsn(resolved.minAmplitude()), 1e-4);
    assertEquals(1500.0, board.communication.coordinateTransform.boardToDsn(resolved.gap()), 1e-4);
  }

  @Test
  void testRoundTripStructureDefaultMeanderExportAndReload() throws Exception {
    String dsn =
        createDsn(
            "(length_amplitude 4000 1000) (length_gap 2000)",
            """
            (net N1 (pins U1-1 U2-1))
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_default", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    assertTrue(
        exportedDsn.contains("(length_amplitude 4000.0 1000.0)"),
        "Exported DSN must contain default length_amplitude");
    assertTrue(
        exportedDsn.contains("(length_gap 2000.0)"),
        "Exported DSN must contain default length_gap");

    // Reload
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    assertNotNull(reloadedBoard.rules.getDefaultMeanderConstraint());
    Net reloadedNet = reloadedBoard.rules.nets.get("N1", 1);
    NetMeanderConstraint resolved = reloadedBoard.rules.resolveMeanderConstraint(reloadedNet);
    assertNotNull(resolved);
    assertEquals(
        4000.0, board.communication.coordinateTransform.boardToDsn(resolved.maxAmplitude()), 1e-4);
    assertEquals(
        1000.0, board.communication.coordinateTransform.boardToDsn(resolved.minAmplitude()), 1e-4);
    assertEquals(2000.0, board.communication.coordinateTransform.boardToDsn(resolved.gap()), 1e-4);
  }

  @Test
  void testMultiTargetMeanderConstraintsParsedAndResolved() throws Exception {
    String dsn =
        createDsn(
            """
            (class BUS_DIFF N1
              (rule
                (width 250)
                (clearance 200)
                (length_amplitude 2000 400)
                (length_gap 1000)
                (length_amplitude 3500 700 (target diff_pair))
                (length_gap 1500 (target diff_pair))
                (length_amplitude 800 100 (target diff_pair_skew))
                (length_gap 500 (target diff_pair_skew))
              )
            )
            (net N1 (pins U1-1 U2-1))
            (net N2 (pins U1-1 U2-1)
              (rule
                (length_amplitude 900 150 (target diff_pair_skew))
              )
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    app.freerouting.rules.NetClass busClass = board.rules.netClasses.get("BUS_DIFF");
    assertNotNull(busClass);

    NetMeanderConstraint singleTrack =
        busClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    assertNotNull(singleTrack);
    assertEquals(
        2000.0,
        board.communication.coordinateTransform.boardToDsn(singleTrack.maxAmplitude()),
        1e-4);
    assertEquals(
        1000.0, board.communication.coordinateTransform.boardToDsn(singleTrack.gap()), 1e-4);

    NetMeanderConstraint diffPair =
        busClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR);
    assertNotNull(diffPair);
    assertEquals(
        3500.0, board.communication.coordinateTransform.boardToDsn(diffPair.maxAmplitude()), 1e-4);
    assertEquals(1500.0, board.communication.coordinateTransform.boardToDsn(diffPair.gap()), 1e-4);

    NetMeanderConstraint skew =
        busClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW);
    assertNotNull(skew);
    assertEquals(
        800.0, board.communication.coordinateTransform.boardToDsn(skew.maxAmplitude()), 1e-4);
    assertEquals(500.0, board.communication.coordinateTransform.boardToDsn(skew.gap()), 1e-4);

    // N1 inherits all 3 from class
    Net n1 = board.rules.nets.get("N1", 1);
    assertEquals(
        3500.0,
        board.communication.coordinateTransform.boardToDsn(
            n1.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR).maxAmplitude()),
        1e-4);

    // N2 has explicit skew override
    Net n2 = board.rules.nets.get("N2", 1);
    assertTrue(n2.hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));
    assertFalse(n2.hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK));
    assertEquals(
        900.0,
        board.communication.coordinateTransform.boardToDsn(
            n2.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW)
                .maxAmplitude()),
        1e-4);
  }

  @Test
  void testMultiTargetRoundTripExportAndReload() throws Exception {
    String dsn =
        createDsn(
            """
            (class HIGH_SPEED N1
              (circuit
                (use_layer F.Cu)
                (length_amplitude 1800 300)
                (length_gap 900)
                (length_amplitude 3200 600 (target diff_pair))
                (length_gap 1400 (target diff_pair))
              )
            )
            (net N1 (pins U1-1 U2-1)
              (circuit
                (length_amplitude 750 120 (target diff_pair_skew))
                (length_gap 450 (target diff_pair_skew))
              )
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_multitarget", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    assertTrue(
        exportedDsn.contains("(target diff_pair)"),
        "Exported DSN must serialize (target diff_pair)");
    assertTrue(
        exportedDsn.contains("(target diff_pair_skew)"),
        "Exported DSN must serialize (target diff_pair_skew)");

    // Reload and verify
    RoutingBoard reloaded = loadBoardFromString(exportedDsn);
    app.freerouting.rules.NetClass reloadedClass = reloaded.rules.netClasses.get("HIGH_SPEED");
    assertNotNull(reloadedClass);

    NetMeanderConstraint singleTrack =
        reloadedClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    assertNotNull(singleTrack);
    assertEquals(
        1800.0,
        reloaded.communication.coordinateTransform.boardToDsn(singleTrack.maxAmplitude()),
        1e-4);

    NetMeanderConstraint diffPair =
        reloadedClass.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR);
    assertNotNull(diffPair);
    assertEquals(
        3200.0,
        reloaded.communication.coordinateTransform.boardToDsn(diffPair.maxAmplitude()),
        1e-4);
    assertEquals(
        1400.0, reloaded.communication.coordinateTransform.boardToDsn(diffPair.gap()), 1e-4);

    Net reloadedN1 = reloaded.rules.nets.get("N1", 1);
    assertNotNull(reloadedN1);
    assertTrue(
        reloadedN1.hasExplicitMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW));
    NetMeanderConstraint skew =
        reloadedN1.getMeanderConstraint(NetMeanderConstraint.MeanderTarget.DIFF_PAIR_SKEW);
    assertNotNull(skew);
    assertEquals(
        750.0, reloaded.communication.coordinateTransform.boardToDsn(skew.maxAmplitude()), 1e-4);
    assertEquals(450.0, reloaded.communication.coordinateTransform.boardToDsn(skew.gap()), 1e-4);
  }
}
