package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaEscapeCommitter;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeSolver;
import app.freerouting.autoroute.fanout.BgaEscapeSolver.PinEscapeProposal;
import app.freerouting.autoroute.fanout.BgaFootprintDetector;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BgaEscapeHardTest extends RoutingFixtureTest {

  @Test
  void testBgaEscapeHardMultiLayerDistributionAndZeroDrc() throws Exception {
    System.setProperty("freerouting.logging.console.level", "INFO");
    FRLogger.granularTraceEnabled = false;

    RoutingBoard board = DsnTestFixtures.loadBoard("BGA_Escape_Test_HARD_v2.dsn");
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.fanout.bgaEscapeEnabled = true;
    settings.fanout.bgaEscapeMinPins = 64;

    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, settings, null);
    assertEquals(1, bgas.size(), "Must detect 1 BGA (Lattice caBGA-381)");

    BgaEscapeDescriptor bga = bgas.get(0);
    assertEquals(381, bga.pins().size(), "caBGA-381 has 381 pins");

    List<PinEscapeProposal> proposals = BgaEscapeSolver.solve(board, bga, null);
    assertTrue(proposals.size() > 0, "CPU wavefront solver must produce proposals");

    BgaEscapeCommitter.CommitResult result = BgaEscapeCommitter.commit(board, bga, proposals);
    assertTrue(result.pinsCommitted() >= 350, "Must successfully commit at least 350 BGA pins");
    assertTrue(result.drcRejections() <= 31, "At most 31 DRC rejections on congested inner rings");

    // Verify multi-layer escape distribution: check that traces exist on multiple layers
    Map<Integer, Integer> tracesPerLayer = new HashMap<>();
    for (Trace trace : board.getTraces()) {
      tracesPerLayer.merge(trace.getLayer(), 1, Integer::sum);
    }

    assertTrue(
        tracesPerLayer.size() >= 4,
        "Escape traces must utilize at least 4 signal layers across the 6-layer stackup (found "
            + tracesPerLayer.keySet()
            + ")");

    // Authoritative full DRC clearance check
    List<?> violations =
        new java.util.ArrayList<>(new DesignRulesChecker(board, null).getAllClearanceViolations());
    assertEquals(0, violations.size(), "Full DRC must have exactly 0 clearance violations");
  }
}
