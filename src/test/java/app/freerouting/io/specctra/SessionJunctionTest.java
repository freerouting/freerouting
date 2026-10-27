package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.Net;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SessionJunctionTest {

  private static final String TINYBOT =
      """
      (wire (path F.Cu 1500 1474104 -1042631 1474104 -1024246 1535834 -962516 1535834 -959107.3484971568))
      (wire (path F.Cu 1500 1535833.9404185733 -959107.3379941758 1535762 -959094.6563769294))
      (wire (path F.Cu 1500 1535834 -959107.3484971568 1535834 -959107 1535833.9404185733 -959107.3379941758))
      (wire (path F.Cu 1500 1547813 -961219 1535834 -959107.3484971568))
      (wire (path F.Cu 1500 1535762 -959094.6563769294 1535762 -952890 1527173 -944301 1476931 -944301 1468750 -936120))
      """;

  private static final String TCKB =
      """
      (wire (path B.Cu 3048 2632240 -1558476 2631630.6248034863 -1558367.375196514))
      (wire (path B.Cu 2032 2631630.6248034863 -1558367.375196514 2631020.4008949995 -1558258.5991050005))
      (wire (path B.Cu 1500 2631020.4008949995 -1558258.5991050005 2632084 -1557195 2632084 -1545161 2623685 -1536762 2453337 -1536762 2450143 -1533568 2434744 -1533568 2395926 -1494750 2387681 -1494750))
      """;

  private static String um(long boardUnits) {
    return String.format(Locale.US, "%.1f", boardUnits / 10.0);
  }

  private static RoutingBoard createBoard(long ax, long ay, long bx, long by) {
    String dsn =
        """
        (pcb junction
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal)) (layer B.Cu (type signal))
            (boundary (rect pcb 100000 -200000 300000 -50000))
            (via V)
            (rule (width 150) (clearance 200)))
          (placement
            (component P (place A %s %s front 0) (place B %s %s front 0)))
          (library
            (image P (pin R 1 0 0))
            (padstack R (shape (rect F.Cu -400 -400 400 400)) (shape (rect B.Cu -400 -400 400 400)))
            (padstack V (shape (circle F.Cu 60)) (shape (circle B.Cu 60))))
          (network (net GND (pins A-1 B-1))))
        """
            .formatted(um(ax), um(ay), um(bx), um(by));

    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    if (result instanceof BoardReadResult.Success success) {
      return (RoutingBoard) success.board();
    }
    throw new IllegalStateException("Failed to read test board: " + result);
  }

  private static SesImportSummary loadSession(RoutingBoard board, String wires) throws Exception {
    String ses =
        """
        (session junction
          (base_design junction)
          (placement (resolution um 10))
          (was_is)
          (routes
            (resolution um 10)
            (library_out)
            (network_out
              (net GND
                %s))))
        """
            .formatted(wires);
    return SesReader.read(new ByteArrayInputStream(ses.getBytes(StandardCharsets.UTF_8)), board);
  }

  static Stream<Arguments> junctions() {
    return Stream.of(
        Arguments.of("tinybot GND", TINYBOT, 1474104L, -1042631L, 1468750L, -936120L, 5),
        Arguments.of("TCKB LED_COL_11", TCKB, 2632240L, -1558476L, 2387681L, -1494750L, 3));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("junctions")
  void theWiresOfAJunctionReloadJoined(
      String name, String wires, long ax, long ay, long bx, long by, int wireCount)
      throws Exception {
    RoutingBoard board = createBoard(ax, ay, bx, by);
    SesImportSummary summary = loadSession(board, wires);
    assertEquals(wireCount, summary.wiresImported());
    assertEquals(0, summary.errorsEncountered());

    Net net = board.rules.nets.get("GND", 1);
    Collection<Collection<Item>> sets = board.getConnectedSets(net.netNumber);
    assertEquals(1, sets.size(), name + ": wires should reload in a single connected set");

    Collection<Item> singleSet = sets.iterator().next();
    long pinCount = singleSet.stream().filter(Pin.class::isInstance).count();
    assertEquals(2, pinCount);
  }
}
