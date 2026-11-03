package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.StoppableThread;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pull-tight after routing must give the same traces however fast the machine runs. */
class PullTightMachineLoadTest {

  /**
   * A zigzag connection between two pins, made of two traces that meet where a dangling tail
   * branches off. Removing the tail joins the two traces, which marks the junction for pull-tight.
   */
  private static final String DSN =
      """
      (pcb zigzag (resolution um 10) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 20000 10000))
          (via V) (rule (width 200) (clearance 200)))
        (placement (component C (place A 2000 5000 front 0) (place B 18000 5000 front 0)))
        (library (image C (pin P 1 0 0))
          (padstack P (shape (circle top 800)) (shape (circle bottom 800)) (attach off))
          (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
        (network (net N (pins A-1 B-1)))
        (wiring
          (wire (path top 200 2000 5000 5000 8000 8000 5000 10000 7000) (net N))
          (wire (path top 200 10000 7000 11000 8000 14000 5000 18000 5000) (net N))
          (wire (path top 200 10000 7000 10000 9500) (net N))))
      """;

  /** A pause of over a second, as when a loaded machine deschedules the thread or the GC runs. */
  private static final long STALL_MILLIS = 1500;

  @Test
  void removeTailsPullsTightTheSameWhenTheThreadStalls() {
    List<String> unloaded = tracesAfterRemoveTails(new IdleThread());
    List<String> stalled = tracesAfterRemoveTails(new OnceStallingThread());

    assertEquals(1, unloaded.size(), "the tail must be removed, the traces joined: " + unloaded);
    assertTrue(unloaded.get(0).split(";").length < 4, "the zigzag must be pulled tight");
    assertEquals(unloaded, stalled);
  }

  private static List<String> tracesAfterRemoveTails(StoppableThread thread) {
    RoutingBoard board = loadBoard();
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    BatchAutorouter router =
        new BatchAutorouter(
            thread, board, settings, true, true, settings.getStartRipupCosts(), 500);

    router.removeTails(Item.StopConnectionOption.NONE);

    return board.getTraces().stream()
        .sorted(Comparator.comparingInt(Trace::getId))
        .map(PullTightMachineLoadTest::corners)
        .toList();
  }

  private static String corners(Trace trace) {
    return String.join(
        ";",
        Arrays.stream(((PolylineTrace) trace).polyline().corners()).map(Object::toString).toList());
  }

  private static RoutingBoard loadBoard() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "zigzag");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static class IdleThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }

  /** Never requests a stop, but stalls the first time the algorithm asks. */
  private static final class OnceStallingThread extends IdleThread {
    private boolean stalled;

    @Override
    public synchronized boolean isStopRequested() {
      if (!this.stalled) {
        this.stalled = true;
        try {
          Thread.sleep(STALL_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return super.isStopRequested();
    }
  }
}
