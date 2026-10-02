package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetLengthConstraint;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for Specctra DSN net-level and class-level trace length constraints.
 */
class DsnLengthConstraintsTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  private static String createDsn(String networkBody) {
    return """
        (pcb test_length.dsn
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
  void testNetClassLengthConstraintParsedFromCircuit() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1))
            (class LENGTH_CLASS N1
              (circuit (length 5000 1000))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertFalse(net.hasExplicitLengthConstraint());
    assertEquals(
        5000.0,
        board.communication.coordinateTransform.boardToDsn(net.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        1000.0,
        board.communication.coordinateTransform.boardToDsn(net.getMinimumTraceLength()),
        1e-4);
  }

  @Test
  void testExplicitNetLengthConstraintParsedFromCircuit() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length 4000 1500))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertTrue(net.hasExplicitLengthConstraint());
    assertEquals(
        4000.0,
        board.communication.coordinateTransform.boardToDsn(net.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        1500.0,
        board.communication.coordinateTransform.boardToDsn(net.getMinimumTraceLength()),
        1e-4);

    NetLengthConstraint constraint = net.getLengthConstraint();
    assertEquals(
        2750.0,
        board.communication.coordinateTransform.boardToDsn(constraint.targetLength()),
        1e-4);
  }

  @Test
  void testNetConstraintOverridesClassConstraint() throws Exception {
    String dsn =
        createDsn(
            """
            (net N_OVERRIDE (pins U1-1 U2-1)
              (circuit (length 3500 2500))
            )
            (net N_DEFAULT (pins U1-1 U2-1))
            (class LENGTH_CLASS N_OVERRIDE N_DEFAULT
              (circuit (length 8000 1000))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    Net netOverride = board.rules.nets.get("N_OVERRIDE", 1);
    assertNotNull(netOverride);
    assertTrue(netOverride.hasExplicitLengthConstraint());
    assertEquals(
        3500.0,
        board.communication.coordinateTransform.boardToDsn(netOverride.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        2500.0,
        board.communication.coordinateTransform.boardToDsn(netOverride.getMinimumTraceLength()),
        1e-4);

    Net netDefault = board.rules.nets.get("N_DEFAULT", 1);
    assertNotNull(netDefault);
    assertFalse(netDefault.hasExplicitLengthConstraint());
    assertEquals(
        8000.0,
        board.communication.coordinateTransform.boardToDsn(netDefault.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        1000.0,
        board.communication.coordinateTransform.boardToDsn(netDefault.getMinimumTraceLength()),
        1e-4);
  }

  @Test
  void testNetLengthConstraintParsedFromRuleScope() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (rule (length 4500 1200))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    assertTrue(net.hasExplicitLengthConstraint());
    assertEquals(
        4500.0,
        board.communication.coordinateTransform.boardToDsn(net.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        1200.0,
        board.communication.coordinateTransform.boardToDsn(net.getMinimumTraceLength()),
        1e-4);
  }

  @Test
  void testSingleValueAndNegativeMaxConstraints() throws Exception {
    String dsn =
        createDsn(
            """
            (net N_MAX_ONLY (pins U1-1 U2-1)
              (circuit (length 6000))
            )
            (net N_MIN_ONLY (pins U1-1 U2-1)
              (circuit (length -1 2200))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);

    Net netMax = board.rules.nets.get("N_MAX_ONLY", 1);
    assertNotNull(netMax);
    assertTrue(netMax.hasExplicitLengthConstraint());
    assertEquals(
        6000.0,
        board.communication.coordinateTransform.boardToDsn(netMax.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        0.0,
        board.communication.coordinateTransform.boardToDsn(netMax.getMinimumTraceLength()),
        1e-4);
    assertTrue(netMax.getLengthConstraint().hasMax());
    assertFalse(netMax.getLengthConstraint().hasMin());

    Net netMin = board.rules.nets.get("N_MIN_ONLY", 1);
    assertNotNull(netMin);
    assertTrue(netMin.hasExplicitLengthConstraint());
    assertEquals(
        0.0,
        board.communication.coordinateTransform.boardToDsn(netMin.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        2200.0,
        board.communication.coordinateTransform.boardToDsn(netMin.getMinimumTraceLength()),
        1e-4);
    assertFalse(netMin.getLengthConstraint().hasMax());
    assertTrue(netMin.getLengthConstraint().hasMin());
  }

  @Test
  void testDsnRoundTripPreservesExplicitNetLengthConstraint() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length 5000 2000))
            )
            (net N2 (pins U1-1 U2-1))
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net origNet1 = board.rules.nets.get("N1", 1);
    assertTrue(origNet1.hasExplicitLengthConstraint());

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    // Verify circuit scope was written inside N1 and not N2
    assertTrue(
        exportedDsn.contains("(circuit"),
        "Exported DSN must contain circuit scope for constrained net");
    assertTrue(
        exportedDsn.contains("(length 5000.0 2000.0)"),
        "Exported DSN must contain length statement with 5000.0 2000.0");

    // Read back exported DSN
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    Net reloadedNet1 = reloadedBoard.rules.nets.get("N1", 1);
    assertNotNull(reloadedNet1);
    assertTrue(reloadedNet1.hasExplicitLengthConstraint());
    assertEquals(
        5000.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedNet1.getMaximumTraceLength()),
        1e-4);
    assertEquals(
        2000.0,
        reloadedBoard.communication.coordinateTransform.boardToDsn(
            reloadedNet1.getMinimumTraceLength()),
        1e-4);

    Net reloadedNet2 = reloadedBoard.rules.nets.get("N2", 1);
    assertNotNull(reloadedNet2);
    assertFalse(reloadedNet2.hasExplicitLengthConstraint());
  }

  @Test
  void testNetIncompletesLengthViolationCalculation() throws Exception {
    String dsn =
        createDsn(
            """
            (net N1 (pins U1-1 U2-1)
              (circuit (length 5000 2000))
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    Net net = board.rules.nets.get("N1", 1);
    assertNotNull(net);

    NetIncompletes incompletes =
        new NetIncompletes(net.netNumber, board.getConnectableItems(net.netNumber), board);
    // Unrouted net has no completed trace, but has incompletes airlines
    // When incompletes exist, min length violation is suppressed (per DRC design)
    assertEquals(0.0, incompletes.getLengthViolation(), 1e-4);
  }
}
