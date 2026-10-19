package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.CornerStyle;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.NetMeanderConstraint;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end integration tests for Specctra DSN net-level and class-level meander constraints. */
class DsnMeanderConstraintsTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  private static String createDsn(String networkBody) {
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
            (rule (width 250) (clearance 200))
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

  private RoutingBoard loadBoardFromString(String dsn) throws Exception {
    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result, "DSN read must succeed");
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void testNetClassMeanderConstraintParsedFromCircuit() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (circuit (length_amplitude 800 200) (length_gap 600) (corner_style chamfer) (radius 75.0) (single_sided on))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertFalse(net.hasExplicitMeanderConstraint());
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        800.0, board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()), 1e-4);
    assertEquals(
        200.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        600.0, board.communication.coordinateTransform.boardToDsn(constraint.spacing()), 1e-4);
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());
    assertEquals(75.0, constraint.radiusPercentage(), 1e-4);
    assertTrue(constraint.singleSided());
  }

  @Test
  void testExplicitNetMeanderConstraintParsedFromCircuit() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length_amplitude 1200 300) (length_gap 800) (corner_style fillet) (radius 85.0) (single_sided off))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertTrue(net.hasExplicitMeanderConstraint());
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        1200.0,
        board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()),
        1e-4);
    assertEquals(
        300.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        800.0, board.communication.coordinateTransform.boardToDsn(constraint.spacing()), 1e-4);
    assertEquals(CornerStyle.FILLET, constraint.cornerStyle());
    assertEquals(85.0, constraint.radiusPercentage(), 1e-4);
    assertFalse(constraint.singleSided());
  }

  @Test
  void testNetConstraintOverridesClassConstraint() throws Exception {
    String dsn =
        createDsn(
            """
            (net N_OVERRIDE (pins U1-1 U2-1)
              (circuit (length_amplitude 900 150) (length_gap 500) (corner_style chamfer))
            )
            (net N_DEFAULT (pins U1-1 U2-1))
            (class MEANDER_CLASS N_OVERRIDE N_DEFAULT
              (circuit (length_amplitude 1500 400) (length_gap 1000) (corner_style fillet))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    Net netOverride = board.rules.nets.get("N_OVERRIDE", 1);
    assertNotNull(netOverride);
    assertTrue(netOverride.hasExplicitMeanderConstraint());
    NetMeanderConstraint overrideConstraint = netOverride.getMeanderConstraint();
    assertEquals(
        900.0,
        board.communication.coordinateTransform.boardToDsn(overrideConstraint.maxAmplitude()),
        1e-4);
    assertEquals(
        150.0,
        board.communication.coordinateTransform.boardToDsn(overrideConstraint.minAmplitude()),
        1e-4);
    assertEquals(CornerStyle.CHAMFER, overrideConstraint.cornerStyle());

    Net netDefault = board.rules.nets.get("N_DEFAULT", 1);
    assertNotNull(netDefault);
    assertFalse(netDefault.hasExplicitMeanderConstraint());
    NetMeanderConstraint defaultConstraint = netDefault.getMeanderConstraint();
    assertEquals(
        1500.0,
        board.communication.coordinateTransform.boardToDsn(defaultConstraint.maxAmplitude()),
        1e-4);
    assertEquals(
        400.0,
        board.communication.coordinateTransform.boardToDsn(defaultConstraint.minAmplitude()),
        1e-4);
    assertEquals(CornerStyle.FILLET, defaultConstraint.cornerStyle());
  }

  @Test
  void testMeanderPatternScopeWithPatternType() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (meander_pattern (pattern_type diff_pair) (length_amplitude 1000 250) (length_gap 750) (corner_style fillet) (radius 90.0) (single_sided off)))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertTrue(net.hasExplicitMeanderConstraint());
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertEquals(
        1000.0,
        board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()),
        1e-4);
    assertEquals(
        250.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        750.0, board.communication.coordinateTransform.boardToDsn(constraint.spacing()), 1e-4);
    assertEquals(CornerStyle.FILLET, constraint.cornerStyle());
    assertEquals(90.0, constraint.radiusPercentage(), 1e-4);
    assertFalse(constraint.singleSided());
  }

  @Test
  void testMultiClauseRuleInClass() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (rule (length_amplitude 850 220) (length_gap 620) (corner_style chamfer) (radius 70.0) (single_sided on))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        850.0, board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()), 1e-4);
    assertEquals(
        220.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        620.0, board.communication.coordinateTransform.boardToDsn(constraint.spacing()), 1e-4);
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());
    assertEquals(70.0, constraint.radiusPercentage(), 1e-4);
    assertTrue(constraint.singleSided());
  }

  @Test
  void testStructureLevelMeanderRuleSetsBoardDefaults() throws Exception {
    String dsn =
        """
        (pcb test_meander_struct.dsn
          (parser (string_quote ") (space_in_quoted_tokens on))
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (boundary
              (path pcb 0 0 0 20000 0 20000 20000 0 20000 0 0)
            )
            (rule (width 250) (clearance 200) (length_amplitude 750 180) (length_gap 520) (corner_style chamfer) (radius 65.0) (single_sided on))
          )
          (placement
            (component PAD (place U1 2000 5000 front 0) (place U2 8000 5000 front 0))
          )
          (library
            (image PAD (pin CirclePad 1 0 0))
            (padstack CirclePad (shape (circle F.Cu 800)) (attach off))
          )
          (network
            (net N1 (pins U1-1 U2-1))
          )
        )
        """;

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    assertFalse(net.hasExplicitMeanderConstraint());
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        750.0, board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()), 1e-4);
    assertEquals(
        180.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
    assertEquals(
        520.0, board.communication.coordinateTransform.boardToDsn(constraint.spacing()), 1e-4);
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());
    assertEquals(65.0, constraint.radiusPercentage(), 1e-4);
    assertTrue(constraint.singleSided());
  }

  @Test
  void testInvertedAmplitudeBoundsNormalize() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length_amplitude 250 1000) (length_gap 600))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);
    NetMeanderConstraint constraint = net.getMeanderConstraint();
    assertNotNull(constraint);
    assertEquals(
        1000.0,
        board.communication.coordinateTransform.boardToDsn(constraint.maxAmplitude()),
        1e-4);
    assertEquals(
        250.0, board.communication.coordinateTransform.boardToDsn(constraint.minAmplitude()), 1e-4);
  }

  @Test
  void testDsnRoundTripPreservesExplicitNetMeanderConstraint() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length_amplitude 1100 280) (length_gap 650) (corner_style chamfer) (radius 70.0) (single_sided on))
            )
            (net N2 (pins U1-1 U2-1))
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net origNet1 = board.rules.nets.get("N1", 1);
    assertTrue(origNet1.hasExplicitMeanderConstraint());

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_meander", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    // Verify meander statements were written
    assertTrue(
        exportedDsn.contains("(length_amplitude 1100.0 280.0)"),
        "Exported DSN must contain length_amplitude 1100.0 280.0");
    assertTrue(
        exportedDsn.contains("(length_gap 650.0)"), "Exported DSN must contain length_gap 650.0");
    assertTrue(
        exportedDsn.contains("(corner_style chamfer)"),
        "Exported DSN must contain corner_style chamfer");
    assertTrue(exportedDsn.contains("(radius 70.0)"), "Exported DSN must contain radius 70.0");
    assertTrue(
        exportedDsn.contains("(single_sided on)"), "Exported DSN must contain single_sided on");

    // Read back exported DSN
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    Net reloadedNet1 = reloadedBoard.rules.nets.get("N1", 1);
    assertNotNull(reloadedNet1);
    assertTrue(reloadedNet1.hasExplicitMeanderConstraint());
    NetMeanderConstraint reloadedConstraint = reloadedNet1.getMeanderConstraint();

    assertEquals(
        1100.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.maxAmplitude()),
        1e-4);
    assertEquals(
        280.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.minAmplitude()),
        1e-4);
    assertEquals(
        650.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(reloadedConstraint.spacing()),
        1e-4);
    assertEquals(CornerStyle.CHAMFER, reloadedConstraint.cornerStyle());
    assertEquals(70.0, reloadedConstraint.radiusPercentage(), 1e-4);
    assertTrue(reloadedConstraint.singleSided());

    Net reloadedNet2 = reloadedBoard.rules.nets.get("N2", 1);
    assertNotNull(reloadedNet2);
    assertFalse(reloadedNet2.hasExplicitMeanderConstraint());
  }

  @Test
  void testDsnRoundTripPreservesNetClassMeanderConstraint() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class MEANDER_CLASS N1
              (circuit (length_amplitude 1400 350) (length_gap 800) (corner_style fillet) (radius 80.0) (single_sided off))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    NetClass origClass = board.rules.netClasses.get("MEANDER_CLASS");
    assertNotNull(origClass);
    assertNotNull(origClass.getMeanderConstraint());

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_class_meander", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    // Verify meander statements inside class
    assertTrue(
        exportedDsn.contains("(length_amplitude 1400.0 350.0)"),
        "Exported DSN class must contain length_amplitude");
    assertTrue(
        exportedDsn.contains("(length_gap 800.0)"), "Exported DSN class must contain length_gap");

    // Read back exported DSN
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    NetClass reloadedClass = reloadedBoard.rules.netClasses.get("MEANDER_CLASS");
    assertNotNull(reloadedClass);
    NetMeanderConstraint reloadedConstraint = reloadedClass.getMeanderConstraint();
    assertNotNull(reloadedConstraint);

    assertEquals(
        1400.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.maxAmplitude()),
        1e-4);
    assertEquals(
        350.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedConstraint.minAmplitude()),
        1e-4);
    assertEquals(
        800.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(reloadedConstraint.spacing()),
        1e-4);
    assertEquals(CornerStyle.FILLET, reloadedConstraint.cornerStyle());
    assertEquals(80.0, reloadedConstraint.radiusPercentage(), 1e-4);
    assertFalse(reloadedConstraint.singleSided());
  }
}
