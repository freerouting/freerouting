package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Erik Gavriluk (freerouting.net): A netless pad whose copper overlaps a netted pad takes that
 * pad's net (FR-102); on KiCad boards netless pads stay netless unless fix_kicad_footprints is set.
 */
class NetlessPadNetTest {

  private static final String DSN =
      """
      (pcb netless (resolution um 1)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 40000 20000)) (via V) (rule (width 200) (clearance 200)))
        (placement (component C (place C1 5000 5000 front 0)) (component D (place D1 30000 5000 front 0))
          (component E (place E1 3200 5000 front 0)))
        (library
          (image C (pin P 1 0 0) (pin S M 300 0) (pin P 2 6000 0) (pin P 3 9000 0) (pin S X 7500 0))
          (image D (pin S Q 0 0))
          (image E (pin S Z 0 0))
          (padstack P (shape (rect top -1000 -600 1000 600)))
          (padstack S (shape (rect top -1000 -300 1000 300)))
          (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
        (network (net A (pins C1-1 C1-3)) (net B (pins C1-2))))
      """;

  private static RoutingBoard load() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "netless");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static Pin pin(RoutingBoard board, String component, int pinIndex) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name)
                    && p.getPinIndex() == pinIndex)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void netlessPadTakesOverlappingPadNet() {
    RoutingBoard board = load();
    Pin m = pin(board, "C1", 1);
    Pin x = pin(board, "C1", 4);
    Pin q = pin(board, "D1", 0);
    Pin z = pin(board, "E1", 0);

    assertEquals(0, m.netCount());
    int[] tiePads = new int[1];
    assertEquals(3, board.connectNetlessPadsToTheirFootprintsNet(tiePads));
    assertEquals(1, tiePads[0]);
    assertEquals(pin(board, "C1", 0).getNetNumber(0), m.getNetNumber(0));
    assertEquals(pin(board, "C1", 0).getNetNumber(0), z.getNetNumber(0));
    assertEquals(2, x.netCount());
    assertTrue(x.containsNet(pin(board, "C1", 2).getNetNumber(0)));
    assertTrue(x.containsNet(pin(board, "C1", 3).getNetNumber(0)));
    assertEquals(0, q.netCount());
    assertEquals(0, board.connectNetlessPadsToTheirFootprintsNet(tiePads));
    assertFalse(m.isObstacle(pin(board, "C1", 0)));
  }

  @ParameterizedTest
  @CsvSource({"false, 0", "true, 1"})
  void kicadBoardsNetlessPadsTakeNetOnlyWhenFootprintsAreFixed(boolean fix, int assigned) {
    RoutingBoard board = load();
    int[] tiePads = new int[1];
    assertEquals(assigned, board.connectNetlessPads(true, fix, tiePads));
    assertEquals(0, tiePads[0]);
    assertEquals(assigned, pin(board, "C1", 1).netCount());
    assertEquals(0, pin(board, "C1", 4).netCount());
    assertEquals(0, pin(board, "E1", 0).netCount());
  }
}
