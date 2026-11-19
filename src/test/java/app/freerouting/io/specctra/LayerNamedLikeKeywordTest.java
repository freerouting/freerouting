package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.io.BoardReadResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * A copper layer may be named like a Specctra keyword: KiCad lets the user call an inner layer
 * {@code Signal}, and boards with the stack {@code F.Cu GND Signal B.Cu} are published. The DSN
 * scanner matches keywords regardless of case, so {@code (polygon Signal …)} used to be read as the
 * {@code signal} layer type (every signal layer): a plane on that layer was rejected and the board
 * did not load, and a keepout on it silently covered every signal layer.
 */
class LayerNamedLikeKeywordTest {

  private static BoardReadResult read(String dsn) {
    return DsnReader.readBoard(
        new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
  }

  private static RoutingBoard load(String dsn) {
    BoardReadResult result = read(dsn);
    assertInstanceOf(BoardReadResult.Success.class, result, "the board must load");
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  /** Three signal layers, the middle one named {@code middleLayerName}, and one keepout. */
  private static String dsnWithKeepout(String middleLayerName, String keepoutLayer) {
    return "(pcb test\n"
        + "  (parser (string_quote \") (host_cad \"KiCad's Pcbnew\"))\n"
        + "  (resolution um 10)\n"
        + "  (unit um)\n"
        + "  (structure\n"
        + "    (layer F.Cu (type signal) (property (index 0)))\n"
        + "    (layer "
        + middleLayerName
        + " (type signal) (property (index 1)))\n"
        + "    (layer B.Cu (type signal) (property (index 2)))\n"
        + "    (boundary (path pcb 0 0 0 20000 0 20000 20000 0 20000 0 0))\n"
        + "    (keepout \"\" (polygon "
        + keepoutLayer
        + " 0 5000 5000 15000 5000 15000 15000 5000 15000))\n"
        + "    (via \"Via[0-2]_800:400_um\")\n"
        + "    (rule (width 200) (clearance 200))\n"
        + "  )\n"
        + "  (placement)\n"
        + "  (library\n"
        + "    (padstack \"Via[0-2]_800:400_um\"\n"
        + "      (shape (circle F.Cu 800))\n"
        + "      (shape (circle "
        + middleLayerName
        + " 800))\n"
        + "      (shape (circle B.Cu 800))\n"
        + "      (attach off)\n"
        + "    )\n"
        + "  )\n"
        + "  (network)\n"
        + "  (wiring)\n"
        + ")\n";
  }

  private static Set<Integer> keepoutLayers(RoutingBoard board) {
    Set<Integer> layers = new TreeSet<>();
    for (Item item : board.getItems()) {
      if (item.getClass() == ObstacleArea.class) {
        layers.add(item.firstLayer());
      }
    }
    return layers;
  }

  @Test
  void planesOnLayersNamedGndAndSignalLoadOnThoseLayers() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Layer-named-signal-garland-4layer.dsn");
    assertNotNull(board, "a plane on the layer named Signal must not stop the board from loading");

    Set<String> planes = new TreeSet<>();
    for (ConductionArea area : board.getConductionAreas()) {
      planes.add(
          board.layerStructure.layers[area.getLayer()].name
              + ":"
              + board.rules.nets.get(area.getNetNumber(0)).name);
    }
    assertEquals(Set.of("GND:GND", "Signal:+3V3"), planes);
  }

  @Test
  void keepoutOnLayerNamedSignalStaysOnThatLayer() {
    assertEquals(Set.of(1), keepoutLayers(load(dsnWithKeepout("Signal", "Signal"))));
  }

  @Test
  void keepoutWithTheKeywordSpellingStillCoversEverySignalLayer() {
    assertEquals(Set.of(0, 1, 2), keepoutLayers(load(dsnWithKeepout("Signal", "signal"))));
  }

  @Test
  void keepoutOnLayerNamedPcbStaysOnThatLayer() {
    assertEquals(Set.of(1), keepoutLayers(load(dsnWithKeepout("PCB", "PCB"))));
    // Exact lowercase "pcb" still resolves to Layer.PCB, which keepout rejects as non-layer.
    assertInstanceOf(BoardReadResult.ParseError.class, read(dsnWithKeepout("PCB", "pcb")));
  }

  @Test
  void keepoutWithCapitalizedKeywordFallsBackWhenNoSuchLayerExists() {
    assertEquals(Set.of(0, 1, 2), keepoutLayers(load(dsnWithKeepout("In1.Cu", "Signal"))));
    // Without a layer named "PCB", capitalized "PCB" falls back to Layer.PCB (rejected in keepout).
    assertInstanceOf(BoardReadResult.ParseError.class, read(dsnWithKeepout("In1.Cu", "PCB")));
  }
}
