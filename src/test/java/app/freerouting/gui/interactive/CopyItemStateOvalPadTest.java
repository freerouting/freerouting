package app.freerouting.gui.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.library.PadOval;
import app.freerouting.core.library.Padstack;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopyItemStateOvalPadTest {

  @Test
  void changePadstackLayersPreservesOvalMetadataWithLayerOffset() {
    String dsn =
        """
        (pcb copy-oval-pad
          (parser (string_quote ") (space_in_quoted_tokens on) (host_cad "hand") (host_version "1"))
          (resolution um 10)
          (unit um)
          (structure
            (layer Top (type signal) (property (index 0)))
            (layer Bottom (type signal) (property (index 1)))
            (boundary (rect pcb -5000 -5000 5000 5000))
            (rule (width 250) (clearance 200))
          )
          (placement
            (component OVAL (place O1 0 0 front 0))
          )
          (library
            (image OVAL (pin Oval 1 0 0))
            (padstack Oval (shape (path Top 1500 -750 0 750 0)) (attach off))
          )
          (network
            (net A (pins O1-1))
          )
          (wiring)
        )
        """;
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    Padstack oldPadstack = board.library.padstacks.get("Oval");
    assertNotNull(oldPadstack);
    PadOval topOval = oldPadstack.getOval(0);
    assertNotNull(topOval);
    assertNull(oldPadstack.getOval(1));

    Map<Padstack, Padstack> padstackPairs = new HashMap<>();
    Padstack shifted = CopyItemState.changePadstackLayers(oldPadstack, 1, board, padstackPairs);

    assertNull(shifted.getOval(0));
    assertEquals(topOval, shifted.getOval(1));
  }
}
