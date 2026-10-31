package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.BoardReadResult;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Host CAD tools such as KiCad treat a wire whose end lies anywhere inside a pad as connected to
 * that pad. The DSN reader must import such a wire as connected, not only one that ends exactly at
 * the pad centre.
 */
class WireEndInsidePadTest {

  /** Pad P2 is centred at (15000, -10000) um and spans +-300 um by +-200 um. */
  private static final int P2_CENTER_X = 15000;

  private static final int P2_CENTER_Y = -10000;

  /** Centre of pad Q of net N2, below P2 and well clear of it unless a test moves it closer. */
  private static final int FAR_NEIGHBOUR_Y = -15000;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  /** Two-pad net N1 with one imported wire from the P1 centre to (endX, endY). */
  private static String dsnWithWireEndingAt(int endX, int endY) {
    return dsn(wireFromP1To(endX, endY), FAR_NEIGHBOUR_Y);
  }

  private static String wireFromP1To(int endX, int endY) {
    return "(wire (path F.Cu 200 5000 -10000 %d %d) (net N1))".formatted(endX, endY);
  }

  private static String dsn(String wiring, int neighbourY) {
    return """
        (pcb wireend
          (parser (string_quote ") (host_cad "KiCad's Pcbnew"))
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (boundary (rect pcb 0 -20000 20000 0))
            (via V)
            (rule (width 200) (clearance 200)))
          (placement (component P (place P1 5000 -10000 front 0) (place P2 %d %d front 0)
            (place Q %d %d front 0)))
          (library
            (image P (pin Pad 1 0 0))
            (padstack Pad (shape (rect F.Cu -300 -200 300 200)) (attach off))
            (padstack V (shape (circle F.Cu 600)) (attach off)))
          (network (net N1 (pins P1-1 P2-1)) (net N2 (pins Q-1)))
          (wiring %s))
        """
        .formatted(P2_CENTER_X, P2_CENTER_Y, P2_CENTER_X, neighbourY, wiring);
  }

  private static RoutingBoard read(String dsn) {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static int incompletes(RoutingBoard board) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    return drc.getIncompleteCount();
  }

  private static int clearanceViolations(RoutingBoard board) {
    return new DesignRulesChecker(board, null).getAllClearanceViolations().size();
  }

  @Test
  void wireEndingInsidePadOffItsCentreIsConnected() {
    int endX = P2_CENTER_X - 3;
    int endY = P2_CENTER_Y + 2;
    RoutingBoard board = read(dsnWithWireEndingAt(endX, endY));

    assertEquals(0, incompletes(board), "a wire end inside the pad must connect to the pin");
    assertEquals(0, clearanceViolations(board));
    IntPoint wireEnd = new IntPoint(endX * 10, endY * 10);
    assertTrue(
        board.getTraces().stream()
            .map(PolylineTrace.class::cast)
            .anyMatch(t -> Arrays.asList(t.polyline().corners()).contains(wireEnd)),
        "the imported wire must keep its own end point");
  }

  @Test
  void wireEndingAtPadCentreIsImportedUnchanged() {
    RoutingBoard board = read(dsnWithWireEndingAt(P2_CENTER_X, P2_CENTER_Y));

    assertEquals(0, incompletes(board));
    assertEquals(1, board.getTraces().size());
  }

  @Test
  void wireEndingOutsidePadStaysUnconnected() {
    RoutingBoard board = read(dsnWithWireEndingAt(P2_CENTER_X - 400, P2_CENTER_Y));

    assertEquals(1, incompletes(board));
    assertEquals(1, board.getTraces().size());
  }

  @Test
  void wireEndingInsideViaOffItsCentreIsConnected() {
    String via = "(via V 10000 -10000 (net N1))";
    RoutingBoard board = read(dsn(wireFromP1To(10000 - 3, -10000 + 2) + via, FAR_NEIGHBOUR_Y));

    Pin pinP1 =
        board.getPins().stream()
            .filter(p -> "P1".equals(board.components.get(p.getComponentId()).name))
            .findFirst()
            .orElseThrow();
    assertTrue(
        pinP1.getConnectedSet(pinP1.getNetNumber(0)).stream().anyMatch(Via.class::isInstance),
        "a wire end inside the via pad must connect to the via");
    assertEquals(0, clearanceViolations(board));
  }

  /**
   * Pad Q of net N2 is 50 um below P2, closer than the 200 um clearance. The wire end near the top
   * of P2 keeps its clearance to Q, but a stub to the P2 centre would not, so the wire is left as
   * the design drew it.
   */
  @Test
  void stubThatWouldBreakClearanceIsLeftOut() {
    int neighbourY = P2_CENTER_Y - 450;
    RoutingBoard board = read(dsn(wireFromP1To(P2_CENTER_X, P2_CENTER_Y + 150), neighbourY));

    assertEquals(1, incompletes(board));
    assertEquals(1, board.getTraces().size());
    assertTrue(
        new DesignRulesChecker(board, null)
            .getAllClearanceViolations().stream()
                .noneMatch(v -> v.firstItem instanceof Trace || v.secondItem instanceof Trace),
        "the import must not add wire clearance violations");
  }
}
