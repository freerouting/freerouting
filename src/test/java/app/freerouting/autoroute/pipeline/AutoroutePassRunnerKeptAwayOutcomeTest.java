package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.StoppableThread;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoroutePassRunnerKeptAwayOutcomeTest {

  private static final String DSN =
      """
      (pcb outcome (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 5095 4751 front 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off)))
        (network (net N1 (pins A-1)) (net N2 (pins B-1))))
      """;

  private static RoutingBoard loadBoard() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "test");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static class IdleThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }

  @Test
  void relationOnlyResolvesWhenNetHasNoRemainingIncompletes() {
    RoutingBoard board = loadBoard();
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    BatchAutorouter router =
        new BatchAutorouter(
            new IdleThread(), board, settings, true, true, settings.getStartRipupCosts(), 500);

    int net1 = 1;
    int net2 = 2;

    // Net 1 ripped net 2 twice -> net 2 is kept away from net 1
    router.ripupMemory.recordRipupOfNets(net1, List.of(net2), 1);
    router.ripupMemory.recordRipupOfNets(net1, List.of(net2), 2);
    assertTrue(router.ripupMemory.isKeptAway(net2));
    assertEquals(Set.of(net1), router.ripupMemory.netsNotToRip(net2));

    AutoroutePassRunner runner = new AutoroutePassRunner(router);
    RouterCounters counters = new RouterCounters();

    // Pass 3: Net 2 was attempted, but incompleteNets still contains net 2 (e.g. multi-pin net had
    // a connection fail)
    runner.resolveKeptAwayOutcomes(Set.of(net2), Set.of(net2), counters, 3);

    // Net 2 is STILL kept away from net 1 because it did not fully complete
    assertTrue(router.ripupMemory.isKeptAway(net2));
    assertEquals(Set.of(net1), router.ripupMemory.netsNotToRip(net2));

    // Pass 4: Net 2 was attempted, and no incompletes remain for net 2
    runner.resolveKeptAwayOutcomes(Set.of(net2), Set.of(), counters, 4);

    // Now net 2 is resolved and no longer kept away
    assertFalse(router.ripupMemory.isKeptAway(net2));
    assertEquals(0, router.ripupMemory.activeCount());
  }
}
