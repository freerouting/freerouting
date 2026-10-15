package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.BoardReadResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests that footprints and padstacks differing only in case stay separate and don't overwrite each
 * other.
 */
class LibraryNameCaseTest {

  @Test
  void imagesAndPadstacksWhoseNamesDifferOnlyInCaseStaySeparate() {
    // eeg_brainboardv2 has footprints "SOT23-5" and "sot23-5"; both components got the first one's
    // pads (freerouting.net / Erik Gavriluk).
    String dsn =
        """
        (pcb case (resolution um 10)
          (structure (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 20000 20000)) (via V)
            (rule (width 200) (clearance 200)))
          (placement (component "SOT23-5" (place A 5000 5000 front 0)) (component "sot23-5" (place B 15000 5000 front 0)))
          (library (image "SOT23-5" (pin p 1 0 1270)) (image "sot23-5" (pin P 1 0 1056.64))
            (padstack p (shape (rect top -254 -381 254 381)))
            (padstack P (shape (rect top -299.72 -500.38 299.72 500.38)))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600))))
          (network (net N (pins A-1 B-1))))
        """;

    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null, "test");
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    Map<String, Pin> pinsByComponent =
        board.getPins().stream()
            .collect(Collectors.toMap(p -> board.components.get(p.getComponentId()).name, p -> p));

    assertEquals(
        new IntBox(-2540 + 50000, -3810 + 62700, 2540 + 50000, 3810 + 62700).toString(),
        pinsByComponent.get("A").getShape(0).boundingBox().toString());
    assertEquals(
        new IntBox(-2997 + 150000, -5004 + 60566, 2997 + 150000, 5004 + 60566).toString(),
        pinsByComponent.get("B").getShape(0).boundingBox().toString());
  }
}
