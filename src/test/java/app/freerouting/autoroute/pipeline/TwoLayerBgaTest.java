package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class TwoLayerBgaTest {

  private static String dsn(
      int layers, String pad, int columns, int rows, BiPredicate<Integer, Integer> keep) {
    StringBuilder sb = new StringBuilder();
    sb.append("(pcb grid (resolution um 1) (structure ");
    for (int i = 0; i < layers; i++) {
      sb.append(
          i == 0
              ? "(layer top (type signal)) "
              : i == layers - 1
                  ? "(layer bottom (type signal)) "
                  : "(layer in" + i + " (type signal)) ");
    }
    sb.append("(boundary (rect pcb 0 0 20000 20000)) (via V) (rule (width 100) (clearance 100))) ");
    sb.append("(placement (component G (place U1 5000 5000 front 0))) (library (image G ");
    int n = 0;
    for (int c = 0; c < columns; c++) {
      for (int r = 0; r < rows; r++) {
        if (keep == null || keep.test(c, r)) {
          sb.append("(pin B ")
              .append(++n)
              .append(' ')
              .append(c * 800)
              .append(' ')
              .append(r * 800)
              .append(") ");
        }
      }
    }
    sb.append(") (padstack B (shape ").append(pad).append(")) ");
    sb.append("(padstack V (shape (circle top 400)) (shape (circle bottom 400)) (attach off))) ");
    sb.append("(network (net N (pins U1-1 U1-").append(n).append("))))");
    return sb.toString();
  }

  private static RoutingBoard load(String dsn) {
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static RouterSettings settings(String... arguments) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(arguments)).merge();
  }

  @Test
  void byDefaultTwoLayersWithABallGridStartWithoutTheFanout() {
    RouterSettings settings = settings();
    assertEquals("no_fanout", settings.fanout.twoLayerBga);
    String line = TwoLayerBga.apply(settings, load(dsn(2, "(circle top 400)", 4, 4, null)));
    assertNotNull(line);
    assertTrue(line.contains("U1"));
    assertFalse(settings.fanout.enabled);
    assertEquals("eager", settings.fanout.strategy);
  }

  @Test
  void offKeepsTheFanout() {
    RouterSettings settings = settings("--router.fanout.two_layer_bga=off");
    assertNull(TwoLayerBga.apply(settings, load(dsn(2, "(circle top 400)", 4, 4, null))));
    assertTrue(settings.fanout.enabled);
    assertEquals("eager", settings.fanout.strategy);
  }

  @Test
  void directFirstStartsWithTheDirectFirstFanout() {
    RouterSettings settings = settings("--router.fanout.two_layer_bga=direct_first");
    assertNotNull(TwoLayerBga.apply(settings, load(dsn(2, "(circle top 400)", 4, 4, null))));
    assertTrue(settings.fanout.enabled);
    assertEquals("direct_first", settings.fanout.strategy);
    assertTrue(settings.usesDirectPinRouting());
  }

  @Test
  void aBallGridWithDepopulatedBallsCounts() {
    // A 5 x 5 array without its centre ball: 24 balls.
    RouterSettings settings = settings("--router.fanout.two_layer_bga=no_fanout");
    assertNotNull(
        TwoLayerBga.apply(
            settings, load(dsn(2, "(circle top 400)", 5, 5, (c, r) -> !(c == 2 && r == 2)))));
    assertFalse(settings.fanout.enabled);
  }

  @Test
  void fourLayersRectangularPadsAndPerimeterPartsKeepTheFanout() {
    String[] dsns =
        new String[] {
          dsn(4, "(circle top 400)", 4, 4, null),
          dsn(2, "(rect top -200 -200 200 200)", 4, 4, null),
          // A QFP-64's perimeter (16 pins a side, the corners empty), round pads.
          dsn(2, "(circle top 400)", 18, 18, (c, r) -> (c == 0 || c == 17) != (r == 0 || r == 17))
        };
    for (String dsnContent : dsns) {
      RouterSettings settings = settings("--router.fanout.two_layer_bga=no_fanout");
      assertNull(TwoLayerBga.apply(settings, load(dsnContent)));
      assertTrue(settings.fanout.enabled);
    }
  }

  @Test
  void anExplicitFanoutSettingIsLeftAlone() {
    RouterSettings direct =
        settings(
            "--router.fanout.two_layer_bga=no_fanout", "--router.fanout.strategy=direct_first");
    assertNull(TwoLayerBga.apply(direct, load(dsn(2, "(circle top 400)", 4, 4, null))));
    assertTrue(direct.fanout.enabled);

    RouterSettings off =
        settings("--router.fanout.two_layer_bga=direct_first", "--router.fanout.enabled=false");
    assertNull(TwoLayerBga.apply(off, load(dsn(2, "(circle top 400)", 4, 4, null))));
    assertEquals("eager", off.fanout.strategy);
  }

  @Test
  void anUnknownValueIsRejected() {
    RouterSettings settings = settings("--router.fanout.two_layer_bga=sometimes");
    assertThrows(
        IllegalArgumentException.class,
        () -> TwoLayerBga.apply(settings, load(dsn(2, "(circle top 400)", 4, 4, null))));
  }
}
