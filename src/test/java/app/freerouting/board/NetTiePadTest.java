package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Erik Gavriluk (freerouting.net): A netless pad whose copper overlaps pads of several nets is a
 * net tie and carries all of them (FR-102).
 */
class NetTiePadTest {

  private static final String DSN =
      """
      (pcb nettie (resolution um 1) (unit um)
        (structure (layer F.Cu (type signal)) (layer B.Cu (type signal))
          (boundary (rect pcb 0 0 40000 20000)) (via V)
          (rule (width 508) (clearance 254) (clearance 63.5 (type smd_smd))))
        (placement
          (component T (place R3 5000 5000 front 0))
          (component P (place A1 20000 5000 front 0) (place B1 20000 12000 front 0)))
        (library
          (image T (pin R 2 533.4 0) (pin R 1 -533.4 0) (pin S @1 0 0))
          (image P (pin Q 1 0 0))
          (padstack R (shape (rect F.Cu -254 -279.4 254 279.4)) (attach off))
          (padstack S (shape (rect F.Cu -381 -127 381 127)) (attach off))
          (padstack Q (shape (rect F.Cu -500 -500 500 500)) (attach off))
          (padstack V (shape (circle F.Cu 600)) (shape (circle B.Cu 600)) (attach off)))
        (network (net GND (pins R3-1 A1-1)) (net /SOURCE (pins R3-2 B1-1))))
      """;

  private static RoutingBoard load() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "nettie");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static Pin pin(RoutingBoard board, String component, String name) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name)
                    && name.equals(p.name()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void bridgingPadBecomesTiePinOfBothNets() {
    RoutingBoard board = load();
    Pin pad1 = pin(board, "R3", "1");
    Pin pad2 = pin(board, "R3", "2");
    Pin tie = pin(board, "R3", "@1");
    int gnd = pad1.getNetNumber(0);
    int source = pad2.getNetNumber(0);

    assertEquals(0, tie.netCount());
    assertEquals(2, new DesignRulesChecker(board, null).getAllClearanceViolations().size());

    int[] tiePads = new int[1];
    int assigned = board.connectNetlessPadsToTheirFootprintsNet(tiePads);
    assertEquals(1, assigned);
    assertEquals(1, tiePads[0]);
    assertEquals(2, tie.netCount());
    assertTrue(tie.containsNet(gnd));
    assertTrue(tie.containsNet(source));

    // The tie's copper is both nets' copper: no violation with either pad, and the pads stay
    // foreign to each other.
    assertFalse(tie.isObstacle(pad1));
    assertFalse(tie.isObstacle(pad2));
    assertTrue(pad1.isObstacle(pad2));
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());

    // Each net reaches its pad and the tie, not the other net's pad: the nets stay two nets.
    Set<Item> gndSet = pad1.getConnectedSet(gnd);
    Set<Item> sourceSet = pad2.getConnectedSet(source);
    assertTrue(gndSet.contains(tie));
    assertFalse(gndSet.contains(pad2));
    assertTrue(sourceSet.contains(tie));
    assertFalse(sourceSet.contains(pad1));

    assertEquals(0, board.connectNetlessPadsToTheirFootprintsNet(tiePads));
    assertEquals(0, tiePads[0]);
  }
}
