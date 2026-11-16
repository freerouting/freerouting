package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import app.freerouting.autoroute.accelerator.AcceleratorRegistry;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * With GPU acceleration enabled but no usable device, the escape pre-pass runs the same kernels on
 * the CPU instead of being skipped, so a board gets the same escapes with or without a GPU.
 */
class GpuBgaEscapeCpuFallbackTest {

  @AfterEach
  void resetRegistry() {
    AcceleratorRegistry.reset();
  }

  private static RouterSettings settings(String devicePreference, boolean enabled, int layers) {
    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuAccelerationEnabled = enabled;
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuDevicePreference = devicePreference;
    settings.setLayerCount(layers);
    return settings;
  }

  @Test
  void enabledWithoutDeviceRunsTheKernelsOnTheCpu() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("BGA_Escape_Test_HARD_v2.dsn");
    // "tornadovm" without the TornadoVM runtime on the class path: enabled, no device.
    RouterSettings settings = settings("tornadovm", true, board.getLayerCount());
    AcceleratorRegistry.reset();
    assumeFalse(
        AcceleratorRegistry.discover(settings.fanout).isAvailable(),
        "a TornadoVM device is available, so this run does not exercise the fallback");

    BgaEscapeDescriptor bga = BgaFootprintDetector.detect(board, settings, null).get(0);
    List<PinEscapeProposal> fallback = GpuBgaEscapeSolver.solve(board, bga, settings);
    List<PinEscapeProposal> cpu = GpuBgaEscapeSolver.solveOnCpu(board, bga, settings, null);

    assertFalse(fallback.isEmpty(), "the pre-pass must not be skipped");
    assertEquals(cpu.size(), fallback.size());
    for (int i = 0; i < cpu.size(); i++) {
      PinEscapeProposal a = fallback.get(i);
      PinEscapeProposal b = cpu.get(i);
      assertEquals(b.escaped(), a.escaped(), "pin " + i);
      assertEquals(b.cost(), a.cost(), "pin " + i);
      assertEquals(b.targetLayer(), a.targetLayer(), "pin " + i);
      assertEquals(b.viaCenter() == null, a.viaCenter() == null, "pin " + i);
      if (b.viaCenter() != null) {
        assertEquals(b.viaCenter().x, a.viaCenter().x, "pin " + i);
        assertEquals(b.viaCenter().y, a.viaCenter().y, "pin " + i);
      }
    }
    assertTrue(cpu.stream().filter(PinEscapeProposal::escaped).count() > 300);
  }

  @Test
  void disabledStillSkipsThePrePass() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("BGA_Escape_Test_HARD_v2.dsn");
    RouterSettings settings = settings("tornadovm", false, board.getLayerCount());
    BgaEscapeDescriptor bga = BgaFootprintDetector.detect(board, settings, null).get(0);

    assertTrue(GpuBgaEscapeSolver.solve(board, bga, settings).isEmpty());
  }
}
